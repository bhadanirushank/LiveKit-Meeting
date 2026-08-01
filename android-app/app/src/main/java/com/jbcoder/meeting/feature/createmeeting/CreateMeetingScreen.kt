package com.jbcoder.meeting.feature.createmeeting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.MeetingInlineError
import com.jbcoder.meeting.core.designsystem.MeetingLoadingIndicator
import com.jbcoder.meeting.core.designsystem.MeetingPrimaryButton
import com.jbcoder.meeting.core.designsystem.MeetingTextField
import com.jbcoder.meeting.core.designsystem.MeetingTopBar
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CreateMeetingScreen(
    onNavigateBack: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: CreateMeetingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val formState by viewModel.formState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.uiState.collectLatest { state ->
            if (state is CreateMeetingUiState.Success) {
                onSuccess()
            }
        }
    }

    Scaffold(
        topBar = {
            MeetingTopBar(
                title = "Create Meeting",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "Host a secure meeting",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Text(
                    text = "Set up your meeting preferences before inviting others.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                MeetingTextField(
                    value = formState.title,
                    onValueChange = { viewModel.updateTitle(it); viewModel.resetError() },
                    label = "Meeting Title",
                    placeholder = "e.g. Weekly Standup",
                    enabled = uiState !is CreateMeetingUiState.Loading
                )

                MeetingTextField(
                    value = formState.maximumParticipants,
                    onValueChange = { viewModel.updateMaximumParticipants(it); viewModel.resetError() },
                    label = "Max Participants",
                    placeholder = "100",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = uiState !is CreateMeetingUiState.Loading
                )

                MeetingTextField(
                    value = formState.passcode,
                    onValueChange = { viewModel.updatePasscode(it); viewModel.resetError() },
                    label = "Passcode (Optional)",
                    placeholder = "Leave blank for no passcode",
                    enabled = uiState !is CreateMeetingUiState.Loading
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Waiting Room",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Participants must be admitted by a host",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = formState.waitingRoomEnabled,
                                onCheckedChange = { viewModel.updateWaitingRoomEnabled(it); viewModel.resetError() },
                                enabled = uiState !is CreateMeetingUiState.Loading
                            )
                        }
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Join Before Host",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Allow participants to enter the room before you",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = formState.joinBeforeHostEnabled,
                                onCheckedChange = { viewModel.updateJoinBeforeHostEnabled(it); viewModel.resetError() },
                                enabled = uiState !is CreateMeetingUiState.Loading
                            )
                        }
                    }
                }

                if (uiState is CreateMeetingUiState.Error) {
                    MeetingInlineError(message = (uiState as CreateMeetingUiState.Error).message)
                }

                Spacer(modifier = Modifier.height(16.dp))

                MeetingPrimaryButton(
                    text = "Create Meeting",
                    onClick = { viewModel.submit() },
                    enabled = uiState !is CreateMeetingUiState.Loading
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            if (uiState is CreateMeetingUiState.Loading) {
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
