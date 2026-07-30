package com.jbcoder.meeting.feature.waitingroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbcoder.meeting.data.meeting.HostSessionStore
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoff
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoffStore
import com.jbcoder.meeting.data.meeting.MeetingRepository
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoff
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import com.jbcoder.meeting.network.HostExchangeRequest
import com.jbcoder.meeting.network.PendingJoinRequest
import com.jbcoder.meeting.storage.InstallationIdProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jbcoder.meeting.lifecycle.AppLifecycleManager

sealed interface HostWaitingRoomState {
    object Initializing : HostWaitingRoomState
    object Active : HostWaitingRoomState
    data class Error(val message: String) : HostWaitingRoomState
    object HandoffLost : HostWaitingRoomState
}

@HiltViewModel
class HostWaitingRoomViewModel @Inject constructor(
    private val handoffStore: MeetingEntryHandoffStore,
    private val hostSessionStore: HostSessionStore,
    private val roomConnectionHandoffStore: RoomConnectionHandoffStore,
    private val repository: MeetingRepository,
    private val installationIdProvider: InstallationIdProvider,
    private val appLifecycleManager: AppLifecycleManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<HostWaitingRoomState>(HostWaitingRoomState.Initializing)
    val uiState: StateFlow<HostWaitingRoomState> = _uiState.asStateFlow()

    private val _requests = MutableStateFlow<List<PendingJoinRequest>>(emptyList())
    val requests: StateFlow<List<PendingJoinRequest>> = _requests.asStateFlow()

    private val _meetingCode = MutableStateFlow("")
    val meetingCode: StateFlow<String> = _meetingCode.asStateFlow()

    private var livekitRoomName: String? = null
    private var pollingJob: Job? = null

    init {
        initialize()
    }

    private fun initialize() {
        val handoff = handoffStore.consumeAndClear() as? MeetingEntryHandoff.HostCreated
        if (handoff == null) {
            _uiState.value = HostWaitingRoomState.HandoffLost
            return
        }

        _meetingCode.value = handoff.publicMeetingCode
        livekitRoomName = handoff.livekitRoomName

        viewModelScope.launch {
            val installId = installationIdProvider.getInstallationId()
            val exchangeResult = repository.exchangeHostSession(
                HostExchangeRequest(handoff.publicMeetingCode, handoff.hostSecret),
                installId
            )

            exchangeResult.onSuccess { response ->
                hostSessionStore.setHostCredential(response.accessToken)
                _uiState.value = HostWaitingRoomState.Active
                startPolling()
            }.onFailure {
                _uiState.value = HostWaitingRoomState.Error(it.message ?: "Authentication failed")
            }
        }
    }

    fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            appLifecycleManager.isForeground.collectLatest { isForeground ->
                if (isForeground) {
                    while (isActive) {
                        fetchWaitingRoom()
                        delay(3000)
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
    }

    private suspend fun fetchWaitingRoom() {
        val result = repository.getWaitingRoom(_meetingCode.value)
        result.onSuccess { response ->
            _requests.value = response.requests
        }
    }

    fun admitParticipant(requestId: String) {
        viewModelScope.launch {
            val installId = installationIdProvider.getInstallationId()
            repository.admitParticipant(_meetingCode.value, requestId, installId)
            fetchWaitingRoom()
        }
    }

    fun rejectParticipant(requestId: String) {
        viewModelScope.launch {
            repository.rejectParticipant(_meetingCode.value, requestId, null)
            fetchWaitingRoom()
        }
    }

    fun startMeeting(onReady: () -> Unit) {
        viewModelScope.launch {
            val installId = installationIdProvider.getInstallationId()
            val result = repository.startMeeting(_meetingCode.value, installId)
            result.onSuccess {
                val tokenResult = repository.getHostLiveKitToken()
                tokenResult.onSuccess { tokenResp ->
                    tokenResp.token?.let {
                        roomConnectionHandoffStore.setHandoff(
                            RoomConnectionHandoff.HostReady(
                                publicMeetingCode = _meetingCode.value,
                                livekitToken = it,
                                livekitRoomName = livekitRoomName
                            )
                        )
                        stopPolling()
                        onReady()
                    } ?: run {
                        _uiState.value = HostWaitingRoomState.Error("LiveKit token missing")
                    }
                }.onFailure {
                    _uiState.value = HostWaitingRoomState.Error(it.message ?: "Failed to get LiveKit token")
                }
            }.onFailure {
                _uiState.value = HostWaitingRoomState.Error(it.message ?: "Failed to start meeting")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
    }
}
