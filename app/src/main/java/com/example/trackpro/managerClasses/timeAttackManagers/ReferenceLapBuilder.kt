package com.example.trackpro.managerClasses.timeAttackManagers

import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.utilities.haversineDistance
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Rebuilds a stored lap into a live-delta reference: its progress as (distance into the lap,
 * elapsed ms), and the direction it was driven in.
 *
 * A stored lap is not directly usable. Its points are every fix recorded while that lap row
 * was current, which is not the same as the lap: the first lap of a session also holds the
 * out-lap driven before the first crossing, and every lap can hold a fix or two from after
 * its finish, because the next lap's row is created a moment after the line. So the lap is
 * cut out between its own finish-line crossings here.
 *
 * Points are timed by their stored timestamps where every point has one. Older laps have
 * none; they are timed by assuming their fixes were evenly spaced across the lap's recorded
 * time, which holds for a steady GPS rate - close, but not exact, which is why the timestamp
 * column was added.
 */
object ReferenceLapBuilder {

    /** A reference lap and the direction it was driven in, relative to the recorded track. */
    data class Built(val direction: TrackGeometry.CrossingDirection, val lap: ReferenceLap)

    /**
     * No circuit is shorter than this. Used to tell a lap's start crossing from GPS jitter
     * wobbling back and forth over the line at its finish, which is metres, not a lap.
     */
    private const val MIN_LAP_METERS = 200.0

    /**
     * A timed lap whose first stored fix is this long after where its start should be is
     * missing the beginning of the lap, not merely a fix or two of it.
     */
    private const val MAX_MISSING_HEAD_MS = 2_000L

    /**
     * Builds the reference, or returns null if these points cannot be trusted as one: too
     * few of them, no finish-line crossing to anchor the end on, or a lap too short to be real.
     */
    fun build(points: List<LapInfoData>, lapMs: Long, finishLine: List<TrackCoordinatesData>): Built? {
        if (points.size < 2 || lapMs <= 0 || finishLine.size < 2) return null
        val fixes = points.map { it.asFix() }

        // Distance along the recorded points, so a fractional position can be turned into
        // metres. Position p means p whole points in, plus a fraction of the next segment.
        val cumulative = DoubleArray(fixes.size)
        for (i in 1 until fixes.size) {
            cumulative[i] = cumulative[i - 1] + haversineDistance(
                fixes[i - 1].latitude, fixes[i - 1].longitude,
                fixes[i].latitude, fixes[i].longitude
            )
        }
        fun distanceAt(position: Double): Double {
            val i = floor(position).toInt().coerceIn(0, fixes.lastIndex)
            if (i >= fixes.lastIndex) return cumulative.last()
            return cumulative[i] + (position - i) * (cumulative[i + 1] - cumulative[i])
        }

        val crossings = (0 until fixes.lastIndex).mapNotNull { i ->
            TrackGeometry.checkLineCrossing(fixes[i], fixes[i + 1], finishLine)
                ?.let { Crossing(i + it.fraction, it.direction) }
        }
        // The lap ends at its last crossing; anything recorded after it belongs to the next.
        val end = crossings.lastOrNull() ?: return null

        val timestamps = points.map { it.timestamp }
        val timed = timestamps.all { it != null } &&
                timestamps.zipWithNext().all { (a, b) -> b!! > a!! }

        return if (timed) {
            buildTimed(timestamps.map { it!! }, lapMs, end, ::distanceAt, fixes)
        } else {
            buildUntimed(crossings, end, lapMs, ::distanceAt)
        }
    }

    private data class Crossing(val position: Double, val direction: TrackGeometry.CrossingDirection)

