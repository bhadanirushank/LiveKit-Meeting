package com.jbcoder.meeting.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.renderer.TextureViewRenderer

@Composable
fun LiveRoomScreen(
    viewModel: LiveRoomViewModel = hiltViewModel(),
    hostViewModel: HostModerationViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val hostState by hostViewModel.state.collectAsState()
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var showHostControls by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.toggleMic()
            }
        }
    )

    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.toggleCamera()
            }
        }
    )

    LaunchedEffect(uiState.meetingCode) {
        if (uiState.meetingCode.isNotBlank()) {
            hostViewModel.setMeetingCode(uiState.meetingCode)
        }
    }

    if (showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmation = false },
            title = { Text("Leave Meeting") },
            text = { Text("Are you sure you want to leave the meeting?") },
            confirmButton = {
                Button(onClick = {
                    showLeaveConfirmation = false
                    viewModel.disconnect()
                    onNavigateHome()
                }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val state = uiState.roomState) {
            is RoomState.Disconnected -> {
                // If we get here and there's no room, it might be the initial state.
                // Wait to navigate home until explicitly disconnected.
                if (uiState.room != null) {
                    LaunchedEffect(Unit) {
                        onNavigateHome()
                    }
                }
            }
            is RoomState.FatalError -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connection Failed:", color = Color.White)
                    Text(state.message, color = Color.Red)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { 
                        viewModel.disconnect()
                        onNavigateHome()
                    }) {
                        Text("Go Back")
                    }
                }
            }
            is RoomState.Connecting -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connecting...", color = Color.White)
                }
            }
            is RoomState.Connected,
            is RoomState.Reconnecting -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Meeting: ${uiState.meetingCode}",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (hostState.role == MeetingRole.HOST) {
                            IconButton(onClick = { hostViewModel.showWaitingRoomDialog() }) {
                                BadgedBox(
                                    badge = {
                                        if (hostState.pendingRequests.isNotEmpty()) {
                                            Badge { Text(hostState.pendingRequests.size.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = "Waiting Room", tint = Color.White)
                                }
                            }
                        }
                    }

                    if (state is RoomState.Reconnecting) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFB71C1C))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reconnecting...", color = Color.White)
                            }
                        }
                    }

                    if (uiState.lastError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFF9800))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(uiState.lastError ?: "", color = Color.White)
                        }
                    }

                    Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                        ParticipantGrid(
                            room = uiState.room, 
                            participants = uiState.participants,
                            updateCounter = uiState.updateCounter
                        )
                    }
                    
                    RoomControls(
                        isMicEnabled = uiState.isMicEnabled,
                        isCameraEnabled = uiState.isCameraEnabled,
                        onToggleMic = { 
                            if (!uiState.isMicEnabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.toggleMic() 
                            }
                        },
                        onToggleCamera = { 
                            if (!uiState.isCameraEnabled && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                            } else {
                                viewModel.toggleCamera() 
                            }
                        },
                        onSwitchCamera = { viewModel.switchCamera() },
                        onLeave = { showLeaveConfirmation = true },
                        onHostControlsClick = { showHostControls = true },
                        enabled = (state is RoomState.Connected)
                    )
                }
                
                if (showHostControls) {
                    HostControlsSheet(
                        state = hostState,
                        onLockMeeting = { hostViewModel.lockMeeting() },
                        onUnlockMeeting = { hostViewModel.unlockMeeting() },
                        onEndMeetingClick = { hostViewModel.showConfirmation(ConfirmationDialogState.EndMeeting()) },
                        onMuteParticipant = { hostViewModel.muteParticipant(it) },
                        onAskToUnmute = { hostViewModel.askToUnmute(it) },
                        onDisablePublishing = { hostViewModel.disablePublishing(it) },
                        onRestorePublishing = { hostViewModel.restorePublishing(it) },
                        onRemoveParticipantClick = { id, name -> hostViewModel.showConfirmation(ConfirmationDialogState.RemoveParticipant(id, name)) },
                        onPromoteClick = { id, name -> hostViewModel.showConfirmation(ConfirmationDialogState.PromoteParticipant(id, name)) },
                        onDemoteClick = { id, name -> hostViewModel.showConfirmation(ConfirmationDialogState.DemoteParticipant(id, name)) },
                        onDismiss = { showHostControls = false }
                    )
                }
                
                hostState.activeConfirmation?.let { conf ->
                    when (conf) {
                        is ConfirmationDialogState.EndMeeting -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("End Meeting for All?") },
                                text = { Text("Are you sure you want to end the meeting for everyone?") },
                                confirmButton = { Button(onClick = { hostViewModel.endMeetingConfirmed() }) { Text("End Meeting") } },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel") } }
                            )
                        }
                        is ConfirmationDialogState.RemoveParticipant -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("Remove ${conf.name}?") },
                                text = { Text("Are you sure you want to remove ${conf.name} from the meeting? They will not be able to rejoin.") },
                                confirmButton = { Button(onClick = { hostViewModel.removeParticipantConfirmed(conf.participantId) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Remove") } },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel") } }
                            )
                        }
                        is ConfirmationDialogState.PromoteParticipant -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("Promote ${conf.name}?") },
                                text = { Text("Promote ${conf.name} to Co-host?") },
                                confirmButton = { Button(onClick = { hostViewModel.promoteCohostConfirmed(conf.participantId) }) { Text("Promote") } },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel") } }
                            )
                        }
                        is ConfirmationDialogState.DemoteParticipant -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("Demote ${conf.name}?") },
                                text = { Text("Demote ${conf.name} to regular participant?") },
                                confirmButton = { Button(onClick = { hostViewModel.demoteCohostConfirmed(conf.participantId) }) { Text("Demote") } },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel") } }
                            )
                        }
                    }
                }
                
                if (hostState.isWaitingRoomDialogVisible) {
                    MidMeetingWaitingRoomDialog(
                        pendingRequests = hostState.pendingRequests,
                        onDismiss = { hostViewModel.hideWaitingRoomDialog() },
                        onAdmit = { hostViewModel.admitWaitingRoomParticipant(it) },
                        onReject = { hostViewModel.rejectWaitingRoomParticipant(it) }
                    )
                }
            }
            is RoomState.FatalError -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Error: ${state.message}", color = Color.Red, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { 
                        viewModel.disconnect()
                        onNavigateHome()
                    }) {
                        Text("Return Home")
                    }
                }
            }
        }
    }
}

