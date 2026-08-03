package com.jbcoder.meeting.feature.room

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// --- Geometric Balance Theme Tokens ---
val MeetingBackground = Color(0xFF050505)
val SurfaceDark = Color(0xFF171717) // neutral-900
val BottomBarBackground = Color(0xCC141414) // #141414/80
val BrandPurple = Color(0xFFA855F7) // purple-500
val BrandPurpleLight = Color(0xFFC084FC) // purple-400
val DangerRed = Color(0xFFEF4444) // red-500
val Emerald = Color(0xFF10B981) // emerald-500
val TextPrimary = Color(0xFFF1F5F9) // slate-100
val TextSecondary = Color(0xFFCBD5E1) // slate-300
val TextMuted = Color(0xFF94A3B8) // slate-400

val LiveRoomColorScheme = darkColorScheme(
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
