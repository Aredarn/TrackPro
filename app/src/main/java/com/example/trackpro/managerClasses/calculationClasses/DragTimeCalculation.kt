package com.example.trackpro.managerClasses.calculationClasses

import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.haversineDistance
import kotlin.math.round

/**
 * One acceleration split, already labelled in the units of the driver.
 *
 * The label travels with the value so a screen can never pair a figure with the wrong
 * milestone - which is what happened when the labels were hardcoded strings in the UI and
 * the thresholds were hardcoded km/h in the calculator.
 */
data class DragSplit(val label: String, val seconds: Double? = null)

data class DragMetrics(
    /** Standing-start splits, in the order [DragSpeedScale.standingSpeeds] defines. */
    val standing: List<DragSplit> = emptyList(),
    /** Rolling splits, in the order [DragSpeedScale.rollingSpeeds] defines. */
    val rolling: List<DragSplit> = emptyList(),
    val quarterMileTime: Double? = null,
    /** Trap speed in km/h, the canonical unit; convert at display time. */
    val quarterMileSpeed: Float? = null,
    val halfMileTime: Double? = null,
    val maxSpeed: Float = 0f,
    val totalDistance: Float = 0f,
    /** Launches made this session. Every figure above is the best of them. */
    val runCount: Int = 0
)

/**
 * Drag metrics for a recording session.
 *
 * A session holds as many runs as the driver makes: a run begins when the car pulls away
 * from a standstill and ends when it comes back to one, at which point the timer re-arms for
 * the next launch. Every figure reported is the **best** of the session's runs, so a botched
 * first launch - or a roll out of the paddock - is beaten by a later one rather than owning
 * the number for the rest of the session.
 *
 * Which milestones get measured comes from [scale], so an imperial driver is timed to 0-60
 * mph rather than to 0-60 km/h wearing an mph label. The quarter and half mile are imperial
 * in both, being imperial measures to begin with.
 */
