package com.jbcoder.meeting.feature.waitingroom

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Waiting Room") })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val state = uiState) {
                is ParticipantWaitingRoomState.Initializing -> {
                    CircularProgressIndicator()
                }
                is ParticipantWaitingRoomState.Waiting -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        Text("Waiting for host to admit you...", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Meeting Code: $meetingCode", style = MaterialTheme.typography.bodyMedium)
                        Text("Name: $displayName", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ParticipantWaitingRoomState.Admitted,
                is ParticipantWaitingRoomState.TokenReady -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                        Text("Admitted! Preparing connection...", style = MaterialTheme.typography.titleMedium)
                    }
                }
                is ParticipantWaitingRoomState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateHome) {
                            Text("Go Home")
                        }
                    }
                }
            }
        }
    }
}
