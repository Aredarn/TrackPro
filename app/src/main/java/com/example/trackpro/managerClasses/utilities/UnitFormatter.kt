package com.example.trackpro.managerClasses.utilities

import kotlin.math.roundToInt
import java.util.Locale

/**
 * Converts and formats GPS-derived speed/distance values for display.
 * `metric = true` means km/h and meters/kilometers; `metric = false` means mph and feet/miles.
 */
object UnitFormatter {
    private const val KM_TO_MILES = 0.621371
    private const val METERS_TO_FEET = 3.28084
    private const val FEET_PER_MILE = 5280.0

    fun speedUnitLabel(metric: Boolean) = if (metric) "KM/H" else "MPH"

    /** Converts a km/h speed value to the display unit (no formatting). */
    fun convertSpeed(kmh: Double, metric: Boolean): Double =
        if (metric) kmh else kmh * KM_TO_MILES

    fun convertSpeed(kmh: Float, metric: Boolean): Float =
        if (metric) kmh else (kmh * KM_TO_MILES).toFloat()

    /** Converts a speed value in the display unit back to km/h (the app's canonical storage unit). */
    fun convertSpeedToKmh(value: Double, metric: Boolean): Double =
        if (metric) value else value / KM_TO_MILES

    fun temperatureUnitLabel(metric: Boolean) = if (metric) "°C" else "°F"

    /** Formats a Celsius temperature in the display unit, e.g. "23.7 °C" / "74.7 °F". */
    fun formatTemperature(celsius: Double, metric: Boolean): String {
        val value = if (metric) celsius else celsius * 9.0 / 5.0 + 32.0
        return String.format(Locale.US, "%.1f %s", value, temperatureUnitLabel(metric))
    }

    /** Formats a km/h speed as "123" / "76" (no unit suffix) rounded to the nearest whole number. */
    fun formatSpeed(kmh: Double, metric: Boolean): String =
        convertSpeed(kmh, metric).roundToInt().toString()

    fun formatSpeed(kmh: Float, metric: Boolean): String =
        convertSpeed(kmh, metric).roundToInt().toString()

    /** Formats a km/h speed with one decimal place, e.g. "123.4". */
    fun formatSpeedPrecise(kmh: Double, metric: Boolean): String =
        String.format(Locale.US, "%.1f", convertSpeed(kmh, metric))

    /**
     * Formats a distance given in meters as e.g. "402 m" / "1320 ft" / "1.20 km" / "2.71 mi".
     *
     * Each system steps up to its larger unit at one of that unit: a kilometre, a mile. The
     * imperial side used to switch at a thousand *feet* - a fifth of a mile - so a quarter
     * mile run read as "0.25 mi" where metric showed "402 m", and everything between 305 m
     * and a mile was quoted as an awkward fraction instead of the feet a drag strip is
     * actually measured in.
     */
    fun formatDistance(meters: Double, metric: Boolean): String {
        return if (metric) {
            if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000.0)
            else String.format(Locale.US, "%.0f m", meters)
        } else {
            val feet = meters * METERS_TO_FEET
            if (feet >= FEET_PER_MILE) String.format(Locale.US, "%.2f mi", feet / FEET_PER_MILE)
            else String.format(Locale.US, "%.0f ft", feet)
        }
    }
}
