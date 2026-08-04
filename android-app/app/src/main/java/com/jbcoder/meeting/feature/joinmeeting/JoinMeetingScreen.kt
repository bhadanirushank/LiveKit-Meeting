package com.jbcoder.meeting.feature.joinmeeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.MeetingInlineError
import com.jbcoder.meeting.core.designsystem.MeetingLoadingIndicator
import com.jbcoder.meeting.core.designsystem.MeetingPrimaryButton
import com.jbcoder.meeting.core.designsystem.MeetingTextField
import com.jbcoder.meeting.core.designsystem.MeetingTopBar
import kotlinx.coroutines.flow.collectLatest

@Composable
fun JoinMeetingScreen(
    onNavigateBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: JoinMeetingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.uiState.collectLatest { state ->
            if (state is JoinMeetingUiState.Success) {
                onSuccess()
            }
        }
    }

    Scaffold(
        topBar = {
            MeetingTopBar(
                title = "Join Meeting",
                onBackClick = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val configuration = LocalConfiguration.current
            val isExpanded = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            
            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Enter meeting details",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Provide the 10-digit code and your name to join.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        MeetingTextField(
                            value = formState.meetingCode,
                            onValueChange = { viewModel.updateMeetingCode(it); viewModel.resetError() },
                            label = "Meeting Code",
                            placeholder = "e.g. 1234567890",
                            enabled = uiState !is JoinMeetingUiState.Loading,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        MeetingTextField(
                            value = formState.displayName,
                            onValueChange = { viewModel.updateDisplayName(it); viewModel.resetError() },
                            label = "Display Name",
                            placeholder = "How others will see you",
                            enabled = uiState !is JoinMeetingUiState.Loading
                        )

                        if (uiState is JoinMeetingUiState.Error) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MeetingInlineError(message = (uiState as JoinMeetingUiState.Error).message)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        MeetingPrimaryButton(
                            text = "Join Meeting",
                            onClick = { viewModel.submit() },
                            enabled = uiState !is JoinMeetingUiState.Loading
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Enter meeting details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Text(
                        text = "Provide the 10-digit code and your name to join.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    MeetingTextField(
                        value = formState.meetingCode,
                        onValueChange = { viewModel.updateMeetingCode(it); viewModel.resetError() },
                        label = "Meeting Code",
                        placeholder = "e.g. 5192837402",
                        enabled = uiState !is JoinMeetingUiState.Loading
                    )

                    MeetingTextField(
                        value = formState.displayName,
                        onValueChange = { viewModel.updateDisplayName(it); viewModel.resetError() },
                        label = "Display Name",
                        placeholder = "How others will see you",
                        enabled = uiState !is JoinMeetingUiState.Loading
                    )



                    if (uiState is JoinMeetingUiState.Error) {
                        MeetingInlineError(message = (uiState as JoinMeetingUiState.Error).message)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    MeetingPrimaryButton(
                        text = "Join Meeting",
                        onClick = { viewModel.submit() },
                        enabled = uiState !is JoinMeetingUiState.Loading
                    )
                }
            }
            
            if (uiState is JoinMeetingUiState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    MeetingLoadingIndicator()
                }
            }
        }
    }
}
