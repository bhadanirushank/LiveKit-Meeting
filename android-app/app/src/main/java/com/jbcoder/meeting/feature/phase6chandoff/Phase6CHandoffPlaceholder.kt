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
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoff
import com.jbcoder.meeting.data.meeting.MeetingEntryHandoffStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class Phase6CHandoffViewModel @Inject constructor(
    private val handoffStore: MeetingEntryHandoffStore
) : ViewModel() {
    val handoffState = handoffStore.currentHandoff
    
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
                Text("Error: Session lost (Process death). Returning home.")
                Button(onClick = { viewModel.onLeave(); onNavigateHome() }) {
                    Text("Go Home")
                }
            } else {
                Text(
                    text = "Phase 6C Handoff",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val code = when (val h = handoff) {
                    is MeetingEntryHandoff.HostCreated -> h.publicMeetingCode
                    is MeetingEntryHandoff.ParticipantRequested -> h.publicMeetingCode
                    else -> ""
                }

                Text("Meeting Code: $code", style = MaterialTheme.typography.bodyLarge)
                
                when (val h = handoff) {
                    is MeetingEntryHandoff.HostCreated -> {
                        Text("Role: Host", style = MaterialTheme.typography.bodyLarge)
                        Text("Host Secret exists in memory", style = MaterialTheme.typography.labelSmall)
                    }
                    is MeetingEntryHandoff.ParticipantRequested -> {
                        Text("Role: Participant", style = MaterialTheme.typography.bodyLarge)
                        Text("Request ID: ${h.requestId}", style = MaterialTheme.typography.labelSmall)
                        Text("Status: ${h.requestStatus}", style = MaterialTheme.typography.labelSmall)
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
                    Text("Leave Entry Flow")
                }
            }
        }
    }
}
