package com.example.trackpro

import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.calculationClasses.GeoPoint
import com.example.trackpro.managerClasses.calculationClasses.LapPoint
import com.example.trackpro.managerClasses.calculationClasses.LocalPlane
import com.example.trackpro.managerClasses.calculationClasses.TrackCarInput
import com.example.trackpro.managerClasses.calculationClasses.TrackCompare
import com.example.trackpro.managerClasses.calculationClasses.TrackLap
import com.example.trackpro.managerClasses.calculationClasses.TrackLapExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A stadium: a 100 m straight, a half-circle, a 200 m straight, another half-circle and the
 * last 100 m home, driven anticlockwise from the middle of the bottom straight. The corner
 * circles are fixed; a car's [radius] moves its straights in or out (60 is the reference line).
 */
class TrackComparisonTest {

    private val plane = LocalPlane(47.0, 17.0)
    private val centreY = 60.0

    private fun lapLength(radius: Double) = 400.0 + 2 * PI * radius

    private fun pathAt(distance: Double, radius: Double): Pair<Double, Double> {
        val arc = PI * radius
        val d = ((distance % lapLength(radius)) + lapLength(radius)) % lapLength(radius)
        return when {
            d < 100.0 -> d to centreY - radius
            d < 100.0 + arc -> {
                val a = -PI / 2 + (d - 100.0) / radius
                (100.0 + radius * cos(a)) to (centreY + radius * sin(a))
            }
            d < 300.0 + arc -> (100.0 - (d - 100.0 - arc)) to centreY + radius
            d < 300.0 + 2 * arc -> {
                val a = PI / 2 + (d - 300.0 - arc) / radius
                (-100.0 + radius * cos(a)) to (centreY + radius * sin(a))
            }
            else -> (-100.0 + (d - 300.0 - 2 * arc)) to centreY - radius
        }
    }

    /** Slows towards the corners over 40 m either side of them, as a real car does. */
    private fun speedAt(distance: Double, radius: Double, straightKmh: Double, cornerKmh: Double): Double {
        val arc = PI * radius
        val d = distance % lapLength(radius)
        fun weight(lo: Double, hi: Double): Double {
            val outside = max(0.0, max(lo - d, d - hi))
            return (1.0 - outside / 40.0).coerceIn(0.0, 1.0)
        }
        val w = max(weight(100.0, 100.0 + arc), weight(300.0 + arc, 300.0 + 2 * arc))
        return (straightKmh - (straightKmh - cornerKmh) * w) / 3.6
    }

    private class Drive(val seconds: Double, val fixes: List<Triple<Double, Double, Double>>)

    /** Positions (x, y) and speed in m/s every 0.1 s from [from] for [length] metres of path. */
    private fun drive(
        radius: Double, straightKmh: Double, cornerKmh: Double,
        from: Double = 0.0, length: Double = lapLength(radius), reverse: Boolean = false
    ): Drive {
        val fixes = ArrayList<Triple<Double, Double, Double>>()
        var s = from
        var t = 0.0
        val total = lapLength(radius)
        while (s < from + length) {
            val position = if (reverse) pathAt(total - s, radius) else pathAt(s, radius)
            val v = speedAt(s, radius, straightKmh, cornerKmh)
            fixes += Triple(position.first, position.second, v)
            s += v * 0.1
            t += 0.1
        }
        return Drive(t, fixes)
    }

    private fun lap(
        id: Long, radius: Double = 60.0, straightKmh: Double = 120.0, cornerKmh: Double = 60.0,
        reverse: Boolean = false
    ): TrackLap {
        val d = drive(radius, straightKmh, cornerKmh, reverse = reverse)
        val points = d.fixes.mapIndexed { i, f ->
            LapPoint(plane.lat(f.second), plane.lon(f.first), f.third * 3.6, i * 0.1)
        }
        return TrackLap(id, Math.round(d.seconds * 1000.0), points)
    }

    private fun car(id: Long, vararg laps: TrackLap) = TrackCarInput(id, "Car $id", laps.toList())

    private fun gate(x: Double, y: Double) = GeoPoint(plane.lat(y), plane.lon(x))

