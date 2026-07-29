package com.jbcoder.meeting.feature.waitingroom

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoff

@Composable
fun WaitingRoomRouterScreen(
    viewModel: WaitingRoomRouterViewModel = hiltViewModel(),
    onNavigateToHost: () -> Unit,
    onNavigateToParticipant: () -> Unit,
    onNavigateHome: () -> Unit
) {
    LaunchedEffect(Unit) {
        val handoff = viewModel.consumeHandoff()
        if (handoff == null) {
            onNavigateHome()
        } else {
            when (handoff) {
                is MeetingEntryHandoff.HostCreated -> {
                    onNavigateToHost()
                }
                is MeetingEntryHandoff.ParticipantRequested -> {
                    onNavigateToParticipant()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
