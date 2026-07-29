package com.jbcoder.meeting.feature.waitingroom

import androidx.lifecycle.ViewModel
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoff
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoffStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WaitingRoomRouterViewModel @Inject constructor(
    private val handoffStore: MeetingEntryHandoffStore
) : ViewModel() {

    // Store the handoff locally in the ViewModel so that when configuration changes happen
    // after the store has been cleared, we don't accidentally navigate home.
    private var cachedHandoff: MeetingEntryHandoff? = null

    fun consumeHandoff(): MeetingEntryHandoff? {
        if (cachedHandoff == null) {
            cachedHandoff = handoffStore.peek()
        }
        return cachedHandoff
    }
}