    // ── Laying a lap out ──────────────────────────────────────

    @Test
    fun `a lap is laid out every five metres of its path`() {
        val resampled = TrackCompare.resample(lap(1), plane)!!
        assertEquals(lapLength(60.0), resampled.lengthM, 8.0)
        assertEquals(5.0, resampled.ds, 0.0)
    }

    @Test
    fun `a stadium has two left-hand corners of about a half turn`() {
        val comparison = TrackCompare.compare(listOf(car(1, lap(1))), emptyList(), 3)!!
        assertEquals(2, comparison.corners.size)
        comparison.corners.forEach {
            assertEquals(1, it.direction)
            assertEquals(180.0, it.angleDegrees, 35.0)
        }
    }

    // ── Cars on different lines ───────────────────────────────

    @Test
    fun `a wider and slower line shows in the corner speed, radius and offset`() {
        val fast = lap(1, radius = 60.0, straightKmh = 120.0, cornerKmh = 60.0)
        val wide = lap(2, radius = 70.0, straightKmh = 110.0, cornerKmh = 55.0)
        val comparison = TrackCompare.compare(listOf(car(1, fast), car(2, wide)), emptyList(), 3)!!
        val (a, b) = comparison.cars

        assertEquals(1, a.validLaps)
        assertEquals(1, b.validLaps)
        assertTrue(b.bestLapMs!! > a.bestLapMs!!)

        val cornerA = a.corners.first()
        val cornerB = b.corners.first()
        assertEquals(60.0, cornerA.apexKmh, 3.0)
        assertEquals(55.0, cornerB.apexKmh, 3.0)
        assertEquals(60.0, cornerA.minRadius!!, 5.0)
        assertEquals(70.0, cornerB.minRadius!!, 6.0)
        // The wide line is on the outside of a left-hander: negative, about ten metres.
        assertEquals(0.0, cornerA.insideOffset, 1.0)
        assertEquals(-10.0, cornerB.insideOffset, 3.0)
    }

    @Test
    fun `a lap driven the other way round is skipped, not compared`() {
        val forward = lap(1)
        val backwards = lap(2, reverse = true)
        val comparison = TrackCompare.compare(listOf(car(1, forward), car(2, backwards)), emptyList(), 3)!!
        val other = comparison.cars[1]
        assertEquals(0, other.validLaps)
        assertEquals(1, other.skippedLaps)
        assertNull(other.bestLapMs)
        assertNull(other.best)
    }

    // ── Sectors ───────────────────────────────────────────────

    @Test
    fun `a track without gates is cut into equal sectors that add up to the lap`() {
        val comparison = TrackCompare.compare(listOf(car(1, lap(1))), emptyList(), 3)!!
        assertTrue(comparison.equalSectors)
        assertEquals(4, comparison.sectorEdges.size)
        val result = comparison.cars.single()
        assertEquals(3, result.bestLapSectors.size)
        assertEquals(result.bestLapMs!! / 1000.0, result.bestLapSectors.sum(), 1e-6)
    }

    @Test
    fun `sector gates put the edges where the gates are`() {
        val gates = listOf(gate(100.0, 0.0), gate(100.0, 120.0), gate(-100.0, 120.0), gate(-100.0, 0.0))
        val comparison = TrackCompare.compare(listOf(car(1, lap(1))), gates, 3)!!
        assertFalse(comparison.equalSectors)
        assertEquals(6, comparison.sectorEdges.size)
        // The first gate is 100 m into the lap: 20 samples of 5 m.
        assertEquals(20.0, comparison.sectorEdges[1], 2.0)
        val sectors = comparison.cars.single().bestLapSectors
        assertEquals(5, sectors.size)
        // 100 m straight, then the half-circle: the corner sector is the longest in time.
        assertTrue(sectors[1] > sectors[0])
    }

