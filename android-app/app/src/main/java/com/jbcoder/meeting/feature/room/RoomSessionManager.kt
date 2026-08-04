package com.jbcoder.meeting.feature.room

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RoomState {
    object Disconnected : RoomState
    object Connecting : RoomState
    object Connected : RoomState
    object Reconnecting : RoomState
    data class FatalError(val message: String) : RoomState
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderName: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLocal: Boolean
)

data class RoomUiState(
    val room: Room? = null,
    val meetingCode: String = "",
    val localDisplayName: String = "",
    val updateCounter: Int = 0,
    val roomState: RoomState = RoomState.Disconnected,
    val participants: List<Participant> = emptyList(),
    val isMicEnabled: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val isScreenSharing: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    val meetingDurationSeconds: Long = 0,
    val lastError: String? = null
)

@Singleton
class RoomSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    private var room: Room? = null
    private var eventJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentLiveKitToken: String? = null
    private var currentUrl: String? = null
    private var isConnectInProgress = false
    private var timerJob: Job? = null

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var seconds = 0L
            while (true) {
                kotlinx.coroutines.delay(1000)
                seconds++
                _uiState.update { it.copy(meetingDurationSeconds = seconds) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(meetingDurationSeconds = 0) }
    }

    fun prepare(hasAudioPerm: Boolean, hasCameraPerm: Boolean) {
        _uiState.update { 
            it.copy(
                hasAudioPermission = hasAudioPerm,
                hasCameraPermission = hasCameraPerm
            )
        }
    }

    fun setInitialMediaState(micEnabled: Boolean, cameraEnabled: Boolean) {
        _uiState.update { 
            it.copy(isMicEnabled = micEnabled, isCameraEnabled = cameraEnabled)
        }
    }

    fun connect(url: String, token: String, meetingCode: String, localDisplayName: String = "") {
        _uiState.update { it.copy(meetingCode = meetingCode, localDisplayName = localDisplayName, roomState = RoomState.Connecting, lastError = null) }
        if (room != null || isConnectInProgress) return
        isConnectInProgress = true
        currentLiveKitToken = token
        currentUrl = url

        scope.launch {
            try {
                val newRoom = LiveKit.create(
                    appContext = context,
                    options = RoomOptions(
                        adaptiveStream = true,
                        dynacast = true
                    )
                )
                
                room = newRoom
                
                _uiState.update { 
                    it.copy(
                        roomState = RoomState.Connecting,
                        room = newRoom,
                        meetingCode = meetingCode
                    ) 
                }
                
                eventJob = scope.launch {
                    newRoom.events.collect { event ->
                        handleRoomEvent(event)
                    }
                }

                newRoom.connect(
                    url = url,
                    token = token,
                    options = ConnectOptions()
                )
                
                // Automatically publish tracks if enabled
                publishInitialTracks()
                
            } catch (e: Exception) {
                _uiState.update { it.copy(roomState = RoomState.FatalError(e.message ?: "Failed to connect")) }
                cleanup(preserveError = true)
            } finally {
                isConnectInProgress = false
            }
        }
    }

    private suspend fun publishInitialTracks() {
        val r = room ?: return
        val localParticipant = r.localParticipant
        
        if (_uiState.value.isMicEnabled && _uiState.value.hasAudioPermission) {
            localParticipant.setMicrophoneEnabled(true)
        }
        if (_uiState.value.isCameraEnabled && _uiState.value.hasCameraPermission) {
            localParticipant.setCameraEnabled(true)
        }
    }

    fun toggleMic() {
        val r = room ?: return
        scope.launch {
            val localParticipant = r.localParticipant
            val current = _uiState.value.isMicEnabled
            try {
                localParticipant.setMicrophoneEnabled(!current)
                _uiState.update { it.copy(isMicEnabled = !current, lastError = null) }
                updateParticipants()
            } catch (e: Exception) {
                _uiState.update { it.copy(lastError = "Mic Error: ${e.message}") }
            }
        }
    }

    fun toggleCamera() {
        val r = room ?: return
        scope.launch {
            val localParticipant = r.localParticipant
            val current = _uiState.value.isCameraEnabled
            try {
                localParticipant.setCameraEnabled(!current)
                _uiState.update { it.copy(isCameraEnabled = !current, lastError = null) }
                updateParticipants()
            } catch (e: Exception) {
                _uiState.update { it.copy(lastError = "Camera Error: ${e.message} \n ${android.util.Log.getStackTraceString(e)}") }
            }
        }
    }

    fun switchCamera() {
        val r = room ?: return
        scope.launch {
            try {
                val localParticipant = r.localParticipant
                val track = localParticipant.videoTrackPublications.firstOrNull()?.first?.track as? LocalVideoTrack
                track?.switchCamera()
            } catch (e: Exception) {
                android.util.Log.e("RoomSessionManager", "Failed to switch camera", e)
                _uiState.update { it.copy(lastError = "Switch Camera Error: ${e.message}") }
            }
        }
    }

    fun startScreenShare(intentData: Intent) {
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.setScreenShareEnabled(
                    enabled = true,
                    screenCaptureParams = ScreenCaptureParams(mediaProjectionPermissionResultData = intentData)
                )
                _uiState.update { it.copy(isScreenSharing = true, lastError = null) }
                updateParticipants()
            } catch (e: Exception) {
                android.util.Log.e("RoomSessionManager", "Failed to start screen share", e)
                _uiState.update { it.copy(lastError = "Screen Share Error: ${e.message}") }
            }
        }
    }

    fun stopScreenShare() {
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.setScreenShareEnabled(false)
                _uiState.update { it.copy(isScreenSharing = false, lastError = null) }
                updateParticipants()
            } catch (e: Exception) {
                android.util.Log.e("RoomSessionManager", "Failed to stop screen share", e)
            }
        }
    }

    fun sendChatMessage(text: String) {
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.publishData(
                    data = text.toByteArray(Charsets.UTF_8),
                    topic = "chat"
                )
                val msg = ChatMessage(
                    senderName = "You",
                    message = text,
                    isLocal = true
                )
                _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }
            } catch (e: Exception) {
                android.util.Log.e("RoomSessionManager", "Failed to send chat", e)
            }
        }
    }

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Connected -> {
                _uiState.update { it.copy(roomState = RoomState.Connected) }
                updateParticipants()
                startTimer()
            }
            is RoomEvent.Reconnecting -> {
                _uiState.update { it.copy(roomState = RoomState.Reconnecting) }
            }
            is RoomEvent.Reconnected -> {
                _uiState.update { it.copy(roomState = RoomState.Connected) }
                updateParticipants()
            }
            is RoomEvent.Disconnected -> {
                if (_uiState.value.roomState !is RoomState.FatalError) {
                    _uiState.update { it.copy(roomState = RoomState.Disconnected) }
                }
            }
            is RoomEvent.ParticipantConnected,
            is RoomEvent.ParticipantDisconnected -> {
                updateParticipants()
            }
            is RoomEvent.TrackPublished,
            is RoomEvent.TrackUnpublished,
            is RoomEvent.TrackSubscribed,
            is RoomEvent.TrackUnsubscribed,
            is RoomEvent.TrackMuted,
            is RoomEvent.TrackUnmuted,
            is RoomEvent.ActiveSpeakersChanged -> {
                updateParticipants() // Simple refresh
            }
            is RoomEvent.FailedToConnect -> {
                _uiState.update { it.copy(roomState = RoomState.FatalError(event.error.message ?: "Failed to connect")) }
            }
            is RoomEvent.DataReceived -> {
                if (event.topic == "chat") {
                    val text = String(event.data, Charsets.UTF_8)
                    val sender = event.participant?.name?.takeIf { it.isNotBlank() } ?: event.participant?.identity?.value ?: "Unknown"
                    val msg = ChatMessage(
                        senderName = sender,
                        message = text,
                        isLocal = false
                    )
                    _uiState.update { it.copy(chatMessages = it.chatMessages + msg) }
                }
            }
            else -> {}
        }
    }

    private fun updateParticipants() {
        val r = room ?: return
        val list = mutableListOf<Participant>()
        list.add(r.localParticipant)
        list.addAll(r.remoteParticipants.values)
        _uiState.update { 
            it.copy(
                participants = list.toList(),
                updateCounter = it.updateCounter + 1
            ) 
        }
    }

    fun disconnect() {
        cleanup()
    }

    private fun cleanup(preserveError: Boolean = false) {
        val currentError = if (preserveError) _uiState.value.roomState else RoomState.Disconnected
        room?.disconnect()
        eventJob?.cancel()
        eventJob = null
        stopTimer()
        room?.release()
        room = null
        currentLiveKitToken = null
        currentUrl = null
        
        _uiState.update { 
            RoomUiState(roomState = currentError) 
        }
    }

    private var wasCameraEnabledBeforeBackground = false

    fun onAppBackgrounded() {
        val r = room ?: return
        scope.launch {
            wasCameraEnabledBeforeBackground = _uiState.value.isCameraEnabled
            if (wasCameraEnabledBeforeBackground) {
                try {
                    r.localParticipant.setCameraEnabled(false)
                } catch (e: Exception) {
                    android.util.Log.e("RoomSessionManager", "Failed to pause camera", e)
                }
            }
        }
    }

    fun onAppForegrounded() {
        val r = room ?: return
        scope.launch {
            if (wasCameraEnabledBeforeBackground && _uiState.value.hasCameraPermission) {
                try {
                    r.localParticipant.setCameraEnabled(true)
                } catch (e: Exception) {
                    android.util.Log.e("RoomSessionManager", "Failed to resume camera", e)
                }
            }
        }
    }
}
