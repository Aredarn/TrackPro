package com.example.trackpro.managerClasses.timeAttackManagers

import android.os.SystemClock
import com.example.trackpro.dataClasses.TrackCoordinatesData
import kotlinx.coroutines.channels.Channel
import com.example.trackpro.dataClasses.RawGPSData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One completed sector split for the lap currently in progress. deltaMs is vs. this session's best for that sector, or null if this is the first time it's been recorded. */
data class SectorSplit(val sectorIndex: Int, val splitMs: Long, val deltaMs: Long?)

class CircuitTimingManager(
    private val finishLine: List<TrackCoordinatesData>,
    private val sectorLines: List<List<TrackCoordinatesData>> = emptyList()
) : TimingManager() {
    private var lapStartTime = SystemClock.elapsedRealtime()
    private var lastCrossTime = 0L
    private var lastSplitTime = lapStartTime
    private var bestLapSeconds = Double.POSITIVE_INFINITY
    private var hasStarted = false
    private var currentSectorIndex = 0
    private val bestSectorMs = mutableMapOf<Int, Long>()

    val lapCompletedChannel = Channel<Long>(Channel.UNLIMITED)
    val sectorCompletedChannel = Channel<SectorSplit>(Channel.UNLIMITED)

    private val _currentLapSplits = MutableStateFlow<List<SectorSplit>>(emptyList())
    val currentLapSplits: StateFlow<List<SectorSplit>> = _currentLapSplits.asStateFlow()

    override fun handleGpsUpdate(
        prev: RawGPSData?,
        current: RawGPSData
    ) {
        val now = SystemClock.elapsedRealtime()
        prev?.let { prevData ->
            val finishCrossing = TrackGeometry.checkLineCrossing(prevData, current, finishLine)
            var finishHandled = false

            if (finishCrossing != null && finishCrossing.isValid && now - lastCrossTime > 5000) {
                finishHandled = true
                if (!hasStarted) {
                    hasStarted = true
                    lastCrossTime = now
                    lapStartTime = now
                    lastSplitTime = now
                    currentSectorIndex = 0
                    _currentLapSplits.value = emptyList()
                } else {
                    val lapMs = now - lapStartTime
                    updateTimes(lapMs)
                    lastCrossTime = now
                    lapStartTime = now
                    lastSplitTime = now
                    currentSectorIndex = 0
                    _currentLapSplits.value = emptyList()
                    _eventCount.value += 1
                    lapCompletedChannel.trySend(lapMs)
                }
            }

            // Only look for the next expected sector gate while a lap is in progress, and
            // only if the finish line didn't just fire on this same update.
            if (!finishHandled && hasStarted && currentSectorIndex < sectorLines.size) {
                val gate = sectorLines[currentSectorIndex]
                val sectorCrossing = TrackGeometry.checkLineCrossing(prevData, current, gate)
                if (sectorCrossing != null && sectorCrossing.isValid && now - lastCrossTime > 5000) {
                    val splitMs = now - lastSplitTime
                    val best = bestSectorMs[currentSectorIndex]
                    val deltaMs = best?.let { splitMs - it }
                    if (best == null || splitMs < best) bestSectorMs[currentSectorIndex] = splitMs

                    val split = SectorSplit(currentSectorIndex, splitMs, deltaMs)
                    _currentLapSplits.value = _currentLapSplits.value + split
                    sectorCompletedChannel.trySend(split)

                    lastCrossTime = now
                    lastSplitTime = now
                    currentSectorIndex += 1
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
        lastSplitTime = lapStartTime
        hasStarted = false
        currentSectorIndex = 0
        bestSectorMs.clear()
        _currentLapSplits.value = emptyList()
        _stintStart.value = lapStartTime
        _eventCount.value = 0
    }

    override fun startNewEvent() {
        lapStartTime = SystemClock.elapsedRealtime()
        lastSplitTime = lapStartTime
        currentSectorIndex = 0
        _currentLapSplits.value = emptyList()
        _currentTime.value = formatTime(0)
    }
}
