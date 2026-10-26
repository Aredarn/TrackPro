package com.example.trackpro

import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.timeAttackManagers.SprintTimingManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Sprint timing against a scripted drive. Start and finish gates run north-south; driving east
 * across either is the recorded direction.
 *
 * Real sprints begin where the driver stood when the track was built, so most runs never drive
 * *across* the start gate - they set off from it. Those cases are what this guards.
 */
class SprintTimingTest {

    private companion object {
        const val START_LON = 19.0000
        const val FINISH_LON = 19.0100
        const val LAT = 47.0
        const val WALL_ZERO = 1_700_000_000_000L
    }

    private fun gate(lon: Double, idBase: Long) = listOf(
        TrackCoordinatesData(id = idBase, trackId = 1, latitude = 46.9999, longitude = lon, altitude = 0.0),
        TrackCoordinatesData(id = idBase - 1, trackId = 1, latitude = 47.0001, longitude = lon, altitude = 0.0)
    )

    private var elapsed = 500_000L
    private var wall = WALL_ZERO
    private lateinit var timing: SprintTimingManager
    private var prev: RawGPSData? = null

    @Before
    fun setUp() {
        timing = SprintTimingManager(
            gate(START_LON, -10), gate(FINISH_LON, -20),
            clock = { elapsed }, wallClock = { wall }
        )
    }

    private fun feed(lon: Double, at: Long) {
        val fix = RawGPSData(
            sessionid = 1, latitude = LAT, longitude = lon, altitude = 0.0,
            timestamp = WALL_ZERO + at, speed = 0f, fixQuality = 1
        )
        elapsed = 500_000L + at
        wall = WALL_ZERO + at
        timing.handleGpsUpdate(prev, fix)
        prev = fix
    }

    /** Sits still just past the start gate for [forMs], then drives east at ~38 m/s. */
    private fun standAndGo(forMs: Long, until: Long): Long {
        var t = 0L
        while (t <= forMs) { feed(START_LON + 0.00002, t); t += 500 }
        // Last moment at rest is the previous step.
        val lastStill = t - 500
        var k = 1
        while (true) {
            val at = lastStill + 1000L * k
            if (at > until) break
            feed(START_LON + 0.00002 + 0.0005 * k, at)
            k++
        }
        return lastStill
    }

    @Test
    fun `standing start off the gate begins timing when the car moves off`() {
        val lastStill = standAndGo(forMs = 3_000, until = 12_000)

        assertTrue(timing.currentTime.value != "00:00.00")
        // Not finished yet
        assertEquals(0, timing.eventCount.value)
        assertTrue(lastStill == 3_000L)
    }

    @Test
    fun `standing start runs through to a finish-line crossing`() {
        val lastStill = standAndGo(forMs = 3_000, until = 30_000)

        assertEquals(1, timing.eventCount.value)
        val run = timing.completedLaps.value.single()
        // Finish gate crossed between the fixes either side of 19.0100.
        val crossedAtMs = lastStill + ((FINISH_LON - (START_LON + 0.00002)) / 0.0005 * 1000).toLong()
        assertEquals((crossedAtMs - lastStill).toDouble(), run.timeMs.toDouble(), 5.0)
    }

    @Test
    fun `parking short of the finish gate still ends the run`() {
        val lastStill = standAndGo(forMs = 3_000, until = 18_000)
        assertEquals(0, timing.eventCount.value)

        // Stops about 8 m before the finish gate and stays there.
        val parkLon = FINISH_LON - 0.0001
        val parkAt = 20_000L
        var t = parkAt
        while (t <= parkAt + 3_000) { feed(parkLon, t); t += 500 }

        assertEquals(1, timing.eventCount.value)
        assertEquals(parkAt - lastStill, timing.completedLaps.value.single().timeMs)
    }

    @Test
    fun `a car merely idling at the start for a moment is not timed`() {
        // 0.5 s of stillness is below the rest threshold, so moving off is not a launch.
        feed(START_LON + 0.00002, 0)
        feed(START_LON + 0.00002, 500)
        feed(START_LON + 0.00052, 1_500)
        feed(START_LON + 0.00102, 2_500)

        assertEquals("00:00.00", timing.currentTime.value)
    }

    @Test
    fun `rolling start through a route gate when the first point is off the road`() {
        // Extra start gate 30 m past the (unreachable) main gate, as built for off-road first points.
        val extra = gate(START_LON + 0.0004, -30)
        val t = SprintTimingManager(
            gate(START_LON - 0.01, -10), gate(FINISH_LON, -20),
            clock = { elapsed }, wallClock = { wall },
            extraStartLines = listOf(extra)
        )
        prev = null
        timing = t
        feed(START_LON + 0.0001, 0)
        feed(START_LON + 0.0008, 1_000)
        feed(START_LON + 0.0016, 2_000)

        assertTrue(timing.currentTime.value != "00:00.00")
    }

    @Test
    fun `a reverse run is timed from the finish gate back to the start gate`() {
        // West across the finish gate, then west across the start gate.
        feed(FINISH_LON + 0.0004, 0)
        feed(FINISH_LON - 0.0004, 1_000)
        assertTrue(timing.currentTime.value != "00:00.00")
        assertEquals(0, timing.eventCount.value)

        var t = 2_000L
        var lon = FINISH_LON - 0.0004
        while (lon > START_LON - 0.0004) {
            lon -= 0.0005
            feed(lon, t)
            t += 1_000
        }

        assertEquals(1, timing.eventCount.value)
        val ms = timing.completedLaps.value.single().timeMs
        // About (0.0100 deg at 0.0005 deg/s) = 20 s.
        assertEquals(20_000.0, ms.toDouble(), 1_500.0)
    }

    @Test
    fun `reverse and forward runs keep separate bests`() {
        // Forward run, 20 s.
        feed(START_LON - 0.0004, 0)
        var t = 1_000L
        var lon = START_LON - 0.0004
        while (lon < FINISH_LON + 0.0004) { lon += 0.0005; feed(lon, t); t += 1_000 }
        assertEquals(1, timing.eventCount.value)

        // Then straight back, slower: must not be compared against the forward time.
        t += 10_000
        while (lon > START_LON - 0.0004) { lon -= 0.00025; feed(lon, t); t += 1_000 }
        assertEquals(2, timing.eventCount.value)
        assertEquals(0.0, timing.delta.value, 0.0)
    }

    @Test
    fun `rolling back over the finish gate right after finishing does not start a reverse run`() {
        feed(START_LON - 0.0004, 0)
        feed(START_LON + 0.0004, 1_000)
        feed(FINISH_LON - 0.0004, 20_000)
        feed(FINISH_LON + 0.0004, 21_000)   // finish
        assertEquals(1, timing.eventCount.value)
        feed(FINISH_LON - 0.0001, 22_000)   // rolls back over the gate
        // If that had started a reverse run, driving west over the start gate would close it.
        feed(START_LON + 0.0004, 40_000)
        feed(START_LON - 0.0004, 41_000)
        assertEquals(1, timing.eventCount.value)
    }

    @Test
    fun `driving across the start gate still starts timing`() {
        feed(START_LON - 0.0004, 0)
        feed(START_LON + 0.0004, 1_000)
        feed(START_LON + 0.0012, 2_000)

        assertTrue(timing.currentTime.value != "00:00.00")
    }
}
