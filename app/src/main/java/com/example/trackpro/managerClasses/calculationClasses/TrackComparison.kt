package com.example.trackpro.managerClasses.calculationClasses

import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A fix on a lap: where, how fast, and how long after the lap's start. */
class LapPoint(val lat: Double, val lon: Double, val speedKmh: Double, val seconds: Double)

/** One timed lap (or sprint run), cut to its own start and finish. */
class TrackLap(val lapId: Long, val lapMs: Long, val points: List<LapPoint>)

class GeoPoint(val lat: Double, val lon: Double)

/** A flat metric plane around a track: x east, y north, in metres. Good for a few kilometres. */
class LocalPlane(private val lat0: Double, private val lon0: Double) {
    private val metresPerDegreeLon = METRES_PER_DEGREE * cos(Math.toRadians(lat0))
    fun x(lon: Double) = (lon - lon0) * metresPerDegreeLon
    fun y(lat: Double) = (lat - lat0) * METRES_PER_DEGREE
    fun lat(y: Double) = lat0 + y / METRES_PER_DEGREE
    fun lon(x: Double) = lon0 + x / metresPerDegreeLon

    private companion object {
        const val METRES_PER_DEGREE = 111_320.0
    }
}

/**
 * Cuts a stored lap out of the fixes recorded while its row was current.
 *
 * A circuit lap row also holds the out-lap before the first crossing and a fix or two after
 * its own finish, so the lap is taken to end at its last finish-line crossing and to have
 * started its recorded time before that. A sprint run row holds only the run, whole.
 * See ReferenceLapBuilder, which makes the same cut for the live delta.
 */
object TrackLapExtractor {
    private const val MIN_LAP_METERS = 200.0
    private const val MAX_MISSING_HEAD_MS = 2_000L

    fun extract(
        points: List<LapInfoData>,
        lapId: Long,
        lapMs: Long,
        finishLine: List<TrackCoordinatesData>?,
        circuit: Boolean
    ): TrackLap? {
        if (points.size < 10 || lapMs <= 0) return null
        val stamps = points.map { it.timestamp }
        val timed = stamps.all { it != null } && stamps.zipWithNext().all { (a, b) -> b!! > a!! }

        if (!circuit) {
            if (timed) {
                // The run ends where its row ends (the next run's row opens at the finish), and
                // began its recorded time before that. A session's first run also holds every
                // fix from before the start, which this drops.
                val startStamp = stamps.last()!! - lapMs
                val inside = points.filter { it.timestamp!! >= startStamp }
                if (inside.size < 10) return null
                return TrackLap(lapId, lapMs, inside.map { it.toPoint((it.timestamp!! - startStamp) / 1000.0) })
            }
            return TrackLap(lapId, lapMs, points.mapIndexed { i, p ->
                p.toPoint(i.toDouble() / (points.size - 1) * lapMs / 1000.0)
            })
        }

        if (finishLine == null || finishLine.size < 2) return null
        val fixes = points.map {
            RawGPSData(
                sessionid = 0L, latitude = it.lat, longitude = it.lon, altitude = it.alt,
                timestamp = it.timestamp ?: 0L, speed = it.spd, fixQuality = null
            )
        }
        class Crossing(val position: Double, val entering: Boolean)
        val crossings = (0 until fixes.lastIndex).mapNotNull { i ->
            TrackGeometry.checkLineCrossing(fixes[i], fixes[i + 1], finishLine)
                ?.let { Crossing(i + it.fraction, it.direction == TrackGeometry.CrossingDirection.ENTERING) }
        }
        val end = crossings.lastOrNull() ?: return null

        if (timed) {
            val i = floor(end.position).toInt().coerceIn(0, points.lastIndex - 1)
            val endStamp = stamps[i]!! + (end.position - i) * (stamps[i + 1]!! - stamps[i]!!)
            val startStamp = endStamp - lapMs
            val inside = points.filter { it.timestamp!! >= startStamp && it.timestamp <= endStamp }
            if (inside.size < 10) return null
            if (inside.first().timestamp!! - startStamp > MAX_MISSING_HEAD_MS) return null
            return TrackLap(lapId, lapMs, inside.map { it.toPoint((it.timestamp!! - startStamp) / 1000.0) })
        }

        // No timestamps: from the previous crossing the same way (a lap away, not jitter on
        // the line) or the first point, with the fixes assumed evenly spaced over the lap.
        val start = crossings.lastOrNull { it.position < end.position && it.entering == end.entering }
        val startPosition = start?.position ?: 0.0
        val from = kotlin.math.ceil(startPosition).toInt()
        val to = floor(end.position).toInt()
        if (to - from < 10) return null
        val span = end.position - startPosition
        return TrackLap(lapId, lapMs, (from..to).map { i ->
            points[i].toPoint((i - startPosition) / span * lapMs / 1000.0)
        })
    }

