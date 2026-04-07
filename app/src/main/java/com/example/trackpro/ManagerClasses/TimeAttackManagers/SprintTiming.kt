package com.example.trackpro.ManagerClasses.TimeAttackManagers

import android.location.Location
import android.os.SystemClock
import android.util.Log
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.DataClasses.RawGPSData
import kotlinx.coroutines.channels.Channel

class SprintTimingManager(
    private val startLine: List<TrackCoordinatesData>,
    private val finishLine: List<TrackCoordinatesData>,
) : TimingManager() {

    private var sprintStartTime = 0L
    private var bestSprintSeconds = Double.POSITIVE_INFINITY
    private var hasStarted = false
    private var hasFinished = false

    val sprintCompletedChannel = Channel<Long>(Channel.UNLIMITED)

    override fun handleGpsUpdate(prev: com.example.trackpro.ManagerClasses.RawGPSData?, current: RawGPSData) {
        val now = SystemClock.elapsedRealtime()
        if (prev == null) return

        // 1. START LOGIC: Only look for start if we haven't moved yet
        if (!hasStarted) {
            val startResult = TrackGeometry.checkLineCrossing(prev, current, startLine)
            if (startResult != null && startResult.isValid) {
                sprintStartTime = now
                hasStarted = true
                hasFinished = false
                Log.d("SprintManager", "START LINE CROSSED")
            }
        }

        // 2. FINISH LOGIC: Only look for finish if we are currently running
        else if (hasStarted && !hasFinished) {
            val finishResult = TrackGeometry.checkLineCrossing(prev, current, finishLine)
            if (finishResult != null && finishResult.isValid) {
                val sprintMs = now - sprintStartTime
                updateTimes(sprintMs)
                _eventCount.value += 1
                sprintCompletedChannel.trySend(sprintMs)

                hasStarted = false // Reset for next run
                hasFinished = true
                Log.d("SprintManager", "FINISH LINE CROSSED: $sprintMs ms")
            }
        }

        // 3. Live UI Update
        if (hasStarted && !hasFinished) {
            _currentTime.value = formatTime(now - sprintStartTime)
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
        sprintStartTime = 0L
        hasStarted = false
        hasFinished = false
        _stintStart.value = SystemClock.elapsedRealtime()
        _eventCount.value = 0
        _currentTime.value = formatTime(0)
    }

    override fun startNewEvent() {
        hasStarted = false
        hasFinished = false
        _currentTime.value = formatTime(0)
    }
}
