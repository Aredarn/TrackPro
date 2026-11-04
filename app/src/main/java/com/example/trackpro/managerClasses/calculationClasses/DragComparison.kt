package com.example.trackpro.managerClasses.calculationClasses

import com.example.trackpro.dataClasses.RawGPSData
import kotlin.math.max
import kotlin.math.min

/** One gear change inside a pull: where it happened and how much acceleration it cost. */
data class DragShift(
    val speedKmh: Double,
    val atSeconds: Double,
    /** Seconds of full acceleration lost around the change. */
    val lostSeconds: Double
)

/**
 * One launch-to-peak pull, reduced to what a comparison needs.
 *
 * [seconds] is time since the launch (the moment speed passed the standstill threshold), so
 * the first sample, the last one at rest, is slightly negative. [accel] is in m/s^2, taken from
 * the GPS speed over a 0.4 s window.
 */
class DragRunProfile(
    val sessionId: Long,
    val seconds: DoubleArray,
    val speedKmh: DoubleArray,
    val accel: DoubleArray,
    /** Stretches where fixes were missing; acceleration across them is not a measurement. */
    val gaps: List<ClosedFloatingPointRange<Double>>,
    val shifts: List<DragShift>
) {
    val peakKmh: Double get() = speedKmh.maxOrNull() ?: 0.0

    /** Seconds since launch at which [kmh] was first reached, interpolated; null if it never was. */
    fun timeAt(kmh: Double): Double? {
        if (kmh <= 0.0) return 0.0
        for (i in 1 until seconds.size) {
            if (speedKmh[i] >= kmh) {
                val v0 = speedKmh[i - 1]
                val v1 = speedKmh[i]
                if (v0 >= kmh || v1 <= v0) return seconds[i]
                return seconds[i - 1] + (kmh - v0) / (v1 - v0) * (seconds[i] - seconds[i - 1])
            }
        }
        return null
    }

    fun isNearGap(second: Double, margin: Double = 0.3): Boolean =
        gaps.any { second >= it.start - margin && second <= it.endInclusive + margin }
}

/**
 * Finds the main pull of a recording and measures it.
 *
 * The run window follows [DragTimeCalculation]: a run opens when speed passes the standstill
 * threshold after having been at rest, and closes on the next standstill. Of the runs in a
 * recording the one reaching the highest speed is the pull; a roll out of the paddock or a
 * false start does not outrank it.
 */
object DragRunAnalyzer {
    private const val STANDSTILL_KMH = 2.0
    private const val MIN_PEAK_KMH = 20.0

    /** Longer than this between two fixes (10 Hz nominal) counts as a dropout. */
    private const val GAP_SECONDS = 0.35

    fun analyze(sessionId: Long, samples: List<RawGPSData>): DragRunProfile? {
        if (samples.size < 5) return null
        val sorted = samples.sortedBy { it.timestamp }
        val n = sorted.size
        val t = LongArray(n) { sorted[it].timestamp }
        val v = DoubleArray(n) { (sorted[it].speed ?: 0f).toDouble() }

        var ready = v[0] <= STANDSTILL_KMH
        var curStart = -1
        var curLaunch = 0.0
        var curMax = 0.0
        var bestStart = -1
        var bestEnd = -1
        var bestLaunch = 0.0
        var bestMax = 0.0

        fun close(end: Int) {
            if (curStart >= 0 && curMax >= MIN_PEAK_KMH && curMax > bestMax) {
                bestStart = curStart
                bestEnd = end
                bestLaunch = curLaunch
                bestMax = curMax
            }
            curStart = -1
            curMax = 0.0
        }

        for (i in 1 until n) {
            if (v[i] <= STANDSTILL_KMH) {
                ready = true
                close(i)
            } else {
                if (ready && curStart < 0) {
                    curStart = i
                    curLaunch = crossing(t[i - 1], v[i - 1], t[i], v[i], STANDSTILL_KMH)
                }
                if (curStart >= 0 && v[i] > curMax) curMax = v[i]
            }
        }
        close(n - 1)
        if (bestStart < 0) return null

        var peakIndex = bestStart
        for (i in bestStart..bestEnd) if (v[i] > v[peakIndex]) peakIndex = i

        val first = bestStart - 1
        val count = peakIndex - first + 1
        if (count < 5) return null
        val seconds = DoubleArray(count) { (t[first + it] - bestLaunch) / 1000.0 }
        val speed = DoubleArray(count) { v[first + it] }
        val accel = centralSlope(seconds, speed)

        val gaps = ArrayList<ClosedFloatingPointRange<Double>>()
        for (k in 0 until count - 1) {
            if (seconds[k + 1] - seconds[k] > GAP_SECONDS) gaps += seconds[k]..seconds[k + 1]
        }

        return DragRunProfile(sessionId, seconds, speed, accel, gaps, detectShifts(seconds, speed, accel, gaps))
    }

