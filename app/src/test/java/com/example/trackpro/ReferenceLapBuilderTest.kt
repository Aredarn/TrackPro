package com.example.trackpro

import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.timeAttackManagers.ReferenceLapBuilder
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry.CrossingDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rebuilding stored laps as live-delta references.
 *
 * The finish line runs north-south at [LINE_LON]; east is the recorded direction. A stored
 * lap is the same loop the timing tests drive: across the line, round clear of it, and back.
 */
class ReferenceLapBuilderTest {

    private companion object {
        const val LINE_LON = 19.0005
        const val LAT = 47.0
    }

    private val finishLine = listOf(
        TrackCoordinatesData(id = -1, trackId = 1, latitude = 46.999, longitude = LINE_LON, altitude = 0.0),
        TrackCoordinatesData(id = -2, trackId = 1, latitude = 47.001, longitude = LINE_LON, altitude = 0.0)
    )

    private data class P(val lon: Double, val lat: Double)

    /**
     * A stored lap: [outLapPoints] approaching from the west, then across the line, the loop,
     * and back across the line with one fix after it - one fix per second throughout.
     * Crossings land 30% and 20% of the way through their seconds, so the lap runs from
     * (outLapPoints) s + 300 ms to (outLapPoints + 1 + loop) s + 200 ms.
     */
    private fun storedLap(outLapPoints: Int, eastward: Boolean = true): List<P> {
        val sign = if (eastward) 1.0 else -1.0
        val out = (outLapPoints downTo 1).map { i -> P(LINE_LON - sign * (0.0003 + i * 0.0005), LAT) }
        val loop = mutableListOf<P>()
        loop += P(LINE_LON - sign * 0.0003, LAT)                  // just before the line
        loop += P(LINE_LON + sign * 0.0007, LAT)                  // just after: crossed at 30%
        fun leg(to: P, steps: Int) {
            val from = loop.last()
            for (s in 1..steps) {
                val f = s.toDouble() / steps
                loop += P(from.lon + f * (to.lon - from.lon), from.lat + f * (to.lat - from.lat))
            }
        }
        leg(P(LINE_LON + sign * 0.01, LAT), 10)
        leg(P(LINE_LON + sign * 0.01, LAT + 0.01), 10)
        leg(P(LINE_LON - sign * 0.01, LAT + 0.01), 20)
        leg(P(LINE_LON - sign * 0.01, LAT), 10)
        leg(P(LINE_LON - sign * 0.0002, LAT), 10)                 // just before the line
        loop += P(LINE_LON + sign * 0.0008, LAT)                  // just after: crossed at 20%
        loop += P(LINE_LON + sign * 0.004, LAT)                   // one fix into the next lap
        return out + loop
    }

    /** The lap's true time, from the crossings [storedLap] puts in it. */
    private fun lapMsOf(points: List<P>, outLapPoints: Int): Long {
        val startCrossing = outLapPoints * 1_000L + 300          // 30% of the second after it
        val endCrossing = (points.size - 3) * 1_000L + 200       // 20% of the last-but-one second
        return endCrossing - startCrossing
    }

    private fun rows(points: List<P>, timed: Boolean) = points.mapIndexed { i, p ->
        LapInfoData(
            lapid = 1L,
            lat = p.lat,
            lon = p.lon,
            alt = 0.0,
            spd = 150f,
            latgforce = null,
            longforce = null,
            timestamp = if (timed) 1_700_000_000_000L + i * 1_000L else null
        )
    }

    @Test
    fun `a timed lap is cut between its crossings and ends at exactly its time`() {
        val points = storedLap(outLapPoints = 5)
        val lapMs = lapMsOf(points, 5)

        val built = ReferenceLapBuilder.build(rows(points, timed = true), lapMs, finishLine)

        assertNotNull(built)
        assertEquals(CrossingDirection.ENTERING, built!!.direction)
        val trace = built.lap.trace
        assertEquals(0.0, trace.first().first, 0.01)
        assertEquals(0L, trace.first().second)
        assertEquals(lapMs, trace.last().second)
        assertTrue("distances rise", trace.zipWithNext().all { (a, b) -> b.first > a.first })
        assertTrue("times rise", trace.zipWithNext().all { (a, b) -> b.second >= a.second })
    }

    @Test
    fun `an out-lap of any length is trimmed off`() {
        // The first lap of a session is stored with the out-lap driven before the line. It
        // must not count towards the lap's distance, however long it was.
        val short = storedLap(outLapPoints = 1)
        val long = storedLap(outLapPoints = 30)

        val a = ReferenceLapBuilder.build(rows(short, true), lapMsOf(short, 1), finishLine)!!
        val b = ReferenceLapBuilder.build(rows(long, true), lapMsOf(long, 30), finishLine)!!

        assertEquals(a.lap.trace.last().first, b.lap.trace.last().first, 0.01)
    }

    @Test
    fun `an untimed lap finds its start crossing and spreads the lap time evenly`() {
        // Laps recorded before trace points had timestamps.
        val points = storedLap(outLapPoints = 5)
        val lapMs = lapMsOf(points, 5)

        val untimed = ReferenceLapBuilder.build(rows(points, timed = false), lapMs, finishLine)!!
        val timed = ReferenceLapBuilder.build(rows(points, timed = true), lapMs, finishLine)!!

        assertEquals(lapMs, untimed.lap.trace.last().second)
        // The same stretch of road either way, and - these fixes really were evenly spaced -
        // the same times along it.
        assertEquals(timed.lap.trace.last().first, untimed.lap.trace.last().first, 0.01)
        val mid = untimed.lap.trace.size / 2
        assertEquals(timed.lap.trace[mid].second.toDouble(), untimed.lap.trace[mid].second.toDouble(), 5.0)
    }

    @Test
    fun `a lap without its own start crossing starts at its first point`() {
        // Every lap but a session's first begins a moment after the line, so its stored
        // points never cross it at the start.
        val points = storedLap(outLapPoints = 0).drop(1)   // lose the fix before the line
        val built = ReferenceLapBuilder.build(rows(points, timed = false), 60_000L, finishLine)

        assertNotNull(built)
        assertEquals(60_000L, built!!.lap.trace.last().second)
    }

    @Test
    fun `a timed lap missing its beginning is refused`() {
        // Claims to be 20 s longer than the points it has: the start is not in the data.
        val points = storedLap(outLapPoints = 0)
        val built = ReferenceLapBuilder.build(rows(points, true), lapMsOf(points, 0) + 20_000, finishLine)

        assertNull(built)
    }

    @Test
    fun `a lap that never crosses the line is refused`() {
        val points = (0..20).map { i -> P(LINE_LON + 0.001 + i * 0.001, LAT) }
        assertNull(ReferenceLapBuilder.build(rows(points, true), 20_000L, finishLine))
    }

    @Test
    fun `a lap driven the other way reports that direction`() {
        val points = storedLap(outLapPoints = 3, eastward = false)
        val built = ReferenceLapBuilder.build(rows(points, true), lapMsOf(points, 3), finishLine)

        assertEquals(CrossingDirection.EXITING, built!!.direction)
    }
}
