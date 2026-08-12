package com.example.trackpro.managerClasses.utilities

/**
 * Maps a normalized speed value (0..1, slow..fast) to a hex color for heatmap-style
 * track/session traces.
 *
 * Green (slow) -> red (fast), the convention every other timing tool uses, so a trace
 * reads without a legend: braking zones go green, straights go red.
 *
 * The endpoints are deliberately the app's existing semantic colors - `deltaGood`
 * (#2FBF71) and `deltaBad` (#EF4E3A) - with the palette yellow at the midpoint, so the
 * ramp belongs to the same color world as the rest of the app rather than being a third
 * unrelated scale.
 *
 * Known tradeoff: green->red is the one axis red-green colorblind users can't separate,
 * and unlike the previous ramp its lightness is not monotonic (it peaks at the yellow
 * midpoint and falls off at both ends). The midpoint keeps the two halves distinguishable
 * by brightness, but slow and fast are genuinely similar in lightness. It is still the
 * right call here: this is the established convention for speed traces, and readers of a
 * racing telemetry app expect it.
 */
object SpeedColorUtils {

    // Heat ramp, slow -> fast.
    private val stops = listOf(
        Triple(0x2F, 0xBF, 0x71), // slow - green   (deltaGood)
        Triple(0xA3, 0xD1, 0x4B), // .    - yellow-green
        Triple(0xFE, 0xEA, 0x00), // mid  - yellow  (palette accent)
        Triple(0xF5, 0x90, 0x1E), // .    - orange
        Triple(0xEF, 0x4E, 0x3A)  // fast - red     (deltaBad)
    )

    fun speedToHex(t: Float): String {
        val clamped = t.coerceIn(0f, 1f)
        // Position within the ramp, then linearly interpolate between the two
        // neighbouring stops.
        val scaled = clamped * (stops.size - 1)
        val i = scaled.toInt().coerceAtMost(stops.size - 2)
        val f = scaled - i

        val (r0, g0, b0) = stops[i]
        val (r1, g1, b1) = stops[i + 1]

        val r = (r0 + (r1 - r0) * f).toInt().coerceIn(0, 255)
        val g = (g0 + (g1 - g0) * f).toInt().coerceIn(0, 255)
        val b = (b0 + (b1 - b0) * f).toInt().coerceIn(0, 255)

        return String.format("#%02X%02X%02X", r, g, b)
    }
}
