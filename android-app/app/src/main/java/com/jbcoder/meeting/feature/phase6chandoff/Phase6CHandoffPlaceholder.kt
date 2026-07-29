package com.jbcoder.meeting.feature.phase6chandoff

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoff
import com.jbcoder.meeting.data.meeting.RoomConnectionHandoffStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class Phase6CHandoffViewModel @Inject constructor(
    private val handoffStore: RoomConnectionHandoffStore
) : ViewModel() {
    val handoffState = handoffStore.handoff
    
    fun onLeave() {
        handoffStore.clear()
    }
}

@Composable
fun Phase6CHandoffPlaceholder(
    onNavigateHome: () -> Unit,
    viewModel: Phase6CHandoffViewModel = hiltViewModel()
) {
    val handoff by viewModel.handoffState.collectAsState()
    val context = LocalContext.current

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (handoff == null) {
                Text("Error: LiveKit connection handoff missing. Returning home.")
                Button(onClick = { viewModel.onLeave(); onNavigateHome() }) {
                    Text("Go Home")
                }
            } else {
                Text(
                    text = "Phase 6D Placeholder (Live Room)",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val code = when (val h = handoff) {
                    is RoomConnectionHandoff.HostReady -> h.publicMeetingCode
                    is RoomConnectionHandoff.ParticipantReady -> h.publicMeetingCode
                    else -> ""
                }

                Text("Meeting Code: $code", style = MaterialTheme.typography.bodyLarge)
                
                when (val h = handoff) {
                    is RoomConnectionHandoff.HostReady -> {
                        Text("Role: Host", style = MaterialTheme.typography.bodyLarge)
                        Text("LiveKit Room: ${h.livekitRoomName}", style = MaterialTheme.typography.labelSmall)
                        Text("Token Length: ${h.livekitToken.length}", style = MaterialTheme.typography.labelSmall)
                    }
                    is RoomConnectionHandoff.ParticipantReady -> {
                        Text("Role: Participant", style = MaterialTheme.typography.bodyLarge)
                        Text("Display Name: ${h.displayName}", style = MaterialTheme.typography.labelSmall)
                        Text("Token Length: ${h.livekitToken.length}", style = MaterialTheme.typography.labelSmall)
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Join my meeting using code: $code")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("Share Meeting Code")
                }

                OutlinedButton(
                    onClick = {
                        viewModel.onLeave()
                        onNavigateHome()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Leave Meeting")
                }
            }
        }
    }
}
