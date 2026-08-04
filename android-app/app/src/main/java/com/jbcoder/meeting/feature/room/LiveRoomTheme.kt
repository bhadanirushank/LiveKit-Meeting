package com.jbcoder.meeting.feature.room

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Minimalist Black & White Theme Tokens ---
val MeetingBackground = Color(0xFFF9FAFB) // gray-50
val SurfaceDark = Color(0xFFFFFFFF) // Keep name SurfaceDark for compat, but it's pure white
val BottomBarBackground = Color(0xFFFFFFFF) // White bottom bar
val TileBackground = Color(0xFF9CA3AF) // gray-400 for empty video tile
val BrandPurple = Color(0xFF000000) // Replace purple with Black for active states
val BrandPurpleLight = Color(0xFFE5E7EB) // Replace light purple with light gray
val DangerRed = Color(0xFFEF4444) // Keep red for end call/errors
val Emerald = Color(0xFF10B981) // Keep emerald for minor highlights if needed
val TextPrimary = Color(0xFF111827) // gray-900 (black)
val TextSecondary = Color(0xFF4B5563) // gray-600 (dark gray)
val TextMuted = Color(0xFF9CA3AF) // gray-400 (light gray)

val LiveRoomColorScheme = lightColorScheme(
    background = MeetingBackground,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDark,
    primary = BrandPurple,
    onPrimary = Color.White,
    error = DangerRed,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    onBackground = TextPrimary
)