    @Test
    fun `the best sectors of different laps make a faster theoretical lap`() {
        val gates = listOf(gate(100.0, 0.0), gate(100.0, 120.0), gate(-100.0, 120.0), gate(-100.0, 0.0))
        // One lap is quick on the straights, the other in the corners.
        val straights = lap(1, straightKmh = 120.0, cornerKmh = 50.0)
        val corners = lap(2, straightKmh = 100.0, cornerKmh = 65.0)
        val result = TrackCompare.compare(listOf(car(1, straights, corners)), gates, 3)!!.cars.single()
        assertEquals(2, result.validLaps)
        assertTrue(
            "theoretical ${result.theoreticalSeconds} against best ${result.bestLapMs}",
            result.theoreticalSeconds!! < result.bestLapMs!! / 1000.0 - 0.8
        )
    }

    @Test
    fun `one lap has no theoretical gain`() {
        val result = TrackCompare.compare(listOf(car(1, lap(1))), emptyList(), 3)!!.cars.single()
        assertEquals(result.bestLapMs!! / 1000.0, result.theoreticalSeconds!!, 1e-6)
    }

    @Test
    fun `no laps gives no comparison`() {
        assertNull(TrackCompare.compare(listOf(car(1)), emptyList(), 3))
    }

    // ── Cutting a lap out of the recording ────────────────────

    @Test
    fun `a circuit lap is cut between its own finish-line crossings`() {
        val radius = 60.0
        val timed = lap(1)
        // 600 m of out-lap, a full lap through the line, then a lap more and a few fixes after.
        val d = drive(radius, 120.0, 60.0, from = 600.0, length = lapLength(radius) * 2 + 30.0)
        val base = 1_000_000L
        val points = d.fixes.mapIndexed { i, f ->
            LapInfoData(
                lapid = 1, lat = plane.lat(f.second), lon = plane.lon(f.first), alt = 0.0,
                spd = (f.third * 3.6).toFloat(), latgforce = null, longforce = null,
                timestamp = base + i * 100L
            )
        }
        val finishLine = listOf(
            TrackCoordinatesData(trackId = 1, latitude = plane.lat(-10.0), longitude = plane.lon(0.0), altitude = null),
            TrackCoordinatesData(trackId = 1, latitude = plane.lat(10.0), longitude = plane.lon(0.0), altitude = null)
        )

        val cut = TrackLapExtractor.extract(points, lapId = 1, lapMs = timed.lapMs, finishLine = finishLine, circuit = true)
        assertNotNull(cut)
        assertEquals(0.0, cut!!.points.first().seconds, 0.25)
        assertEquals(timed.lapMs / 1000.0, cut.points.last().seconds, 0.25)
        assertEquals(timed.points.size.toDouble(), cut.points.size.toDouble(), 5.0)
        // And it lies on the same line as the lap driven from the line.
        val resampled = TrackCompare.resample(cut, plane)!!
        assertEquals(lapLength(radius), resampled.lengthM, 12.0)
    }

    @Test
    fun `a circuit lap with no finish line cannot be cut`() {
        val points = (0 until 50).map {
            LapInfoData(lapid = 1, lat = 47.0, lon = 17.0 + it * 1e-5, alt = 0.0, spd = 50f, latgforce = null, longforce = null, timestamp = 1000L + it * 100)
        }
        assertNull(TrackLapExtractor.extract(points, 1, 5000, null, circuit = true))
    }

    private fun straightFixes(count: Int) = (0 until count).map {
        LapInfoData(lapid = 1, lat = 47.0, lon = 17.0 + it * 1e-5, alt = 0.0, spd = 50f, latgforce = null, longforce = null, timestamp = 1000L + it * 100)
    }

    @Test
    fun `a sprint run that fills its row is taken whole`() {
        val run = TrackLapExtractor.extract(straightFixes(50), 1, 4900, null, circuit = false)!!
        assertEquals(50, run.points.size)
        assertEquals(0.0, run.points.first().seconds, 1e-6)
        assertEquals(4.9, run.points.last().seconds, 1e-6)
    }

    @Test
    fun `a sprint run keeps only its own time from a row that holds the way to the start`() {
        // Ten seconds of fixes, of which the run was the last five.
        val run = TrackLapExtractor.extract(straightFixes(100), 1, 5000, null, circuit = false)!!
        assertEquals(51, run.points.size)
        assertEquals(0.0, run.points.first().seconds, 1e-6)
        assertEquals(5.0, run.points.last().seconds, 1e-6)
    }
}
