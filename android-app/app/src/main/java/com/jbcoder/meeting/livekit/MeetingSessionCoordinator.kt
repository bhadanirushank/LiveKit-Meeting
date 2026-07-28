package com.jbcoder.meeting.livekit

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.room.Room
import io.livekit.android.RoomOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class LiveKitState {
    IDLE,
    INITIALIZED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

@Singleton
class MeetingSessionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(LiveKitState.IDLE)
    val state: StateFlow<LiveKitState> = _state.asStateFlow()

    private var currentRoom: Room? = null
    private var inMemoryToken: String? = null

    init {
        initializeFoundation()
    }

    private fun initializeFoundation() {
        try {
            // Verify LiveKit SDK classes load successfully
            LiveKit.loggingLevel = io.livekit.android.util.LoggingLevel.INFO
            _state.value = LiveKitState.INITIALIZED
        } catch (e: Exception) {
            _state.value = LiveKitState.ERROR
        }
    }

    fun connectToRoom(url: String, token: String) {
        if (_state.value == LiveKitState.CONNECTING || _state.value == LiveKitState.CONNECTED) {
            return
        }

        try {
            _state.value = LiveKitState.CONNECTING
            this.inMemoryToken = token
            
            val roomOptions = RoomOptions(
                adaptiveStream = true,
                dynacast = true
            )
            
            val room = LiveKit.create(
                appContext = context,
                options = roomOptions,
                overrides = LiveKitOverrides()
            )
            this.currentRoom = room
            
            // In Phase 6A, we do not connect to a real meeting room.
            // This abstraction defines the connect boundary.
            // When connecting:
            // room.connect(url, token)
            
        } catch (e: Exception) {
            _state.value = LiveKitState.ERROR
            releaseResources()
        }
    }

    fun disconnect() {
        releaseResources()
        _state.value = LiveKitState.DISCONNECTED
    }

    private fun releaseResources() {
        currentRoom?.disconnect()
        currentRoom?.release()
        currentRoom = null
        inMemoryToken = null
    }
}
