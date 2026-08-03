package com.jbcoder.meeting.feature.room

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.*
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.Track

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
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirmation = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    MaterialTheme(colorScheme = LiveRoomColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MeetingBackground)
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
                            tint = DangerRed,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Connection Failed",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DangerRed,
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
                                color = TextPrimary
                            )
                        }
                    }
                }
                is RoomState.Connected,
                is RoomState.Reconnecting -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .padding(bottom = 16.dp)
                    ) {
                        TopMeetingBar(
                            meetingCode = uiState.meetingCode, 
                            participantCount = uiState.participants.size
                        )

                        if (state is RoomState.Reconnecting) {
                            MeetingInlineError(
                                message = "Reconnecting to meeting...",
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (uiState.lastError != null) {
                            MeetingInlineError(
                                message = uiState.lastError ?: "",
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        if (uiState.isScreenSharing) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = BrandPurple.copy(alpha = 0.2f),
                                contentColor = TextPrimary
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(20.dp), tint = BrandPurpleLight)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("You're sharing your screen", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TextButton(
                                        onClick = { viewModel.stopScreenShare() },
                                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                                    ) {
                                        Text("Stop")
                                    }
                                }
                            }
                        }

                        // Video Grid Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(bottom = 24.dp)
                        ) {
                            val localParticipant = uiState.participants.firstOrNull { it is io.livekit.android.room.participant.LocalParticipant }
                            val remoteParticipants = uiState.participants.filter { it !is io.livekit.android.room.participant.LocalParticipant }
                            
                            ParticipantGrid(
                                room = uiState.room,
                                participants = remoteParticipants, 
                                allParticipants = uiState.participants, 
                                updateCounter = uiState.updateCounter
                            )

                            // Floating Self Preview (PIP)
                            if (localParticipant != null) {
                                SelfPreviewPIP(
                                    room = uiState.room,
                                    participant = localParticipant,
                                    isCameraOff = !uiState.isCameraEnabled,
                                    updateTrigger = uiState.updateCounter,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 16.dp, end = 16.dp)
                                )
                            }
                        }

                        // Bottom Control Bar
                        BottomControlBar(
                            isMicMuted = !uiState.isMicEnabled,
                            isCameraOff = !uiState.isCameraEnabled,
                            onMicToggle = { 
                                if (!uiState.isMicEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleMic() 
                                }
                            },
                            onCameraToggle = { 
                                if (!uiState.isCameraEnabled && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    viewModel.toggleCamera() 
                                }
                            },
                            onSwitchCamera = { viewModel.switchCamera() },
                            onMoreClick = { showHostControls = true },
                            onLeaveClick = { showLeaveConfirmation = true },
                            enabled = (state is RoomState.Connected)
                        )
                    }
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
                                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                                ) { Text("End Meeting") } 
                            },
                            dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } }
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
                                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                                ) { Text("Remove") } 
                            },
                            dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } }
                        )
                    }
                    is ConfirmationDialogState.PromoteParticipant -> {
                        AlertDialog(
                            onDismissRequest = { hostViewModel.dismissConfirmation() },
                            title = { Text("Promote ${conf.name}?", fontWeight = FontWeight.Bold) },
                            text = { Text("Promote ${conf.name} to Co-host?") },
                            confirmButton = { Button(onClick = { hostViewModel.promoteCohostConfirmed(conf.participantId) }) { Text("Promote") } },
                            dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } }
                        )
                    }
                    is ConfirmationDialogState.DemoteParticipant -> {
                        AlertDialog(
                            onDismissRequest = { hostViewModel.dismissConfirmation() },
                            title = { Text("Demote ${conf.name}?", fontWeight = FontWeight.Bold) },
                            text = { Text("Demote ${conf.name} to regular participant?") },
                            confirmButton = { Button(onClick = { hostViewModel.demoteCohostConfirmed(conf.participantId) }) { Text("Demote") } },
                            dismissButton = { TextButton(onClick = { hostViewModel.dismissConfirmation() }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMeetingBar(meetingCode: String, participantCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, top = 8.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Meeting Room",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.025).sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Code: $meetingCode • $participantCount Participants",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ParticipantGrid(
    room: io.livekit.android.room.Room?, 
    participants: List<Participant>, 
    allParticipants: List<Participant>, 
    updateCounter: Int
) {
    if (participants.isEmpty() && allParticipants.size <= 1) { 
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Waiting for others...",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted
            )
        }
        return
    }

    val screenShareParticipant = allParticipants.firstOrNull { p ->
        p.videoTrackPublications.any { it.first.source == Track.Source.SCREEN_SHARE && !it.first.muted }
    }

    if (screenShareParticipant != null) {
        val screenTrack = screenShareParticipant.videoTrackPublications.firstOrNull { it.first.source == Track.Source.SCREEN_SHARE }?.first?.track as? VideoTrack
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(SurfaceDark)
            ) {
                if (screenTrack != null) {
                    LiveKitVideoRenderer(
                        room = room,
                        videoTrack = screenTrack,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topEnd = 16.dp),
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Text(
                        text = "${screenShareParticipant.name ?: "Someone"} is sharing their screen",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
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
        if (participants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Waiting for others...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMuted
                )
            }
            return
        }

        when (participants.size) {
            1 -> {
                ParticipantTile(
                    room = room,
                    participant = participants[0],
                    updateTrigger = updateCounter,
                    modifier = Modifier.fillMaxSize()
                )
            }
            2 -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ParticipantTile(
                        room = room,
                        participant = participants[0],
                        updateTrigger = updateCounter,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    ParticipantTile(
                        room = room,
                        participant = participants[1],
                        updateTrigger = updateCounter,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(participants, key = { it.sid.value ?: it.identity?.value ?: it.hashCode().toString() }) { participant ->
                        ParticipantTile(
                            room = room, 
                            participant = participant, 
                            updateTrigger = updateCounter,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                    }
                }
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

    val borderColor = if (isSpeaking) BrandPurple else Color.White.copy(alpha = 0.05f)
    val borderWidth = if (isSpeaking) 2.dp else 1.dp
    val shadowColor = if (isSpeaking) BrandPurple.copy(alpha = 0.1f) else Color.Transparent

    Box(
        modifier = modifier
            .shadow(elevation = if(isSpeaking) 12.dp else 0.dp, shape = RoundedCornerShape(32.dp), spotColor = shadowColor, ambientColor = shadowColor)
            .clip(RoundedCornerShape(32.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(32.dp))
            .background(SurfaceDark)
    ) {
        if (videoTrack != null && !isVideoMuted) {
            LiveKitVideoRenderer(
                room = room,
                videoTrack = videoTrack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                ParticipantAvatar(
                    name = participant.name ?: participant.identity?.value ?: "?",
                    size = 80
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 100f
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = participant.name ?: participant.identity?.value ?: "User",
                color = if (isSpeaking) Color.White else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
        ) {
            if (isSpeaking) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BrandPurple.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq, 
                        contentDescription = "Speaking",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else if (isAudioMuted) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Muted",
                        tint = DangerRed,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelfPreviewPIP(
    room: io.livekit.android.room.Room?,
    participant: Participant,
    isCameraOff: Boolean,
    updateTrigger: Int,
    modifier: Modifier = Modifier
) {
    val videoTracks = remember(updateTrigger, participant) { participant.videoTrackPublications }
    val videoTrack = remember(updateTrigger, videoTracks) { 
        videoTracks.firstOrNull { it.first.source == Track.Source.CAMERA }?.first?.track as? VideoTrack 
    }

    Box(
        modifier = modifier
            .size(width = 112.dp, height = 160.dp)
            .shadow(24.dp, RoundedCornerShape(16.dp))
            .border(4.dp, Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF262626)) 
            .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
    ) {
        if (!isCameraOff && videoTrack != null) {
            LiveKitVideoRenderer(
                room = room,
                videoTrack = videoTrack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF334155).copy(alpha = 0.2f)), 
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideocamOff,
                    contentDescription = "Camera Off",
                    tint = TextMuted,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "You",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!isCameraOff) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Emerald.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomControlBar(
    isMicMuted: Boolean,
    isCameraOff: Boolean,
    onMicToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onMoreClick: () -> Unit,
    onLeaveClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = BottomBarBackground,
        shape = RoundedCornerShape(40.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(40.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(40.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryButton(
                    icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    isActive = !isMicMuted,
                    onClick = onMicToggle,
                    enabled = enabled
                )
                
                PrimaryButton(
                    icon = if (isCameraOff) Icons.Outlined.VideocamOff else Icons.Default.Videocam,
                    isActive = !isCameraOff,
                    onClick = onCameraToggle,
                    enabled = enabled
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCameraOff) {
                    SecondaryButton(
                        icon = Icons.Default.Cameraswitch,
                        onClick = onSwitchCamera,
                        enabled = enabled
                    )
                }
                SecondaryButton(
                    icon = Icons.Default.MoreVert, 
                    onClick = onMoreClick,
                    enabled = enabled
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DangerRed,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .height(48.dp)
                    .clickable(enabled = true, onClick = onLeaveClick)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "LEAVE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val bgColor = if (isActive) BrandPurple else Color.White.copy(alpha = 0.1f)
    val borderColor = if (isActive) BrandPurpleLight.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .border(1.dp, borderColor, CircleShape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SecondaryButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
            .size(40.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
