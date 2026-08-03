package com.jbcoder.meeting.data.meeting

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RoomConnectionHandoff {
    data class HostReady(
        val publicMeetingCode: String,
        val livekitToken: String,
        val livekitRoomName: String?,
        val displayName: String
    ) : RoomConnectionHandoff

    data class ParticipantReady(
        val publicMeetingCode: String,
        val displayName: String,
        val livekitToken: String
    ) : RoomConnectionHandoff
}

@Singleton
class RoomConnectionHandoffStore @Inject constructor() {
    private val _handoff = MutableStateFlow<RoomConnectionHandoff?>(null)
    val handoff: StateFlow<RoomConnectionHandoff?> = _handoff.asStateFlow()

    fun setHandoff(handoff: RoomConnectionHandoff?) {
        _handoff.value = handoff
    }

    fun consume(): RoomConnectionHandoff? {
        val current = _handoff.value
        _handoff.value = null
        return current
    }

    fun clear() {
        _handoff.value = null
    }
}
