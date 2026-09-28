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

    // Shown to the driver.
    fun getDateFormat() = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
    fun getTimeFormat() = SimpleDateFormat(TIME_PATTERN, Locale.getDefault())
    fun getDateTimeFormat() = SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault())

    /** Read from and written to the rig. Never localised. */
    fun getLogTimestampFormat() = SimpleDateFormat(LOG_TIMESTAMP_PATTERN, Locale.ROOT)
}
