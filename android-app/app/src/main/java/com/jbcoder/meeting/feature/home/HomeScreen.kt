package com.jbcoder.meeting.feature.home

import androidx.compose.ui.tooling.preview.Preview
import com.jbcoder.meeting.presentation.theme.MeetingTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.feature.home.components.HomeHeadline
import com.jbcoder.meeting.feature.home.components.MeetingStatusIndicator
import com.jbcoder.meeting.feature.home.components.MinimalMeetingMark
import com.jbcoder.meeting.feature.home.components.PrimaryMeetingButton
import com.jbcoder.meeting.feature.home.components.SecondaryMeetingButton

@Composable
fun HomeScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToJoin: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    HomeScreenContent(
        uiState = uiState,
        onNavigateToCreate = onNavigateToCreate,
        onNavigateToJoin = onNavigateToJoin,
        onRetry = viewModel::retry
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onNavigateToCreate: () -> Unit,
    onNavigateToJoin: () -> Unit,
    onRetry: () -> Unit
) {
    val isReady = uiState is HomeUiState.Ready

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // 1. App Header
                MinimalMeetingMark()

                Spacer(modifier = Modifier.height(48.dp))

                // 2. Main Headline and Supporting Text
                HomeHeadline()

                Spacer(modifier = Modifier.height(48.dp))

                // 3. Actions
                PrimaryMeetingButton(
                    text = "Create Meeting",
                    onClick = onNavigateToCreate,
                    enabled = isReady
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                SecondaryMeetingButton(
                    text = "Join with Code",
                    onClick = onNavigateToJoin,
                    enabled = isReady
                )

                // 4. Flexible Space to push status to bottom
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(48.dp))

                // 5. Connection Status and Retry
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MeetingStatusIndicator(status = uiState)

                    if (uiState is HomeUiState.Offline || uiState is HomeUiState.Error) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onRetry) {
                            Text("Retry Connection")
                        }
                    }
                }
            }
        }
    }
}

// --- Previews ---




@Preview(showBackground = true, name = "Light Ready")
@Composable
fun HomeScreenLightPreview() {
    MeetingTheme(darkTheme = false) {
        HomeScreenContent(
            uiState = HomeUiState.Ready,
            onNavigateToCreate = {},
            onNavigateToJoin = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Ready")
@Composable
fun HomeScreenDarkPreview() {
    MeetingTheme(darkTheme = true) {
        HomeScreenContent(
            uiState = HomeUiState.Ready,
            onNavigateToCreate = {},
            onNavigateToJoin = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Initializing")
@Composable
fun HomeScreenInitializingPreview() {
    MeetingTheme {
        HomeScreenContent(
            uiState = HomeUiState.Initializing,
            onNavigateToCreate = {},
            onNavigateToJoin = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Offline", widthDp = 320)
@Composable
fun HomeScreenOfflineNarrowPreview() {
    MeetingTheme {
        HomeScreenContent(
            uiState = HomeUiState.Offline("Network unreachable"),
            onNavigateToCreate = {},
            onNavigateToJoin = {},
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, name = "Large Font", fontScale = 2f)
@Composable
fun HomeScreenLargeFontPreview() {
    MeetingTheme {
        HomeScreenContent(
            uiState = HomeUiState.Ready,
            onNavigateToCreate = {},
            onNavigateToJoin = {},
            onRetry = {}
        )
    }
}