    private fun LapInfoData.toPoint(seconds: Double) = LapPoint(lat, lon, (spd ?: 0f).toDouble(), seconds)
}

/** A lap laid out every [ds] metres of its own path. */
class ResampledLap(
    val ds: Double,
    val x: DoubleArray,
    val y: DoubleArray,
    val t: DoubleArray,
    val v: DoubleArray
) {
    val size: Int get() = x.size
    val lengthM: Double get() = (size - 1) * ds
}

/** A lap measured along the reference lap: every quantity is a function of progress on it. */
class AlignedLap(
    val lapSeconds: Double,
    /** Seconds since the lap's start, at each reference sample. */
    val t: DoubleArray,
    val v: DoubleArray,
    /** Metres to the left of the reference line (negative: to its right). */
    val offset: DoubleArray,
    val x: DoubleArray,
    val y: DoubleArray
) {
    /** Time at a fractional reference sample. */
    fun timeAt(index: Double): Double = lerp(t, index)

    fun speedAt(index: Double): Double = lerp(v, index)

    fun offsetAt(index: Double): Double = lerp(offset, index)
}

private fun lerp(a: DoubleArray, index: Double): Double {
    val i = floor(index).toInt().coerceIn(0, a.size - 1)
    if (i >= a.size - 1) return a[a.size - 1]
    val f = (index - i).coerceIn(0.0, 1.0)
    return a[i] + f * (a[i + 1] - a[i])
}

/** A bend of the reference lap, in samples of the reference grid. */
class Corner(
    val index: Int,
    val startIndex: Int,
    val apexIndex: Int,
    val endIndex: Int,
    /** +1 a left-hander, -1 a right-hander. */
    val direction: Int,
    val angleDegrees: Double
)

/** How one car took a corner. [insideOffset] is metres towards the inside of it, vs the reference. */
class CornerStat(
    val entryKmh: Double,
    val apexKmh: Double,
    val exitKmh: Double,
    val seconds: Double,
    /** The tightest radius of the car's own path through the corner; null if it was nearly straight. */
    val minRadius: Double?,
    val insideOffset: Double
)

class TrackCarInput(val vehicleId: Long, val name: String, val laps: List<TrackLap>)

class TrackCarResult(
    val vehicleId: Long,
    val name: String,
    val validLaps: Int,
    val skippedLaps: Int,
    val bestLapMs: Long?,
    val averageLapMs: Double?,
    /** The best sectors of all this car's laps added up; equals the best lap with one lap. */
    val theoreticalSeconds: Double?,
    val bestLapSectors: List<Double>,
    val bestSectors: List<Double>,
    val best: AlignedLap?,
    val corners: List<CornerStat>
)

class TrackComparison(
    val reference: ResampledLap,
    /** Sector boundaries as reference samples, first 0 and last the finish. */
    val sectorEdges: List<Double>,
    /** True when the track has no sector gates and the lap was cut into equal parts. */
    val equalSectors: Boolean,
    val corners: List<Corner>,
    val cars: List<TrackCarResult>
)

object TrackCompare {
    const val STEP_METERS = 5.0

    private const val MIN_LAP_METERS = 100.0
    private const val STANDSTILL_KMH = 3.0
    private const val MIN_CORNER_KMH = 10.0
    private const val JITTER_METERS = 8.0
    private const val SEARCH_AHEAD = 60
    private const val MIN_COVERAGE = 0.85
    private const val MAX_TYPICAL_OFFSET = 25.0
    private const val CORNER_CURVATURE = 1.0 / 200.0
    private const val CORNER_MIN_ANGLE = 25.0
    private const val CORNER_MERGE_GAP = 4
    private const val CORNER_MIN_SAMPLES = 3

