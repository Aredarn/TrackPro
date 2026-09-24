package com.example.trackpro.managerClasses.timeAttackManagers

import android.os.SystemClock
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.utilities.haversineDistance
import kotlinx.coroutines.channels.Channel
import com.example.trackpro.dataClasses.RawGPSData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToLong

/** One completed sector split for the lap currently in progress. deltaMs is vs. this session's best for that sector, or null if this is the first time it's been recorded. */
data class SectorSplit(val sectorIndex: Int, val splitMs: Long, val deltaMs: Long?)

/** What the live delta is measured against. */
enum class DeltaReference {
    /** The fastest lap driven so far in this session. Nothing to compare until lap 2. */
    SESSION_BEST,
    /** The fastest clean lap ever recorded on this track in this direction, from lap 1. */
    TRACK_BEST
}

/**
 * A lap to measure against: its time, and its progress as (distance into the lap in metres,
 * elapsed ms), which is what the live delta interpolates along.
 */
data class ReferenceLap(val lapMs: Long, val trace: List<Pair<Double, Long>>)

/**
 * Circuit lap timing: laps, sectors, and a live delta.
 *
 * [clock] is the monotonic clock everything is timed on; [wallClock] is the clock GPS fixes
 * are stamped with on receipt. Both are injectable so tests can drive time.
 */
