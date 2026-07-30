package com.jbcoder.meeting.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MeetingPrimary,
    onPrimary = MeetingOnPrimaryDark,
    background = MeetingBackgroundDark,
    onBackground = MeetingTextPrimaryDark,
    surface = MeetingSurfaceDark,
    onSurface = MeetingTextPrimaryDark,
    surfaceVariant = MeetingSurfaceElevatedDark,
    onSurfaceVariant = MeetingTextSecondaryDark,
    outline = MeetingBorderDark,
    error = MeetingError
)

private val LightColorScheme = lightColorScheme(
    primary = MeetingPrimary,
    onPrimary = MeetingOnPrimaryLight,
    background = MeetingBackgroundLight,
    onBackground = MeetingTextPrimaryLight,
    surface = MeetingSurfaceLight,
    onSurface = MeetingTextPrimaryLight,
    surfaceVariant = MeetingSurfaceElevatedLight,
    onSurfaceVariant = MeetingTextSecondaryLight,
    outline = MeetingBorderLight,
    error = MeetingError
)

@Composable
fun MeetingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is explicitly disabled by design to preserve brand colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
