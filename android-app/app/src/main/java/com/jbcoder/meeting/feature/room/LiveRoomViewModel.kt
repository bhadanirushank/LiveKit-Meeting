package com.jbcoder.meeting.feature.room

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LiveRoomViewModel @Inject constructor(
    private val roomSessionManager: RoomSessionManager
) : ViewModel() {

    val uiState = roomSessionManager.uiState

    fun toggleMic() {
        roomSessionManager.toggleMic()
    }

    fun toggleCamera() {
        roomSessionManager.toggleCamera()
    }

    fun switchCamera() {
        roomSessionManager.switchCamera()
    }

    fun disconnect() {
        roomSessionManager.disconnect()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Wait, should we disconnect on onCleared?
        // Only if not explicitly handled. But if it's application scoped for the meeting, 
        // process death handles it. Let's disconnect if ViewModel is cleared (user pressed back).
        roomSessionManager.disconnect()
    }
}
