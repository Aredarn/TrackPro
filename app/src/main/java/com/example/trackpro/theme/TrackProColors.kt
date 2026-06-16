package com.example.trackpro.theme

import androidx.compose.ui.graphics.Color

data class TrackProColorScheme(
    val bgDeep: Color,
    val bgCard: Color,
    val bgElevated: Color,
    val accentCyan: Color,
    val accentBlue: Color,
    val accentAmber: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val deltaGood: Color,
    val deltaBad: Color,
    val sectorLine: Color
)


val DarkTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFF0F1219),
    bgCard = Color(0xFF161B26),
    bgElevated = Color(0xFF1F2635),
    accentCyan = Color(0xFF00B4D8),
    accentBlue = Color(0xFF6C7AEC),
    accentAmber = Color(0xFFF7A23B),
    textPrimary = Color(0xFFF1F3F7),
    textMuted = Color(0xFF8A94A6),
    deltaGood = Color(0xFF22C55E),
    deltaBad = Color(0xFFEF4444),
    sectorLine = Color(0xFF2E374A)
)

val LightTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFFF4F6F9),
    bgCard = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFEAEFF5),
    accentCyan = Color(0xFF0077B6),
    accentBlue = Color(0xFF4A56E2),
    accentAmber = Color(0xFFD97706),
    textPrimary = Color(0xFF111827),
    textMuted = Color(0xFF6B7280),
    deltaGood = Color(0xFF16A34A),
    deltaBad = Color(0xFFDC2626),
    sectorLine = Color(0xFFE5E7EB)
)