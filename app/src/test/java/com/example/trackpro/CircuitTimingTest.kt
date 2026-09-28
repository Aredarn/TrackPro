package com.example.trackpro

import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.managerClasses.timeAttackManagers.CircuitTimingManager
import com.example.trackpro.managerClasses.timeAttackManagers.DeltaReference
import com.example.trackpro.managerClasses.timeAttackManagers.ReferenceLap
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry.CrossingDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.max

/**
 * Lap timing against a scripted drive, with both clocks under the test's control.
 *
 * The finish line runs north-south at [LINE_LON]; driving east across it is the recorded
 * direction. A lap drives east across the line, loops round well clear of it, and comes
 * back to cross it heading east again.
 */
class CircuitTimingTest {

    private companion object {
        const val LINE_LON = 19.0005
        const val LAT = 47.0
        const val WALL_ZERO = 1_700_000_000_000L
    }

    private val finishLine = listOf(
        TrackCoordinatesData(id = -1, trackId = 1, latitude = 46.999, longitude = LINE_LON, altitude = 0.0),
        TrackCoordinatesData(id = -2, trackId = 1, latitude = 47.001, longitude = LINE_LON, altitude = 0.0)
    )

    // The monotonic clock the timer runs on, and the wall clock fixes are stamped with.
    private var elapsed = 500_000L
    private var wall = WALL_ZERO
    private lateinit var timing: CircuitTimingManager
    private var prev: RawGPSData? = null

    @Before
    fun setUp() {
        timing = CircuitTimingManager(finishLine, clock = { elapsed }, wallClock = { wall })
    }

    /**
     * A fix received [receivedAt] ms into the drive and handed to the timer
     * [processingDelayMs] after that - the variable wait that used to leak into lap times.
     */
    private fun feed(
        lon: Double,
        receivedAt: Long,
        lat: Double = LAT,
        processingDelayMs: Long = 0,
        speed: Float = 150f
    ) {
        val fix = RawGPSData(
            sessionid = 1L,
            latitude = lat,
            longitude = lon,
            altitude = 0.0,
            timestamp = WALL_ZERO + receivedAt,
            speed = speed,
            fixQuality = 9
        )
        // Fixes are processed in order, so processing time never goes backwards.
        val processedAt = max(wall, WALL_ZERO + receivedAt + processingDelayMs)
        elapsed += processedAt - wall
        wall = processedAt
        timing.handleGpsUpdate(prev, fix)
        prev = fix
    }

    /** Drives in a straight line to (lon, lat), arriving at [arriveAt], one fix every [stepMs]. */
    private fun driveTo(lon: Double, lat: Double, arriveAt: Long, stepMs: Long = 1_000) {
        val from = prev!!
        val start = from.timestamp - WALL_ZERO
        var t = start + stepMs
        while (t < arriveAt) {
            val f = (t - start).toDouble() / (arriveAt - start)
            feed(
                lon = from.longitude + f * (lon - from.longitude),
                lat = from.latitude + f * (lat - from.latitude),
                receivedAt = t
            )
            t += stepMs
        }
        feed(lon = lon, lat = lat, receivedAt = arriveAt)
    }

    /**
     * The loop back round to just west of the line: east, north, west well clear of the gate,
     * south, and back east towards the line - arriving just short of it at [arriveAt].
     */
    private fun lapLoop(arriveAt: Long, stepMs: Long = 1_000) {
        val t0 = prev!!.timestamp - WALL_ZERO
        val leg = (arriveAt - t0) / 5
        driveTo(LINE_LON + 0.01, LAT, t0 + leg, stepMs)
        driveTo(LINE_LON + 0.01, LAT + 0.01, t0 + 2 * leg, stepMs)
        driveTo(LINE_LON - 0.01, LAT + 0.01, t0 + 3 * leg, stepMs)
        driveTo(LINE_LON - 0.01, LAT, t0 + 4 * leg, stepMs)
        driveTo(LINE_LON - 0.0002, LAT, arriveAt, stepMs)
    }

    /** Crosses the line eastward, 30% of the way from a fix at [at] to one 100 ms later. */
    private fun startLap(at: Long, delayBefore: Long = 0, delayAfter: Long = 0) {
        feed(LINE_LON - 0.0003, at, processingDelayMs = delayBefore)
        feed(LINE_LON + 0.0007, at + 100, processingDelayMs = delayAfter)
    }

    // ─────────────────────────────────────────────
    // Crossing interpolation
    // ─────────────────────────────────────────────

    @Test
    fun `a lap is timed between the instants the line was crossed`() {
        startLap(at = 1_000)                        // crossed at 1_030
        lapLoop(arriveAt = 61_000)
        feed(LINE_LON + 0.0008, 61_100)             // 20% of the way: crossed at 61_020

        val lap = timing.completedLaps.value.single()
        assertEquals(59_990L, lap.timeMs)
    }