    /**
     * Lays every car's laps along the first car's best lap and measures sectors and corners.
     * [gates] are the track's sector split points in sector order; without them the lap is cut
     * into [fallbackSectors] equal parts. Null when the first car has no usable lap.
     */
    fun compare(cars: List<TrackCarInput>, gates: List<GeoPoint>, fallbackSectors: Int): TrackComparison? {
        val first = cars.firstOrNull()?.laps?.firstOrNull() ?: return null
        val startPoint = first.points.first()
        val plane = LocalPlane(startPoint.lat, startPoint.lon)

        val resampled = cars.map { car -> car.laps.mapNotNull { lap -> resample(lap, plane)?.let { lap to it } } }
        val reference = resampled.first().minByOrNull { it.first.lapMs }?.second ?: return null

        val projected = gates.mapNotNull { g ->
            project(reference, plane.x(g.lon), plane.y(g.lat), null)?.progress
        }.filter { it > 8.0 && it < reference.size - 9.0 }.sorted()
        val equal = projected.isEmpty()
        val edges = if (equal) {
            val n = max(1, fallbackSectors)
            (0..n).map { it * (reference.size - 1.0) / n }
        } else {
            listOf(0.0) + projected + (reference.size - 1.0)
        }

        // Bends driven at a walking pace are the car being parked or turned round, not a corner.
        val corners = detectCorners(reference)
            .filter { reference.v[it.apexIndex] >= MIN_CORNER_KMH }
            .mapIndexed { i, c -> Corner(i + 1, c.startIndex, c.apexIndex, c.endIndex, c.direction, c.angleDegrees) }

        val results = cars.mapIndexed { index, car ->
            val aligned = resampled[index].mapNotNull { (lap, lapResampled) ->
                align(reference, lapResampled, lap.lapMs / 1000.0)?.let { lap to it }
            }
            val bestLap = aligned.minByOrNull { it.first.lapMs }
            val sectorTimes = aligned.map { (_, a) -> sectorTimes(a, edges) }
            val bestSectors = if (sectorTimes.isEmpty()) emptyList()
            else edges.indices.drop(1).map { s -> sectorTimes.minOf { it[s - 1] } }
            TrackCarResult(
                vehicleId = car.vehicleId,
                name = car.name,
                validLaps = aligned.size,
                skippedLaps = car.laps.size - aligned.size,
                bestLapMs = bestLap?.first?.lapMs,
                averageLapMs = aligned.takeIf { it.isNotEmpty() }?.map { it.first.lapMs }?.average(),
                theoreticalSeconds = bestSectors.takeIf { it.isNotEmpty() }?.sum(),
                bestLapSectors = bestLap?.let { sectorTimes(it.second, edges) } ?: emptyList(),
                bestSectors = bestSectors,
                best = bestLap?.second,
                corners = bestLap?.let { (_, a) -> corners.map { cornerStat(reference, a, it) } } ?: emptyList()
            )
        }
        return TrackComparison(reference, edges, equal, corners, results)
    }

    // ── Laying a lap out ──────────────────────────────────────

    fun resample(lap: TrackLap, plane: LocalPlane): ResampledLap? {
        if (lap.points.size < 10) return null
        val kept = withoutStandstillJitter(lap.points, plane)
        val n = kept.size
        if (n < 10) return null
        val px = DoubleArray(n) { plane.x(kept[it].lon) }
        val py = DoubleArray(n) { plane.y(kept[it].lat) }
        val cumulative = DoubleArray(n)
        for (i in 1 until n) cumulative[i] = cumulative[i - 1] + hypot(px[i] - px[i - 1], py[i] - py[i - 1])
        val total = cumulative[n - 1]
        if (total < MIN_LAP_METERS) return null

        val m = floor(total / STEP_METERS).toInt() + 1
        val x = DoubleArray(m)
        val y = DoubleArray(m)
        val t = DoubleArray(m)
        val v = DoubleArray(m)
        var j = 0
        for (k in 0 until m) {
            val target = k * STEP_METERS
            while (j < n - 2 && cumulative[j + 1] < target) j++
            val segment = cumulative[j + 1] - cumulative[j]
            val f = if (segment <= 1e-9) 0.0 else ((target - cumulative[j]) / segment).coerceIn(0.0, 1.0)
            x[k] = px[j] + f * (px[j + 1] - px[j])
            y[k] = py[j] + f * (py[j + 1] - py[j])
            t[k] = kept[j].seconds + f * (kept[j + 1].seconds - kept[j].seconds)
            v[k] = kept[j].speedKmh + f * (kept[j + 1].speedKmh - kept[j].speedKmh)
        }
        return ResampledLap(STEP_METERS, smooth(x), smooth(y), t, v)
    }

