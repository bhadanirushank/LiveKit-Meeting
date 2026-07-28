package com.jbcoder.meeting.feature.createmeeting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.MeetingInlineError
import com.jbcoder.meeting.core.designsystem.MeetingLoadingIndicator
import com.jbcoder.meeting.core.designsystem.MeetingPrimaryButton
import com.jbcoder.meeting.core.designsystem.MeetingTextField
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text("Create Meeting") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("<-") // Replace with Icon in real app if desired, keeping simple
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MeetingTextField(
                value = formState.title,
                onValueChange = { viewModel.updateTitle(it); viewModel.resetError() },
                label = "Meeting Title",
                enabled = uiState !is CreateMeetingUiState.Loading
            )

            MeetingTextField(
                value = formState.maximumParticipants,
                onValueChange = { viewModel.updateMaximumParticipants(it); viewModel.resetError() },
                label = "Max Participants",
                enabled = uiState !is CreateMeetingUiState.Loading
            )

            MeetingTextField(
                value = formState.passcode,
                onValueChange = { viewModel.updatePasscode(it); viewModel.resetError() },
                label = "Passcode (Optional)",
                enabled = uiState !is CreateMeetingUiState.Loading
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = formState.waitingRoomEnabled,
                    onCheckedChange = { viewModel.updateWaitingRoomEnabled(it); viewModel.resetError() },
                    enabled = uiState !is CreateMeetingUiState.Loading
                )
                Text("Enable Waiting Room")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = formState.joinBeforeHostEnabled,
                    onCheckedChange = { viewModel.updateJoinBeforeHostEnabled(it); viewModel.resetError() },
                    enabled = uiState !is CreateMeetingUiState.Loading
                )
                Text("Allow join before host")
            }

            if (uiState is CreateMeetingUiState.Error) {
                MeetingInlineError(message = (uiState as CreateMeetingUiState.Error).message)
            }

            MeetingPrimaryButton(
                text = "Create",
                onClick = { viewModel.submit() },
                enabled = uiState !is CreateMeetingUiState.Loading
            )

            if (uiState is CreateMeetingUiState.Loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MeetingLoadingIndicator()
                }
            }
        }
    }
}
