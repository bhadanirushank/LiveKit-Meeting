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

data class RoomUiState(
    val room: Room? = null,
    val meetingCode: String = "",
    val updateCounter: Int = 0,
    val roomState: RoomState = RoomState.Disconnected,
    val participants: List<Participant> = emptyList(),
    val isMicEnabled: Boolean = false,
    val isCameraEnabled: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasCameraPermission: Boolean = false
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

    suspend fun connect(url: String, token: String, meetingCode: String) {
        if (room != null || isConnectInProgress) return
        isConnectInProgress = true
        currentLiveKitToken = token
        currentUrl = url

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
            cleanup()
        } finally {
            isConnectInProgress = false
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
                _uiState.update { it.copy(isMicEnabled = !current) }
            } catch (e: Exception) {
                // Ignore for now
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
                _uiState.update { it.copy(isCameraEnabled = !current) }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun switchCamera() {
        val r = room ?: return
        scope.launch {
            val localParticipant = r.localParticipant
            val track = localParticipant.videoTrackPublications.firstOrNull()?.second as? LocalVideoTrack
            track?.options?.let { options ->
                val newPosition = if (options.position == CameraPosition.FRONT) CameraPosition.BACK else CameraPosition.FRONT
                track.restartTrack(options.copy(position = newPosition))
            }
        }
    }

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Connected -> {
                _uiState.update { it.copy(roomState = RoomState.Connected) }
                updateParticipants()
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

    private fun cleanup() {
        room?.disconnect()
        eventJob?.cancel()
        eventJob = null
        room?.release()
        room = null
        currentLiveKitToken = null
        currentUrl = null
        _uiState.value = RoomUiState() // Reset
    }
}
