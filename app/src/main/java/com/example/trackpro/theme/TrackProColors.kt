package com.example.trackpro.theme

import androidx.compose.ui.graphics.Color

/**
 * TrackPro's colour world: a blackout race dash.
 *
 * The reference is a purpose-built display - AiM, MoTeC, Racelogic - where the panel is
 * unlit until it has something to say, markings are luminous rather than coloured, and
 * nothing on the face is decorative. That discipline is the whole point: this world sits
 * one careless step from the fake-carbon racing skin, and the thing that separates them is
 * that every element here is a real readout.
 *
 * **Night and Day are one face at two luminances, not two themes.** A real dash does not
 * turn into a white document in sunlight; it drives its markings harder. So Day keeps the
 * blackout ground, pushes it to true black for maximum contrast against glare, and lifts
 * every marking. Anyone expecting a conventional light mode will find this surprising, and
 * it is deliberate: a white screen on a windscreen mount at dusk is worse than useless.
 *
 * Severity colour is the sport's own: purple for session best, green for personal best,
 * amber for down on your own time. Red is reserved for genuine fault - never for "slower".
 *
 * Field names are unchanged from previous schemes so every screen inherits without a
 * rename sweep; the world-native aliases below are what new code should read.
 */
data class TrackProColorScheme(
    /** The unlit panel. */
    val bgDeep: Color,
    /** An instrument field raised out of the panel. */
    val bgCard: Color,
    /** A field that is currently the subject. */
    val bgElevated: Color,

    /** Session best. Purple, per timing convention. */
    val accent: Color,
    /** Knocked out of an [accent] fill. */
    val onAccent: Color,
    /** Inactive segment, placard caps, structural marks. */
    val accentMuted: Color,

    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,

    /** Personal best / up on your time. */
    val deltaGood: Color,
    /** Down on your own time. Amber, never red. */
    val deltaBad: Color,
    /** Bezel hairline and segment gaps. */
    val sectorLine: Color,
    /** Genuine fault or destructive action only. */
    val danger: Color
)

// ── World-native aliases ───────────────────────────────────

/** The unlit panel behind everything. */
val TrackProColorScheme.panel: Color get() = bgDeep

/** An instrument field. */
val TrackProColorScheme.field: Color get() = bgCard

/** The field currently under the eye. */
val TrackProColorScheme.fieldLive: Color get() = bgElevated

/** Luminous marking - numerals and legends. */
val TrackProColorScheme.marking: Color get() = textPrimary

/** A marking that is present but not being read. */
val TrackProColorScheme.markingDim: Color get() = textMuted

/** An unlit segment: drawn, not absent, so the bar's full range stays visible. */
val TrackProColorScheme.segmentOff: Color get() = sectorLine

/** Bezel hairline between fields. */
val TrackProColorScheme.bezel: Color get() = sectorLine

/**
 * Night: the default face. Dusk, garage, evening sessions, and anything indoors.
 */
val NightDashColors = TrackProColorScheme(
    bgDeep = Color(0xFF0A0C0B),
    bgCard = Color(0xFF14171A),
    bgElevated = Color(0xFF1E2225),

    accent = Color(0xFFB473E8),
    onAccent = Color(0xFF150E1C),
    accentMuted = Color(0xFF9AA1A4),

    textPrimary = Color(0xFFF2F4F3),   // 16.9:1 on the panel
    textMuted = Color(0xFF9AA1A4),     // 7.2:1
    textFaint = Color(0xFF5E6669),     // 3.2:1 - large marks only

    deltaGood = Color(0xFF3FD07A),
    deltaBad = Color(0xFFE8B33A),
    sectorLine = Color(0xFF2A2F31),
    danger = Color(0xFFE5453A)
)

/**
 * Day: the same face driven harder.
 *
 * Ground goes to true black because against direct glare the limiting factor is the
 * difference between marking and ground, and every marking lifts toward full luminance.
 * This is a backlight step, not an inversion - see the class note.
 */
val DayDashColors = TrackProColorScheme(
    bgDeep = Color(0xFF000000),
    bgCard = Color(0xFF0B0E10),
    bgElevated = Color(0xFF16191C),

    accent = Color(0xFFD9A6FF),
    onAccent = Color(0xFF14091F),
    accentMuted = Color(0xFFC2C8CB),

    textPrimary = Color(0xFFFFFFFF),   // 21:1
    textMuted = Color(0xFFC2C8CB),     // 12.4:1
    textFaint = Color(0xFF7C8488),     // 4.8:1

    deltaGood = Color(0xFF4FF08C),
    deltaBad = Color(0xFFFFC64A),
    sectorLine = Color(0xFF333A3D),
    danger = Color(0xFFFF5A47)
)

// Legacy names kept so existing call sites resolve unchanged. The app's existing
// dark/light toggle now selects night vs day luminance rather than two themes.
val DarkTrackProColors = NightDashColors
val LightTrackProColors = DayDashColors
