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
                            text = "Host a secure meeting",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Set up your meeting preferences before inviting others.",
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
                            value = formState.displayName,
                            onValueChange = { viewModel.updateDisplayName(it); viewModel.resetError() },
                            label = "Your Name",
                            placeholder = "e.g. Jane Doe",
                            enabled = uiState !is CreateMeetingUiState.Loading
                        )

                        if (uiState is CreateMeetingUiState.Error) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MeetingInlineError(message = (uiState as CreateMeetingUiState.Error).message)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        MeetingPrimaryButton(
                            text = "Create Meeting",
                            onClick = { viewModel.submit() },
                            enabled = uiState !is CreateMeetingUiState.Loading
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
                        value = formState.displayName,
                        onValueChange = { viewModel.updateDisplayName(it); viewModel.resetError() },
                        label = "Your Name",
                        placeholder = "e.g. Jane Doe",
                        enabled = uiState !is CreateMeetingUiState.Loading
                    )


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