class CircuitTimingManager(
    private val finishLine: List<TrackCoordinatesData>,
    private val sectorLines: List<List<TrackCoordinatesData>> = emptyList(),
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClock: () -> Long = { System.currentTimeMillis() }
) : TimingManager() {

    // Lap boundaries are instants on [clock], kept fractional: they are interpolated between
    // fixes (see handleGpsUpdate) rather than snapped to whichever fix happened to follow them.
    private var lapStartTime: Double = clock().toDouble()
    private var lastCrossTime = Double.NEGATIVE_INFINITY
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

    // Live delta, updated continuously by distance travelled in the current lap rather than
    // only once per lap/sector boundary. currentLapTrace records (distanceMeters, elapsedMs)
    // as the lap is driven; a clean lap that turns out to be a new session best is promoted
    // to bestLapTrace for future comparisons.
    private var currentLapDistanceMeters = 0.0
    private val currentLapTrace = mutableListOf<Pair<Double, Long>>()
    private var bestLapTrace: List<Pair<Double, Long>> = emptyList()
    /**
     * The time of the lap [bestLapTrace] came from. Not always the session best: a lap with a
     * GPS gap can set the best time without its trace being trusted as a reference.
     */
    private var bestLapTraceMs = Long.MAX_VALUE

    /**
     * What the driver has chosen to measure against. Read on every fix, so a change takes
     * effect straight away, mid-lap included.
     */
    @Volatile var preferredReference: DeltaReference = DeltaReference.SESSION_BEST

    /** Earlier sessions' best laps on this track, one per direction; see [setTrackBests]. */
    @Volatile private var trackBests: Map<TrackGeometry.CrossingDirection, ReferenceLap> = emptyMap()

    val lapCompletedChannel = Channel<CompletedLap>(Channel.UNLIMITED)

    /** Whether the lap in progress has seen a GPS gap; see [CompletedLap.signalGap]. */
    private var currentLapHadGap = false
    val sectorCompletedChannel = Channel<SectorSplit>(Channel.UNLIMITED)

    private val _currentLapSplits = MutableStateFlow<List<SectorSplit>>(emptyList())
    val currentLapSplits: StateFlow<List<SectorSplit>> = _currentLapSplits.asStateFlow()

    private val _liveDelta = MutableStateFlow<Double?>(null)
    val liveDelta: StateFlow<Double?> = _liveDelta.asStateFlow()

    /**
     * What [liveDelta] is actually being measured against, which is not always what was
     * preferred: a track best is only available once one has been recorded in the direction
     * this session is running. Null while there is nothing to measure against.
     */
    private val _activeReference = MutableStateFlow<DeltaReference?>(null)
    val activeReference: StateFlow<DeltaReference?> = _activeReference.asStateFlow()

    /**
     * Supplies the best laps from earlier sessions on this track, keyed by the direction
     * they were driven in. Loaded from the database after the timer is created, so this may
     * land mid-session; it is picked up on the next fix.
     */
    fun setTrackBests(bests: Map<TrackGeometry.CrossingDirection, ReferenceLap>) {
        trackBests = bests
    }

    override fun handleGpsUpdate(
        prev: RawGPSData?,
        current: RawGPSData
    ) {
        val now = clock()
        val nowWall = wallClock()

        // Fixes are stamped with the wall clock when they arrive; timing runs on the monotonic
        // clock. Converting through the offset between the two *now* measures an instant by
        // how long ago it was, which ignores however long this fix waited to be processed -
        // on the main thread, behind a map redraw, that wait varies from fix to fix - and is
        // immune to the wall clock being stepped by a network time update.
        fun onClock(wallMs: Double): Double = now - (nowWall - wallMs)

        prev?.let { prevData ->
            // Checked before the finish line below, so a gap that swallowed the line marks
            // the lap it merged rather than the one that follows.
            if (hasStarted && isSignalGap(prevData, current)) currentLapHadGap = true

            val segmentMeters = haversineDistance(
                prevData.latitude, prevData.longitude,
                current.latitude, current.longitude
            )
            if (hasStarted) currentLapDistanceMeters += segmentMeters

            val finishCrossing = TrackGeometry.checkLineCrossing(prevData, current, finishLine)
            val finishCrossedAt = finishCrossing?.let {
                onClock(TrackGeometry.crossingTimeMs(prevData, current, it.fraction))
            }
            var finishHandled = false

            // The very first crossing is accepted in either direction and locks the
            // session's direction; every crossing after that must match it.
            val movingFastEnoughToLock = (current.speed ?: 0f) >= minDirectionLockSpeedKmh
            val finishMatchesDirection = finishCrossing != null && when (lapDirection) {
                null -> movingFastEnoughToLock
                else -> finishCrossing.direction == lapDirection
            }

            if (finishCrossing != null && finishCrossedAt != null && finishMatchesDirection &&
                finishCrossedAt - lastCrossTime > MIN_CROSSING_INTERVAL_MS
            ) {
                finishHandled = true
                // The part of this segment driven after the line belongs to the new lap.
                val metersPastLine = (1.0 - finishCrossing.fraction) * segmentMeters

                if (!hasStarted) {
                    hasStarted = true
                    lapDirection = finishCrossing.direction
                } else {
                    val lapMs = (finishCrossedAt - lapStartTime).roundToLong()
                    // Close the trace exactly on the line, so a lap promoted to the reference
                    // ends at its true distance and time rather than one fix past them.
                    currentLapTrace.add((currentLapDistanceMeters - metersPastLine) to lapMs)

                    val isNewBest = updateTimes(lapMs)
                    // A lap with a GPS gap can still hold the best time - both crossings may
                    // have been seen - but its trace cannot be the live-delta reference: the
                    // gap is a straight chord where distance was under-counted, which would
                    // skew every delta measured against it for the rest of the session. The
                    // previous reference stays until a clean lap replaces it.
                    if (isNewBest && !currentLapHadGap) {
                        bestLapTrace = currentLapTrace.toList()
                        bestLapTraceMs = lapMs
                    }
                    val finishedLap = CompletedLap(
                        number = _eventCount.value + 1,
                        timeMs = lapMs,
                        splits = closeFinalSector(_currentLapSplits.value, finishCrossedAt),
                        signalGap = currentLapHadGap
                    )
                    _eventCount.value += 1
                    _completedLaps.value = _completedLaps.value + finishedLap
                    lapCompletedChannel.trySend(finishedLap)
                }

                currentLapHadGap = false
                lastCrossTime = finishCrossedAt
                lapStartTime = finishCrossedAt
                lastSplitTime = finishCrossedAt
                currentSectorIndex = 0
                currentLapTrace.clear()
                _currentLapSplits.value = emptyList()
                // The new lap starts at the line, part-way through this segment, so it
                // already has the distance and time driven since then.
                currentLapDistanceMeters = metersPastLine
                recordTracePoint(onClock(current.timestamp.toDouble()))
            }

            // Only look for the next expected sector gate while a lap is in progress, and
            // only if the finish line didn't just fire on this same update.
            if (!finishHandled && hasStarted && currentSectorIndex < sectorLines.size) {
                val gate = gateForDrivingOrder(currentSectorIndex)
                val sectorCrossing = TrackGeometry.checkLineCrossing(prevData, current, gate)
                val sectorMatchesDirection = sectorCrossing != null &&
                        sectorCrossing.direction == lapDirection
                val sectorCrossedAt = sectorCrossing?.let {
                    onClock(TrackGeometry.crossingTimeMs(prevData, current, it.fraction))
                }
                if (sectorCrossing != null && sectorCrossedAt != null && sectorMatchesDirection &&
                    sectorCrossedAt - lastCrossTime > MIN_CROSSING_INTERVAL_MS
                ) {
                    val splitMs = (sectorCrossedAt - lastSplitTime).roundToLong()
                    val best = bestSectorMs[currentSectorIndex]
                    val deltaMs = best?.let { splitMs - it }
                    if (best == null || splitMs < best) bestSectorMs[currentSectorIndex] = splitMs

                    val split = SectorSplit(currentSectorIndex, splitMs, deltaMs)
                    _currentLapSplits.value = _currentLapSplits.value + split
                    sectorCompletedChannel.trySend(split)

                    lastCrossTime = sectorCrossedAt
                    lastSplitTime = sectorCrossedAt
                    currentSectorIndex += 1
                }
            }

            // Record this point into the current lap's trace and compute the continuous
            // delta against the reference lap at the same distance-into-lap.
            if (!finishHandled && hasStarted) {
                recordTracePoint(onClock(current.timestamp.toDouble()))
            }
        }
        _currentTime.value = formatTime((now - lapStartTime).roundToLong().coerceAtLeast(0))
    }

    /**
     * Adds the current distance at [fixTime] to the lap's trace and updates the live delta.
     * Timed by the fix, not by when it was processed, for the same reason crossings are.
     */
    private fun recordTracePoint(fixTime: Double) {
        val elapsedMs = (fixTime - lapStartTime).roundToLong().coerceAtLeast(0)
        currentLapTrace.add(currentLapDistanceMeters to elapsedMs)

        val reference = referenceLap()
        _activeReference.value = reference?.first
        _liveDelta.value = reference
            ?.let { (_, trace) -> interpolatedElapsedAtDistance(trace, currentLapDistanceMeters) }
            ?.let { referenceElapsedMs -> (elapsedMs - referenceElapsedMs) / 1000.0 }
    }

    /**
     * The lap to measure the live delta against, and which kind it is.
     *
     * With [DeltaReference.TRACK_BEST] preferred, the stored best for this direction is used
     * until a clean lap this session beats it - at which point that lap *is* the new track
     * best and becomes the reference. With no stored best for this direction yet, the session
     * best stands in, and [activeReference] says so.
     */
    private fun referenceLap(): Pair<DeltaReference, List<Pair<Double, Long>>>? {
        if (preferredReference == DeltaReference.TRACK_BEST) {
            val stored = lapDirection?.let { trackBests[it] }
            if (stored != null) {
                return if (stored.lapMs <= bestLapTraceMs) {
                    DeltaReference.TRACK_BEST to stored.trace
                } else {
                    DeltaReference.TRACK_BEST to bestLapTrace
                }
            }
        }
        return if (bestLapTrace.isEmpty()) null else DeltaReference.SESSION_BEST to bestLapTrace
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
    private fun closeFinalSector(recorded: List<SectorSplit>, crossedAt: Double): List<SectorSplit> {
        if (sectorLines.isEmpty() || recorded.size != sectorLines.size) return recorded

        val finalIndex = sectorLines.size
        val finalMs = (crossedAt - lastSplitTime).roundToLong()
        val best = bestSectorMs[finalIndex]
        val deltaMs = best?.let { finalMs - it }
        if (best == null || finalMs < best) bestSectorMs[finalIndex] = finalMs

        return recorded + SectorSplit(finalIndex, finalMs, deltaMs)
    }

    /** Linearly interpolates a reference lap's elapsed time at the given distance into the lap. */
    private fun interpolatedElapsedAtDistance(trace: List<Pair<Double, Long>>, distance: Double): Long? {
        if (trace.isEmpty()) return null
        val first = trace.first()
        val last = trace.last()
        if (distance <= first.first) return first.second
        if (distance >= last.first) return last.second

        for (i in 1 until trace.size) {
            val (d0, t0) = trace[i - 1]
            val (d1, t1) = trace[i]
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
        lapStartTime = clock().toDouble()
        lastCrossTime = Double.NEGATIVE_INFINITY
        lastSplitTime = lapStartTime
        hasStarted = false
        lapDirection = null
        currentLapHadGap = false
        currentSectorIndex = 0
        bestSectorMs.clear()
        currentLapDistanceMeters = 0.0
        currentLapTrace.clear()
        bestLapTrace = emptyList()
        bestLapTraceMs = Long.MAX_VALUE
        _currentLapSplits.value = emptyList()
        _liveDelta.value = null
        _activeReference.value = null
        _completedLaps.value = emptyList()
        _stintStart.value = lapStartTime.toLong()
        _eventCount.value = 0
    }

    override fun startNewEvent() {
        currentLapHadGap = false
        lapStartTime = clock().toDouble()
        lastSplitTime = lapStartTime
        currentSectorIndex = 0
        currentLapDistanceMeters = 0.0
        currentLapTrace.clear()
        _currentLapSplits.value = emptyList()
        _liveDelta.value = null
        _currentTime.value = formatTime(0)
    }

    private companion object {
        /**
         * Two line crossings closer together than this are one crossing seen twice - GPS
         * jitter either side of a gate - never two real ones.
         */
        const val MIN_CROSSING_INTERVAL_MS = 5_000.0
    }
}
