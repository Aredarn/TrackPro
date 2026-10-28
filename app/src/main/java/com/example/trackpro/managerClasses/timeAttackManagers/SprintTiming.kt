package com.example.trackpro.managerClasses.timeAttackManagers

import android.os.SystemClock
import android.util.Log
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.utilities.haversineDistance
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToLong

/**
 * Point-to-point timing between two ends of a route, driven in either direction.
 *
 * Whichever end the car leaves from is the start and the other is the finish: leaving the
 * recorded start towards the finish is a forward run, leaving the finish back towards the
 * start is a reverse run. Crossing times are interpolated between fixes and mapped onto
 * [clock] exactly as in CircuitTimingManager - see there for why. The clocks are injectable
 * so tests can drive time.
 *
 * Crossing a gate is the primary trigger, but a sprint's gates sit on the first and last
 * recorded points - which is where the driver stood when the track was built. So a run often
 * begins *on* a gate and ends *at* one, and a car that never drives across it never trips it.
 * Two fallbacks cover that: a standing start (rest at an end, then move off) and a standing
 * finish (come to rest at the other end).
 */
class SprintTimingManager(
    private val startLine: List<TrackCoordinatesData>,
    private val finishLine: List<TrackCoordinatesData>,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() },
    /** Further gates at the start end; see [TrackGeometry.calculateSprintBackupGates]. */
    private val extraStartLines: List<List<TrackCoordinatesData>> = emptyList(),
    /** Further gates at the finish end. */
    private val extraFinishLines: List<List<TrackCoordinatesData>> = emptyList()
) : TimingManager() {

    private enum class Way { FORWARD, REVERSE }

    private val startGates = listOf(startLine) + extraStartLines
    private val finishGates = listOf(finishLine) + extraFinishLines

    private var way = Way.FORWARD
    private var sprintStartTime = 0.0
    // Forward and reverse are different roads as far as pace goes, so each has its own best.
    private val bestSeconds = mutableMapOf(Way.FORWARD to Double.POSITIVE_INFINITY, Way.REVERSE to Double.POSITIVE_INFINITY)
    private var hasStarted = false
    private var hasFinished = false
    private var lastFinishTimestamp = NEVER

    /** Closest fix to the run's target end so far, for the passed-by finish; see tryFinish. */
    private var closestApproach: RawGPSData? = null
    private var closestApproachM = Double.MAX_VALUE

    val sprintCompletedChannel = Channel<CompletedLap>(Channel.UNLIMITED)

    /** True from the start of a run until it finishes. */
    val isRunning: Boolean get() = hasStarted && !hasFinished

    /** Whether the run in progress has seen a GPS gap; see [CompletedLap.signalGap]. */
    private var runHadGap = false

    private val startCentre = centreOf(startLine)
    private val finishCentre = centreOf(finishLine)

    /** Standstill near the start end / the finish end, whichever way the run goes. */
    private val restAtStart = Rest()
    private val restAtFinish = Rest()

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

        fun feed(fix: RawGPSData) {
            val a = anchor
            if (a == null || distance(fix, a) > REST_RADIUS_M) {
                anchor = fix
                last = fix
            } else {
                last = fix
            }
        }
    }

    /** First gate in [gates] crossed in the wanted direction (forward = recorded direction). */
    private fun crossing(
        prev: RawGPSData, current: RawGPSData,
        gates: List<List<TrackCoordinatesData>>, forward: Boolean
    ): TrackGeometry.CrossingResult? = gates.firstNotNullOfOrNull {
        TrackGeometry.checkLineCrossing(prev, current, it)?.takeIf { r -> r.isForward == forward }
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

        if (!hasStarted) {
            // Right after a finish the car overshoots and may roll back over the line it just
            // crossed - which would otherwise read as the start of a reverse run.
            val settling = current.timestamp - lastFinishTimestamp < ROLLBACK_GUARD_MS
            if (!settling) tryStart(prev, current, ::crossedAt, ::mapped)
        } else if (!hasFinished) {
            tryFinish(prev, current, ::crossedAt, ::mapped)
        }

        // Live UI update
        if (hasStarted && !hasFinished) {
            _currentTime.value = formatTime((now - sprintStartTime).roundToLong().coerceAtLeast(0))
        }
    }

    private fun tryStart(
        prev: RawGPSData, current: RawGPSData,
        crossedAt: (Double) -> Double, mapped: (Long) -> Double
    ) {
        // Forward: out of the start gate towards the finish. Reverse: out of the finish gate
        // back the way the track was recorded *against*.
        crossing(prev, current, startGates, forward = true)?.let {
            beginRun(Way.FORWARD, crossedAt(it.fraction))
            Log.d("SprintManager", "START LINE CROSSED")
            return
        }
        crossing(prev, current, finishGates, forward = false)?.let {
            beginRun(Way.REVERSE, crossedAt(it.fraction))
            Log.d("SprintManager", "REVERSE START: FINISH LINE CROSSED BACKWARDS")
            return
        }

        // Standing start from either end. The run starts at the last moment the car was
        // still, not when movement was noticed.
        launchFrom(restAtStart, startCentre, current, Way.FORWARD, mapped)
        launchFrom(restAtFinish, finishCentre, current, Way.REVERSE, mapped)
    }

    private fun launchFrom(
        rest: Rest, centre: Pair<Double, Double>?, current: RawGPSData,
        goes: Way, mapped: (Long) -> Double
    ) {
        if (hasStarted) return
        val anchor = rest.anchor
        if (rest.settled && anchor != null && distance(current, anchor) > REST_RADIUS_M) {
            // Checked before the zone test: a hard launch can be well outside the zone by the
            // very next fix.
            beginRun(goes, mapped(rest.last?.timestamp ?: current.timestamp))
            Log.d("SprintManager", "STANDING START ($goes)")
        } else if (centre != null && distance(current, centre) <= LINE_ZONE_M) {
            rest.feed(current)
        } else {
            rest.clear()
        }
    }

    private fun tryFinish(
        prev: RawGPSData, current: RawGPSData,
        crossedAt: (Double) -> Double, mapped: (Long) -> Double
    ) {
        val forward = way == Way.FORWARD
        // The gates this run is heading for, crossed in the direction it is heading.
        val endGates = if (forward) finishGates else startGates
        crossing(prev, current, endGates, forward = forward)?.let {
            finishRun((crossedAt(it.fraction) - sprintStartTime).roundToLong(), current.timestamp)
            Log.d("SprintManager", "FINISH LINE CROSSED")
            return
        }

        // Crossing the gate this run left from again, the same way, is a restart: a false
        // start, or backing up and having another go.
        val homeGates = if (forward) startGates else finishGates
        crossing(prev, current, homeGates, forward = forward)?.let {
            beginRun(way, crossedAt(it.fraction))
            Log.d("SprintManager", "RESTART")
            return
        }

        val rest = if (forward) restAtFinish else restAtStart
        val centre = if (forward) finishCentre else startCentre

        // Passed by: the car came close to the end but never crossed a gate - the track's end
        // points sit wherever the driver stood while building it, and a gate there can lie
        // to one side of the road the GPS actually traces. Once the car is clearly heading
        // away again, the run ended at the point of closest approach.
        if (centre != null) {
            val d = distance(current, centre)
            if (d <= PASS_ZONE_M && d < closestApproachM) {
                closestApproach = current
                closestApproachM = d
            }
            val closest = closestApproach
            if (closest != null && d > closestApproachM + PASS_LEAVE_M) {
                val ms = (mapped(closest.timestamp) - sprintStartTime).roundToLong()
                if (ms >= MIN_RUN_MS) {
                    finishRun(ms, closest.timestamp)
                    Log.d("SprintManager", "PASSED FINISH AREA")
                    return
                }
            }
        }

        // Standing finish: the car stopped at the end without driving across the gate
        // (parked short of it, or GPS drifted to one side). Timed to when it stopped.
        if (centre != null && distance(current, centre) <= LINE_ZONE_M) {
            rest.feed(current)
            val stoppedAt = rest.anchor?.timestamp
            if (rest.settled && stoppedAt != null) {
                val ms = (mapped(stoppedAt) - sprintStartTime).roundToLong()
                if (ms >= MIN_RUN_MS) {
                    finishRun(ms, stoppedAt)
                    Log.d("SprintManager", "STANDING FINISH")
                }
            }
        } else {
            rest.clear()
        }
    }

    private fun beginRun(goes: Way, startMs: Double) {
        way = goes
        sprintStartTime = startMs
        hasStarted = true
        hasFinished = false
        runHadGap = false
        restAtStart.clear()
        restAtFinish.clear()
        clearApproach()
    }

    private fun clearApproach() {
        closestApproach = null
        closestApproachM = Double.MAX_VALUE
    }

    private fun finishRun(sprintMs: Long, finishedAtTimestamp: Long) {
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
        lastFinishTimestamp = finishedAtTimestamp
        restAtStart.clear()
        restAtFinish.clear()
        clearApproach()
        Log.d("SprintManager", "RUN FINISHED ($way): $sprintMs ms")
    }

    private fun updateTimes(sprintMs: Long) {
        val seconds = sprintMs / 1000.0
        val best = bestSeconds.getValue(way)
        _delta.value = if (best.isFinite()) seconds - best else 0.0
        if (seconds < best) {
            bestSeconds[way] = seconds
        }
        // Shows the best for the direction just driven, which is the one the delta is against.
        _bestTime.value = formatTime((bestSeconds.getValue(way) * 1000).roundToLong())
        _lastTime.value = formatTime(sprintMs)
    }

    override fun reset() {
        runHadGap = false
        sprintStartTime = 0.0
        hasStarted = false
        hasFinished = false
        lastFinishTimestamp = NEVER
        restAtStart.clear()
        restAtFinish.clear()
        _stintStart.value = clock()
        _eventCount.value = 0
        _completedLaps.value = emptyList()
        _currentTime.value = formatTime(0)
    }

    override fun startNewEvent() {
        hasStarted = false
        hasFinished = false
        restAtStart.clear()
        restAtFinish.clear()
        _currentTime.value = formatTime(0)
    }

    private companion object {
        /** "No finish yet"; far enough back that subtracting it cannot overflow. */
        const val NEVER = Long.MIN_VALUE / 2

        /** How near a gate's centre the car must be for a standing start/finish to count. */
        const val LINE_ZONE_M = 30.0

        /** Drift allowed while "standing still" - above typical GPS jitter, below real driving. */
        const val REST_RADIUS_M = 3.0

        /** How long the car must stay put before it counts as a deliberate stop. */
        const val REST_MIN_MS = 1_000L

        /** A "run" shorter than this is the car shuffling about, not a run. */
        const val MIN_RUN_MS = 3_000L

        /** Within this of the end counts as having been at it, for the passed-by finish. */
        const val PASS_ZONE_M = 45.0

        /** How much farther than its closest approach the car must get to be "leaving". */
        const val PASS_LEAVE_M = 40.0

        /** After a finish, how long a crossing is ignored as the car settling past the line. */
        const val ROLLBACK_GUARD_MS = 5_000L

        fun centreOf(line: List<TrackCoordinatesData>): Pair<Double, Double>? =
            if (line.size < 2) null
            else line.map { it.latitude }.average() to line.map { it.longitude }.average()

        fun distance(a: RawGPSData, b: RawGPSData): Double =
            haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)

        fun distance(a: RawGPSData, centre: Pair<Double, Double>): Double =
            haversineDistance(a.latitude, a.longitude, centre.first, centre.second)
    }
}
