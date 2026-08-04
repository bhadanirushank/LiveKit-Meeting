package com.jbcoder.meeting.feature.room

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.R
import com.jbcoder.meeting.core.designsystem.*
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.Track

enum class RoomSubScreen {
    MAIN, CHAT, PARTICIPANTS
}

@Composable
fun LiveRoomScreen(
    viewModel: LiveRoomViewModel = hiltViewModel(),
    hostViewModel: HostModerationViewModel = hiltViewModel(),
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val hostState by hostViewModel.state.collectAsState()
    
    var subScreen by remember { mutableStateOf(RoomSubScreen.MAIN) }

    BackHandler(enabled = subScreen != RoomSubScreen.MAIN) {
        subScreen = RoomSubScreen.MAIN
    }

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

    hostState.activeConfirmation?.let { dialogState ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { hostViewModel.dismissConfirmation() },
            title = {
                val title = when (dialogState) {
                    is ConfirmationDialogState.RemoveParticipant -> "Remove Participant"
                    is ConfirmationDialogState.EndMeeting -> "End Meeting"
                    is ConfirmationDialogState.PromoteParticipant -> "Promote to Co-host"
                    is ConfirmationDialogState.DemoteParticipant -> "Demote to Participant"
                }
                Text(title)
            },
            text = {
                val text = when (dialogState) {
                    is ConfirmationDialogState.RemoveParticipant -> "Are you sure you want to remove ${dialogState.name}?"
                    is ConfirmationDialogState.EndMeeting -> "Are you sure you want to end this meeting for everyone?"
                    is ConfirmationDialogState.PromoteParticipant -> "Are you sure you want to promote ${dialogState.name} to co-host?"
                    is ConfirmationDialogState.DemoteParticipant -> "Are you sure you want to demote ${dialogState.name}?"
                }
                Text(text)
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        when (dialogState) {
                            is ConfirmationDialogState.RemoveParticipant -> hostViewModel.removeParticipantConfirmed(dialogState.participantId)
                            is ConfirmationDialogState.EndMeeting -> hostViewModel.endMeetingConfirmed()
                            is ConfirmationDialogState.PromoteParticipant -> hostViewModel.promoteCohostConfirmed(dialogState.participantId)
                            is ConfirmationDialogState.DemoteParticipant -> hostViewModel.demoteCohostConfirmed(dialogState.participantId)
                        }
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { hostViewModel.dismissConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    MaterialTheme(colorScheme = LiveRoomColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MeetingBackground)
                .systemBarsPadding()
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
                    val configuration = LocalConfiguration.current
                    val isExpanded = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    
                    if (isExpanded) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Main Content (Video)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                MainRoomContent(
                                    uiState = uiState,
                                    onNavigateHome = onNavigateHome,
                                    onOpenChat = { subScreen = RoomSubScreen.CHAT },
                                    onOpenParticipants = { subScreen = RoomSubScreen.PARTICIPANTS },
                                    onStartScreenShare = {
                                        screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                    },
                                    onStopScreenShare = { viewModel.stopScreenShare() },
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
                                    onDisconnect = {
                                        viewModel.disconnect()
                                        onNavigateHome()
                                    }
                                )
                            }
                            
                            // Side Panel
                            if (subScreen != RoomSubScreen.MAIN) {
                                Box(
                                    modifier = Modifier
                                        .width(360.dp)
                                        .fillMaxHeight()
                                        .background(MeetingBackground)
                                ) {
                                    if (subScreen == RoomSubScreen.CHAT) {
                                        ChatScreen(
                                            messages = uiState.chatMessages,
                                            onSendMessage = { viewModel.sendChatMessage(it) },
                                            onBack = { subScreen = RoomSubScreen.MAIN }
                                        )
                                    } else if (subScreen == RoomSubScreen.PARTICIPANTS) {
                                        ParticipantsScreen(
                                            state = hostState,
                                            onBack = { subScreen = RoomSubScreen.MAIN },
                                            onMuteParticipant = { hostViewModel.muteParticipant(it) },
                                            onAskToUnmute = { hostViewModel.askToUnmute(it) },
                                            onDisablePublishing = { hostViewModel.disablePublishing(it) },
                                            onRestorePublishing = { hostViewModel.restorePublishing(it) },
                                            onRemoveParticipantClick = { id, name -> 
                                                hostViewModel.showConfirmation(ConfirmationDialogState.RemoveParticipant(id, name))
                                            },
                                            onPromoteClick = { id, name -> 
                                                hostViewModel.showConfirmation(ConfirmationDialogState.PromoteParticipant(id, name))
                                            },
                                            onDemoteClick = { id, name -> 
                                                hostViewModel.showConfirmation(ConfirmationDialogState.DemoteParticipant(id, name))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Mobile Portrait mode
                        if (subScreen == RoomSubScreen.CHAT) {
                            ChatScreen(
                                messages = uiState.chatMessages,
                                onSendMessage = { viewModel.sendChatMessage(it) },
                                onBack = { subScreen = RoomSubScreen.MAIN }
                            )
                        } else if (subScreen == RoomSubScreen.PARTICIPANTS) {
                            ParticipantsScreen(
                                state = hostState,
                                onBack = { subScreen = RoomSubScreen.MAIN },
                                onMuteParticipant = { hostViewModel.muteParticipant(it) },
                                onAskToUnmute = { hostViewModel.askToUnmute(it) },
                                onDisablePublishing = { hostViewModel.disablePublishing(it) },
                                onRestorePublishing = { hostViewModel.restorePublishing(it) },
                                onRemoveParticipantClick = { id, name -> 
                                    hostViewModel.showConfirmation(ConfirmationDialogState.RemoveParticipant(id, name))
                                },
                                onPromoteClick = { id, name -> 
                                    hostViewModel.showConfirmation(ConfirmationDialogState.PromoteParticipant(id, name))
                                },
                                onDemoteClick = { id, name -> 
                                    hostViewModel.showConfirmation(ConfirmationDialogState.DemoteParticipant(id, name))
                                }
                            )
                        } else {
                            MainRoomContent(
                                uiState = uiState,
                                onNavigateHome = onNavigateHome,
                                onOpenChat = { subScreen = RoomSubScreen.CHAT },
                                onOpenParticipants = { subScreen = RoomSubScreen.PARTICIPANTS },
                                onStartScreenShare = {
                                    screenShareLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                                },
                                onStopScreenShare = { viewModel.stopScreenShare() },
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
                                onDisconnect = {
                                    viewModel.disconnect()
                                    onNavigateHome()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainRoomContent(
    uiState: RoomUiState,
    onNavigateHome: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenParticipants: () -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 16.dp, start = 12.dp, end = 12.dp)
    ) {
        TopMeetingBar(
            meetingCode = uiState.meetingCode,
            durationSeconds = uiState.meetingDurationSeconds,
            participantCount = uiState.participants.size,
            onOpenParticipants = onOpenParticipants,
            onStartScreenShare = onStartScreenShare
        )
        
        if (uiState.roomState is RoomState.Reconnecting) {
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
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 24.dp)
        ) {
            val parentWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
            val parentHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }

            val localParticipant = uiState.participants.firstOrNull { it is io.livekit.android.room.participant.LocalParticipant }
            val remoteParticipants = uiState.participants.filter { it !is io.livekit.android.room.participant.LocalParticipant }
            val activeSpeaker = remoteParticipants.firstOrNull { it.isSpeaking } ?: remoteParticipants.firstOrNull()
            
            if (uiState.isScreenSharing) {
                // Presenting State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFF3F4F6)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("You are presenting to everyone", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onStopScreenShare,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            Text("Stop Presenting", color = Color.White)
                        }
                    }
                }
                
                // Small PIP for Local User
                if (localParticipant != null) {
                    SelfPreviewPIP(
                        room = uiState.room,
                        participant = localParticipant,
                        localDisplayName = uiState.localDisplayName,
                        isCameraOff = !uiState.isCameraEnabled,
                        updateTrigger = uiState.updateCounter,
                        parentWidthPx = parentWidthPx,
                        parentHeightPx = parentHeightPx,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 16.dp, end = 16.dp)
                    )
                }
            } else {
                // Normal layout - show dominant speaker
                if (activeSpeaker != null) {
                    DominantParticipantTile(
                        room = uiState.room,
                        participant = activeSpeaker,
                        localDisplayName = uiState.localDisplayName,
                        updateTrigger = uiState.updateCounter,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (localParticipant != null) {
                        SelfPreviewPIP(
                            room = uiState.room,
                            participant = localParticipant,
                            localDisplayName = uiState.localDisplayName,
                            isCameraOff = !uiState.isCameraEnabled,
                            updateTrigger = uiState.updateCounter,
                            parentWidthPx = parentWidthPx,
                            parentHeightPx = parentHeightPx,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 16.dp, end = 16.dp)
                        )
                    }
                } else if (localParticipant != null) {
                    // Only me
                    DominantParticipantTile(
                        room = uiState.room,
                        participant = localParticipant,
                        localDisplayName = uiState.localDisplayName,
                        updateTrigger = uiState.updateCounter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        
        BottomControlBar(
            isMicMuted = !uiState.isMicEnabled,
            isCameraOff = !uiState.isCameraEnabled,
            onMicToggle = onToggleMic,
            onCameraToggle = onToggleCamera,
            onSwitchCamera = onSwitchCamera,
            onChatClick = onOpenChat,
            onLeaveClick = onDisconnect,
            enabled = (uiState.roomState is RoomState.Connected)
        )
    }
}

@Composable
private fun TopMeetingBar(
    meetingCode: String,
    durationSeconds: Long,
    participantCount: Int,
    onOpenParticipants: () -> Unit,
    onStartScreenShare: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val timeStr = String.format("%02d:%02d:%02d", minutes / 60, minutes % 60, seconds)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = meetingCode,
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = timeStr,
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(meetingCode))
                Toast.makeText(context, "Meeting code copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Code", tint = Color.Black)
            }
            IconButton(onClick = {
                val sendIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Join my video meeting! Code: $meetingCode")
                    type = "text/plain"
                }
                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.Black)
            }
            
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.Black)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF333333))
                ) {
                    DropdownMenuItem(
                        text = { Text("Share screen", color = Color.White) },
                        onClick = { 
                            expanded = false
                            onStartScreenShare()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, tint = Color.White)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Participants ($participantCount)", color = Color.White) },
                        onClick = {
                            expanded = false
                            onOpenParticipants()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Group, contentDescription = null, tint = Color.White)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DominantParticipantTile(
    room: io.livekit.android.room.Room?, 
    participant: Participant,
    localDisplayName: String = "",
    updateTrigger: Int,
    modifier: Modifier = Modifier
) {
    val videoTracks = remember(updateTrigger, participant) { participant.videoTrackPublications }
    val audioTracks = remember(updateTrigger, participant) { participant.audioTrackPublications }
    val isLocal = participant is io.livekit.android.room.participant.LocalParticipant
    
    val activeVideoPub = remember(updateTrigger, videoTracks) {
        videoTracks.firstOrNull { 
            (it.first.source == Track.Source.SCREEN_SHARE || it.first.name?.contains("screen", ignoreCase = true) == true) && 
            it.first.track != null 
        } ?: videoTracks.firstOrNull { it.first.source == Track.Source.CAMERA }
    }
    
    val videoTrack = activeVideoPub?.first?.track as? VideoTrack
    val isScreenShareTrack = activeVideoPub?.first?.source == Track.Source.SCREEN_SHARE || activeVideoPub?.first?.name?.contains("screen", ignoreCase = true) == true
    val isVideoMuted = activeVideoPub?.first?.muted ?: true
    val isAudioMuted = remember(updateTrigger, audioTracks) { audioTracks.firstOrNull()?.first?.muted ?: true }

    val participantName = if (isLocal && localDisplayName.isNotBlank()) localDisplayName else participant.name?.takeIf { it.isNotBlank() } ?: participant.identity?.value ?: "Unknown"
    val displayName = if (isLocal) "You" else participantName
    val initial = participantName.firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF9CA3AF)),
        contentAlignment = Alignment.Center
    ) {
        if (videoTrack != null && !isVideoMuted) {
            LiveKitVideoRenderer(
                room = room,
                videoTrack = videoTrack,
                scaleType = livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT,
                isMirrored = isLocal && !isScreenShareTrack
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9CA3AF),
                        fontSize = 36.sp
                    )
                }
            }
        }

        // Top Left: "You" badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 16.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Top Right: Speaker icon if muted
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Audio Status",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SelfPreviewPIP(
    room: io.livekit.android.room.Room?,
    participant: Participant,
    localDisplayName: String = "",
    isCameraOff: Boolean,
    updateTrigger: Int,
    parentWidthPx: Float = Float.MAX_VALUE,
    parentHeightPx: Float = Float.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val videoTracks = remember(updateTrigger, participant) { participant.videoTrackPublications }
    val videoTrack = remember(updateTrigger, videoTracks) { 
        videoTracks.firstOrNull { it.first.source == Track.Source.CAMERA }?.first?.track as? VideoTrack 
    }
    
    val isLocal = participant is io.livekit.android.room.participant.LocalParticipant
    val participantName = if (isLocal && localDisplayName.isNotBlank()) localDisplayName else participant.name?.takeIf { it.isNotBlank() } ?: participant.identity?.value ?: "Unknown"
    val initial = participantName.firstOrNull()?.uppercase() ?: "?"

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val pipWidthDp = 100.dp
    val pipHeightDp = 150.dp
    val paddingDp = 16.dp
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pipWidthPx = with(density) { pipWidthDp.toPx() }
    val pipHeightPx = with(density) { pipHeightDp.toPx() }
    val paddingPx = with(density) { paddingDp.toPx() }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Since it's anchored BottomEnd, offsetX/Y is 0 at the bottom-right corner.
                    // The minimum X is when the left edge hits the left side of the parent.
                    // Left edge in relative coordinates = -(parentWidth - pipWidth - padding)
                    val minX = -(parentWidthPx - pipWidthPx - paddingPx * 2)
                    val maxX = 0f
                    
                    val minY = -(parentHeightPx - pipHeightPx - paddingPx * 2)
                    val maxY = 0f

                    offsetX = (offsetX + dragAmount.x).coerceIn(minX, maxX)
                    offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
                }
            }
            .size(width = pipWidthDp, height = pipHeightDp)
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF9CA3AF)) 
    ) {
        if (!isCameraOff && videoTrack != null) {
            LiveKitVideoRenderer(
                room = room,
                videoTrack = videoTrack,
                isMirrored = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(), 
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9CA3AF),
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LiveKitVideoRenderer(
    room: io.livekit.android.room.Room?,
    videoTrack: VideoTrack,
    scaleType: livekit.org.webrtc.RendererCommon.ScalingType = livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL,
    isMirrored: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (room == null) return
    var currentTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var videoRatio by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(videoTrack) {
        val sink = livekit.org.webrtc.VideoSink { frame ->
            val w = frame.rotatedWidth
            val h = frame.rotatedHeight
            if (w > 0 && h > 0) {
                val ratio = w.toFloat() / h.toFloat()
                if (videoRatio != ratio) {
                    videoRatio = ratio
                }
            }
        }
        videoTrack.addRenderer(sink)
        onDispose {
            videoTrack.removeRenderer(sink)
        }
    }

    val dynamicModifier = if (videoRatio != null && scaleType == livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT) {
        modifier.aspectRatio(videoRatio!!, matchHeightConstraintsFirst = false)
    } else {
        modifier
    }

    key(scaleType) {
        AndroidView(
            factory = { context ->
                livekit.org.webrtc.SurfaceViewRenderer(context).apply {
                    room.initVideoRenderer(this)
                    setMirror(isMirrored)
                    setScalingType(scaleType)
                    setEnableHardwareScaler(true)
                }
            },
            update = { view ->
                view.setMirror(isMirrored)
                view.setScalingType(scaleType)
                
                if (currentTrack != videoTrack) {
                    currentTrack?.removeRenderer(view)
                    videoTrack.addRenderer(view)
                    currentTrack = videoTrack
                }
            },
            onRelease = { view ->
                currentTrack?.removeRenderer(view)
                view.release()
            },
            modifier = dynamicModifier
        )
    }
}

@Composable
private fun BottomControlBar(
    isMicMuted: Boolean,
    isCameraOff: Boolean,
    onMicToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onSwitchCamera: () -> Unit,
    onChatClick: () -> Unit,
    onLeaveClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(40.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(40.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCameraToggle, enabled = enabled) {
                Icon(
                    imageVector = if (isCameraOff) Icons.Outlined.VideocamOff else Icons.Default.Videocam,
                    contentDescription = "Toggle Camera",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onMicToggle, enabled = enabled) {
                Icon(
                    imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Toggle Mic",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = onLeaveClick, 
                enabled = enabled,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFEF4444), CircleShape)
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onSwitchCamera, enabled = enabled && !isCameraOff) {
                Icon(
                    Icons.Default.FlipCameraIos,
                    contentDescription = "Switch Camera",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onChatClick, enabled = enabled) {
                Icon(
                    Icons.Outlined.Chat,
                    contentDescription = "Chat",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
