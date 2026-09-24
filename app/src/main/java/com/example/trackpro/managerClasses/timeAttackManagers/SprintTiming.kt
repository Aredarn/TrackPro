package com.example.trackpro.managerClasses.timeAttackManagers

import android.os.SystemClock
import android.util.Log
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.RawGPSData
import kotlinx.coroutines.channels.Channel
import kotlin.math.roundToLong

/**
 * Point-to-point timing between a start line and a finish line.
 *
 * Crossing times are interpolated between fixes and mapped onto [clock] exactly as in
 * CircuitTimingManager - see there for why. The clocks are injectable so tests can drive time.
 */
class SprintTimingManager(
    private val startLine: List<TrackCoordinatesData>,
    private val finishLine: List<TrackCoordinatesData>,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() }
) : TimingManager() {

    private var sprintStartTime = 0.0
    private var bestSprintSeconds = Double.POSITIVE_INFINITY
    private var hasStarted = false
    private var hasFinished = false

    val sprintCompletedChannel = Channel<CompletedLap>(Channel.UNLIMITED)

    /** Whether the run in progress has seen a GPS gap; see [CompletedLap.signalGap]. */
    private var runHadGap = false

    override fun handleGpsUpdate(prev: RawGPSData?, current: RawGPSData) {
        val now = clock()
        val nowWall = wallClock()
        if (prev == null) return

        fun crossedAt(fraction: Double): Double =
            now - (nowWall - TrackGeometry.crossingTimeMs(prev, current, fraction))

        // Before the finish check, so a gap that swallowed the line marks this run.
        if (hasStarted && !hasFinished && isSignalGap(prev, current)) runHadGap = true

        // 1. START LOGIC: Only look for start if we haven't moved yet
        if (!hasStarted) {
            val startResult = TrackGeometry.checkLineCrossing(prev, current, startLine)
            if (startResult != null && startResult.isForward) {
                sprintStartTime = crossedAt(startResult.fraction)
                hasStarted = true
                hasFinished = false
                runHadGap = false
                Log.d("SprintManager", "START LINE CROSSED")
            }
        }

        // 2. FINISH LOGIC: Only look for finish if we are currently running
        else if (hasStarted && !hasFinished) {
            val finishResult = TrackGeometry.checkLineCrossing(prev, current, finishLine)
            if (finishResult != null && finishResult.isForward) {
                val sprintMs = (crossedAt(finishResult.fraction) - sprintStartTime).roundToLong()
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
                Log.d("SprintManager", "FINISH LINE CROSSED: $sprintMs ms")
            }
        }

        // 3. Live UI Update
        if (hasStarted && !hasFinished) {
            _currentTime.value = formatTime((now - sprintStartTime).roundToLong().coerceAtLeast(0))
        }
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
        _stintStart.value = clock()
        _eventCount.value = 0
        _completedLaps.value = emptyList()
        _currentTime.value = formatTime(0)
    }

    override fun startNewEvent() {
        hasStarted = false
        hasFinished = false
        _currentTime.value = formatTime(0)
    }
}
