package com.example.trackpro.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/**
 * Square, everywhere.
 *
 * The dash world is milled metal and glass: apertures are cut, not moulded, so nothing
 * on the panel carries a radius. Kept as RoundedCornerShape rather than RectangleShape so
 * the handful of call sites that address individual corners still compile.
 */
object TrackProShapes {
    val badge = RoundedCornerShape(0.dp)
    val control = RoundedCornerShape(0.dp)
    val card = RoundedCornerShape(0.dp)
}
