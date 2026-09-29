package com.example.trackpro.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/** One family throughout. Hierarchy comes from size, weight and tone, not from a second face. */
val TrackProFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Tabular, lining figures: the monospaced variant of the numerals. A running lap timer with
 * proportional digits physically shifts as it counts; fixed advances hold it still.
 */
private const val TabularFigures = "tnum, lnum"

/**
 * Resize a style, scaling leading and tracking with it, so a style keeps the spacing it was
 * drawn with at any size.
 */
fun TextStyle.atSize(size: TextUnit): TextStyle {
    if (!fontSize.isSpecified || fontSize.value <= 0f) return copy(fontSize = size)
    val k = size.value / fontSize.value
    return copy(
        fontSize = size,
        lineHeight = if (lineHeight.isSpecified && lineHeight.value > 0f)
            (lineHeight.value * k).sp else lineHeight,
        letterSpacing = if (letterSpacing.isSpecified)
            (letterSpacing.value * k).sp else letterSpacing
    )
}

/**
 * Four sizes, two weights.
 *
 * - **Display** 40 SemiBold - the one number a screen exists to show.
 * - **Title** 20 SemiBold - card titles, stat values, a car's name.
 * - **Body** 15 Regular / SemiBold - running text, row labels.
 * - **Caption** 12 Regular / SemiBold - supporting lines and metadata.
 *
 * Labels are sentence case; nothing is set in capitals. The live HUDs enlarge [displayNumeric]
 * with [atSize] - reading at speed is the one place size outranks the scale.
 */
object TrackProType {

    val displayNumeric = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.8).sp,
        fontFeatureSettings = TabularFigures
    )

    /** A stat value: best, last, split, speed, count. */
    val statValue = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = TabularFigures
    )

    /** Card titles, a car's name, a screen's own heading. */
    val titleLarge = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = TabularFigures
    )

    /** Row labels and emphasised body. */
    val titleMedium = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularFigures
    )

    /** Captions, metadata, the name under a stat. Sentence case. */
    val label = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = TabularFigures
    )

    /** Running text. */
    val body = TextStyle(
        fontFamily = TrackProFontFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        fontFeatureSettings = TabularFigures
    )
}
