package com.jbcoder.meeting.presentation.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.MeetingInlineError
import com.jbcoder.meeting.core.designsystem.MeetingLoadingIndicator
import com.jbcoder.meeting.core.designsystem.MeetingSecondaryButton

@Composable
fun FoundationScreen(
    onReady: () -> Unit,
    viewModel: FoundationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    FoundationScreenContent(state = state, onReady = onReady, onRetry = { viewModel.retry() })
}

@Composable
fun FoundationScreenContent(
    state: FoundationState,
    onReady: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is FoundationState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MeetingLoadingIndicator()
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Initializing LiveKit Meeting...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            is FoundationState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Initialization Failed",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MeetingInlineError(message = state.message)
                    Spacer(modifier = Modifier.height(32.dp))
                    MeetingSecondaryButton(
                        text = "Retry",
                        onClick = onRetry,
                        modifier = Modifier.width(200.dp)
                    )
                }
            }
            is FoundationState.Offline -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Service Unreachable",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MeetingInlineError(message = state.error ?: "Backend is offline or unreachable.")
                    Spacer(modifier = Modifier.height(32.dp))
                    MeetingSecondaryButton(
                        text = "Retry Connection",
                        onClick = onRetry,
                        modifier = Modifier.width(200.dp)
                    )
                }
            }
            is FoundationState.Ready -> {
                onReady()
            }
        }
    }
}
