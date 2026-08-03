package com.jbcoder.meeting.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jbcoder.meeting.core.designsystem.*
// import com.jbcoder.meeting.presentation.theme.MeetingPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuSheet(
    state: HostControlsUiState,
    isScreenSharing: Boolean,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onMuteParticipant: (String) -> Unit,
    onAskToUnmute: (String) -> Unit,
    onDisablePublishing: (String) -> Unit,
    onRestorePublishing: (String) -> Unit,
    onRemoveParticipantClick: (String, String) -> Unit,
    onPromoteClick: (String, String) -> Unit,
    onDemoteClick: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "More Options",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Meeting Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isScreenSharing) {
                    Button(
                        onClick = {
                            onStopScreenShare()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.StopScreenShare, contentDescription = "Stop sharing")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop sharing")
                    }
                } else {
                    Button(
                        onClick = {
                            onStartScreenShare()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.ScreenShare, contentDescription = "Share screen")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share screen")
                    }
                }
            }

            Text(
                text = "Participants (${state.participants.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.participants, key = { it.id }) { participant ->
                    ParticipantRow(
                        participant = participant,
                        amIHost = state.role == MeetingRole.HOST,
                        onMute = { onMuteParticipant(participant.id) },
                        onAskToUnmute = { onAskToUnmute(participant.id) },
                        onDisablePublishing = { onDisablePublishing(participant.id) },
                        onRestorePublishing = { onRestorePublishing(participant.id) },
                        onRemoveClick = { onRemoveParticipantClick(participant.id, participant.displayName) },
                        onPromoteClick = { onPromoteClick(participant.id, participant.displayName) },
                        onDemoteClick = { onDemoteClick(participant.id, participant.displayName) }
                    )
                }
            }
        }
    }
}

@Composable
fun ParticipantRow(
    participant: ModerationParticipantUi,
    amIHost: Boolean,
    onMute: () -> Unit,
    onAskToUnmute: () -> Unit,
    onDisablePublishing: () -> Unit,
    onRestorePublishing: () -> Unit,
    onRemoveClick: () -> Unit,
    onPromoteClick: () -> Unit,
    onDemoteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParticipantAvatar(name = participant.displayName, size = 48)

            Spacer(modifier = Modifier.width(16.dp))

            // Name and Badge
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = participant.displayName + if (participant.isLocal) " (You)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (participant.role != MeetingRole.PARTICIPANT) {
                        Spacer(modifier = Modifier.width(8.dp))
                        MeetingStatusChip(
                            text = participant.role.name.replace("_", " "),
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (participant.isPublishRestricted) {
                    Text(
                        text = "Publishing restricted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Media states
            Icon(
                imageVector = if (participant.isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (participant.isMicOn) "Mic on" else "Mic off",
                tint = if (participant.isMicOn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (participant.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                contentDescription = if (participant.isCameraOn) "Camera on" else "Camera off",
                tint = if (participant.isCameraOn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )

            // Action Menu
            if (amIHost && !participant.isLocal && participant.role != MeetingRole.HOST) {
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        if (participant.actionState == ParticipantActionState.LOADING) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${participant.displayName}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (participant.isMicOn) {
                            DropdownMenuItem(
                                text = { Text("Mute") },
                                onClick = { onMute(); menuExpanded = false }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Ask to Unmute") },
                                onClick = { onAskToUnmute(); menuExpanded = false }
                            )
                        }
                        if (participant.isPublishRestricted) {
                            DropdownMenuItem(
                                text = { Text("Restore Publishing") },
                                onClick = { onRestorePublishing(); menuExpanded = false }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Disable Publishing") },
                                onClick = { onDisablePublishing(); menuExpanded = false }
                            )
                        }
                        if (participant.role == MeetingRole.CO_HOST) {
                            DropdownMenuItem(
                                text = { Text("Demote to Participant") },
                                onClick = { onDemoteClick(); menuExpanded = false }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Promote to Co-host") },
                                onClick = { onPromoteClick(); menuExpanded = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove from Meeting", color = MaterialTheme.colorScheme.error) },
                            onClick = { onRemoveClick(); menuExpanded = false }
                        )
                    }
                }
            }
        }
    }
}
