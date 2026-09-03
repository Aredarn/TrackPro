package com.example.trackpro.theme

import androidx.compose.ui.graphics.Color

/**
 * TrackPro's colour world: the club timing-and-scoring printout.
 *
 * Two grounds, one grammar. **Print** is the fanfold sheet you carry back from the event.
 * **Board** is the live timing monitor hanging in race control - the same columns, the
 * same face, the same rules, illuminated instead of printed. Neither is a "dark mode" of
 * the other; they are two artifacts a timing system genuinely produces, and the app moves
 * between them the way an event does: paper for reading afterwards, board while running.
 *
 * The data colours are not invented. Purple for the session's fastest, green for a
 * personal best, amber for down on your own time is the convention every timing screen in
 * motorsport has used for decades, which means this audience reads the colour before it
 * reads the number, and no legend is owed.
 *
 * Field names are deliberately unchanged from the previous scheme so every existing screen
 * inherits the new world without a rename sweep; the world-native names below
 * ([paper], [ink], [rule], [stamp]) are what new code should read.
 */
data class TrackProColorScheme(
    /** The sheet itself, or the unlit board. */
    val bgDeep: Color,
    /** A printed block on the sheet - form areas, ruled tables. */
    val bgCard: Color,
    /** The margin band: tractor-feed edge, column headers, the strip behind a legend. */
    val bgElevated: Color,

    /** Session best. Purple, per timing convention. */
    val accent: Color,
    /** Knocked-out figures sitting on an [accent] fill. */
    val onAccent: Color,
    /** Structural ink: rules that repeat, tick marks, secondary legends. */
    val accentMuted: Color,

    val textPrimary: Color,
    val textMuted: Color,
    val textFaint: Color,

    /** Personal best. Green, per timing convention. */
    val deltaGood: Color,
    /** Down on your own time. Amber, per timing convention - not red. */
    val deltaBad: Color,
    /** Hairline rules and column separators. */
    val sectorLine: Color,
    /** Destructive only. The stamp. */
    val danger: Color
)

// ── World-native aliases ───────────────────────────────────
// New code reads these; they carry the world's own vocabulary without forcing a rename
// across every existing screen.

/** The sheet / the board. */
val TrackProColorScheme.paper: Color get() = bgDeep

/** A printed block on the sheet. */
val TrackProColorScheme.printedBlock: Color get() = bgCard

/** The tractor-feed margin band. */
val TrackProColorScheme.margin: Color get() = bgElevated

/** Dot-matrix ink. */
val TrackProColorScheme.ink: Color get() = textPrimary

/** Ink that struck the ribbon lightly. */
val TrackProColorScheme.inkFaint: Color get() = textFaint

/** Hairline rule between columns and rows. */
val TrackProColorScheme.rule: Color get() = sectorLine

/** The red stamp: destructive actions only, never decoration. */
val TrackProColorScheme.stamp: Color get() = danger

/**
 * Print: fanfold paper, dot-matrix ink.
 *
 * This is the default ground. It is also the honest answer to the product's primary scene:
 * a phone mounted in direct sunlight, where a light sheet outreads an illuminated panel.
 */
val PrintTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFFF2EEE4),      // fanfold stock
    bgCard = Color(0xFFFBF8F1),      // the whiter form area printed onto it
    bgElevated = Color(0xFFE7E2D5),  // tractor-feed margin, column headers

    accent = Color(0xFF6B2D8F),      // session best - purple ink, 7.4:1 on stock
    onAccent = Color(0xFFF7F3EA),
    accentMuted = Color(0xFF57524A),

    textPrimary = Color(0xFF191712),  // 14.8:1
    textMuted = Color(0xFF57524A),    // 7.1:1
    textFaint = Color(0xFF8B8578),    // 3.6:1 - large/label use only

    deltaGood = Color(0xFF14663A),   // personal best - green ink
    deltaBad = Color(0xFF8A5A0F),    // down on your time - ochre, readable on paper
    sectorLine = Color(0xFFBCB6A8),
    danger = Color(0xFFA8321F)
)

/**
 * Board: the live timing monitor, unlit glass with the same grammar lit up.
 *
 * Used wherever a session is actually running, regardless of the user's preferred ground -
 * a driver at night should not be handed a white screen.
 */
val BoardTrackProColors = TrackProColorScheme(
    bgDeep = Color(0xFF121110),
    bgCard = Color(0xFF1A1917),
    bgElevated = Color(0xFF24221F),

    accent = Color(0xFFB473E8),      // session best
    onAccent = Color(0xFF17111E),
    accentMuted = Color(0xFFA8A296),

    textPrimary = Color(0xFFF4F1E8),
    textMuted = Color(0xFFA8A296),
    textFaint = Color(0xFF6C675E),

    deltaGood = Color(0xFF3FD07A),   // personal best
    deltaBad = Color(0xFFE8B33A),    // down on your time
    sectorLine = Color(0xFF35322C),
    danger = Color(0xFFF2603C)
)

// Legacy names kept so existing call sites resolve unchanged.
val DarkTrackProColors = BoardTrackProColors
val LightTrackProColors = PrintTrackProColors
