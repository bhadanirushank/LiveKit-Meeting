package com.jbcoder.meeting.feature.createmeeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.jbcoder.meeting.data.meeting.MeetingError
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.data.meeting.HostSessionStore
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoff
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import com.jbcoder.meeting.network.CreateMeetingRequest
import com.jbcoder.meeting.network.HostExchangeRequest
import com.jbcoder.meeting.storage.InstallationIdProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface CreateMeetingUiState {
    object Idle : CreateMeetingUiState
    object Loading : CreateMeetingUiState
    object Success : CreateMeetingUiState
    data class Error(val message: String) : CreateMeetingUiState
}

data class CreateMeetingFormState(
    val displayName: String = ""
)

@HiltViewModel
class CreateMeetingViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val hostSessionStore: HostSessionStore,
    private val roomConnectionHandoffStore: RoomConnectionHandoffStore,
    private val installationIdProvider: InstallationIdProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateMeetingUiState>(CreateMeetingUiState.Idle)
    val uiState: StateFlow<CreateMeetingUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(CreateMeetingFormState())
    val formState: StateFlow<CreateMeetingFormState> = _formState.asStateFlow()

    private var idempotencyKey = UUID.randomUUID().toString()

    private var lastAttemptedRequest: CreateMeetingRequest? = null

    fun updateDisplayName(name: String) {
        _formState.update { it.copy(displayName = name) }
    }
    
    fun resetError() {
        if (_uiState.value is CreateMeetingUiState.Error) {
            _uiState.value = CreateMeetingUiState.Idle
        }
    }

    fun submit() {
        if (_uiState.value is CreateMeetingUiState.Loading) {
            return
        }

        val form = _formState.value
        val name = form.displayName.trim()
        if (name.isBlank() || name.length > 50) {
            _uiState.value = CreateMeetingUiState.Error("Display name must be between 1 and 50 characters")
            return
        }

        _uiState.value = CreateMeetingUiState.Loading

        val currentPayload = CreateMeetingRequest(
            title = "$name's Meeting",
            passcode = null,
            waitingRoomEnabled = false,
            joinBeforeHostEnabled = true,
            maximumParticipants = 100,
            idempotencyKey = ""
        )

        val lastReq = lastAttemptedRequest
        if (lastReq != null) {
            if (lastReq.title != currentPayload.title ||
                lastReq.passcode != currentPayload.passcode ||
                lastReq.waitingRoomEnabled != currentPayload.waitingRoomEnabled ||
                lastReq.joinBeforeHostEnabled != currentPayload.joinBeforeHostEnabled ||
                lastReq.maximumParticipants != currentPayload.maximumParticipants) {
                idempotencyKey = UUID.randomUUID().toString()
            }
        }

        val request = currentPayload.copy(idempotencyKey = idempotencyKey)
        lastAttemptedRequest = request

        viewModelScope.launch {
            val installationId = installationIdProvider.getInstallationId()
            val result = repository.createMeeting(request, installationId)

            if (result.isSuccess) {
                val response = result.getOrThrow()
                
                // 2. Exchange host session
                val exchangeReq = HostExchangeRequest(response.publicMeetingCode, response.hostSecret)
                val exchangeResult = repository.exchangeHostSession(exchangeReq, installationId)
                if (exchangeResult.isFailure) {
                    _uiState.value = CreateMeetingUiState.Error(exchangeResult.exceptionOrNull()?.message ?: "Authentication failed")
                    return@launch
                }
                val exchangeResponse = exchangeResult.getOrThrow()
                hostSessionStore.setHostCredential(exchangeResponse.accessToken)

                // 3. Start meeting
                val startResult = repository.startMeeting(response.publicMeetingCode, installationId)
                if (startResult.isFailure) {
                    _uiState.value = CreateMeetingUiState.Error(startResult.exceptionOrNull()?.message ?: "Failed to start meeting")
                    return@launch
                }

                // 4. Get LiveKit token
                val tokenResult = repository.getHostLiveKitToken()
                if (tokenResult.isFailure) {
                    _uiState.value = CreateMeetingUiState.Error(tokenResult.exceptionOrNull()?.message ?: "Failed to get LiveKit token")
                    return@launch
                }
                
                val token = tokenResult.getOrThrow().token
                if (token == null) {
                    _uiState.value = CreateMeetingUiState.Error("LiveKit token missing")
                    return@launch
                }

                roomConnectionHandoffStore.setHandoff(
                    RoomConnectionHandoff.HostReady(
                        publicMeetingCode = response.publicMeetingCode,
                        livekitToken = token,
                        livekitRoomName = response.livekitRoomName,
                        displayName = name
                    )
                )

                // Generate a new idempotency key so next time they visit it's fresh
                idempotencyKey = UUID.randomUUID().toString()
                lastAttemptedRequest = null
                _uiState.value = CreateMeetingUiState.Success
            } else {
                val ex = result.exceptionOrNull()
                val msg = when (ex) {
                    is MeetingError.CreateFailed -> ex.detail
                    is MeetingError.Offline -> "You are offline."
                    is MeetingError.IdempotencyConflict -> "Meeting details changed after the previous attempt. Please try again."
                    is MeetingError.UnexpectedServerResponse -> "Server error: ${ex.message}"
                    else -> "Unknown error"
                }
                
                if (ex is MeetingError.IdempotencyConflict) {
                    idempotencyKey = UUID.randomUUID().toString()
                }
                
                _uiState.value = CreateMeetingUiState.Error(msg)
            }
        }
    }
}
