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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
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
        onRetry = viewModel::retry,
        currentIp = viewModel.getCustomIp(),
        onSaveIp = { viewModel.saveCustomIp(it) }
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onNavigateToCreate: () -> Unit,
    onNavigateToJoin: () -> Unit,
    onRetry: () -> Unit,
    currentIp: String = "",
    onSaveIp: (String) -> Unit = {}
) {
    val isReady = uiState is HomeUiState.Ready
    var showDevDialog by remember { mutableStateOf(false) }
    var ipInput by remember { mutableStateOf(currentIp) }
    
    val configuration = LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            val currentMaxHeight = maxHeight
            
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    // Left Column: Branding and Headline
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MinimalMeetingMark()
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                        HomeHeadline()
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                    
                    // Right Column: Actions and Status
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { 
                                ipInput = currentIp
                                showDevDialog = true 
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Developer Settings", tint = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
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
                        Spacer(modifier = Modifier.height(48.dp))
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
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = currentMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // 1. App Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MinimalMeetingMark()
                        IconButton(onClick = { 
                            ipInput = currentIp
                            showDevDialog = true 
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Developer Settings", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }

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

    if (showDevDialog) {
        AlertDialog(
            onDismissRequest = { showDevDialog = false },
            title = { Text("Developer Settings") },
            text = {
                Column {
                    Text("Backend IP Address:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        placeholder = { Text("e.g. 192.168.8.224") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveIp(ipInput.trim())
                    showDevDialog = false
                }) { Text("Save & Restart") }
            },
            dismissButton = {
                TextButton(onClick = { showDevDialog = false }) { Text("Cancel") }
            }
        )
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

