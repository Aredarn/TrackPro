package com.example.trackpro.managerClasses.utilities

import java.text.SimpleDateFormat
import java.util.Locale

object DateFormatterUtil {
    private const val DATE_PATTERN = "dd MMM yyyy"
    private const val TIME_PATTERN = "HH:mm"
    private const val DATE_TIME_PATTERN = "dd MMM yyyy, HH:mm"
    private const val LOG_TIMESTAMP_PATTERN = "HH:mm:ss.SSS"

    fun getDateFormat() = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
    fun getTimeFormat() = SimpleDateFormat(TIME_PATTERN, Locale.getDefault())
    fun getDateTimeFormat() = SimpleDateFormat(DATE_TIME_PATTERN, Locale.getDefault())
    fun getLogTimestampFormat() = SimpleDateFormat(LOG_TIMESTAMP_PATTERN, Locale.getDefault())
}
