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

    // Which way this session is running, locked in by the first finish-line crossing.
    //
    // A track can be driven in either direction, and "reverse" days are common. Gate lines
    // are built relative to the direction the track was *recorded* in, so a reversed session
    // crosses every gate the "wrong" way - previously that meant no lap was ever counted.
    // Locking onto the first crossing supports both directions while still rejecting an
    // overshoot-and-rollback, which is always the opposite of the established direction.
    private var lapDirection: TrackGeometry.CrossingDirection? = null

    // A crossing only gets to *establish* the session's direction if the car is actually
    // driving. Otherwise a slow creep backwards across the line on the grid or in the pit
    // lane would lock the wrong direction and every real lap after it would be rejected.
    // Only the first, direction-locking crossing is gated on speed; once locked, a slow
    // final crossing (cool-down lap) still counts.
    private val minDirectionLockSpeedKmh = 15f

    // Live delta vs. the session's best lap, updated continuously by distance travelled
    // in the current lap rather than only once per lap/sector boundary. currentLapTrace
    // records (distanceMeters, elapsedMs) as the lap is driven; if the lap turns out to be
    // a new best on completion, it's promoted to bestLapTrace for future comparisons.
    private var currentLapDistanceMeters = 0.0
    private val currentLapTrace = mutableListOf<Pair<Double, Long>>()
    private var bestLapTrace: List<Pair<Double, Long>> = emptyList()

    val lapCompletedChannel = Channel<CompletedLap>(Channel.UNLIMITED)

    /** Whether the lap in progress has seen a GPS gap; see [CompletedLap.signalGap]. */
    private var currentLapHadGap = false
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
            // Checked before the finish line below, so a gap that swallowed the line marks
            // the lap it merged rather than the one that follows.
            if (hasStarted && isSignalGap(prevData, current)) currentLapHadGap = true

            if (hasStarted) {
                currentLapDistanceMeters += haversineDistance(
                    prevData.latitude, prevData.longitude,
                    current.latitude, current.longitude
                )
            }

            val finishCrossing = TrackGeometry.checkLineCrossing(prevData, current, finishLine)
            var finishHandled = false

            // The very first crossing is accepted in either direction and locks the
            // session's direction; every crossing after that must match it.
            val movingFastEnoughToLock = (current.speed ?: 0f) >= minDirectionLockSpeedKmh
            val finishMatchesDirection = finishCrossing != null && when (lapDirection) {
                null -> movingFastEnoughToLock
                else -> finishCrossing.direction == lapDirection
            }

            if (finishCrossing != null && finishMatchesDirection && now - lastCrossTime > 5000) {
                finishHandled = true
                if (!hasStarted) {
                    hasStarted = true
                    lapDirection = finishCrossing.direction
                    currentLapHadGap = false
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
                    // A lap with a GPS gap can still hold the best time - both crossings may
                    // have been seen - but its trace cannot be the live-delta reference: the
                    // gap is a straight chord where distance was under-counted, which would
                    // skew every delta measured against it for the rest of the session. The
                    // previous reference stays until a clean lap replaces it.
                    if (isNewBest && !currentLapHadGap) {
                        bestLapTrace = currentLapTrace.toList()
                    }
                    val finishedLap = CompletedLap(
                        number = _eventCount.value + 1,
                        timeMs = lapMs,
                        splits = closeFinalSector(_currentLapSplits.value, now),
                        signalGap = currentLapHadGap
                    )
                    currentLapHadGap = false
                    lastCrossTime = now
                    lapStartTime = now
                    lastSplitTime = now
                    currentSectorIndex = 0
                    currentLapDistanceMeters = 0.0
                    currentLapTrace.clear()
                    _currentLapSplits.value = emptyList()
                    _liveDelta.value = null
                    _eventCount.value += 1
                    _completedLaps.value = _completedLaps.value + finishedLap
                    lapCompletedChannel.trySend(finishedLap)
                }
            }

            // Only look for the next expected sector gate while a lap is in progress, and
            // only if the finish line didn't just fire on this same update.
            if (!finishHandled && hasStarted && currentSectorIndex < sectorLines.size) {
                val gate = gateForDrivingOrder(currentSectorIndex)
                val sectorCrossing = TrackGeometry.checkLineCrossing(prevData, current, gate)
                val sectorMatchesDirection = sectorCrossing != null &&
                        sectorCrossing.direction == lapDirection
                if (sectorCrossing != null && sectorMatchesDirection && now - lastCrossTime > 5000) {
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

    /**
     * The sector gate the car should hit next, given how many it has already passed this
     * lap. Sectors are stored in recorded-track order; when the session is running the
     * track in reverse the car meets them last-to-first, so the list is walked backwards.
     * Sector numbers reported to the UI stay in *driving* order either way - "S1" is always
     * the first sector you drive through.
     */
    private fun gateForDrivingOrder(drivingIndex: Int): List<TrackCoordinatesData> =
        if (lapDirection == TrackGeometry.CrossingDirection.EXITING) {
            sectorLines[sectorLines.size - 1 - drivingIndex]
        } else {
            sectorLines[drivingIndex]
        }

    /**
     * The finish line is the last sector's gate, so a lap's splits are only complete once it
     * has been added here. Only done when every marked gate was hit this lap - otherwise the
     * remainder would silently absorb a missed sector and look like a real split.
     *
     * Tracked against the session's best like any other sector so it colours the same way,
     * but never sent down [sectorCompletedChannel]: that would race the lap-completion
     * handler for the current lap id and could land it on the *next* lap in the database.
     * It lives on the [CompletedLap] record for the HUD only.
     */
    private fun closeFinalSector(recorded: List<SectorSplit>, now: Long): List<SectorSplit> {
        if (sectorLines.isEmpty() || recorded.size != sectorLines.size) return recorded

        val finalIndex = sectorLines.size
        val finalMs = now - lastSplitTime
        val best = bestSectorMs[finalIndex]
        val deltaMs = best?.let { finalMs - it }
        if (best == null || finalMs < best) bestSectorMs[finalIndex] = finalMs

        return recorded + SectorSplit(finalIndex, finalMs, deltaMs)
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
        lapDirection = null
        currentLapHadGap = false
        currentSectorIndex = 0
        bestSectorMs.clear()
        currentLapDistanceMeters = 0.0
        currentLapTrace.clear()
        bestLapTrace = emptyList()
        _currentLapSplits.value = emptyList()
        _liveDelta.value = null
        _completedLaps.value = emptyList()
        _stintStart.value = lapStartTime
        _eventCount.value = 0
    }

    override fun startNewEvent() {
        currentLapHadGap = false
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
