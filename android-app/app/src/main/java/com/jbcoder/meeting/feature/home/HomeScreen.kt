package com.jbcoder.meeting.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.MeetingPrimaryButton
import com.jbcoder.meeting.core.designsystem.MeetingSecondaryButton

@Composable
fun HomeScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToJoin: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LiveKit Meetings",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "Secure ephemeral meetings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            MeetingPrimaryButton(
                text = "Create Meeting",
                onClick = onNavigateToCreate,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            MeetingSecondaryButton(
                text = "Join Meeting",
                onClick = onNavigateToJoin,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            val statusText = if (isConnected) "Connected" else "Offline"
            val statusColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }
    }
}
