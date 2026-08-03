package com.jbcoder.meeting.feature.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.*
import com.jbcoder.meeting.presentation.theme.MeetingPrimary
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.Track
import android.content.Context
import android.media.projection.MediaProjectionManager

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
    var showScreenShareConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val mediaProjectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    val screenShareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            viewModel.startScreenShare(result.data!!)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.toggleMic()
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
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
            title = { Text("Leave Meeting", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave the meeting?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirmation = false
                        viewModel.disconnect()
                        onNavigateHome()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState.roomState) {
            is RoomState.Disconnected -> {
                if (uiState.room != null) {
                    LaunchedEffect(Unit) {
                        onNavigateHome()
                    }
                }
            }
            is RoomState.FatalError -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Connection Failed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    MeetingSecondaryButton(
                        text = "Return Home",
                        onClick = { 
                            viewModel.disconnect()
                            onNavigateHome()
                        }
                    )
                }
            }
            is RoomState.Connecting -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MeetingLoadingIndicator()
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Connecting to secure room...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            is RoomState.Connected,
            is RoomState.Reconnecting -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "Code: ${uiState.meetingCode}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showHostControls = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More meeting options",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (state is RoomState.Reconnecting) {
                        MeetingInlineError(
                            message = "Reconnecting to meeting...",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (uiState.lastError != null) {
                        MeetingInlineError(
                            message = uiState.lastError ?: "",
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (uiState.isScreenSharing) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("You're sharing your screen", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(
                                    onClick = { viewModel.stopScreenShare() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Stop")
                                }
                            }
                        }
                    }

                    // Grid
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        ParticipantGrid(
                            room = uiState.room, 
                            participants = uiState.participants,
                            updateCounter = uiState.updateCounter
                        )
                    }
                    
                    // Controls Footer
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        RoomControls(
                            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
                            isMicEnabled = uiState.isMicEnabled,
                            isCameraEnabled = uiState.isCameraEnabled,
                            onToggleMic = { 
                                if (!uiState.isMicEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleMic() 
                                }
                            },
                            onToggleCamera = { 
                                if (!uiState.isCameraEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    viewModel.toggleCamera() 
                                }
                            },
                            onSwitchCamera = { viewModel.switchCamera() },
                            onLeave = { showLeaveConfirmation = true },
                            enabled = (state is RoomState.Connected)
                        )
                    }
                }
                if (showHostControls) {
                    MoreMenuSheet(
                        state = hostState,
                        isScreenSharing = uiState.isScreenSharing,
                        onStartScreenShare = { showScreenShareConfirm = true },
                        onStopScreenShare = { viewModel.stopScreenShare() },
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

                if (showScreenShareConfirm) {
                    AlertDialog(
                        onDismissRequest = { showScreenShareConfirm = false },
                        title = { Text("Share your screen?") },
                        text = { Text("Everything visible in the selected screen or app may be shared with meeting participants. Avoid opening private information or notifications.") },
                        confirmButton = {
                            Button(onClick = {
                                showScreenShareConfirm = false
                                screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                            }) { Text("Continue") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showScreenShareConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
                
                hostState.activeConfirmation?.let { conf ->
                    when (conf) {
                        is ConfirmationDialogState.EndMeeting -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("End Meeting for All?", fontWeight = FontWeight.Bold) },
                                text = { Text("Are you sure you want to end the meeting for everyone?") },
                                confirmButton = { 
                                    Button(
                                        onClick = { hostViewModel.endMeetingConfirmed() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("End Meeting") } 
                                },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
                                containerColor = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                        is ConfirmationDialogState.RemoveParticipant -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("Remove ${conf.name}?", fontWeight = FontWeight.Bold) },
                                text = { Text("Are you sure you want to remove ${conf.name} from the meeting? They will not be able to rejoin.") },
                                confirmButton = { 
                                    Button(
                                        onClick = { hostViewModel.removeParticipantConfirmed(conf.participantId) }, 
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Remove") } 
                                },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
                                containerColor = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                        is ConfirmationDialogState.PromoteParticipant -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("Promote ${conf.name}?", fontWeight = FontWeight.Bold) },
                                text = { Text("Promote ${conf.name} to Co-host?") },
                                confirmButton = { Button(onClick = { hostViewModel.promoteCohostConfirmed(conf.participantId) }) { Text("Promote") } },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
                                containerColor = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                        is ConfirmationDialogState.DemoteParticipant -> {
                            AlertDialog(
                                onDismissRequest = { hostViewModel.dismissConfirmation() },
                                title = { Text("Demote ${conf.name}?", fontWeight = FontWeight.Bold) },
                                text = { Text("Demote ${conf.name} to regular participant?") },
                                confirmButton = { Button(onClick = { hostViewModel.demoteCohostConfirmed(conf.participantId) }) { Text("Demote") } },
                                dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } },
                                containerColor = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
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
            Text(
                text = "Waiting for others...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        return
    }

    val screenShareParticipant = participants.firstOrNull { p ->
        p.videoTrackPublications.any { it.first.source == Track.Source.SCREEN_SHARE && !it.first.muted }
    }

    if (screenShareParticipant != null) {
        val screenTrack = screenShareParticipant.videoTrackPublications.firstOrNull { it.first.source == Track.Source.SCREEN_SHARE }?.first?.track as? VideoTrack
        Column(modifier = Modifier.fillMaxSize()) {
            // Featured Screen Share
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                if (screenTrack != null) {
                    LiveKitVideoRenderer(
                        room = room,
                        videoTrack = screenTrack,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Name Overlay
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topEnd = 8.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = "${screenShareParticipant.name ?: "Someone"} is sharing their screen",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Other participants in a small strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                lazyItems(participants, key = { it.sid.value ?: it.identity?.value ?: it.hashCode().toString() }) { participant ->
                    ParticipantTile(
                        room = room,
                        participant = participant,
                        updateTrigger = updateCounter,
                        modifier = Modifier.width(90.dp).fillMaxHeight()
                    )
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (participants.size > 2) 2 else 1),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(participants, key = { it.sid.value ?: it.identity?.value ?: it.hashCode().toString() }) { participant ->
                ParticipantTile(
                    room = room, 
                    participant = participant, 
                    updateTrigger = updateCounter,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                )
            }
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
            // Update handled by LiveKit internal
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
    updateTrigger: Int,
    modifier: Modifier = Modifier
) {
    val videoTracks = remember(updateTrigger, participant) { participant.videoTrackPublications }
    val audioTracks = remember(updateTrigger, participant) { participant.audioTrackPublications }
    val isSpeaking = remember(updateTrigger, participant) { participant.isSpeaking }
    
    val videoTrack = remember(updateTrigger, videoTracks) { 
        videoTracks.firstOrNull { it.first.source == Track.Source.CAMERA }?.first?.track as? VideoTrack 
    }
    val isAudioMuted = remember(updateTrigger, audioTracks) { audioTracks.firstOrNull()?.first?.muted ?: true }
    val isVideoMuted = remember(updateTrigger, videoTracks) { 
        videoTracks.firstOrNull { it.first.source == Track.Source.CAMERA }?.first?.muted ?: true 
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSpeaking) 3.dp else 0.dp,
                color = if (isSpeaking) MeetingPrimary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        if (videoTrack != null && !isVideoMuted) {
            LiveKitVideoRenderer(
                room = room,
                videoTrack = videoTrack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(), 
                contentAlignment = Alignment.Center
            ) {
                ParticipantAvatar(
                    name = participant.name ?: participant.identity?.value ?: "?",
                    size = 72
                )
            }
        }

        // Overlay with name and mic status
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = null,
                tint = if (isAudioMuted) MaterialTheme.colorScheme.error else Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = participant.name ?: participant.identity?.value ?: "User",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RoomControls(
    modifier: Modifier = Modifier,
    isMicEnabled: Boolean,
    isCameraEnabled: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onLeave: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MeetingControlButton(
            icon = if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = "Toggle Mic",
            isActive = isMicEnabled,
            onClick = onToggleMic
        )
        
        MeetingControlButton(
            icon = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
            contentDescription = "Toggle Camera",
            isActive = isCameraEnabled,
            onClick = onToggleCamera
        )
        
        if (isCameraEnabled) {
            MeetingControlButton(
                icon = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera",
                isActive = true,
                onClick = onSwitchCamera
            )
        }
        

        MeetingControlButton(
            icon = Icons.Default.CallEnd,
            contentDescription = "Leave Meeting",
            isActive = true,
            isDestructive = true,
            onClick = onLeave
        )
    }
}


