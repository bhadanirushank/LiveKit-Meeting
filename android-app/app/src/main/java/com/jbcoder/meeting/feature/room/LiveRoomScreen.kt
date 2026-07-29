package com.jbcoder.meeting.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
                LaunchedEffect(Unit) {
                    onNavigateHome()
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
                        onToggleMic = { viewModel.toggleMic() },
                        onToggleCamera = { viewModel.toggleCamera() },
                        onSwitchCamera = { viewModel.switchCamera() },
                        onLeave = { showLeaveConfirmation = true },
                        onHostControlsClick = { showHostControls = true }
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
        items(participants, key = { it.sid }) { participant ->
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
    val videoTracks = participant.videoTrackPublications
    val audioTracks = participant.audioTrackPublications
    val isSpeaking = participant.isSpeaking
    
    val videoTrack = videoTracks.firstOrNull { it.first.subscribed }?.second as? VideoTrack
    val isAudioMuted = audioTracks.firstOrNull()?.first?.muted ?: true

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
    onHostControlsClick: () -> Unit
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
