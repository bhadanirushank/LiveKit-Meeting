package com.jbcoder.meeting.feature.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jbcoder.meeting.BuildConfig
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoff
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RoomPreJoinState {
    object Initializing : RoomPreJoinState
    data class Ready(
        val meetingCode: String,
        val displayName: String,
        val isHost: Boolean
    ) : RoomPreJoinState
    data class Error(val message: String) : RoomPreJoinState
}

@HiltViewModel
class RoomPreJoinViewModel @Inject constructor(
    private val handoffStore: RoomConnectionHandoffStore,
    private val roomSessionManager: RoomSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<RoomPreJoinState>(RoomPreJoinState.Initializing)
    val uiState: StateFlow<RoomPreJoinState> = _uiState.asStateFlow()
    
    private var livekitToken: String? = null
    
    // Default privacy-safe state: camera and mic off
    private val _isMicEnabled = MutableStateFlow(false)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()
    
    private val _isCameraEnabled = MutableStateFlow(false)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    init {
        initialize()
    }

    private fun initialize() {
        // We do NOT consume the handoff here because the RoomSessionManager needs the token to connect later.
        // Or wait, we CAN consume it and hold it securely in memory here until connection.
        val handoff = handoffStore.consume()
        if (handoff == null) {
            _uiState.value = RoomPreJoinState.Error("Session lost or already consumed")
            return
        }

        when (handoff) {
            is RoomConnectionHandoff.HostReady -> {
                livekitToken = handoff.livekitToken
                _uiState.value = RoomPreJoinState.Ready(
                    meetingCode = handoff.publicMeetingCode,
                    displayName = "Host",
                    isHost = true
                )
            }
            is RoomConnectionHandoff.ParticipantReady -> {
                livekitToken = handoff.livekitToken
                _uiState.value = RoomPreJoinState.Ready(
                    meetingCode = handoff.publicMeetingCode,
                    displayName = handoff.displayName,
                    isHost = false
                )
            }
        }
    }

    fun toggleMic() {
        _isMicEnabled.value = !_isMicEnabled.value
    }

    fun toggleCamera() {
        _isCameraEnabled.value = !_isCameraEnabled.value
    }

    fun enterMeeting(hasAudioPerm: Boolean, hasCameraPerm: Boolean, onStarted: () -> Unit) {
        val token = livekitToken ?: return
        val url = BuildConfig.LIVEKIT_URL
        if (url.isNullOrBlank()) {
            _uiState.value = RoomPreJoinState.Error("LiveKit URL is missing")
            return
        }
        
        // Give session manager the permission state and intended media state
        roomSessionManager.prepare(hasAudioPerm, hasCameraPerm)
        roomSessionManager.setInitialMediaState(_isMicEnabled.value, _isCameraEnabled.value)
        
        // Initiate connection
        val code = (uiState.value as? RoomPreJoinState.Ready)?.meetingCode ?: ""
        
        viewModelScope.launch {
            roomSessionManager.connect(url, token, code)
        }
        
        // Clear local token ref to avoid memory lingering longer than needed
        livekitToken = null
        
        // Move to Live Room UI (which will show "Connecting..." state)
        onStarted()
    }
}
