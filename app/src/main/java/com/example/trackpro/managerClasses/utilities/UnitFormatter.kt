package com.example.trackpro.managerClasses.utilities

import kotlin.math.roundToInt

/**
 * Converts and formats GPS-derived speed/distance values for display.
 * `metric = true` means km/h and meters/kilometers; `metric = false` means mph and feet/miles.
 */
object UnitFormatter {
    private const val KM_TO_MILES = 0.621371
    private const val METERS_TO_FEET = 3.28084

    fun speedUnitLabel(metric: Boolean) = if (metric) "KM/H" else "MPH"

    /** Converts a km/h speed value to the display unit (no formatting). */
    fun convertSpeed(kmh: Double, metric: Boolean): Double =
        if (metric) kmh else kmh * KM_TO_MILES

    fun convertSpeed(kmh: Float, metric: Boolean): Float =
        if (metric) kmh else (kmh * KM_TO_MILES).toFloat()

    /** Converts a speed value in the display unit back to km/h (the app's canonical storage unit). */
    fun convertSpeedToKmh(value: Double, metric: Boolean): Double =
        if (metric) value else value / KM_TO_MILES

    /** Formats a km/h speed as "123" / "76" (no unit suffix) rounded to the nearest whole number. */
    fun formatSpeed(kmh: Double, metric: Boolean): String =
        convertSpeed(kmh, metric).roundToInt().toString()

    fun formatSpeed(kmh: Float, metric: Boolean): String =
        convertSpeed(kmh, metric).roundToInt().toString()

    /** Formats a km/h speed with one decimal place, e.g. "123.4". */
    fun formatSpeedPrecise(kmh: Double, metric: Boolean): String =
        String.format("%.1f", convertSpeed(kmh, metric))

    /** Formats a distance given in meters as e.g. "402 m" / "0.25 mi" / "1.20 km" / "1320 ft". */
    fun formatDistance(meters: Double, metric: Boolean): String {
        return if (metric) {
            if (meters >= 1000) String.format("%.2f km", meters / 1000.0)
            else String.format("%.0f m", meters)
        } else {
            val feet = meters * METERS_TO_FEET
            if (feet >= 1000) String.format("%.2f mi", (meters / 1000.0) * KM_TO_MILES)
            else String.format("%.0f ft", feet)
        }
    }
}
