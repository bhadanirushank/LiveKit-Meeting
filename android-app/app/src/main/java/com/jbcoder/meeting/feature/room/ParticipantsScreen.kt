package com.jbcoder.meeting.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.LocalParticipant
import com.jbcoder.meeting.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsScreen(
    state: HostControlsUiState,
    onBack: () -> Unit,
    onMuteParticipant: (String) -> Unit,
    onAskToUnmute: (String) -> Unit,
    onDisablePublishing: (String) -> Unit,
    onRestorePublishing: (String) -> Unit,
    onRemoveParticipantClick: (String, String) -> Unit,
    onPromoteClick: (String, String) -> Unit,
    onDemoteClick: (String, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopAppBar(
            title = { Text("Participants", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black
            )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(state.participants, key = { it.id }) { participant ->
                    ModerationParticipantRow(
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
