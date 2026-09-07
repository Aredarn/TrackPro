package com.example.trackpro.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The dash's face.
 *
 * Intended face: a condensed grotesque with hard-edged, high-x-height numerals - the
 * lettering a purpose-built display uses, where a 9 and an 8 can never be confused at a
 * glance. Not bundled yet, so this resolves to the platform sans; swapping it is this one
 * constant plus a file in `res/font/`.
 */
val TrackProFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Tabular, lining figures.
 *
 * `tnum` is not cosmetic on a live readout. Without it every digit has its own advance, so
 * a running lap timer physically shifts left and right as the digits change - the number
 * dances while you are trying to read it at speed. Fixed advances hold it still.
 */
private const val TabularFigures = "tnum, lnum"

/** Resize while preserving the leading ratio, so the scale's rhythm survives. */
fun TextStyle.atSize(size: TextUnit): TextStyle {
    if (fontSize.value <= 0f || lineHeight.value <= 0f) return copy(fontSize = size)
    val ratio = lineHeight.value / fontSize.value
    return copy(fontSize = size, lineHeight = (size.value * ratio).sp)
}

/**
 * A dash's scale is brutally top-heavy: one readout dominates every frame and everything
 * else is a placard beneath it. There is no display tier for prose, because a dash has no
 * prose.
 */
object TrackProType {

    /**
     * The readout. Delta on track, the split just crossed on the strip, the running clock
     * everywhere else. Sized to be read peripherally, tracked tight because at this scale
     * default spacing reads as gappy.
     */
    val displayNumeric = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 56.sp,
        lineHeight = 58.sp,            // 1.04 - a lone figure needs no leading
        fontWeight = FontWeight.Black,
        letterSpacing = (-2.2).sp,
        fontFeatureSettings = TabularFigures
    )

    /** A secondary instrument value: best, last, split, speed, count. */
    val statValue = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = TabularFigures
    )

    /** A field's own name, or the car on its placard. */
    val titleLarge = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        fontFeatureSettings = TabularFigures
    )

    /** A row label. */
    val titleMedium = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = TabularFigures
    )

    /**
     * The placard cap under every instrument. Always upper case at the call site, tracked
     * open because small caps close up and a placard has to be legible without being read.
     */
    val label = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp,
        fontFeatureSettings = TabularFigures
    )

    /** Running text, where any survives. */
    val body = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularFigures
    )
}
