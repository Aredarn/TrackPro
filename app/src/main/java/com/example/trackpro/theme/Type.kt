package com.example.trackpro.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Resize a style while keeping its leading *ratio* intact.
 *
 * Prefer this over `.copy(fontSize = ...)`: a plain copy keeps the original absolute
 * lineHeight, so shrinking a 13sp/19sp body style to 9sp would leave it at 19sp leading -
 * a 2.1x ratio. Since leading should tighten as size grows and loosen as it shrinks,
 * carrying the ratio is the only thing that stays correct across the scale.
 */
fun TextStyle.atSize(size: TextUnit): TextStyle {
    if (fontSize.value <= 0f || lineHeight.value <= 0f) return copy(fontSize = size)
    val ratio = lineHeight.value / fontSize.value
    return copy(fontSize = size, lineHeight = (size.value * ratio).sp)
}

/**
 * Text styles are purely typographic (no color) so they compose with
 * TrackProTheme.colors at the call site instead of baking a color in here.
 *
 * Three rules the scale follows:
 *
 *  1. **Tracking is size-specific.** Letters read as drifting apart the larger they get,
 *     so display sizes take negative tracking (~-0.02em) while small uppercase labels
 *     take positive tracking to stay legible. A single letterSpacing value across a
 *     scale is wrong at one end or the other.
 *  2. **Leading tracks size inversely.** Tight on the big numerics (1.05x), generous on
 *     body copy (1.5x). This scale previously set no lineHeight at all, which left every
 *     multi-line block on the platform default.
 *  3. **Hierarchy comes from weight + size + leading together,** not size alone -
 *     weight adds presence without consuming more space, which matters on a HUD.
 *
 * FontWeight.Black is intentionally absent - Bold is reserved for displayNumeric.
 */
object TrackProType {

    /**
     * Trim ensures the first line's ascent and last line's descent don't add stray
     * padding, so a 40sp readout occupies predictable space in a HUD row.
     */
    private val tightLineHeight = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )

    /** Lap times, live speed. Tabular figures so digits don't jitter as they count. */
    val displayNumeric = TextStyle(
        fontSize = 40.sp,
        lineHeight = 42.sp,          // 1.05 - tight, large text needs no air
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,   // ~-0.02em
        fontFeatureSettings = "tnum",
        lineHeightStyle = tightLineHeight
    )

    val titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,          // 1.2
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,   // ~-0.015em
        lineHeightStyle = tightLineHeight
    )

    val titleMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,          // 1.33
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
    )

    /** Small uppercase micro-labels - positive tracking keeps caps from colliding. */
    val label = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,          // 1.27
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp
    )

    val body = TextStyle(
        fontSize = 13.sp,
        lineHeight = 19.sp,          // 1.46 - the loosest in the scale, it's read in runs
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )

    /** Stat readouts. Tabular so columns of numbers align down a list. */
    val statValue = TextStyle(
        fontSize = 17.sp,
        lineHeight = 20.sp,          // 1.18
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = "tnum",
        lineHeightStyle = tightLineHeight
    )
}