    /** Milliseconds at which a rising speed passed [goal], interpolated between two fixes. */
    private fun crossing(t0: Long, v0: Double, t1: Long, v1: Double, goal: Double): Double {
        if (v0 >= goal || v1 <= v0) return t1.toDouble()
        return t0 + (goal - v0) / (v1 - v0) * (t1 - t0)
    }

    /** Acceleration in m/s^2 from a +-2 sample window of km/h speeds. */
    private fun centralSlope(sec: DoubleArray, spd: DoubleArray, half: Int = 2): DoubleArray =
        DoubleArray(sec.size) { i ->
            val lo = max(0, i - half)
            val hi = min(sec.size - 1, i + half)
            if (sec[hi] > sec[lo]) (spd[hi] - spd[lo]) / (sec[hi] - sec[lo]) / 3.6 else 0.0
        }

    private fun medianIn(sec: DoubleArray, a: DoubleArray, from: Double, to: Double): Double? {
        val xs = ArrayList<Double>()
        for (i in sec.indices) if (sec[i] in from..to) xs += a[i]
        if (xs.size < 3) return null
        xs.sort()
        return xs[xs.size / 2]
    }

    private class Dip(val t: Double, val v: Double, val pre: Double, var post: Double)

    private class MergedDip(val first: Dip, var last: Dip)

    /**
     * Gear changes show as a dip in acceleration between two steadier stretches. A dip counts
     * when it falls below 45% of the weaker side and below 1.2 m/s^2. The end of a pull never
     * counts: it has no steady stretch after it. Dips under 1.2 s apart are one change (a
     * clutch that bounces), and a change next to a dropout is dropped, because the dropout
     * hides its shape.
     */
    private fun detectShifts(
        sec: DoubleArray,
        spd: DoubleArray,
        a: DoubleArray,
        gaps: List<ClosedFloatingPointRange<Double>>
    ): List<DragShift> {
        val peak = spd.maxOrNull() ?: return emptyList()
        val dips = ArrayList<Dip>()
        for (i in 2 until sec.size - 2) {
            if (a[i] < 1.2 && a[i] <= a[i - 1] && a[i] <= a[i + 1] && spd[i] > 15.0 && spd[i] < peak - 3.0) {
                val pre = medianIn(sec, a, sec[i] - 2.0, sec[i] - 0.7) ?: continue
                val post = medianIn(sec, a, sec[i] + 0.7, sec[i] + 2.0) ?: continue
                if (min(pre, post) > 1.2 && a[i] < 0.45 * min(pre, post)) dips += Dip(sec[i], spd[i], pre, post)
            }
        }
        val merged = ArrayList<MergedDip>()
        for (d in dips) {
            val last = merged.lastOrNull()
            if (last != null && d.t - last.last.t < 1.2) last.last = d else merged += MergedDip(d, d)
        }
        val shifts = ArrayList<DragShift>()
        for (m in merged) {
            val from = m.first.t
            val to = m.last.t
            if (gaps.any { it.endInclusive >= from - 1.0 && it.start <= to + 1.0 }) continue
            val pre = m.first.pre
            val post = m.last.post
            val windowStart = from - 0.8
            val windowEnd = to + 0.8
            var lost = 0.0
            for (j in 0 until sec.size - 1) {
                if (sec[j] in windowStart..windowEnd) {
                    val reference = pre + (post - pre) * (sec[j] - windowStart) / (windowEnd - windowStart)
                    lost += max(0.0, reference - a[j]) * (sec[j + 1] - sec[j])
                }
            }
            shifts += DragShift(m.first.v, (from + to) / 2.0, lost / ((pre + post) / 2.0))
        }
        return shifts
    }
}

