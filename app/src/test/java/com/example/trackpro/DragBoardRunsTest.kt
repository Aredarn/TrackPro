package com.example.trackpro

import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.calculationClasses.DragBoardMetric
import com.example.trackpro.managerClasses.calculationClasses.DragBoardMetric.HundredToTwoHundred
import com.example.trackpro.managerClasses.calculationClasses.DragBoardMetric.QuarterMile
import com.example.trackpro.managerClasses.calculationClasses.DragBoardMetric.ZeroToHundred
import com.example.trackpro.managerClasses.calculationClasses.DragBoardResult
import com.example.trackpro.managerClasses.calculationClasses.DragBoardRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragBoardRunsTest {

    private val external = DragBoardRuns.maxGapSeconds(phoneGps = false)
    private val phone = DragBoardRuns.maxGapSeconds(phoneGps = true)

    /** Metres per degree of latitude. */
    private val metersPerDegree = 111_195.0

    /**
     * A recording built fix by fix: rest, then constant acceleration in a straight line, then
     * braking to a stop. [altitudeAt] maps distance from the launch to height.
     */
    private inner class Trace(private val intervalMs: Long = 100L) {
        val fixes = ArrayList<RawGPSData>()
        private var t = 1_000_000L
        private var meters = 0.0

        private fun fix(kmh: Double, altitude: Double?) {
            fixes += RawGPSData(
                sessionid = 1,
                latitude = 47.0 + meters / metersPerDegree,
                longitude = 19.0,
                altitude = altitude,
                timestamp = t,
                speed = kmh.toFloat(),
                fixQuality = 1,
            )
            t += intervalMs
        }

        fun rest(seconds: Double = 2.0, altitude: Double? = 200.0): Trace {
            repeat((seconds * 1000 / intervalMs).toInt()) { fix(0.0, altitude) }
            return this
        }

        fun pull(
            toKmh: Double,
            kmhPerSecond: Double,
            altitudeAt: (Double) -> Double? = { 200.0 },
        ): Trace {
            var kmh = 0.0
            val startMeters = meters
            while (kmh < toKmh) {
                kmh += kmhPerSecond * intervalMs / 1000.0
                meters += kmh / 3.6 * intervalMs / 1000.0
                fix(kmh, altitudeAt(meters - startMeters))
            }
            while (kmh > 0) {
                kmh = (kmh - 40.0 * intervalMs / 1000.0).coerceAtLeast(0.0)
                meters += kmh / 3.6 * intervalMs / 1000.0
                fix(kmh, altitudeAt(meters - startMeters))
            }
            return this
        }

        /** Loses the fixes between two moments of the recording, as a dropout would. */
        fun drop(fromMs: Long, toMs: Long): Trace {
            val first = fixes.first().timestamp
            fixes.removeAll { it.timestamp - first in fromMs..toMs }
            return this
        }
    }

    private fun List<DragBoardResult>.of(metric: DragBoardMetric) = firstOrNull { it.metric == metric }

    @Test
    fun `a clean pull yields every metric it reaches`() {
        // 25 km/h per second: 0-100 in 4 s, 100-200 in 4 s.
        val results = DragBoardRuns.bestResults(Trace().rest().pull(240.0, 25.0).rest().fixes, external)

        assertEquals(4.0, results.of(ZeroToHundred)!!.seconds, 0.15)
        assertEquals(4.0, results.of(HundredToTwoHundred)!!.seconds, 0.15)
        val quarter = results.of(QuarterMile)
        assertNotNull(quarter)
        assertNotNull(quarter!!.trapSpeedKmh)
    }

    @Test
    fun `a pull that stops short only yields what it reached`() {
        val results = DragBoardRuns.bestResults(Trace().rest().pull(120.0, 25.0).rest().fixes, external)

        assertNotNull(results.of(ZeroToHundred))
        assertNull(results.of(HundredToTwoHundred))
    }

    @Test
    fun `the best run of a session wins`() {
        val fixes = Trace().rest().pull(110.0, 20.0).rest().pull(110.0, 25.0).rest().pull(110.0, 15.0).rest().fixes

        assertEquals(4.0, DragBoardRuns.bestResults(fixes, external).of(ZeroToHundred)!!.seconds, 0.15)
    }

    @Test
    fun `a dropout during the pull costs that run, and the next best stands in`() {
        val trace = Trace().rest().pull(110.0, 25.0).rest().pull(110.0, 20.0).rest()
        // Two seconds out of the quick run's 0-100, which starts at 2 s.
        trace.drop(3_000, 5_000)

        val result = DragBoardRuns.bestResults(trace.fixes, external).of(ZeroToHundred)

        // Timing starts as the car passes 2 km/h, a tenth of a second into the pull.
        assertEquals(4.9, result!!.seconds, 0.15)
    }

    @Test
    fun `a dropout after the finish costs nothing`() {
        val trace = Trace().rest().pull(110.0, 25.0).rest()
        // Launch at 2 s, 100 km/h at 6 s, peak at 6.4 s, then braking.
        trace.drop(7_000, 8_000)

        assertNotNull(DragBoardRuns.bestResults(trace.fixes, external).of(ZeroToHundred))
    }

    @Test
    fun `one second phone fixes are not treated as a dropout`() {
        val fixes = Trace(intervalMs = 1_000).rest(seconds = 3.0).pull(110.0, 25.0).rest(seconds = 3.0).fixes

        assertNotNull(DragBoardRuns.bestResults(fixes, phone).of(ZeroToHundred))
        assertTrue(DragBoardRuns.bestResults(fixes, external).isEmpty())
    }

    @Test
    fun `a clear downhill run is refused`() {
        // 5% all the way down, from 200 m. A 0-100 alone covers only some 55 m of it.
        val fixes = Trace().rest().pull(240.0, 25.0, altitudeAt = { 200.0 - it * 0.05 }).rest(altitude = 150.0).fixes

        assertTrue(DragBoardRuns.bestResults(fixes, external).isEmpty())
    }

    @Test
    fun `a gentle road is not mistaken for a downhill run`() {
        val fixes = Trace().rest().pull(240.0, 25.0, altitudeAt = { 200.0 - it * 0.01 }).rest().fixes

        assertEquals(3, DragBoardRuns.bestResults(fixes, external).size)
    }

    @Test
    fun `altitude noise within a few metres passes`() {
        val fixes = Trace().rest().pull(150.0, 25.0, altitudeAt = { 200.0 + (it.toInt() % 7 - 3) }).rest().fixes

        assertNotNull(DragBoardRuns.bestResults(fixes, external).of(ZeroToHundred))
    }

    @Test
    fun `climbing is never held against a run`() {
        val fixes = Trace().rest().pull(150.0, 25.0, altitudeAt = { 200.0 + it * 0.05 }).rest().fixes

        assertNotNull(DragBoardRuns.bestResults(fixes, external).of(ZeroToHundred))
    }

    @Test
    fun `a trace without altitude is judged on its gaps alone`() {
        val fixes = Trace().rest(altitude = null).pull(150.0, 25.0, altitudeAt = { null }).rest(altitude = null).fixes

        assertNotNull(DragBoardRuns.bestResults(fixes, external).of(ZeroToHundred))
    }

    @Test
    fun `fixes marked invalid do not count, so they read as a gap`() {
        val trace = Trace().rest().pull(110.0, 25.0).rest()
        val first = trace.fixes.first().timestamp
        val fixes = trace.fixes.map {
            if (it.timestamp - first in 3_000..5_000) it.copy(valid = false) else it
        }

        assertTrue(DragBoardRuns.bestResults(fixes, external).isEmpty())
    }

    @Test
    fun `a recording that starts on the move has no standing start to rank`() {
        val fixes = Trace().pull(110.0, 25.0).rest().fixes.drop(5)

        assertNull(DragBoardRuns.bestResults(fixes, external).of(ZeroToHundred))
    }

    @Test
    fun `no fixes means no results`() {
        assertTrue(DragBoardRuns.bestResults(emptyList(), external).isEmpty())
    }
}
