package com.example.trackpro.ManagerClasses.TimeAttackManagers

import android.os.SystemClock
import com.example.trackpro.DataClasses.TrackCoordinatesData
import kotlinx.coroutines.channels.Channel

class CircuitTimingManager(
    private val finishLine: List<TrackCoordinatesData>
) : TimingManager() {
    private var lapStartTime = SystemClock.elapsedRealtime()
    private var lastCrossTime = 0L
    private var bestLapSeconds = Double.POSITIVE_INFINITY
    private var hasStarted = false
    val lapCompletedChannel = Channel<Long>(Channel.UNLIMITED)

    override fun handleGpsUpdate(prev: com.example.trackpro.ManagerClasses.RawGPSData?, current: com.example.trackpro.ManagerClasses.RawGPSData) {
        val now = SystemClock.elapsedRealtime()
        prev?.let { prevData ->
            TrackGeometry.checkLineCrossing(prevData, current, finishLine)?.let { crossing ->
                if (crossing.isValid && now - lastCrossTime > 5000) {
                    if (!hasStarted) {
                        hasStarted = true
                        lastCrossTime = now
                        lapStartTime = now
                        return
                    }

                    val lapMs = now - lapStartTime
                    updateTimes(lapMs)
                    lastCrossTime = now
                    lapStartTime = now
                    _eventCount.value += 1
                    lapCompletedChannel.trySend(lapMs)
                }
            }
        }
        _currentTime.value = formatTime(now - lapStartTime)
    }

    private fun updateTimes(lapMs: Long) {
        val seconds = lapMs / 1000.0
        _delta.value = if (bestLapSeconds.isFinite()) seconds - bestLapSeconds else 0.0
        if (seconds < bestLapSeconds) {
            bestLapSeconds = seconds
            _bestTime.value = formatTime(lapMs)
        }
        _lastTime.value = formatTime(lapMs)
    }

    override fun reset() {
        lapStartTime = SystemClock.elapsedRealtime()
        lastCrossTime = 0L
        hasStarted = false
        _stintStart.value = lapStartTime
        _eventCount.value = 0
    }

    override fun startNewEvent() {
        lapStartTime = SystemClock.elapsedRealtime()
        _currentTime.value = formatTime(0)
    }
}