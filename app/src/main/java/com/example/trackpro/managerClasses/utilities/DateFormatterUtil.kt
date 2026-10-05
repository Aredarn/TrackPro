package com.example.trackpro.managerClasses.utilities

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Two kinds of time string, and they do not share a locale.
 *
 * The display formats follow the phone: a session date shown to a Hungarian driver should
 * read in Hungarian. The wire format does not - it parses the timestamp the ESP32 sends,
 * and on a locale whose default numbering system is not Latin (Arabic-Indic, for one)
 * `Locale.getDefault()` would fail to read machine-written ASCII digits back.
 */
object DateFormatterUtil {
    private const val DATE_PATTERN = "dd MMM yyyy"
    private const val TIME_PATTERN = "HH:mm"
    private const val DATE_TIME_PATTERN = "dd MMM yyyy, HH:mm"
    private const val LOG_TIMESTAMP_PATTERN = "HH:mm:ss.SSS"

    // Hungarian writes dates year first and ends the day with a full stop: 2026. szept. 28.
    private val hungarian get() = Locale.getDefault().language == "hu"

    // Shown to the driver.
    fun getDateFormat() = SimpleDateFormat(if (hungarian) "yyyy. MMM d." else DATE_PATTERN, Locale.getDefault())
    fun getTimeFormat() = SimpleDateFormat(TIME_PATTERN, Locale.getDefault())
    fun getDateTimeFormat() = SimpleDateFormat(if (hungarian) "yyyy. MMM d., HH:mm" else DATE_TIME_PATTERN, Locale.getDefault())

    /** Day and month, for java.time: "28 Sep" / "szept. 28.". */
    fun dayMonthPattern() = if (hungarian) "MMM d." else "dd MMM"
    /** Weekday and time, for java.time: "Monday, 11:24" / "hétfő 11:24". */
    fun weekdayTimePattern() = if (hungarian) "EEEE HH:mm" else "EEEE, HH:mm"

    /** Read from and written to the rig. Never localised. */
    fun getLogTimestampFormat() = SimpleDateFormat(LOG_TIMESTAMP_PATTERN, Locale.ROOT)
}