@Composable
fun ParticipantGrid(
    room: io.livekit.android.room.Room?, 
    participants: List<Participant>,
    updateCounter: Int
) {
    if (participants.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Waiting for others...", color = Color.White)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (participants.size > 2) 2 else 1),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(participants, key = { it.sid?.value ?: it.identity?.value ?: it.hashCode().toString() }) { participant ->
            ParticipantTile(
                room = room, 
                participant = participant, 
                updateTrigger = updateCounter
            )
        }
    }
}

@Composable
fun LiveKitVideoRenderer(
    room: io.livekit.android.room.Room?,
    videoTrack: VideoTrack,
    modifier: Modifier = Modifier
) {
    if (room == null) return
    AndroidView(
        factory = { context ->
            TextureViewRenderer(context).apply {
                room.initVideoRenderer(this)
                videoTrack.addRenderer(this)
            }
        },
        update = { view ->
            // In a more complex setup, you'd handle track switching here
        },
        onRelease = { view ->
            videoTrack.removeRenderer(view)
            view.release()
        },
        modifier = modifier
    )
}

@Composable
fun ParticipantTile(
    room: io.livekit.android.room.Room?, 
    participant: Participant, 
    updateTrigger: Int
) {
    // Read properties directly. 
    // Composable recomposes when updateTrigger changes.
    val videoTracks = remember(updateTrigger, participant) { participant.videoTrackPublications }
    val audioTracks = remember(updateTrigger, participant) { participant.audioTrackPublications }
    val isSpeaking = remember(updateTrigger, participant) { participant.isSpeaking }
    
    val videoTrack = remember(updateTrigger, videoTracks) { videoTracks.firstOrNull()?.first?.track as? VideoTrack }
    val isAudioMuted = remember(updateTrigger, audioTracks) { audioTracks.firstOrNull()?.first?.muted ?: true }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
    ) {
        if (videoTrack != null) {
            LiveKitVideoRenderer(
                room = room,
                videoTrack = videoTrack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = participant.identity?.value ?: "Unknown",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Overlay with name and mic status
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = null,
                tint = if (isAudioMuted) Color.Red else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = participant.name ?: participant.identity?.value ?: "User",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        // Active speaker indicator
        if (isSpeaking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun RoomControls(
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onLeave: () -> Unit,
    onHostControlsClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.DarkGray)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleMic) {
            Icon(
                imageVector = if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Toggle Mic",
                tint = if (isMicEnabled) Color.White else Color.Red
            )
        }
        IconButton(onClick = onToggleCamera) {
            Icon(
                imageVector = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                contentDescription = "Toggle Camera",
                tint = if (isCameraEnabled) Color.White else Color.Red
            )
        }
        if (isCameraEnabled) {
            IconButton(onClick = onSwitchCamera) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }
        }
        
        IconButton(onClick = onHostControlsClick) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
                contentDescription = "Host Controls",
                tint = Color.White
            )
        }
        
        Button(
            onClick = onLeave,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = "Leave Meeting")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidMeetingWaitingRoomDialog(
    pendingRequests: List<com.jbcoder.meeting.network.PendingJoinRequest>,
    onDismiss: () -> Unit,
    onAdmit: (String) -> Unit,
    onReject: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Waiting Room (${pendingRequests.size})",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (pendingRequests.isEmpty()) {
                Text("No pending requests.", modifier = Modifier.padding(vertical = 32.dp))
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    lazyItems(pendingRequests) { request ->
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
                                }
                                Row {
                                    TextButton(onClick = { onReject(request.id) }) {
                                        Text("Reject", color = MaterialTheme.colorScheme.error)
                                    }
                                    Button(onClick = { onAdmit(request.id) }) {
                                        Text("Admit")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