/**
 * The speed step every comparison is built on: 10 km/h for a metric driver, 10 mph for an
 * imperial one. Spans below are written in display units (30 means 30 km/h or 30 mph) and
 * [kmh] is the only place they cross to the km/h the GPS reports.
 */
class DragCompareScale private constructor(private val kmhPerUnit: Double) {
    fun kmh(displayUnits: Double): Double = displayUnits * kmhPerUnit

    fun displayUnits(kmh: Double): Double = kmh / kmhPerUnit

    companion object {
        val Metric = DragCompareScale(1.0)
        val Imperial = DragCompareScale(1.609344)
        fun of(metric: Boolean): DragCompareScale = if (metric) Metric else Imperial
    }
}

/** The ranges the comparison tables report, in display units. */
object DragCompareSpans {
    val splits = listOf(0 to 30, 0 to 60, 0 to 100, 30 to 60, 60 to 100)
    val segments = listOf(0 to 30, 30 to 60, 60 to 80, 80 to 100)
    val bands = (0 until 10).map { it * 10 to (it + 1) * 10 }
    val power = listOf(40 to 60, 60 to 80, 85 to 100)
}

/** The mean and best time over a speed range across a car's runs; null when no run covered it. */
data class SpanStat(val mean: Double?, val best: Double?, val runs: Int)

/** A gear change seen in several runs at about the same speed. [index] counts from 1. */
data class ShiftCluster(
    val index: Int,
    val meanSpeedKmh: Double,
    val meanLost: Double,
    val bestLost: Double,
    val worstLost: Double,
    val count: Int
)

/**
 * The best time a car could post if every part of its 0-100 went as well as it ever has.
 * Parts are split around the gear changes, so a change that falls in one speed band in one
 * run and the next band in another is never dropped from the sum.
 */
data class TheoreticalBest(val seconds: Double, val actualBest: Double)

class DragCarInput(
    val vehicleId: Long,
    val name: String,
    val weightKg: Double,
    val horsepower: Int,
    val runs: List<DragRunProfile>
)

class DragCarSummary(
    val vehicleId: Long,
    val name: String,
    val runCount: Int,
    val massKg: Double?,
    val horsepower: Int,
    val splits: List<SpanStat>,
    val segments: List<SpanStat>,
    val bands: List<SpanStat>,
    val theoretical: TheoreticalBest?,
    val shifts: List<ShiftCluster>,
    /** Wheel power per kg of the loaded car, one per [DragCompareSpans.power]; null without a mass. */
    val powerWkg: List<Double?>
)

object DragCompare {
    const val PERSON_KG = 80.0

    // Wheel power estimate: rolling resistance, drag and the mass of the rotating parts.
    private const val ROTATING_MASS_FACTOR = 1.07
    private const val AIR_DENSITY = 1.2
    private const val DRAG_AREA_M2 = 0.65
    private const val ROLLING_RESISTANCE = 0.012
    private const val GRAVITY = 9.81

    /** Below this acceleration a sample is a shift or a lift, not the car pulling. */
    private const val PULLING_ACCEL = 1.0

    fun summarize(
        input: DragCarInput,
        scale: DragCompareScale,
        occupants: Int
    ): DragCarSummary {
        val runs = input.runs
        val mass = if (input.weightKg > 0.0) input.weightKg + occupants * PERSON_KG else null
        return DragCarSummary(
            vehicleId = input.vehicleId,
            name = input.name,
            runCount = runs.size,
            massKg = mass,
            horsepower = input.horsepower,
            splits = DragCompareSpans.splits.map { spanStat(runs, scale, it) },
            segments = DragCompareSpans.segments.map { spanStat(runs, scale, it) },
            bands = DragCompareSpans.bands.map { spanStat(runs, scale, it) },
            theoretical = theoreticalBest(runs, scale),
            shifts = clusterShifts(runs, scale).mapIndexed { i, c -> summarizeCluster(i + 1, c) },
            powerWkg = DragCompareSpans.power.map { powerWkg(runs, scale, it, mass) }
        )
    }

    private fun spanSeconds(run: DragRunProfile, scale: DragCompareScale, span: Pair<Int, Int>): Double? {
        val start = if (span.first == 0) 0.0 else run.timeAt(scale.kmh(span.first.toDouble())) ?: return null
        val end = run.timeAt(scale.kmh(span.second.toDouble())) ?: return null
        return end - start
    }

