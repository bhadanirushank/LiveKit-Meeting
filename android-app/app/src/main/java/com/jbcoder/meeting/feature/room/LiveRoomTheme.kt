package com.jbcoder.meeting.feature.room

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Geometric Balance Theme Tokens (Light Mode) ---
val MeetingBackground = Color(0xFFF8FAFC) // slate-50
val SurfaceDark = Color(0xFFFFFFFF) // White tiles (kept name SurfaceDark for compat)
val BottomBarBackground = Color(0xCCFFFFFF) // Translucent white bottom bar
val BrandPurple = Color(0xFFA855F7) // purple-500
val BrandPurpleLight = Color(0xFFC084FC) // purple-400
val DangerRed = Color(0xFFEF4444) // red-500
val Emerald = Color(0xFF10B981) // emerald-500
val TextPrimary = Color(0xFF0F172A) // slate-900
val TextSecondary = Color(0xFF475569) // slate-600
val TextMuted = Color(0xFF94A3B8) // slate-400

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
