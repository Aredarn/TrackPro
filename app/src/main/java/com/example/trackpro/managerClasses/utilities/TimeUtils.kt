package com.example.trackpro.managerClasses.utilities

import com.example.trackpro.dataClasses.LapTimeData
import java.util.Locale

/**
 * The sentinel values stored in [LapTimeData.laptime] in place of a time.
 *
 * A lap row is inserted the moment the lap *starts*, so it exists before it has a time and
 * carries [IN_PROGRESS] until the finish line is crossed. Ending a session deletes whatever
 * in-progress row is left over, but that cleanup only runs when the timing screen is
 * disposed normally - a session the OS killed (swipe-away, crash, low memory) keeps its row
 * forever. So every reader has to expect a lap with no time in it.
 */
object LapStatus {
    const val IN_PROGRESS = "IN PROGRESS"
    const val INVALID = "INVALID"
}

/**
 * Parses a stored "MM:SS.hh" lap time, or returns null if this is not one.
 *
 * Strict on purpose. The lenient version this replaced treated every unparseable field as
 * zero, so [LapStatus.IN_PROGRESS] came back as a 0 ms lap - which then won every
 * "best lap" comparison in the app and dragged averages toward zero. Anything that is not a
 * real time now has to be handled explicitly by the caller, and [timed] is usually the
 * easiest way to do that.
 */
fun String.toLapTimeMillisOrNull(): Long? {
    val parts = this.split(":", ".", limit = 3)
    if (parts.size != 3) return null
    val minutes = parts[0].toLongOrNull() ?: return null
    val seconds = parts[1].toLongOrNull() ?: return null
    val hundredths = parts[2].toLongOrNull() ?: return null
    return minutes * 60_000 + seconds * 1_000 + hundredths * 10
}

/** A lap that has actually been timed, with its time already parsed. */
data class TimedLap(val lap: LapTimeData, val millis: Long)

/**
 * The laps that have a recorded time, in lap order, each paired with that time.
 *
 * This is what session analysis should be built on: it drops rows that are still in
 * progress, marked invalid, or otherwise unparseable, and it parses each time once rather
 * than re-parsing the string at every use. Completeness is decided by whether the time
 * parses rather than by matching [LapStatus] strings, so corrupt values are excluded too.
 *
 * Ordered here as well as in the query, because the session trend splits this list
 * positionally into halves and would otherwise depend on the order rows came back in.
 */
fun List<LapTimeData>.timed(): List<TimedLap> =
    mapNotNull { lap -> lap.laptime.toLapTimeMillisOrNull()?.let { TimedLap(lap, it) } }
        .sortedBy { it.lap.lapnumber }

/**
 * Formats a duration as "MM:SS.hh". The exact inverse of [toLapTimeMillisOrNull] for any
 * non-negative value. It renders the magnitude only - use [toLapDeltaString] for anything
 * that can be negative.
 */
fun Long.toLapTimeString(): String {
    val abs = if (this < 0) -this else this
    val minutes = abs / 60_000
    val seconds = (abs % 60_000) / 1_000
    val hundredths = (abs % 1_000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
}

/**
 * Formats a signed time difference, e.g. "+00:01.20" or "-00:01.20".
 *
 * Every delta display has to go through this. [toLapTimeString] renders the magnitude only,
 * so call sites that prefixed a "+" for positive values and nothing for negative ones drew a
 * lap gained and a lap lost identically, leaving colour as the only thing telling them
 * apart. An exact tie gets no sign, because neither one would be true.
 */
fun Long.toLapDeltaString(): String = when {
    this < 0L -> "-" + toLapTimeString()
    this > 0L -> "+" + toLapTimeString()
    else -> toLapTimeString()
}
