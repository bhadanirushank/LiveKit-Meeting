package com.jbcoder.meeting.feature.joinmeeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbcoder.meeting.data.meeting.MeetingError
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoff
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import com.jbcoder.meeting.network.JoinRequestDto
import com.jbcoder.meeting.network.SessionCoordinator
import com.jbcoder.meeting.network.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject

sealed interface JoinMeetingUiState {
    object Idle : JoinMeetingUiState
    object Loading : JoinMeetingUiState
    object Success : JoinMeetingUiState
    data class Error(val message: String) : JoinMeetingUiState
}

data class JoinMeetingFormState(
    val meetingCode: String = "",
    val displayName: String = ""
)

@HiltViewModel
class JoinMeetingViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val roomConnectionHandoffStore: RoomConnectionHandoffStore,
    private val sessionCoordinator: SessionCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow<JoinMeetingUiState>(JoinMeetingUiState.Idle)
    val uiState: StateFlow<JoinMeetingUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(JoinMeetingFormState())
    val formState: StateFlow<JoinMeetingFormState> = _formState.asStateFlow()

    fun updateMeetingCode(code: String) {
        _formState.update { it.copy(meetingCode = code) }
    }

    fun updateDisplayName(name: String) {
        _formState.update { it.copy(displayName = name) }
    }


    fun resetError() {
        if (_uiState.value is JoinMeetingUiState.Error) {
            _uiState.value = JoinMeetingUiState.Idle
        }
    }

    fun submit() {
        val form = _formState.value
        val code = form.meetingCode.trim() // strict whitespace rejection is for inside the code
        if (code.length != 10) {
            _uiState.value = JoinMeetingUiState.Error("Meeting code must be exactly 10 digits")
            return
        }

        val name = form.displayName.trim()
        if (name.isBlank() || name.length > 100) {
            _uiState.value = JoinMeetingUiState.Error("Display name must be between 1 and 100 characters")
            return
        }


        _uiState.value = JoinMeetingUiState.Loading

        viewModelScope.launch {
            if (sessionCoordinator.sessionState.value != SessionState.READY) {
                // Try initializing again if it's offline or errored
                sessionCoordinator.initializeSession()
            }
            if (sessionCoordinator.sessionState.value != SessionState.READY) {
                _uiState.value = JoinMeetingUiState.Error("Session not ready. Please check connection.")
                return@launch
            }

            val deviceSessionId = sessionCoordinator.getDeviceSessionId()
            if (deviceSessionId == null) {
                _uiState.value = JoinMeetingUiState.Error("Session missing.")
                return@launch
            }

            val request = JoinRequestDto(
                displayName = name,
                deviceSessionId = deviceSessionId,
                passcode = null
            )

            val result = repository.submitJoinRequest(code, request)

            if (result.isSuccess) {
                val response = result.getOrThrow()
                val reqId = response.requestId
                var currentStatus = response.status
                
                // Poll until admitted or rejected
                while (isActive && currentStatus != "ADMITTED" && currentStatus != "REJECTED" && currentStatus != "EXPIRED") {
                    delay(2000)
                    val statusResult = repository.getJoinRequestStatus(reqId)
                    if (statusResult.isSuccess) {
                        currentStatus = statusResult.getOrThrow().status
                    } else {
                        _uiState.value = JoinMeetingUiState.Error(statusResult.exceptionOrNull()?.message ?: "Status check failed")
                        return@launch
                    }
                }
                
                when (currentStatus) {
                    "ADMITTED" -> {
                        val tokenResult = repository.getParticipantLiveKitToken(reqId)
                        if (tokenResult.isFailure) {
                            _uiState.value = JoinMeetingUiState.Error(tokenResult.exceptionOrNull()?.message ?: "Failed to get LiveKit token")
                            return@launch
                        }
                        
                        val token = tokenResult.getOrThrow().token
                        if (token == null) {
                            _uiState.value = JoinMeetingUiState.Error("Missing LiveKit token")
                            return@launch
                        }
                        
                        roomConnectionHandoffStore.setHandoff(
                            RoomConnectionHandoff.ParticipantReady(
                                publicMeetingCode = code,
                                displayName = name,
                                livekitToken = token
                            )
                        )
                        _uiState.value = JoinMeetingUiState.Success
                    }
                    "REJECTED" -> _uiState.value = JoinMeetingUiState.Error("Host rejected your request")
                    "EXPIRED" -> _uiState.value = JoinMeetingUiState.Error("Request expired")
                    else -> _uiState.value = JoinMeetingUiState.Error("Unknown request status")
                }
            } else {
                val ex = result.exceptionOrNull()
                val msg = when (ex) {
                    is MeetingError.InvalidCodeOrPasscode -> "Invalid meeting code or passcode"
                    is MeetingError.MeetingLocked -> "The meeting is locked by the host"
                    is MeetingError.MeetingCapacityReached -> "The meeting capacity has been reached"
                    is MeetingError.RateLimited -> "Rate limited. Try again in ${ex.retryAfterSeconds} seconds"
                    is MeetingError.Offline -> "You are offline."
                    is MeetingError.UnexpectedServerResponse -> "Server error: ${ex.message}"
                    else -> "Unknown error"
                }
                _uiState.value = JoinMeetingUiState.Error(msg)
            }
        }
    }
}