    @Test
    fun `processing delays do not reach the lap time`() {
        // The regression: laps used to be timed by when the fix after the line was
        // *processed*. Here that wait is 5 ms at the start and 180 ms at the finish, which
        // would have made this lap 175 ms slow.
        startLap(at = 1_000, delayBefore = 40, delayAfter = 5)
        lapLoop(arriveAt = 61_000)
        feed(LINE_LON + 0.0008, 61_100, processingDelayMs = 180)

        assertEquals(59_990L, timing.completedLaps.value.single().timeMs)
    }

    // ─────────────────────────────────────────────
    // GPS gaps
    // ─────────────────────────────────────────────

    @Test
    fun `a lap with fixes a second apart is not flagged`() {
        startLap(at = 1_000)
        lapLoop(arriveAt = 61_000)
        feed(LINE_LON + 0.0008, 61_100)

        assertFalse(timing.completedLaps.value.single().signalGap)
    }

    @Test
    fun `a lap with a hole in its fixes is flagged`() {
        startLap(at = 1_000)
        lapLoop(arriveAt = 61_000, stepMs = 3_000)  // three seconds between fixes
        feed(LINE_LON + 0.0008, 61_100)

        assertTrue(timing.completedLaps.value.single().signalGap)
    }

    // ─────────────────────────────────────────────
    // Delta reference
    // ─────────────────────────────────────────────

    /** A slow reference: 5 km in two minutes, much slower than the test lap. */
    private val slowTrackBest = ReferenceLap(
        lapMs = 120_000,
        trace = listOf(0.0 to 0L, 5_000.0 to 120_000L)
    )

    @Test
    fun `with session best preferred there is no delta on the first lap`() {
        timing.preferredReference = DeltaReference.SESSION_BEST
        timing.setTrackBests(mapOf(CrossingDirection.ENTERING to slowTrackBest))

        startLap(at = 1_000)
        driveTo(LINE_LON + 0.01, LAT, 10_000)

        assertNull(timing.liveDelta.value)
        assertNull(timing.activeReference.value)
    }

    @Test
    fun `with track best preferred the delta runs from the first lap`() {
        timing.preferredReference = DeltaReference.TRACK_BEST
        timing.setTrackBests(mapOf(CrossingDirection.ENTERING to slowTrackBest))

        startLap(at = 1_000)
        driveTo(LINE_LON + 0.01, LAT, 10_000)

        assertEquals(DeltaReference.TRACK_BEST, timing.activeReference.value)
        assertNotNull(timing.liveDelta.value)
        assertTrue("faster than the reference, so ahead", timing.liveDelta.value!! < 0.0)
    }

    @Test
    fun `a track best driven the other way is not used`() {
        // Reverse-direction laps cover the circuit in the opposite order; measuring against
        // one would be nonsense. Only the session's own direction counts.
        timing.preferredReference = DeltaReference.TRACK_BEST
        timing.setTrackBests(mapOf(CrossingDirection.EXITING to slowTrackBest))

        startLap(at = 1_000)
        driveTo(LINE_LON + 0.01, LAT, 10_000)

        assertNull("nothing to compare against on lap 1", timing.liveDelta.value)
    }

    @Test
    fun `beating the track best makes the new lap the reference`() {
        timing.preferredReference = DeltaReference.TRACK_BEST
        timing.setTrackBests(mapOf(CrossingDirection.ENTERING to slowTrackBest))

        startLap(at = 1_000)
        lapLoop(arriveAt = 61_000)
        feed(LINE_LON + 0.0008, 61_100)             // 59.99 s - faster than 120 s

        // Same pace again on lap 2: against the new best it is level, not two seconds a lap up.
        driveTo(LINE_LON + 0.01, LAT, 61_100 + 12_000)
        assertEquals(DeltaReference.TRACK_BEST, timing.activeReference.value)
        assertEquals(0.0, timing.liveDelta.value!!, 0.5)
    }

    @Test
    fun `switching preference mid-lap takes effect on the next fix`() {
        timing.preferredReference = DeltaReference.SESSION_BEST
        timing.setTrackBests(mapOf(CrossingDirection.ENTERING to slowTrackBest))
        startLap(at = 1_000)
        driveTo(LINE_LON + 0.005, LAT, 5_000)
        assertNull(timing.liveDelta.value)

        timing.preferredReference = DeltaReference.TRACK_BEST
        driveTo(LINE_LON + 0.01, LAT, 10_000)

        assertEquals(DeltaReference.TRACK_BEST, timing.activeReference.value)
        assertNotNull(timing.liveDelta.value)
    }
}
