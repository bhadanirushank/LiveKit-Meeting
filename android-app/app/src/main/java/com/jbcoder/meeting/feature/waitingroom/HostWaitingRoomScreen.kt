package com.jbcoder.meeting.feature.waitingroom

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.*
import com.jbcoder.meeting.network.PendingJoinRequest
import com.jbcoder.meeting.presentation.theme.MeetingPrimary

@Composable
fun HostWaitingRoomScreen(
    viewModel: HostWaitingRoomViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit,
    onNavigateLiveRoom: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val meetingCode by viewModel.meetingCode.collectAsState()

    Scaffold(
        topBar = {
            MeetingTopBar(
                title = "Waiting Room"
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (val state = uiState) {
                is HostWaitingRoomState.Initializing -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        MeetingLoadingIndicator()
                    }
                }
                is HostWaitingRoomState.HandoffLost -> {
                    LaunchedEffect(Unit) {
                        onNavigateHome()
                    }
                }
                is HostWaitingRoomState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MeetingInlineError(message = state.message)
                        MeetingSecondaryButton(
                            text = "Return Home",
                            onClick = onNavigateHome
                        )
                    }
                }
                is HostWaitingRoomState.Active -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        MeetingCodeCard(meetingCode = meetingCode)
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "Pending Requests (${requests.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (requests.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No one is waiting.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(requests) { request ->
                                    RequestItem(
                                        request = request,
                                        onAdmit = { viewModel.admitParticipant(request.id) },
                                        onReject = { viewModel.rejectParticipant(request.id) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        MeetingPrimaryButton(
                            text = "Start Meeting",
                            onClick = { viewModel.startMeeting(onReady = onNavigateLiveRoom) },
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestItem(
    request: PendingJoinRequest,
    onAdmit: () -> Unit,
    onReject: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParticipantAvatar(name = request.displayName, size = 48)
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Waiting to join",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onReject,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Decline", fontWeight = FontWeight.SemiBold)
                }
                
                Button(
                    onClick = onAdmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MeetingPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Admit", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
