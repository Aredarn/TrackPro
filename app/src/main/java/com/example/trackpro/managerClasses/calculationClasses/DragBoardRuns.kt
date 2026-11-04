package com.example.trackpro.managerClasses.calculationClasses

import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.utilities.haversineDistance
import kotlin.math.max

/** The figures the drag leaderboard ranks. Always these, whatever units the driver reads. */
enum class DragBoardMetric { ZeroToHundred, HundredToTwoHundred, QuarterMile }

/** The best clean time a session holds for one metric. */
data class DragBoardResult(
    val metric: DragBoardMetric,
    val seconds: Double,
    /** Quarter mile only, in km/h. */
    val trapSpeedKmh: Float? = null
)

/**
 * What a recording is worth on the leaderboard.
 *
 * [DragTimeCalculation] reports the best of a session whatever the run looked like, which is
 * right for the driver's own history. A public board needs to know which run a time came from,
 * so here each run is timed on its own by that same calculator, and a time only counts if the
 * stretch of road it covers holds up: no dropout in the GPS, and no clear drop in elevation. The
 * next best clean run stands in for one that fails.
 *
 * Gaps are checked only over the stretch a metric actually covers, so a dropout on the way back
 * to the paddock costs a run nothing. Slope is read over at least [MIN_SLOPE_METERS] of the run,
 * because a 0-100 covers too little road for GPS altitude to say anything on its own.
 */
object DragBoardRuns {
    /** [DragTimeCalculation]'s own standstill threshold, so runs split where it splits them. */
    private const val STANDSTILL_KMH = 2f

    /** An ESP32 reports at 10 Hz, so even two missed fixes in a row are still clear of this. */
    private const val MAX_GAP_EXTERNAL_SECONDS = 0.5

    /** A phone reports about once a second; this allows one late fix, not a missing stretch. */
    private const val MAX_GAP_PHONE_SECONDS = 1.5

    /** GPS altitude wanders by a few metres on its own, so a shorter drop proves nothing. */
    private const val MIN_DROP_METERS = 5.0

    /** A 2% grade is worth tenths over a quarter mile. */
    private const val MAX_DOWNHILL_GRADE = 0.02

    /** Long enough that [MAX_DOWNHILL_GRADE] is a bigger drop than [MIN_DROP_METERS]. */
    private const val MIN_SLOPE_METERS = 300.0

    /** How much of each end of a stretch is averaged to read its altitude. */
    private const val ALTITUDE_WINDOW_MS = 1_000L

    private const val ROLLING_FROM_KMH = 100f

    private val zeroToHundred = DragSpeedScale.Metric.standingLabels[1]
    private val hundredToTwoHundred = DragSpeedScale.Metric.rollingLabels[1]

    fun maxGapSeconds(phoneGps: Boolean): Double =
        if (phoneGps) MAX_GAP_PHONE_SECONDS else MAX_GAP_EXTERNAL_SECONDS

    /** The best clean result per metric, in [DragBoardMetric] order. */
    fun bestResults(samples: List<RawGPSData>, maxGapSeconds: Double): List<DragBoardResult> {
        val fixes = samples.filter { it.valid != false }.sortedBy { it.timestamp }
        return runs(fixes)
            .flatMap { clean(it, maxGapSeconds) }
            .groupBy { it.metric }
            .values
            .map { results -> results.minBy { it.seconds } }
            .sortedBy { it.metric }
    }

    /** Each stretch from the last fix at rest to the next one at rest, or the recording's end. */
    private fun runs(fixes: List<RawGPSData>): List<List<RawGPSData>> {
        val runs = ArrayList<List<RawGPSData>>()
        var launch = -1
        for (i in fixes.indices) {
            if (fixes[i].kmh <= STANDSTILL_KMH) {
                if (launch >= 0) runs += fixes.subList(launch, i + 1)
                launch = -1
            } else if (launch < 0 && i > 0 && fixes[i - 1].kmh <= STANDSTILL_KMH) {
                launch = i - 1
            }
        }
        if (launch >= 0) runs += fixes.subList(launch, fixes.size)
        return runs
    }

    /** Every time one run set that survives the checks, improvements on a metric included. */
    private fun clean(run: List<RawGPSData>, maxGapSeconds: Double): List<DragBoardResult> {
        val calculator = DragTimeCalculation(scale = DragSpeedScale.Metric)
        val results = ArrayList<DragBoardResult>()
        var before = calculator.getCurrentMetrics()

        run.forEachIndexed { i, fix ->
            val now = calculator.processRealtimeGPS(fix, fix.timestamp)

            val standing = now.split(zeroToHundred)
            if (standing != null && standing != before.split(zeroToHundred) && holdsUp(run, 0, i, maxGapSeconds)) {
                results += DragBoardResult(DragBoardMetric.ZeroToHundred, standing)
            }

            val rolling = now.split(hundredToTwoHundred)
            if (rolling != null && rolling != before.split(hundredToTwoHundred)) {
                val from = (i downTo 0).firstOrNull { run[it].kmh <= ROLLING_FROM_KMH } ?: 0
                if (holdsUp(run, from, i, maxGapSeconds)) {
                    results += DragBoardResult(DragBoardMetric.HundredToTwoHundred, rolling)
                }
            }

            val quarter = now.quarterMileTime
            if (quarter != null && quarter != before.quarterMileTime && holdsUp(run, 0, i, maxGapSeconds)) {
                results += DragBoardResult(DragBoardMetric.QuarterMile, quarter, now.quarterMileSpeed)
            }

            before = now
        }
        return results
    }

    private fun holdsUp(run: List<RawGPSData>, from: Int, to: Int, maxGapSeconds: Double): Boolean {
        val stretch = run.subList(from, to + 1)
        return !hasGap(stretch, maxGapSeconds) && !isDownhill(slopeStretch(run, from, to))
    }

    /** From [from] to [to], carried on down the run until it covers [MIN_SLOPE_METERS]. */
    private fun slopeStretch(run: List<RawGPSData>, from: Int, to: Int): List<RawGPSData> {
        var end = to
        var meters = distance(run.subList(from, to + 1))
        while (end < run.lastIndex && meters < MIN_SLOPE_METERS) {
            meters += distance(run.subList(end, end + 2))
            end++
        }
        return run.subList(from, end + 1)
    }

    private fun hasGap(stretch: List<RawGPSData>, maxGapSeconds: Double): Boolean =
        stretch.zipWithNext().any { (a, b) -> (b.timestamp - a.timestamp) / 1000.0 > maxGapSeconds }

    /** False when altitude was not recorded: there is nothing to hold against the run. */
    private fun isDownhill(stretch: List<RawGPSData>): Boolean {
        val start = stretch.takeWhile { it.timestamp - stretch.first().timestamp <= ALTITUDE_WINDOW_MS }
            .meanAltitude() ?: return false
        val end = stretch.takeLastWhile { stretch.last().timestamp - it.timestamp <= ALTITUDE_WINDOW_MS }
            .meanAltitude() ?: return false
        return start - end > max(MIN_DROP_METERS, distance(stretch) * MAX_DOWNHILL_GRADE)
    }

    private fun distance(stretch: List<RawGPSData>): Double =
        stretch.zipWithNext().sumOf { (a, b) ->
            haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
        }

    private fun List<RawGPSData>.meanAltitude(): Double? =
        mapNotNull { it.altitude }.takeIf { it.isNotEmpty() }?.average()

    private fun DragMetrics.split(label: String): Double? =
        (standing + rolling).firstOrNull { it.label == label }?.seconds

    private val RawGPSData.kmh: Float get() = speed ?: 0f
}
