package com.jbcoder.meeting.presentation.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FoundationScreen(
    onReady: () -> Unit,
    viewModel: FoundationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            is FoundationState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Initializing Foundation...")
                }
            }
            is FoundationState.Error -> {
                val error = (state as FoundationState.Error).message
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Initialization Failed: $error")
                    Button(onClick = { viewModel.retry() }) {
                        Text("Retry")
                    }
                }
            }
            is FoundationState.Ready -> {
                // Should navigate immediately
                onReady()
            }
        }
    }
}
