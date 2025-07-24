package com.example.trackpro.ManagerClasses.TimeAttackManagers

import android.location.Location
import android.os.SystemClock
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.ManagerClasses.RawGPSData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

class SprintTimingManager(
    private val startLine: List<TrackCoordinatesData>,
    private val finishLine: List<TrackCoordinatesData>,
    private val gateRadiusMeters: Double = 10.0
) : TimingManager() {

    private var sprintStartTime = 0L
    private var bestSprintSeconds = Double.POSITIVE_INFINITY
    private var hasStarted = false
    private var hasFinished = false

    val sprintCompletedChannel = Channel<Long>(Channel.UNLIMITED)

    override fun handleGpsUpdate(prev: RawGPSData?, current: RawGPSData) {
        val now = SystemClock.elapsedRealtime()

        val currentLat = current.latitude
        val currentLon = current.longitude

        val startPoint = startLine.firstOrNull()
        val finishPoint = finishLine.firstOrNull()

        // Start logic
        if (!hasStarted && startPoint != null && isWithinRadius(currentLat, currentLon, startPoint.latitude, startPoint.longitude, gateRadiusMeters)) {
            sprintStartTime = now
            hasStarted = true
            hasFinished = false
            _currentTime.value = formatTime(0)
        }

        // Finish logic
        if (hasStarted && !hasFinished && finishPoint != null &&
            isWithinRadius(currentLat, currentLon, finishPoint.latitude, finishPoint.longitude, gateRadiusMeters)
        ) {
            val sprintMs = now - sprintStartTime
            updateTimes(sprintMs)
            _eventCount.value += 1
            sprintCompletedChannel.trySend(sprintMs)
            hasStarted = false
            hasFinished = true
        }

        // Update live current time if running
        if (hasStarted && !hasFinished) {
            _currentTime.value = formatTime(now - sprintStartTime)
        }
    }

    private fun isWithinRadius(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        radiusMeters: Double
    ): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] <= radiusMeters
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
