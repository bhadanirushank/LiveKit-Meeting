package com.jbcoder.meeting.feature.home.components

import com.jbcoder.meeting.feature.home.HomeUiState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jbcoder.meeting.presentation.theme.MeetingPrimary
import com.jbcoder.meeting.presentation.theme.MeetingSuccess
import com.jbcoder.meeting.presentation.theme.MeetingError
import com.jbcoder.meeting.presentation.theme.MeetingConnecting

@Composable
fun MinimalMeetingMark(modifier: Modifier = Modifier) {
    val accentSurface = MaterialTheme.colorScheme.surfaceVariant
    val onBgColor = MaterialTheme.colorScheme.onBackground
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accentSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null, // decorative
                tint = MeetingPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "LiveKit Meetings",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = onBgColor,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
fun HomeHeadline(modifier: Modifier = Modifier) {
    val onBgColor = MaterialTheme.colorScheme.onBackground
    val onBgSubtle = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.semantics(mergeDescendants = true) {
        heading()
    }) {
        Text(
            text = "Meet clearly.\nConnect simply.",
            style = MaterialTheme.typography.headlineLarge,
            color = onBgColor
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Start a meeting or join securely with a meeting code.",
            style = MaterialTheme.typography.bodyMedium,
            color = onBgSubtle
        )
    }
}

@Composable
fun PrimaryMeetingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.background,
            disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.background.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(16.dp),
        enabled = enabled
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun SecondaryMeetingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val secBtnBg = MaterialTheme.colorScheme.surfaceVariant
    val secBtnBorder = MaterialTheme.colorScheme.outline
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        shape = RoundedCornerShape(20.dp),
        color = secBtnBg,
        border = BorderStroke(1.dp, secBtnBorder),
        enabled = enabled
    ) {
        Box(
            modifier = Modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
fun MeetingStatusIndicator(
    status: HomeUiState,
    modifier: Modifier = Modifier
) {
    val (icon, tint, text) = when (status) {
        is HomeUiState.Ready -> Triple(Icons.Default.CheckCircle, MeetingSuccess, "Ready to connect")
        is HomeUiState.Initializing -> Triple(Icons.Default.Sync, MeetingConnecting, "Initializing...")
        is HomeUiState.Offline -> Triple(Icons.Default.Error, MeetingError, status.error ?: "Backend is offline")
        is HomeUiState.Error -> Triple(Icons.Default.Error, MeetingError, "Connection error")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Status: $text"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