    /**
     * Timestamped points: the lap started exactly [lapMs] before its finish crossing, so that
     * is where it is cut - which trims an out-lap however long it was.
     */
    private fun buildTimed(
        timestamps: List<Long>,
        lapMs: Long,
        end: Crossing,
        distanceAt: (Double) -> Double,
        fixes: List<RawGPSData>
    ): Built? {
        fun timeAt(position: Double): Double {
            val i = floor(position).toInt().coerceIn(0, timestamps.lastIndex)
            if (i >= timestamps.lastIndex) return timestamps.last().toDouble()
            return timestamps[i] + (position - i) * (timestamps[i + 1] - timestamps[i])
        }
        fun positionAt(time: Double): Double {
            for (i in 0 until timestamps.lastIndex) {
                if (time <= timestamps[i + 1]) {
                    val span = (timestamps[i + 1] - timestamps[i]).toDouble()
                    return i + ((time - timestamps[i]) / span).coerceIn(0.0, 1.0)
                }
            }
            return timestamps.lastIndex.toDouble()
        }

        val startTime = timeAt(end.position) - lapMs
        val first = timestamps.first()

        // A lap other than a session's first has no fix of its own before the line - its first
        // point arrives a moment after it. That moment is credited with the distance a car at
        // that speed would have covered since the line.
        val (startPosition, headMeters) = if (startTime < first) {
            val missingMs = first - startTime
            if (missingMs > MAX_MISSING_HEAD_MS) return null
            val metresPerMs = (fixes.first().speed ?: 0f) / 3.6 / 1000.0
            0.0 to metresPerMs * missingMs
        } else {
            positionAt(startTime) to 0.0
        }

        return assemble(
            startPosition, end.position, lapMs, headMeters, distanceAt,
            elapsedAt = { position -> (timeAt(position) - startTime).roundToLong() },
            direction = end.direction
        )
    }

    /**
     * No timestamps: the start is the lap's previous crossing in the same direction, far
     * enough back to be a lap rather than jitter over the finish line - or, for any lap but a
     * session's first, the first point, since those laps begin just after the line. Points
     * are then assumed evenly spaced in time across the lap.
     */
    private fun buildUntimed(
        crossings: List<Crossing>,
        end: Crossing,
        lapMs: Long,
        distanceAt: (Double) -> Double
    ): Built? {
        val endDistance = distanceAt(end.position)
        val start = crossings.lastOrNull {
            it.position < end.position &&
                    it.direction == end.direction &&
                    endDistance - distanceAt(it.position) >= MIN_LAP_METERS
        }
        val startPosition = start?.position ?: 0.0
        val span = end.position - startPosition
        if (span <= 0.0) return null

        return assemble(
            startPosition, end.position, lapMs, 0.0, distanceAt,
            elapsedAt = { position -> ((position - startPosition) / span * lapMs).roundToLong() },
            direction = end.direction
        )
    }

    /** Lays out the trace from [startPosition] to the finish at [endPosition]. */
    private fun assemble(
        startPosition: Double,
        endPosition: Double,
        lapMs: Long,
        headMeters: Double,
        distanceAt: (Double) -> Double,
        elapsedAt: (Double) -> Long,
        direction: TrackGeometry.CrossingDirection
    ): Built? {
        val startDistance = distanceAt(startPosition)
        fun lapDistanceAt(position: Double) = headMeters + distanceAt(position) - startDistance

        val trace = mutableListOf(headMeters to elapsedAt(startPosition))
        val firstWhole = ceil(startPosition).toInt()
        val lastWhole = floor(endPosition).toInt()
        for (i in firstWhole..lastWhole) {
            val position = i.toDouble()
            if (position <= startPosition || position >= endPosition) continue
            trace.add(lapDistanceAt(position) to elapsedAt(position))
        }
        // The lap ends exactly on the line, at exactly its recorded time.
        trace.add(lapDistanceAt(endPosition) to lapMs)

        if (trace.last().first < MIN_LAP_METERS) return null
        return Built(direction, ReferenceLap(lapMs, trace))
    }

    private fun LapInfoData.asFix() = RawGPSData(
        sessionid = 0L,
        latitude = lat,
        longitude = lon,
        altitude = alt,
        timestamp = timestamp ?: 0L,
        speed = spd,
        fixQuality = null
    )
}
