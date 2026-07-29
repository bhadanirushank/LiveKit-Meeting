package com.jbcoder.meeting.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostControlsSheet(
    state: HostControlsUiState,
    onLockMeeting: () -> Unit,
    onUnlockMeeting: () -> Unit,
    onEndMeetingClick: () -> Unit,
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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Host Controls",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Meeting wide actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (state.meetingLocked) {
                    Button(onClick = onUnlockMeeting, enabled = !state.isActionLoading) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Unlock")
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock Meeting")
                    }
                } else {
                    Button(onClick = onLockMeeting, enabled = !state.isActionLoading) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock")
                        Spacer(Modifier.width(8.dp))
                        Text("Lock Meeting")
                    }
                }

                Button(
                    onClick = onEndMeetingClick,
                    enabled = !state.isActionLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "End")
                    Spacer(Modifier.width(8.dp))
                    Text("End for All")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Participants (${state.participants.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = participant.displayName.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name and Badge
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = participant.displayName + if (participant.isLocal) " (You)" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (participant.role != MeetingRole.PARTICIPANT) {
                Text(
                    text = participant.role.name.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (participant.isPublishRestricted) {
                Text(
                    text = "Publishing restricted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Media states
        Icon(
            imageVector = if (participant.isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
            contentDescription = if (participant.isMicOn) "Mic on" else "Mic off",
            tint = if (participant.isMicOn) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (participant.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
            contentDescription = if (participant.isCameraOn) "Camera on" else "Camera off",
            tint = if (participant.isCameraOn) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Action Menu
        if (amIHost && !participant.isLocal && participant.role != MeetingRole.HOST) {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    if (participant.actionState == ParticipantActionState.LOADING) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${participant.displayName}")
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
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
