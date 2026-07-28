package com.jbcoder.meeting.feature.joinmeeting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jbcoder.meeting.core.designsystem.MeetingInlineError
import com.jbcoder.meeting.core.designsystem.MeetingLoadingIndicator
import com.jbcoder.meeting.core.designsystem.MeetingPrimaryButton
import com.jbcoder.meeting.core.designsystem.MeetingTextField
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text("Join Meeting") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("<-")
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
                value = formState.meetingCode,
                onValueChange = { viewModel.updateMeetingCode(it); viewModel.resetError() },
                label = "Meeting Code (12 chars)",
                enabled = uiState !is JoinMeetingUiState.Loading
            )

            MeetingTextField(
                value = formState.displayName,
                onValueChange = { viewModel.updateDisplayName(it); viewModel.resetError() },
                label = "Display Name",
                enabled = uiState !is JoinMeetingUiState.Loading
            )

            MeetingTextField(
                value = formState.passcode,
                onValueChange = { viewModel.updatePasscode(it); viewModel.resetError() },
                label = "Passcode (Optional)",
                enabled = uiState !is JoinMeetingUiState.Loading
            )

            if (uiState is JoinMeetingUiState.Error) {
                MeetingInlineError(message = (uiState as JoinMeetingUiState.Error).message)
            }

            MeetingPrimaryButton(
                text = "Join",
                onClick = { viewModel.submit() },
                enabled = uiState !is JoinMeetingUiState.Loading
            )

            if (uiState is JoinMeetingUiState.Loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MeetingLoadingIndicator()
                }
            }
        }
    }
}
