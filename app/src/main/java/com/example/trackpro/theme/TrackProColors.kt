package com.example.trackpro.theme

import androidx.compose.ui.graphics.Color

/**
 * TrackPro's colour world: **Paddock night**.
 *
 * A dark graphite base, soft raised cards, and one racing-orange accent - the feel of a
 * premium car maker's app rather than a lab instrument. It follows 60/30/10: graphite ground
 * for most of every screen, near-white type and card tones for structure, orange reserved
 * for the one thing you should press or notice.
 *
 * Text hierarchy comes from luminance steps of one near-white, not from extra colours:
 * [textPrimary] for what you read, [textMuted] for what supports it, [textFaint] for
 * metadata. Timing colours keep the sport's convention - green for up on your time, amber
 * for down, red only for a genuine fault - because a driver already reads them that way.
 *
 * The two schemes are one design at two contrasts. [PaddockNight] is the default;
 * [PaddockSunlight] drops the ground to black and lifts every tone for a phone on a mount in
 * direct sun. It is not a light theme.
 */
data class TrackProColorScheme(
    /** Page ground: the 60%. */
    val bgDeep: Color,
    /** A card sitting on the ground. */
    val bgCard: Color,
    /** Something raised off a card: selected, pressed, an input, a chip. */
    val bgElevated: Color,

    /** Racing orange. Primary actions, the selected tab, key indicators. */
    val accent: Color,
    /** Text and icons on an [accent] fill. */
    val onAccent: Color,
    /** Quiet icon and structure tone. */
    val accentMuted: Color,

    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,

    /** Up on your time, healthy link, backed up. */
    val deltaGood: Color,
    /** Down on your time, pending, needs attention. Amber, never red. */
    val deltaBad: Color,
    /** Hairlines and dividers inside cards. */
    val sectorLine: Color,
    /** Genuine fault and destructive actions only. */
    val danger: Color
)

// ── Role aliases ───────────────────────────────────────────
// Earlier code reads the colours by these names; they now mean the Paddock night roles.

/** The page ground. */
val TrackProColorScheme.panel: Color get() = bgDeep

/** A card. */
val TrackProColorScheme.field: Color get() = bgCard

/** A raised or selected surface on a card. */
val TrackProColorScheme.fieldLive: Color get() = bgElevated

/** Primary text. */
val TrackProColorScheme.marking: Color get() = textPrimary

/** Secondary text. */
val TrackProColorScheme.markingDim: Color get() = textMuted

/** An unfilled segment of a progress bar. */
val TrackProColorScheme.segmentOff: Color get() = bgElevated

/** Hairline divider. */
val TrackProColorScheme.bezel: Color get() = sectorLine

/** The accent at 12%: secondary buttons, selected rows, soft highlights. */
val TrackProColorScheme.accentSoft: Color get() = accent.copy(alpha = 0.14f)

/** Tinted shadow colour for glows under accent elements. */
val TrackProColorScheme.accentGlow: Color get() = accent.copy(alpha = 0.45f)

/** Shadow colour for neutral cards: the ground's own hue, never pure black on graphite. */
val TrackProColorScheme.cardShadow: Color get() = Color(0xFF000000).copy(alpha = 0.55f)

/** A 1dp top highlight on raised elements - the "white inner shadow". */
val TrackProColorScheme.highlight: Color get() = Color.White.copy(alpha = 0.06f)

val PaddockNight = TrackProColorScheme(
    bgDeep = Color(0xFF0F1115),
    bgCard = Color(0xFF1A1D23),
    bgElevated = Color(0xFF252932),

    accent = Color(0xFFFF6B1A),
    onAccent = Color(0xFF1F0E04),
    accentMuted = Color(0xFF8A8F98),

    textPrimary = Color(0xFFF4F5F7),   // 16.2:1 on a card
    textMuted = Color(0xFFB4B8C0),     // 8.9:1
    textFaint = Color(0xFF868B95),     // 4.8:1

    deltaGood = Color(0xFF3DDC84),
    deltaBad = Color(0xFFFFB020),
    sectorLine = Color(0xFF2A2E36),
    danger = Color(0xFFFF4D4F)
)

/** Paddock night pushed for glare: black ground, every tone lifted. */
val PaddockSunlight = TrackProColorScheme(
    bgDeep = Color(0xFF000000),
    bgCard = Color(0xFF15171C),
    bgElevated = Color(0xFF22252D),

    accent = Color(0xFFFF7A2E),
    onAccent = Color(0xFF1F0E04),
    accentMuted = Color(0xFFA7ACB5),

    textPrimary = Color(0xFFFFFFFF),
    textMuted = Color(0xFFD3D6DC),
    textFaint = Color(0xFFA2A7B0),

    deltaGood = Color(0xFF4FF08C),
    deltaBad = Color(0xFFFFC24A),
    sectorLine = Color(0xFF31353E),
    danger = Color(0xFFFF5C5E)
)

// The app's dark/light toggle now picks standard vs sunlight contrast.
val DarkTrackProColors = PaddockNight
val LightTrackProColors = PaddockSunlight
val NightDashColors = PaddockNight
val DayDashColors = PaddockSunlight