class DragTimeCalculation(
    val session: Long? = null,
    private val database: ESPDatabase,
    private val scale: DragSpeedScale = DragSpeedScale.Metric
) {
    companion object {
        private const val QUARTER_MILE_METERS = 402.336f
        private const val HALF_MILE_METERS = 804.672f

        /**
         * How far below its start speed a rolling interval must fall before it will time
         * another. A GPS noise guard, so expressed in km/h in both unit systems.
         */
        private const val ROLLING_RESET_MARGIN_KMH = 5f
    }

    /** At or below this the car counts as stopped: a run can start, and an open one ends. */
    private val zeroThreshold = 2.0f

    /**
     * A run only counts toward [DragMetrics.runCount] once it reaches this speed. GPS noise
     * at a standstill flickers either side of [zeroThreshold], which would otherwise tick the
     * counter up without the car ever having moved.
     */
    private val runCommitSpeedKmh = 20f

    // Session-wide
    private var lastPoint: LatLonOffset? = null
    private var totalDistanceMeters: Float = 0f
    private var maxSpeedRecorded: Float = 0f
    private var runCount: Int = 0

    // Current run
    private var runStartTime: Long = 0L
    private var runStartDistanceMeters: Float = 0f
    private var hasStartedRun: Boolean = false
    private var isReadyForRun: Boolean = false
    private var runCommitted: Boolean = false

    // Best of the session. Positional, matching the ordering the scale defines.
    private val standingThresholdsKmh: List<Float> = scale.standingSpeeds.map { scale.toKmh(it) }
    private val standingResults: Array<Double?> = arrayOfNulls(scale.standingSpeeds.size)
    private val rollingIntervals: List<RollingInterval> = scale.rollingSpeeds.map { (from, to) ->
        val fromKmh = scale.toKmh(from)
        RollingInterval(
            fromSpeed = fromKmh,
            toSpeed = scale.toKmh(to),
            resetSpeed = fromKmh - ROLLING_RESET_MARGIN_KMH
        )
    }
    private var quarterMileTimeResult: Double? = null
    private var quarterMileSpeedResult: Float? = null
    private var halfMileTimeResult: Double? = null

    fun processRealtimeGPS(gpsData: RawGPSData, currentTimeMillis: Long): DragMetrics {
        val currentSpeed = gpsData.speed ?: 0f

        // --- 1. SESSION-WIDE STATS ---
        if (currentSpeed > maxSpeedRecorded) maxSpeedRecorded = currentSpeed

        val currentPoint = LatLonOffset(gpsData.latitude, gpsData.longitude)
        lastPoint?.let { last ->
            val dist = haversineDistance(last.lat, last.lon, currentPoint.lat, currentPoint.lon)
            totalDistanceMeters += dist.toFloat()
        }
        lastPoint = currentPoint

        // --- 2. RUN WINDOW ---
        // One run is one launch: it opens when the car moves off from a standstill and closes
        // when it returns to one, which re-arms the timer for the next launch.
        if (hasStartedRun) {
            if (!runCommitted && currentSpeed >= runCommitSpeedKmh) {
                runCommitted = true
                runCount += 1
            }
            if (currentSpeed <= zeroThreshold) {
                hasStartedRun = false
                runCommitted = false
                isReadyForRun = true
            }
        } else {
            if (currentSpeed <= zeroThreshold) isReadyForRun = true
            if (isReadyForRun && currentSpeed > zeroThreshold) {
                hasStartedRun = true
                runCommitted = false
                runStartTime = currentTimeMillis
                runStartDistanceMeters = totalDistanceMeters
            }
        }

        // --- 3. STANDING METRICS (within a run) ---
        // Each keeps the lowest elapsed time ever seen for it. Within one run that is the
        // sample the car first crossed the threshold on, since elapsed only grows; across
        // runs it is whichever run did it quickest.
        if (hasStartedRun) {
            val elapsed = (currentTimeMillis - runStartTime) / 1000.0

            standingThresholdsKmh.forEachIndexed { i, thresholdKmh ->
                if (currentSpeed >= thresholdKmh) {
                    standingResults[i] = bestOf(standingResults[i], elapsed)
                }
            }

            // Distance covered since this launch, not since the recording started, so drift
            // before the run and any earlier run are both excluded.
            val runDistance = totalDistanceMeters - runStartDistanceMeters

            if (runDistance >= QUARTER_MILE_METERS && isBetter(quarterMileTimeResult, elapsed)) {
                quarterMileTimeResult = elapsed
                // Trap speed belongs to the run that set the time, so it is captured here
                // rather than tracked on its own.
                quarterMileSpeedResult = currentSpeed
            }
            if (runDistance >= HALF_MILE_METERS) {
                halfMileTimeResult = bestOf(halfMileTimeResult, elapsed)
            }
        }

        // --- 4. ROLLING METRICS ---
        // Deliberately outside the run window: a roll-on pull needs no standing start, and a
        // session can be started with the car already moving.
        rollingIntervals.forEach { it.update(currentSpeed, currentTimeMillis) }

        return getCurrentMetrics()
    }

    /** Current snapshot: the best of every metric across the session's runs so far. */
    fun getCurrentMetrics(): DragMetrics {
        return DragMetrics(
            standing = scale.standingLabels.mapIndexed { i, label ->
                DragSplit(label, standingResults[i])
            },
            rolling = scale.rollingLabels.mapIndexed { i, label ->
                DragSplit(label, rollingIntervals[i].bestSeconds)
            },
            quarterMileTime = quarterMileTimeResult,
            quarterMileSpeed = quarterMileSpeedResult,
            halfMileTime = halfMileTimeResult,
            maxSpeed = maxSpeedRecorded,
            totalDistance = totalDistanceMeters,
            runCount = runCount
        )
    }

    /**
     * Reset all real-time tracking (call when starting a new session).
     *
     * Not needed between runs within a session: the run window re-arms itself when the car
     * stops, and the results are kept because they are the session's bests.
     */
    fun resetRealtimeTracking() {
        lastPoint = null
        totalDistanceMeters = 0f
        maxSpeedRecorded = 0f
        runCount = 0

        runStartTime = 0L
        runStartDistanceMeters = 0f
        hasStartedRun = false
        isReadyForRun = false
        runCommitted = false

        standingResults.fill(null)
        rollingIntervals.forEach { it.reset() }

        quarterMileTimeResult = null
        quarterMileSpeedResult = null
        halfMileTimeResult = null
    }

    fun calculateFullSessionMetrics(sessionData: List<RawGPSData>): DragMetrics {
        resetRealtimeTracking()

        sessionData.sortedBy { it.timestamp }.forEach { data ->
            processRealtimeGPS(data, data.timestamp)
        }

        return getCurrentMetrics()
    }

    /**
     * Calculate total distance from a list of coordinates
     */
    fun totalDistance(latLngList: List<LatLonOffset>): Double {
        var totalDistance = 0.0
        for (i in 0 until latLngList.size - 1) {
            val point1 = latLngList[i]
            val point2 = latLngList[i + 1]
            totalDistance += haversineDistance(point1.lat, point1.lon, point2.lat, point2.lon)
        }

        return round(totalDistance * 1000) / 1000
    }

    private fun isBetter(current: Double?, candidate: Double): Boolean =
        current == null || candidate < current

    private fun bestOf(current: Double?, candidate: Double): Double =
        if (isBetter(current, candidate)) candidate else current!!

    /**
     * A rolling acceleration interval such as 50-150 km/h, measured every time the car pulls
     * through it rather than only the first time, keeping the quickest.
     *
     * Two things it deliberately refuses to time. It will not open a window unless the car
     * has been seen at or below [fromSpeed], so joining a recording already at 140 km/h
     * cannot produce an instant "50-150". And once an interval is recorded it will not open
     * another until the car drops back below [resetSpeed], so finishing a pull at 160 km/h
     * does not immediately start timing the next one from there.
     */
    private class RollingInterval(
        private val fromSpeed: Float,
        private val toSpeed: Float,
        private val resetSpeed: Float
    ) {
        private var startedAt: Long? = null
        private var armed = false

        var bestSeconds: Double? = null
            private set

        fun update(speedKmh: Float, nowMillis: Long) {
            if (speedKmh <= fromSpeed) armed = true
            // Dropping out of the band abandons an open window; the next approach through
            // fromSpeed starts a fresh one.
            if (speedKmh < resetSpeed) startedAt = null
            if (!armed) return

            if (startedAt == null) {
                if (speedKmh >= fromSpeed) startedAt = nowMillis
                return
            }

            if (speedKmh >= toSpeed) {
                val seconds = (nowMillis - startedAt!!) / 1000.0
                if (bestSeconds == null || seconds < bestSeconds!!) bestSeconds = seconds
                startedAt = null
                armed = false
            }
        }

        fun reset() {
            startedAt = null
            armed = false
            bestSeconds = null
        }
    }
}
