package com.jbcoder.meeting.feature.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RoomPreJoinScreen(
    viewModel: RoomPreJoinViewModel = hiltViewModel(),
    onNavigateLiveRoom: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isMicEnabled by viewModel.isMicEnabled.collectAsState()
    val isCameraEnabled by viewModel.isCameraEnabled.collectAsState()
    
    val context = LocalContext.current
    var audioGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            audioGranted = granted
            if (granted) {
                viewModel.toggleMic()
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            cameraGranted = granted
            if (granted) {
                viewModel.toggleCamera()
            }
        }
    )

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Pre-Join") })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is RoomPreJoinState.Initializing -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is RoomPreJoinState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateHome) {
                            Text("Go Home")
                        }
                    }
                }
                is RoomPreJoinState.Ready -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Meeting: ${state.meetingCode}", style = MaterialTheme.typography.headlineMedium)
                        Text("Joining as: ${state.displayName}", style = MaterialTheme.typography.bodyLarge)
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Placeholder for local preview in pre-join
                        Box(
                            modifier = Modifier
                                .size(240.dp, 320.dp)
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCameraEnabled && cameraGranted) {
                                Text("Camera Preview\n(Will start upon join)", color = Color.White)
                            } else {
                                Text("Camera Off", color = Color.White)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isMicEnabled && !audioGranted) {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        viewModel.toggleMic()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = "Toggle Mic",
                                    tint = if (isMicEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                            
                            IconButton(
                                onClick = {
                                    if (!isCameraEnabled && !cameraGranted) {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    } else {
                                        viewModel.toggleCamera()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = "Toggle Camera",
                                    tint = if (isCameraEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = onNavigateHome) {
                                Text("Cancel")
                            }
                            Button(onClick = { 
                                viewModel.enterMeeting(
                                    hasAudioPerm = audioGranted,
                                    hasCameraPerm = cameraGranted,
                                    onStarted = onNavigateLiveRoom
                                )
                            }) {
                                Text("Enter Meeting")
                            }
                        }
                    }
                }
            }
        }
    }
}
