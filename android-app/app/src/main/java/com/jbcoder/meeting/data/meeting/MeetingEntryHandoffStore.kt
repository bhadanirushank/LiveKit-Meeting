package com.jbcoder.meeting.data.meeting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MeetingEntryHandoff {
    data class HostCreated(
        val publicMeetingCode: String,
        val hostSecret: String,
        val livekitRoomName: String,
        val title: String,
        val waitingRoomEnabled: Boolean,
        val joinBeforeHostEnabled: Boolean,
        val maximumParticipants: Int
    ) : MeetingEntryHandoff
    
    data class ParticipantRequested(
        val publicMeetingCode: String,
        val requestId: String,
        val requestStatus: String,
        val displayName: String
    ) : MeetingEntryHandoff
}

@Singleton
class MeetingEntryHandoffStore @Inject constructor() {
    private val _currentHandoff = MutableStateFlow<MeetingEntryHandoff?>(null)
    val currentHandoff: StateFlow<MeetingEntryHandoff?> = _currentHandoff.asStateFlow()
    
    fun setHandoff(handoff: MeetingEntryHandoff) {
        _currentHandoff.value = handoff
    }
    
    fun consumeAndClear(): MeetingEntryHandoff? {
        val value = _currentHandoff.value
        _currentHandoff.value = null
        return value
    }
    
    fun peek(): MeetingEntryHandoff? {
        return _currentHandoff.value
    }
    
    fun clear() {
        _currentHandoff.value = null
    }
}
