package com.jbcoder.meeting.feature.createmeeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoff
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoffStore
import com.jbcoder.meeting.data.meeting.MeetingError
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.network.CreateMeetingRequest
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
    val title: String = "",
    val maximumParticipants: String = "100",
    val waitingRoomEnabled: Boolean = true,
    val joinBeforeHostEnabled: Boolean = false,
    val passcode: String = ""
)

@HiltViewModel
class CreateMeetingViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val handoffStore: MeetingEntryHandoffStore,
    private val installationIdProvider: InstallationIdProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateMeetingUiState>(CreateMeetingUiState.Idle)
    val uiState: StateFlow<CreateMeetingUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(CreateMeetingFormState())
    val formState: StateFlow<CreateMeetingFormState> = _formState.asStateFlow()

    private var idempotencyKey = UUID.randomUUID().toString()

    private var lastAttemptedRequest: CreateMeetingRequest? = null

    fun updateTitle(title: String) {
        _formState.update { it.copy(title = title) }
    }

    fun updateMaximumParticipants(max: String) {
        _formState.update { it.copy(maximumParticipants = max) }
    }

    fun updateWaitingRoomEnabled(enabled: Boolean) {
        _formState.update { it.copy(waitingRoomEnabled = enabled) }
    }

    fun updateJoinBeforeHostEnabled(enabled: Boolean) {
        _formState.update { it.copy(joinBeforeHostEnabled = enabled) }
    }

    fun updatePasscode(passcode: String) {
        _formState.update { it.copy(passcode = passcode) }
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
        val title = form.title.trim()
        if (title.isBlank() || title.length > 200) {
            _uiState.value = CreateMeetingUiState.Error("Title must be between 1 and 200 characters")
            return
        }

        val maxParticipants = form.maximumParticipants.toIntOrNull()
        if (maxParticipants == null || maxParticipants !in 2..1000) {
            _uiState.value = CreateMeetingUiState.Error("Maximum participants must be between 2 and 1000")
            return
        }

        val pass = if (form.passcode.isNotBlank()) form.passcode.trim() else null
        if (pass != null && pass.length !in 4..20) {
            _uiState.value = CreateMeetingUiState.Error("Passcode must be between 4 and 20 characters")
            return
        }

        _uiState.value = CreateMeetingUiState.Loading

        val currentPayload = CreateMeetingRequest(
            title = title,
            passcode = pass,
            waitingRoomEnabled = form.waitingRoomEnabled,
            joinBeforeHostEnabled = form.joinBeforeHostEnabled,
            maximumParticipants = maxParticipants,
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
                handoffStore.setHandoff(
                    MeetingEntryHandoff.HostCreated(
                        publicMeetingCode = response.publicMeetingCode,
                        hostSecret = response.hostSecret,
                        livekitRoomName = response.livekitRoomName,
                        title = response.title,
                        waitingRoomEnabled = response.waitingRoomEnabled,
                        joinBeforeHostEnabled = response.joinBeforeHostEnabled,
                        maximumParticipants = response.maximumParticipants
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