    private fun spanStat(runs: List<DragRunProfile>, scale: DragCompareScale, span: Pair<Int, Int>): SpanStat {
        val times = runs.mapNotNull { spanSeconds(it, scale, span) }
        if (times.isEmpty()) return SpanStat(null, null, 0)
        return SpanStat(times.average(), times.min(), times.size)
    }

    /**
     * Shifts from all [runs] grouped by the speed they happen at, in speed order. A group seen
     * in only one run of several is dropped as a stray dip.
     */
    private fun clusterShifts(runs: List<DragRunProfile>, scale: DragCompareScale): List<List<DragShift>> {
        val all = runs.flatMap { it.shifts }.sortedBy { it.speedKmh }
        if (all.isEmpty()) return emptyList()
        val groups = ArrayList<MutableList<DragShift>>()
        val apart = scale.kmh(12.0)
        for (s in all) {
            val last = groups.lastOrNull()
            if (last != null && s.speedKmh - last.last().speedKmh <= apart) last += s else groups += mutableListOf(s)
        }
        val minCount = if (runs.size >= 2) 2 else 1
        return groups.filter { it.size >= minCount }
    }

    private fun summarizeCluster(index: Int, shifts: List<DragShift>) = ShiftCluster(
        index = index,
        meanSpeedKmh = shifts.map { it.speedKmh }.average(),
        meanLost = shifts.map { it.lostSeconds }.average(),
        bestLost = shifts.minOf { it.lostSeconds },
        worstLost = shifts.maxOf { it.lostSeconds },
        count = shifts.size
    )

    private fun theoreticalBest(runs: List<DragRunProfile>, scale: DragCompareScale): TheoreticalBest? {
        val top = scale.kmh(100.0)
        val full = runs.filter { it.timeAt(top) != null }
        if (full.isEmpty()) return null
        val actual = full.minOf { it.timeAt(top)!! }
        if (full.size < 2) return TheoreticalBest(actual, actual)

        // Each gear change gets a window around it; the split points are the window edges, so
        // no change straddles one.
        val windows = clusterShifts(full, scale)
            .map { (it.minOf { s -> s.speedKmh } - scale.kmh(8.0))..(it.maxOf { s -> s.speedKmh } + scale.kmh(10.0)) }
            .sortedBy { it.start }
        val merged = ArrayList<ClosedFloatingPointRange<Double>>()
        for (w in windows) {
            val last = merged.lastOrNull()
            if (last != null && w.start <= last.endInclusive) {
                merged[merged.lastIndex] = last.start..max(last.endInclusive, w.endInclusive)
            } else {
                merged += w
            }
        }
        val edges = (listOf(0.0) + merged.flatMap { listOf(it.start, it.endInclusive) } + top)
            .map { it.coerceIn(0.0, top) }
            .distinct()
            .sorted()

        var total = 0.0
        for (k in 0 until edges.size - 1) {
            val a = edges[k]
            val b = edges[k + 1]
            val best = full.minOf { run -> run.timeAt(b)!! - (if (a <= 0.0) 0.0 else run.timeAt(a)!!) }
            total += best
        }
        return TheoreticalBest(total, actual)
    }

    /** Wheel power per kg of loaded car over a speed range, from the pulling samples of every run. */
    private fun powerWkg(
        runs: List<DragRunProfile>,
        scale: DragCompareScale,
        span: Pair<Int, Int>,
        massKg: Double?
    ): Double? {
        if (massKg == null) return null
        val lo = scale.kmh(span.first.toDouble())
        val hi = scale.kmh(span.second.toDouble())
        var sum = 0.0
        var n = 0
        for (run in runs) {
            for (i in run.seconds.indices) {
                val v = run.speedKmh[i]
                if (v < lo || v >= hi || run.accel[i] <= PULLING_ACCEL || run.isNearGap(run.seconds[i])) continue
                sum += wheelPowerWatts(massKg, v, run.accel[i])
                n++
            }
        }
        return if (n < 3) null else sum / n / massKg
    }

    private fun wheelPowerWatts(massKg: Double, kmh: Double, accel: Double): Double {
        val v = kmh / 3.6
        val force = massKg * ROTATING_MASS_FACTOR * accel +
            0.5 * AIR_DENSITY * DRAG_AREA_M2 * v * v +
            massKg * GRAVITY * ROLLING_RESISTANCE
        return force * v
    }
}
