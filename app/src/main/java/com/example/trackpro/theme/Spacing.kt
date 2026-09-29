package com.example.trackpro.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The 8-point grid, with 4 and 12 for the tight end. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Screen side margin: cards float inside it rather than running edge to edge. */
    val gutter = 16.dp
}

/** Soft, generous radii: cards 24, controls 16, chips 12, badges 8. */
object TrackProShapes {
    val badge = RoundedCornerShape(8.dp)
    val chip = RoundedCornerShape(12.dp)
    val control = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(24.dp)
    val sheet = RoundedCornerShape(28.dp)
    val pill = CircleShape
}
