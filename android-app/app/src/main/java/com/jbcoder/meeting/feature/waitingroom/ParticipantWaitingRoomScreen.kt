package com.jbcoder.meeting.feature.waitingroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.MeetingInlineError
import com.jbcoder.meeting.core.designsystem.MeetingLoadingIndicator
import com.jbcoder.meeting.core.designsystem.MeetingSecondaryButton
import com.jbcoder.meeting.core.designsystem.ParticipantAvatar

@Composable
fun ParticipantWaitingRoomScreen(
    viewModel: ParticipantWaitingRoomViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit,
    onNavigateLiveRoom: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val meetingCode by viewModel.meetingCode.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is ParticipantWaitingRoomState.TokenReady) {
            onNavigateLiveRoom()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is ParticipantWaitingRoomState.Initializing -> {
                    MeetingLoadingIndicator()
                }
                is ParticipantWaitingRoomState.HandoffLost -> {
                    LaunchedEffect(Unit) {
                        onNavigateHome()
                    }
                }
                is ParticipantWaitingRoomState.Waiting -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        ParticipantAvatar(name = displayName, size = 80)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "Waiting for the host",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "You will be admitted to $meetingCode shortly. The host has been notified of your arrival.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        MeetingLoadingIndicator()
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        MeetingSecondaryButton(
                            text = "Leave Waiting Room",
                            onClick = onNavigateHome,
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }
                is ParticipantWaitingRoomState.Admitted,
                is ParticipantWaitingRoomState.TokenReady -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        ParticipantAvatar(name = displayName, size = 80)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "Admitted",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Preparing your secure connection to the meeting...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        MeetingLoadingIndicator()
                    }
                }
                is ParticipantWaitingRoomState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Unable to Join",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        MeetingInlineError(message = state.message)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        MeetingSecondaryButton(
                            text = "Return Home",
                            onClick = onNavigateHome,
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }
            }
        }
    }
}
