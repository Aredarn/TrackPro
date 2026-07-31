package com.example.trackpro.managerClasses.timeAttackManagers

import android.os.SystemClock
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.utilities.haversineDistance
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

    // Live delta vs. the session's best lap, updated continuously by distance travelled
    // in the current lap rather than only once per lap/sector boundary. currentLapTrace
    // records (distanceMeters, elapsedMs) as the lap is driven; if the lap turns out to be
    // a new best on completion, it's promoted to bestLapTrace for future comparisons.
    private var currentLapDistanceMeters = 0.0
    private val currentLapTrace = mutableListOf<Pair<Double, Long>>()
    private var bestLapTrace: List<Pair<Double, Long>> = emptyList()

    val lapCompletedChannel = Channel<Long>(Channel.UNLIMITED)
    val sectorCompletedChannel = Channel<SectorSplit>(Channel.UNLIMITED)

    private val _currentLapSplits = MutableStateFlow<List<SectorSplit>>(emptyList())
    val currentLapSplits: StateFlow<List<SectorSplit>> = _currentLapSplits.asStateFlow()

    private val _liveDelta = MutableStateFlow<Double?>(null)
    val liveDelta: StateFlow<Double?> = _liveDelta.asStateFlow()

    override fun handleGpsUpdate(
        prev: RawGPSData?,
        current: RawGPSData
    ) {
        val now = SystemClock.elapsedRealtime()
        prev?.let { prevData ->
            if (hasStarted) {
                currentLapDistanceMeters += haversineDistance(
                    prevData.latitude, prevData.longitude,
                    current.latitude, current.longitude
                )
            }

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
                    currentLapDistanceMeters = 0.0
                    currentLapTrace.clear()
                    _currentLapSplits.value = emptyList()
                    _liveDelta.value = null
                } else {
                    val lapMs = now - lapStartTime
                    val isNewBest = updateTimes(lapMs)
                    if (isNewBest) {
                        bestLapTrace = currentLapTrace.toList()
                    }
                    lastCrossTime = now
                    lapStartTime = now
                    lastSplitTime = now
                    currentSectorIndex = 0
                    currentLapDistanceMeters = 0.0
                    currentLapTrace.clear()
                    _currentLapSplits.value = emptyList()
                    _liveDelta.value = null
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

            // Record this point into the current lap's trace and compute the continuous
            // delta against the best lap's trace at the same distance-into-lap.
            if (!finishHandled && hasStarted) {
                val elapsedMs = now - lapStartTime
                currentLapTrace.add(currentLapDistanceMeters to elapsedMs)
                _liveDelta.value = interpolatedElapsedAtDistance(currentLapDistanceMeters)
                    ?.let { bestElapsedMs -> (elapsedMs - bestElapsedMs) / 1000.0 }
            }
        }
        _currentTime.value = formatTime(now - lapStartTime)
    }

    /** Linearly interpolates the best lap's elapsed time at the given distance into the lap. */
    private fun interpolatedElapsedAtDistance(distance: Double): Long? {
        if (bestLapTrace.isEmpty()) return null
        val first = bestLapTrace.first()
        val last = bestLapTrace.last()
        if (distance <= first.first) return first.second
        if (distance >= last.first) return last.second

        for (i in 1 until bestLapTrace.size) {
            val (d0, t0) = bestLapTrace[i - 1]
            val (d1, t1) = bestLapTrace[i]
            if (distance <= d1) {
                if (d1 <= d0) return t0
                val frac = (distance - d0) / (d1 - d0)
                return (t0 + frac * (t1 - t0)).toLong()
            }
        }
        return last.second
    }

    /** Returns true if this lap became the new session-best. */
    private fun updateTimes(lapMs: Long): Boolean {
        val seconds = lapMs / 1000.0
        _delta.value = if (bestLapSeconds.isFinite()) seconds - bestLapSeconds else 0.0
        val isNewBest = seconds < bestLapSeconds
        if (isNewBest) {
            bestLapSeconds = seconds
            _bestTime.value = formatTime(lapMs)
        }
        _lastTime.value = formatTime(lapMs)
        return isNewBest
    }


    override fun reset() {
        lapStartTime = SystemClock.elapsedRealtime()
        lastCrossTime = 0L
        lastSplitTime = lapStartTime
        hasStarted = false
        currentSectorIndex = 0
        bestSectorMs.clear()
        currentLapDistanceMeters = 0.0
        currentLapTrace.clear()
        bestLapTrace = emptyList()
        _currentLapSplits.value = emptyList()
        _liveDelta.value = null
        _stintStart.value = lapStartTime
        _eventCount.value = 0
    }

    override fun startNewEvent() {
        lapStartTime = SystemClock.elapsedRealtime()
        lastSplitTime = lapStartTime
        currentSectorIndex = 0
        currentLapDistanceMeters = 0.0
        currentLapTrace.clear()
        _currentLapSplits.value = emptyList()
        _liveDelta.value = null
        _currentTime.value = formatTime(0)
    }
}
