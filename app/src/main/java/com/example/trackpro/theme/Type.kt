package com.example.trackpro.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The face of the timing sheet.
 *
 * One monospaced family carries the entire app. That is not a "technical" costume: this
 * product is a measuring instrument whose whole output is columns of times, splits, speeds
 * and dates, and those columns only line up if every glyph occupies the same advance. A
 * proportional face would make lap 9 and lap 10 sit on different axes.
 *
 * Intended face: Azeret Mono - mechanical, squarish, with figures heavy enough to read at
 * a glance off a mounted phone. It is not bundled yet, so this currently resolves to the
 * platform monospace. Swapping it is this one constant plus a file in `res/font/`.
 */
val TrackProFontFamily: FontFamily = FontFamily.Monospace

/**
 * Tabular, lining figures.
 *
 * `tnum` forces every digit to the same width so a column of times aligns on its decimal
 * without hand-placed padding; `lnum` keeps figures at cap height rather than letting the
 * face drop old-style numerals below the baseline. On a timing sheet this is not a
 * refinement, it is the difference between a table and a list.
 */
private const val TabularFigures = "tnum, lnum"

/**
 * Resize a style while preserving its leading ratio.
 *
 * Line height is expressed as a multiple of size in this scale, so a naive
 * `copy(fontSize = x)` would keep the old absolute leading and wreck the rhythm. Guarded
 * against unset metrics, where the ratio is undefined.
 */
fun TextStyle.atSize(size: TextUnit): TextStyle {
    if (fontSize.value <= 0f || lineHeight.value <= 0f) return copy(fontSize = size)
    val ratio = lineHeight.value / fontSize.value
    return copy(fontSize = size, lineHeight = (size.value * ratio).sp)
}

/**
 * The scale is a printout's, not a web page's: a very large figure for the measurement
 * that matters, a workhorse row size, and a small tracked cap for column headers. There is
 * no decorative display tier, because a timing sheet has no headlines.
 */
object TrackProType {

    /** The measurement. Lap time, run time, terminal speed - the number read at speed. */
    val displayNumeric = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 44.sp,
        lineHeight = 46.sp,          // 1.05 - a single figure needs no breathing room
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.2).sp,   // mono is loose by nature; tighten the big sizes back
        fontFeatureSettings = TabularFigures
    )

    /** A figure inside a column: split, speed, count. */
    val statValue = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 19.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = TabularFigures
    )

    /** The sheet's own heading - a section rule's caption, not a headline. */
    val titleLarge = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 17.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        fontFeatureSettings = TabularFigures
    )

    /** A row's own name. */
    val titleMedium = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = TabularFigures
    )

    /**
     * The column header and the stamped legend: small, spaced, always upper case at the
     * call site. Tracking is positive because small caps close up without it.
     */
    val label = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.1.sp,
        fontFeatureSettings = TabularFigures
    )

    /** Running text. Rare here - the sheet mostly prints values. */
    val body = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 13.sp,
        lineHeight = 19.sp,          // 1.46
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularFigures
    )
}
