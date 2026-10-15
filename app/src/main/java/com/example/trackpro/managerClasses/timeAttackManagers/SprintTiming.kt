package com.example.trackpro.managerClasses.timeAttackManagers

import android.os.SystemClock
import android.util.Log
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.utilities.haversineDistance
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToLong

/**
 * Point-to-point timing between a start line and a finish line.
 *
 * Crossing times are interpolated between fixes and mapped onto [clock] exactly as in
 * CircuitTimingManager - see there for why. The clocks are injectable so tests can drive time.
 *
 * Crossing a line is the primary trigger, but a sprint's lines sit on the first and last
 * recorded points - which is where the driver was standing when the track was built. So the
 * run usually begins *on* the start line and ends *at* the finish line, and a car that never
 * drives across a line never trips it. Two fallbacks cover that: a standing start (rest at the
 * start, then move off) and a standing finish (come to rest at the finish).
 */
class SprintTimingManager(
    private val startLine: List<TrackCoordinatesData>,
    private val finishLine: List<TrackCoordinatesData>,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    /** Further gates that also start/finish a run; see [TrackGeometry.calculateSprintBackupGates]. */
    private val extraStartLines: List<List<TrackCoordinatesData>> = emptyList(),
    private val extraFinishLines: List<List<TrackCoordinatesData>> = emptyList()
) : TimingManager() {

    private fun firstForwardCrossing(
        prev: RawGPSData, current: RawGPSData, lines: List<List<TrackCoordinatesData>>
    ): TrackGeometry.CrossingResult? = lines.firstNotNullOfOrNull {
        TrackGeometry.checkLineCrossing(prev, current, it)?.takeIf { r -> r.isForward }
    }

    private var sprintStartTime = 0.0
    private var bestSprintSeconds = Double.POSITIVE_INFINITY
    private var hasStarted = false
    private var hasFinished = false

    val sprintCompletedChannel = Channel<CompletedLap>(Channel.UNLIMITED)

    /** Whether the run in progress has seen a GPS gap; see [CompletedLap.signalGap]. */
    private var runHadGap = false

    private val startCentre = centreOf(startLine)
    private val finishCentre = centreOf(finishLine)

    /** Standstill near the start line, waiting to move off. */
    private val startRest = Rest()
    /** Standstill near the finish line, ending the run. */
    private val finishRest = Rest()

    /** Fixes spent standing still, so a standing start/finish can be told from passing by. */
    private class Rest {
        var anchor: RawGPSData? = null
        var last: RawGPSData? = null

        fun clear() {
            anchor = null
            last = null
        }

        /** Has been within [REST_RADIUS_M] of the anchor for at least [REST_MIN_MS]. */
        val settled: Boolean
            get() {
                val a = anchor ?: return false
                val l = last ?: return false
                return l.timestamp - a.timestamp >= REST_MIN_MS
            }

        /** Feeds a fix. Returns true while it is still at rest around the anchor. */
        fun feed(fix: RawGPSData): Boolean {
            val a = anchor
            if (a == null || distance(fix, a) > REST_RADIUS_M) {
                anchor = fix
                last = fix
                return false
            }
            last = fix
            return true
        }
    }

    override fun handleGpsUpdate(prev: RawGPSData?, current: RawGPSData) {
        val now = clock()
        val nowWall = wallClock()
        if (prev == null) return

        fun mapped(timestamp: Long): Double = now - (nowWall - timestamp.toDouble())
        fun crossedAt(fraction: Double): Double =
            now - (nowWall - TrackGeometry.crossingTimeMs(prev, current, fraction))

        // Before the finish check, so a gap that swallowed the line marks this run.
        if (hasStarted && !hasFinished && isSignalGap(prev, current)) runHadGap = true

        // 1. START LOGIC: Only look for start if we haven't moved yet
        if (!hasStarted) {
            val startResult = firstForwardCrossing(prev, current, listOf(startLine) + extraStartLines)
            if (startResult != null) {
                beginRun(crossedAt(startResult.fraction))
                Log.d("SprintManager", "START LINE CROSSED")
            } else {
                // Standing start: at rest on the line, then away. The run starts at the last
                // moment the car was still, not when movement was noticed.
                val centre = startCentre
                val rest = startRest.anchor
                if (startRest.settled && rest != null && distance(current, rest) > REST_RADIUS_M) {
                    // Checked before the zone test: a hard launch can be well outside the zone
                    // by the very next fix.
                    beginRun(mapped(startRest.last?.timestamp ?: prev.timestamp))
                    Log.d("SprintManager", "STANDING START")
                } else if (centre != null && distance(current, centre) <= LINE_ZONE_M) {
                    startRest.feed(current)
                } else {
                    startRest.clear()
                }
            }
        }

        // 2. FINISH LOGIC: Only look for finish if we are currently running
        else if (hasStarted && !hasFinished) {
            val finishResult = firstForwardCrossing(prev, current, listOf(finishLine) + extraFinishLines)
            if (finishResult != null) {
                finishRun((crossedAt(finishResult.fraction) - sprintStartTime).roundToLong())
                Log.d("SprintManager", "FINISH LINE CROSSED")
            } else {
                // Standing finish: the car stopped at the end without driving across the line
                // (parked short of it, or GPS drifted to one side). Timed to when it stopped.
                val centre = finishCentre
                if (centre != null && distance(current, centre) <= LINE_ZONE_M) {
                    finishRest.feed(current)
                    val stoppedAt = finishRest.anchor?.timestamp
                    if (finishRest.settled && stoppedAt != null) {
                        val ms = (mapped(stoppedAt) - sprintStartTime).roundToLong()
                        if (ms >= MIN_RUN_MS) {
                            finishRun(ms)
                            Log.d("SprintManager", "STANDING FINISH")
                        }
                    }
                } else {
                    finishRest.clear()
                }
            }
        }

        // 3. Live UI Update
        if (hasStarted && !hasFinished) {
            _currentTime.value = formatTime((now - sprintStartTime).roundToLong().coerceAtLeast(0))
        }
    }

    private fun beginRun(startMs: Double) {
        sprintStartTime = startMs
        hasStarted = true
        hasFinished = false
        runHadGap = false
        startRest.clear()
        finishRest.clear()
    }

    private fun finishRun(sprintMs: Long) {
        updateTimes(sprintMs)
        _eventCount.value += 1
        val finishedRun = CompletedLap(
            number = _eventCount.value,
            timeMs = sprintMs,
            signalGap = runHadGap
        )
        _completedLaps.value = _completedLaps.value + finishedRun
        sprintCompletedChannel.trySend(finishedRun)

        hasStarted = false // Reset for next run
        hasFinished = true
        startRest.clear()
        finishRest.clear()
        Log.d("SprintManager", "RUN FINISHED: $sprintMs ms")
    }

    private fun updateTimes(sprintMs: Long) {
        val seconds = sprintMs / 1000.0
        _delta.value = if (bestSprintSeconds.isFinite()) seconds - bestSprintSeconds else 0.0
        if (seconds < bestSprintSeconds) {
            bestSprintSeconds = seconds
            _bestTime.value = formatTime(sprintMs)
        }
        _lastTime.value = formatTime(sprintMs)
    }

    override fun reset() {
        runHadGap = false
        sprintStartTime = 0.0
        hasStarted = false
        hasFinished = false
        startRest.clear()
        finishRest.clear()
        _stintStart.value = clock()
        _eventCount.value = 0
        _completedLaps.value = emptyList()
        _currentTime.value = formatTime(0)
    }

    override fun startNewEvent() {
        hasStarted = false
        hasFinished = false
        startRest.clear()
        finishRest.clear()
        _currentTime.value = formatTime(0)
    }

    private companion object {
        /** How near a line's centre the car must be for a standing start/finish to count. */
        const val LINE_ZONE_M = 30.0

        /** Drift allowed while "standing still" - above typical GPS jitter, below real driving. */
        const val REST_RADIUS_M = 3.0

        /** How long the car must stay put before it counts as a deliberate stop. */
        const val REST_MIN_MS = 1_000L

        /** A "run" shorter than this is the car shuffling about, not a run. */
        const val MIN_RUN_MS = 3_000L

        fun centreOf(line: List<TrackCoordinatesData>): Pair<Double, Double>? =
            if (line.size < 2) null
            else line.map { it.latitude }.average() to line.map { it.longitude }.average()

        fun distance(a: RawGPSData, b: RawGPSData): Double =
            haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)

        fun distance(a: RawGPSData, centre: Pair<Double, Double>): Double =
            haversineDistance(a.latitude, a.longitude, centre.first, centre.second)
    }
}
