package com.example.trackpro

import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.calculationClasses.DragCarInput
import com.example.trackpro.managerClasses.calculationClasses.DragCompare
import com.example.trackpro.managerClasses.calculationClasses.DragCompareScale
import com.example.trackpro.managerClasses.calculationClasses.DragCompareSpans
import com.example.trackpro.managerClasses.calculationClasses.DragRunAnalyzer
import com.example.trackpro.managerClasses.calculationClasses.DragRunProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragComparisonTest {

    private val t0 = 1_000_000L

    private fun sample(tSeconds: Double, kmh: Double) = RawGPSData(
        sessionid = 1L,
        latitude = 47.0,
        longitude = 17.0,
        altitude = 0.0,
        timestamp = t0 + Math.round(tSeconds * 1000.0),
        speed = kmh.toFloat(),
        fixQuality = 12
    )

    /**
     * A pull at 10 Hz: [standingS] seconds at rest, then [a1] m/s^2 up to [shiftAtKmh], a coast of
     * [shiftS] seconds, then [a2] up to [topKmh]. Fixes whose time falls in [dropout] are left out.
     */
    private fun pull(
        a1: Double,
        shiftAtKmh: Double = 0.0,
        shiftS: Double = 0.0,
        a2: Double = a1,
        topKmh: Double = 110.0,
        standingS: Double = 2.0,
        dropout: ClosedFloatingPointRange<Double>? = null
    ): List<RawGPSData> {
        val dt = 0.1
        val out = ArrayList<RawGPSData>()
        var t = 0.0
        var v = 0.0
        var coastLeft = shiftS
        while (t < standingS) {
            out += sample(t, 0.0)
            t += dt
        }
        while (v * 3.6 < topKmh) {
            if (dropout == null || t !in dropout) out += sample(t, v * 3.6)
            val a = when {
                shiftAtKmh <= 0.0 -> a1
                v * 3.6 < shiftAtKmh -> a1
                coastLeft > 0.0 -> { coastLeft -= dt; 0.0 }
                else -> a2
            }
            v += a * dt
            t += dt
        }
        out += sample(t, v * 3.6)
        return out
    }

    private fun profile(id: Long, samples: List<RawGPSData>): DragRunProfile =
        requireNotNull(DragRunAnalyzer.analyze(id, samples)) { "no pull found in run $id" }

    // ── The pull ──────────────────────────────────────────────

    @Test
    fun `a constant pull is timed from the launch`() {
        val run = profile(1, pull(a1 = 3.0))
        // The launch is the 2 km/h crossing, so 30 km/h is 28 km/h (7.78 m/s) of pull: 2.59 s.
        assertEquals(2.59, run.timeAt(30.0)!!, 0.08)
        assertEquals(9.07, run.timeAt(100.0)!!, 0.1)
        assertNull(run.timeAt(200.0))
    }

    @Test
    fun `a roll out of the paddock does not stand in for the pull`() {
        val roll = ArrayList<RawGPSData>()
        // 8 km/h creep, then back to rest for two seconds, then the real run.
        var t = 0.0
        while (t < 1.0) { roll += sample(t, 0.0); t += 0.1 }
        while (t < 2.0) { roll += sample(t, 8.0); t += 0.1 }
        while (t < 4.0) { roll += sample(t, 0.0); t += 0.1 }
        val real = pull(a1 = 3.0, standingS = 0.0).map { it.copy(timestamp = it.timestamp + Math.round(t * 1000.0)) }
        val run = profile(1, roll + real)
        assertEquals(2.59, run.timeAt(30.0)!!, 0.1)
    }

    @Test
    fun `a recording with no pull gives no profile`() {
        val crawl = (0 until 60).map { sample(it * 0.1, 5.0 + (it % 3)) }
        assertNull(DragRunAnalyzer.analyze(1, crawl))
    }

    // ── Gear changes ──────────────────────────────────────────

    @Test
    fun `a smooth pull has no gear change`() {
        assertTrue(profile(1, pull(a1 = 3.0)).shifts.isEmpty())
    }

    @Test
    fun `a gear change is found where it happens and costs about its length`() {
        val run = profile(1, pull(a1 = 4.0, shiftAtKmh = 50.0, shiftS = 0.6, a2 = 2.5))
        assertEquals(1, run.shifts.size)
        val shift = run.shifts.single()
        assertEquals(50.0, shift.speedKmh, 5.0)
        assertEquals(0.6, shift.lostSeconds, 0.2)
    }

    @Test
    fun `a gear change beside a dropout is not reported`() {
        val samples = pull(a1 = 4.0, shiftAtKmh = 50.0, shiftS = 0.6, a2 = 2.5, dropout = 5.0..6.2)
        val run = profile(1, samples)
        assertTrue(run.gaps.isNotEmpty())
        assertTrue(run.shifts.isEmpty())
        // The dropout hides the change's shape, not the time it took to reach 100.
        assertNotNull(run.timeAt(100.0))
    }

    // ── Comparing cars ────────────────────────────────────────

    private fun car(runs: List<DragRunProfile>, weight: Double = 1000.0) =
        DragCarInput(vehicleId = 1, name = "Test", weightKg = weight, horsepower = 150, runs = runs)

    @Test
    fun `splits report the mean and the best of a car's runs`() {
        val fast = profile(1, pull(a1 = 3.5))
        val slow = profile(2, pull(a1 = 3.0))
        val summary = DragCompare.summarize(car(listOf(fast, slow)), DragCompareScale.Metric, occupants = 1)
        val index = DragCompareSpans.splits.indexOf(0 to 60)
        val stat = summary.splits[index]
        assertEquals(2, stat.runs)
        assertEquals(fast.timeAt(60.0)!!, stat.best!!, 0.001)
        assertEquals((fast.timeAt(60.0)!! + slow.timeAt(60.0)!!) / 2.0, stat.mean!!, 0.001)
    }

    @Test
    fun `a range a run never reached is left out, not zeroed`() {
        val short = profile(1, pull(a1 = 3.0, topKmh = 80.0))
        val summary = DragCompare.summarize(car(listOf(short)), DragCompareScale.Metric, occupants = 1)
        val to100 = summary.splits[DragCompareSpans.splits.indexOf(0 to 100)]
        assertNull(to100.mean)
        assertEquals(0, to100.runs)
        assertNull(summary.theoretical)
    }

    @Test
    fun `the theoretical best takes the best of each part, gear change included`() {
        // A launches harder but shifts slowly; B launches softer and shifts quickly.
        val a = profile(1, pull(a1 = 4.0, shiftAtKmh = 50.0, shiftS = 0.8, a2 = 2.5))
        val b = profile(2, pull(a1 = 3.0, shiftAtKmh = 50.0, shiftS = 0.4, a2 = 2.5))
        val summary = DragCompare.summarize(car(listOf(a, b)), DragCompareScale.Metric, occupants = 1)
        val theory = summary.theoretical!!
        assertEquals(minOf(a.timeAt(100.0)!!, b.timeAt(100.0)!!), theory.actualBest, 0.001)
        assertTrue("theoretical ${theory.seconds} should beat ${theory.actualBest}", theory.seconds < theory.actualBest - 0.1)
        // Not a fantasy: it cannot beat a launch of A plus a straight-through B.
        assertTrue(theory.seconds > theory.actualBest - 1.0)
        assertEquals(1, summary.shifts.size)
        assertEquals(50.0, summary.shifts.single().meanSpeedKmh, 6.0)
    }

    @Test
    fun `one run has nothing to improve on`() {
        val only = profile(1, pull(a1 = 3.0))
        val theory = DragCompare.summarize(car(listOf(only)), DragCompareScale.Metric, occupants = 1).theoretical!!
        assertEquals(theory.actualBest, theory.seconds, 0.0001)
    }

    // ── Power per kg ──────────────────────────────────────────

    @Test
    fun `wheel power per kg follows from the acceleration and the loaded mass`() {
        val run = profile(1, pull(a1 = 3.0))
        val summary = DragCompare.summarize(car(listOf(run), weight = 1000.0), DragCompareScale.Metric, occupants = 1)
        val window = DragCompareSpans.power.indexOf(60 to 80)
        // 1080 kg at 3 m/s^2 around 70 km/h: roughly 67 W/kg.
        assertEquals(67.0, summary.powerWkg[window]!!, 5.0)
    }

    @Test
    fun `power per kg needs a weight`() {
        val run = profile(1, pull(a1 = 3.0))
        val summary = DragCompare.summarize(car(listOf(run), weight = 0.0), DragCompareScale.Metric, occupants = 1)
        assertNull(summary.massKg)
        assertTrue(summary.powerWkg.all { it == null })
    }

    @Test
    fun `an imperial scale puts the same labels on mph`() {
        assertEquals(96.56, DragCompareScale.Imperial.kmh(60.0), 0.01)
        assertEquals(60.0, DragCompareScale.Imperial.displayUnits(96.56), 0.01)
        assertEquals(60.0, DragCompareScale.Metric.kmh(60.0), 0.0001)
    }
}
