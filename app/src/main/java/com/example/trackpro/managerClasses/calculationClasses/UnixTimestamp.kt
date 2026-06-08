package com.example.trackpro.managerClasses.calculationClasses

import com.example.trackpro.managerClasses.utilities.DateFormatterUtil
import java.util.*

fun convertToUnixTimestamp(timestamp: String): Long {
    val date = DateFormatterUtil.getLogTimestampFormat().parse(timestamp) ?: return 0

    // Get current date
    val calendar = Calendar.getInstance().apply {
        val timeCal = Calendar.getInstance().apply { time = date }
        set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
        set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
        set(Calendar.SECOND, timeCal.get(Calendar.SECOND))
        set(Calendar.MILLISECOND, timeCal.get(Calendar.MILLISECOND))
    }

    return calendar.timeInMillis // Returns Unix timestamp in milliseconds
}
