package com.example.trackpro.ManagerClasses.TimeAttackManagers

import android.os.SystemClock
import com.example.trackpro.ManagerClasses.RawGPSData
import com.example.trackpro.DataClasses.TrackCoordinatesData
import kotlinx.coroutines.channels.Channel

class SprintTimingManager(
    private val startLine: List<TrackCoordinatesData>,
    private val finishLine: List<TrackCoordinatesData>
) : TimingManager() {
    private var sprintStartTime = 0L
    private var bestSprintSeconds = Double.POSITIVE_INFINITY
    private var hasCrossedStart = false
    val sprintCompletedChannel = Channel<Long>(Channel.UNLIMITED)

    override fun handleGpsUpdate(prev: RawGPSData?, current: RawGPSData) {
        val now = SystemClock.elapsedRealtime()
        prev?.let { prevData ->
            // Only check start line if we haven't started
            if (!hasCrossedStart) {
                TrackGeometry.checkLineCrossing(prevData, current, startLine)?.let { crossing ->
                    if (crossing.direction == TrackGeometry.CrossingDirection.ENTERING) {
                        sprintStartTime = now
                        hasCrossedStart = true
                        _currentTime.value = formatTime(0)
                    }
                }
            }

            // Check finish line only if we've started
            if (hasCrossedStart) {
                TrackGeometry.checkLineCrossing(prevData, current, finishLine)?.let { crossing ->
                    if (crossing.direction == TrackGeometry.CrossingDirection.ENTERING) {
                        val sprintMs = now - sprintStartTime
                        updateTimes(sprintMs)
                        _eventCount.value += 1
                        sprintCompletedChannel.trySend(sprintMs)
                        hasCrossedStart = false
                    }
                }
            }
        }

        // Update current time if sprint is in progress
        if (hasCrossedStart) {
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
        hasCrossedStart = false
        _stintStart.value = SystemClock.elapsedRealtime()
        _eventCount.value = 0
    }

    override fun startNewEvent() {
        hasCrossedStart = false
        _currentTime.value = formatTime(0)
    }
}