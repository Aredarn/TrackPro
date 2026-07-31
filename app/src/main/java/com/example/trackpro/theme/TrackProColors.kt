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
    val textFaint: Color,
    val deltaGood: Color,
    val deltaBad: Color,
    val sectorLine: Color,
    val danger: Color
)

/**
 * Accents are desaturated on purpose and are meant to stay small (icons, hairline
 * underlines, badges, dots) — never a full-bleed fill. bgDeep/bgCard/bgElevated carry the
 * visual weight instead. deltaGood/deltaBad/danger stay closer to full saturation since
 * they're functional signal colors (faster/slower, destructive), not decoration.
 */
val DarkTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFF0B0D11),
    bgCard = Color(0xFF12151A),
    bgElevated = Color(0xFF1A1E26),
    accentCyan = Color(0xFF4FB6C9),
    accentBlue = Color(0xFF7B84D6),
    accentAmber = Color(0xFFD6A44A),
    textPrimary = Color(0xFFF0F2F6),
    textMuted = Color(0xFF8B93A3),
    textFaint = Color(0xFF545C6B),
    deltaGood = Color(0xFF3ECC7A),
    deltaBad = Color(0xFFEF5B54),
    sectorLine = Color(0xFF232830),
    danger = Color(0xFFEF5B54)
)

val LightTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFFF0F2F5),
    bgCard = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFEDF0F4),
    accentCyan = Color(0xFF3E93A6),
    accentBlue = Color(0xFF6169C7),
    accentAmber = Color(0xFFB9812E),
    textPrimary = Color(0xFF12151A),
    textMuted = Color(0xFF667085),
    textFaint = Color(0xFF98A2B3),
    deltaGood = Color(0xFF1C9B57),
    deltaBad = Color(0xFFD3453D),
    sectorLine = Color(0xFFE2E5EA),
    danger = Color(0xFFD3453D)
)