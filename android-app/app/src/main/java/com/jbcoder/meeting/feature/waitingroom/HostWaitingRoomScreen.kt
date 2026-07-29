package com.jbcoder.meeting.feature.waitingroom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.network.PendingJoinRequest

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
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Host Waiting Room: $meetingCode") })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is HostWaitingRoomState.Initializing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is HostWaitingRoomState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateHome) {
                            Text("Go Home")
                        }
                    }
                }
                is HostWaitingRoomState.Active -> {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = "Pending Requests (${requests.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (requests.isEmpty()) {
                            Text("No pending requests.", modifier = Modifier.weight(1f))
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
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
                        Button(
                            onClick = { viewModel.startMeeting(onReady = onNavigateLiveRoom) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start Meeting")
                        }
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(request.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(request.requestedAt, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = onReject) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onAdmit) {
                    Text("Admit")
                }
            }
        }
    }
}
