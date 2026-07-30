package com.jbcoder.meeting.feature.waitingroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoff
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoffStore
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoff
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ParticipantWaitingRoomState {
    object Initializing : ParticipantWaitingRoomState
    object Waiting : ParticipantWaitingRoomState
    object Admitted : ParticipantWaitingRoomState
    object TokenReady : ParticipantWaitingRoomState
    data class Error(val message: String) : ParticipantWaitingRoomState
}

@HiltViewModel
class ParticipantWaitingRoomViewModel @Inject constructor(
    private val handoffStore: MeetingEntryHandoffStore,
    private val roomConnectionHandoffStore: RoomConnectionHandoffStore,
    private val repository: MeetingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ParticipantWaitingRoomState>(ParticipantWaitingRoomState.Initializing)
    val uiState: StateFlow<ParticipantWaitingRoomState> = _uiState.asStateFlow()

    private val _meetingCode = MutableStateFlow("")
    val meetingCode: StateFlow<String> = _meetingCode.asStateFlow()
    
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private var requestId: String? = null
    private var pollingJob: Job? = null

    init {
        initialize()
    }

    private fun initialize() {
        val handoff = handoffStore.consumeAndClear() as? MeetingEntryHandoff.ParticipantRequested
        if (handoff == null) {
            _uiState.value = ParticipantWaitingRoomState.Error("Session lost")
            return
        }

        _meetingCode.value = handoff.publicMeetingCode
        _displayName.value = handoff.displayName
        requestId = handoff.requestId

        _uiState.value = ParticipantWaitingRoomState.Waiting
        startPolling()
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                checkStatus()
                delay(3000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    private suspend fun checkStatus() {
        val reqId = requestId ?: return
        val result = repository.getJoinRequestStatus(reqId)
        result.onSuccess { response ->
            when (response.status) {
                "ADMITTED" -> {
                    _uiState.value = ParticipantWaitingRoomState.Admitted
                    stopPolling()
                    fetchLiveKitToken()
                }
                "REJECTED" -> {
                    _uiState.value = ParticipantWaitingRoomState.Error("Host rejected your request")
                    stopPolling()
                }
                "EXPIRED" -> {
                    _uiState.value = ParticipantWaitingRoomState.Error("Request expired")
                    stopPolling()
                }
            }
        }.onFailure {
            _uiState.value = ParticipantWaitingRoomState.Error(it.message ?: "Status check failed")
            stopPolling()
        }
    }

    private fun fetchLiveKitToken() {
        val reqId = requestId ?: return
        viewModelScope.launch {
            val result = repository.getParticipantLiveKitToken(reqId)
            result.onSuccess { response ->
                response.token?.let {
                    roomConnectionHandoffStore.setHandoff(
                        RoomConnectionHandoff.ParticipantReady(
                            publicMeetingCode = _meetingCode.value,
                            displayName = _displayName.value,
                            livekitToken = it
                        )
                    )
                    _uiState.value = ParticipantWaitingRoomState.TokenReady
                } ?: run {
                    _uiState.value = ParticipantWaitingRoomState.Error("Missing LiveKit token")
                }
            }.onFailure {
                _uiState.value = ParticipantWaitingRoomState.Error(it.message ?: "Failed to get LiveKit token")
            }
        }
    }
}
