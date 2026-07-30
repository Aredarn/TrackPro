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
    bgDeep = Color(0xFF0A0C11),
    bgCard = Color(0xFF151924),
    bgElevated = Color(0xFF212739),
    accentCyan = Color(0xFF22D3EE),
    accentBlue = Color(0xFF818CF8),
    accentAmber = Color(0xFFFBBF24),
    textPrimary = Color(0xFFF3F5F9),
    textMuted = Color(0xFF9AA5B8),
    deltaGood = Color(0xFF4ADE80),
    deltaBad = Color(0xFFF87171),
    sectorLine = Color(0xFF3A4358)
)

val LightTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFFEEF1F6),
    bgCard = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFE2E8F1),
    accentCyan = Color(0xFF0891B2),
    accentBlue = Color(0xFF6366F1),
    accentAmber = Color(0xFFD97706),
    textPrimary = Color(0xFF0F172A),
    textMuted = Color(0xFF64748B),
    deltaGood = Color(0xFF16A34A),
    deltaBad = Color(0xFFDC2626),
    sectorLine = Color(0xFFD8DEE9)
)