    /**
     * A car at a standstill still reports fixes that wander a few metres, which laid end to end
     * are a path of their own - a tight circle at no speed. Fixes that are slow and have not
     * gone anywhere since the last kept one are dropped; the wait stays in the times.
     */
    private fun withoutStandstillJitter(points: List<LapPoint>, plane: LocalPlane): List<LapPoint> {
        val kept = ArrayList<LapPoint>(points.size)
        for (p in points) {
            val last = kept.lastOrNull()
            if (last != null && p.speedKmh < STANDSTILL_KMH &&
                hypot(plane.x(p.lon) - plane.x(last.lon), plane.y(p.lat) - plane.y(last.lat)) < JITTER_METERS
            ) continue
            kept += p
        }
        return kept
    }

    /** A moving average over five samples, ends kept: takes the fix jitter out of the line. */
    private fun smooth(a: DoubleArray): DoubleArray = DoubleArray(a.size) { i ->
        if (i < 2 || i > a.size - 3) a[i] else (a[i - 2] + a[i - 1] + a[i] + a[i + 1] + a[i + 2]) / 5.0
    }

    private class Projection(val progress: Double, val distance: Double, val signedOffset: Double)

    /**
     * The nearest point of the reference line to (px, py), as progress in reference samples.
     * With [near] only the segments just around and ahead of that sample are looked at, which
     * keeps a lap from jumping to another part of a track that passes close to itself.
     */
    private fun project(ref: ResampledLap, px: Double, py: Double, near: Int?): Projection? {
        if (ref.size < 2) return null
        val lo = if (near == null) 0 else max(0, near - 2)
        val hi = if (near == null) ref.size - 2 else min(ref.size - 2, near + SEARCH_AHEAD)
        var best: Projection? = null
        for (j in lo..hi) {
            val dx = ref.x[j + 1] - ref.x[j]
            val dy = ref.y[j + 1] - ref.y[j]
            val len2 = dx * dx + dy * dy
            val u = if (len2 < 1e-9) 0.0 else (((px - ref.x[j]) * dx + (py - ref.y[j]) * dy) / len2).coerceIn(0.0, 1.0)
            val distance = hypot(px - (ref.x[j] + u * dx), py - (ref.y[j] + u * dy))
            if (best == null || distance < best.distance) {
                val cross = dx * (py - ref.y[j]) - dy * (px - ref.x[j])
                best = Projection(j + u, distance, if (cross >= 0) distance else -distance)
            }
        }
        return best
    }

    /**
     * Measures [lap] along [ref]: at each reference sample, when the car was there, how fast,
     * and how far to the side. Null when the lap does not follow the reference round - driven
     * the other way, cut short, or not on this track at all.
     */
    fun align(ref: ResampledLap, lap: ResampledLap, lapSeconds: Double): AlignedLap? {
        val m = lap.size
        val progress = DoubleArray(m)
        val side = DoubleArray(m)
        var near: Int? = null
        for (i in 0 until m) {
            val p = project(ref, lap.x[i], lap.y[i], near) ?: return null
            progress[i] = if (i == 0) p.progress else max(p.progress, progress[i - 1])
            side[i] = p.signedOffset
            near = floor(progress[i]).toInt()
        }
        if (progress[m - 1] - progress[0] < MIN_COVERAGE * (ref.size - 1)) return null
        if (side.map { abs(it) }.sorted()[m / 2] > MAX_TYPICAL_OFFSET) return null

        val n = ref.size
        val t = DoubleArray(n)
        val v = DoubleArray(n)
        val off = DoubleArray(n)
        val x = DoubleArray(n)
        val y = DoubleArray(n)
        var i = 0
        for (k in 0 until n) {
            while (i < m - 2 && progress[i + 1] < k) i++
            val span = progress[i + 1] - progress[i]
            val f = if (span <= 1e-9) 0.0 else ((k - progress[i]) / span).coerceIn(0.0, 1.0)
            t[k] = lap.t[i] + f * (lap.t[i + 1] - lap.t[i])
            v[k] = lap.v[i] + f * (lap.v[i + 1] - lap.v[i])
            off[k] = side[i] + f * (side[i + 1] - side[i])
            x[k] = lap.x[i] + f * (lap.x[i + 1] - lap.x[i])
            y[k] = lap.y[i] + f * (lap.y[i + 1] - lap.y[i])
        }
        val origin = t[0]
        for (k in 0 until n) t[k] -= origin
        return AlignedLap(lapSeconds, t, v, off, x, y)
    }

