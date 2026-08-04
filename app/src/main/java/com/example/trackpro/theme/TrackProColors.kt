package com.example.trackpro.theme

import androidx.compose.ui.graphics.Color

data class TrackProColorScheme(
    val bgDeep: Color,
    val bgCard: Color,
    val bgElevated: Color,
    val accent: Color,
    /** Foreground for content sitting on top of an [accent] fill (selected chips, CTAs). */
    val onAccent: Color,
    /** The quieter accent - structural marks that repeat, where [accent] would shout. */
    val accentMuted: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,
    val deltaGood: Color,
    val deltaBad: Color,
    val sectorLine: Color,
    val danger: Color
)

/**
 * Built on the supplied 5-color palette:
 *
 *   #16262E  darkest navy   -> bgDeep
 *   #2E4756  dark slate     -> bgElevated / chart grid
 *   #3C7A89  teal           -> accentMuted, map + chart lines
 *   #9FA2B2  cool grey      -> textMuted
 *   #FEEA00  yellow         -> accent
 *
 * Five colors can't cover a whole UI's tonal needs, so bgCard, sectorLine and the text
 * tiers are interpolated *within* that ramp rather than invented - bgCard sits between
 * #16262E and #2E4756, sectorLine just above #2E4756, and textPrimary is #9FA2B2 lifted
 * toward white so it clears 11:1 on the deep background.
 *
 * Color hierarchy, in order of loudness:
 *
 *  1. [accent] (#FEEA00) - active / selected / primary action only. It hits 12.6:1 on the
 *     background, so it carries enormous weight; used sparingly it reads as a signal lamp,
 *     used everywhere it reads as a highlighter.
 *  2. [accentMuted] (#3C7A89) - the structural marks that repeat per row or per card:
 *     icon chips, left accent bars, map and chart lines. Same family, a fraction of the
 *     impact. This is a loudness hierarchy, not the per-section color rotation an earlier
 *     revision had - don't reintroduce that.
 *  3. [deltaGood]/[deltaBad] - faster/slower, and nothing else.
 *
 * The palette has no green or red, but a lap timer's delta has to be readable at a glance
 * at speed and green/red is the one convention drivers already know. They're tuned to sit
 * with the palette (the green leans teal, the red leans toward the yellow's warmth) rather
 * than taken off the shelf.
 *
 * Neutral, always: surfaces, top bars, card headers, form fields, and static spec values.
 */
val DarkTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFF16262E),
    bgCard = Color(0xFF1D3038),
    bgElevated = Color(0xFF2E4756),
    accent = Color(0xFFFEEA00),
    onAccent = Color(0xFF16262E),
    accentMuted = Color(0xFF3C7A89),
    textPrimary = Color(0xFFDCDEE6),
    textMuted = Color(0xFF9FA2B2),
    textFaint = Color(0xFF6E7385),
    deltaGood = Color(0xFF2FBF71),
    deltaBad = Color(0xFFEF4E3A),
    sectorLine = Color(0xFF3A5563),
    danger = Color(0xFFEF4E3A)
)

/**
 * The supplied palette is inherently dark, so light mode is a derived inversion: the
 * darkest navy becomes the text color and the teal becomes the accent.
 *
 * Yellow is deliberately *not* the light-mode accent. [accent] is used as text as well as
 * fill (link hints, the top-bar dot), and #FEEA00 on white is ~1.2:1 - illegible. Teal
 * darkened to #2C6B7A clears 5.9:1 and keeps the palette's family.
 */
val LightTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFFEDEFF2),
    bgCard = Color(0xFFFFFFFF),
    bgElevated = Color(0xFFE3E7EB),
    accent = Color(0xFF2C6B7A),
    onAccent = Color(0xFFFFFFFF),
    accentMuted = Color(0xFF3C7A89),
    textPrimary = Color(0xFF16262E),
    textMuted = Color(0xFF4F5F6C),
    textFaint = Color(0xFF8A95A1),
    deltaGood = Color(0xFF158A4E),
    deltaBad = Color(0xFFC93A28),
    sectorLine = Color(0xFFC8D0D8),
    danger = Color(0xFFC93A28)
)