    /**
     * Seconds spent in each sector. The finish is the lap's recorded time, so the last sector
     * takes up whatever the cut left over and the sectors always add up to the lap.
     */
    fun sectorTimes(lap: AlignedLap, edges: List<Double>): List<Double> {
        val raw = edges.zipWithNext { a, b -> lap.timeAt(b) - lap.timeAt(a) }
        val rest = lap.lapSeconds - raw.sum()
        return raw.dropLast(1) + (raw.last() + rest)
    }

    // ── Corners ───────────────────────────────────────────────

    /** Signed curvature per sample, 1/metres, positive turning left. */
    fun curvature(x: DoubleArray, y: DoubleArray, ds: Double): DoubleArray {
        val n = x.size
        val heading = DoubleArray(n) { k ->
            val a = max(0, k - 2)
            val b = min(n - 1, k + 2)
            atan2(y[b] - y[a], x[b] - x[a])
        }
        val raw = DoubleArray(n) { k ->
            if (k < 1 || k > n - 2) 0.0 else wrap(heading[k + 1] - heading[k - 1]) / (2 * ds)
        }
        return DoubleArray(n) { k ->
            val a = max(0, k - 2)
            val b = min(n - 1, k + 2)
            (a..b).sumOf { raw[it] } / (b - a + 1)
        }
    }

    private fun wrap(angle: Double): Double {
        var a = angle
        while (a > PI) a -= 2 * PI
        while (a <= -PI) a += 2 * PI
        return a
    }

    fun detectCorners(ref: ResampledLap): List<Corner> {
        val kappa = curvature(ref.x, ref.y, ref.ds)
        class Run(var start: Int, var end: Int, val sign: Int)
        val runs = ArrayList<Run>()
        for (k in kappa.indices) {
            if (abs(kappa[k]) < CORNER_CURVATURE) continue
            val sign = if (kappa[k] > 0) 1 else -1
            val last = runs.lastOrNull()
            if (last != null && last.sign == sign && k - last.end <= CORNER_MERGE_GAP) last.end = k
            else runs += Run(k, k, sign)
        }
        val corners = ArrayList<Corner>()
        for (run in runs) {
            if (run.end - run.start + 1 < CORNER_MIN_SAMPLES) continue
            val angle = Math.toDegrees((run.start..run.end).sumOf { abs(kappa[it]) } * ref.ds)
            if (angle < CORNER_MIN_ANGLE) continue
            val apex = (run.start..run.end).maxByOrNull { abs(kappa[it]) }!!
            corners += Corner(corners.size + 1, run.start, apex, run.end, run.sign, angle)
        }
        return corners
    }

    private fun cornerStat(ref: ResampledLap, lap: AlignedLap, corner: Corner): CornerStat {
        val from = max(0, corner.startIndex - 2)
        val to = min(ref.size - 1, corner.endIndex)
        val apexSpeed = (from..to).minOf { lap.v[it] }
        return CornerStat(
            entryKmh = lap.v[corner.startIndex],
            apexKmh = apexSpeed,
            exitKmh = lap.v[corner.endIndex],
            seconds = lap.t[corner.endIndex] - lap.t[corner.startIndex],
            minRadius = minRadius(lap.x, lap.y, corner.startIndex, corner.endIndex),
            insideOffset = lap.offset[corner.apexIndex] * corner.direction
        )
    }

    /** The tightest circle through three samples three apart, over the stretch; null if never bent. */
    private fun minRadius(x: DoubleArray, y: DoubleArray, from: Int, to: Int): Double? {
        var best: Double? = null
        for (k in max(3, from)..min(x.size - 4, to)) {
            val ax = x[k - 3]; val ay = y[k - 3]
            val bx = x[k]; val by = y[k]
            val cx = x[k + 3]; val cy = y[k + 3]
            val area2 = abs((bx - ax) * (cy - ay) - (by - ay) * (cx - ax))
            if (area2 < 1e-6) continue
            val radius = hypot(bx - ax, by - ay) * hypot(cx - bx, cy - by) * hypot(cx - ax, cy - ay) / (2 * area2)
            if (best == null || radius < best) best = radius
        }
        return best?.takeIf { it < 2000.0 }
    }
}
