package com.example.trackpro

import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.calculationClasses.DragMetrics
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class DragTimeCalculationTest {

    private lateinit var mockDatabase: ESPDatabase
    private lateinit var calc: DragTimeCalculation

    // Base timestamp to build test sequences from
    private val t0 = 1_000_000L

    @Before
    fun setUp() {
        mockDatabase = mock(ESPDatabase::class.java)
        calc = DragTimeCalculation(session = 1L, database = mockDatabase)
    }

    // ─────────────────────────────────────────────
    // Helper builders
    // ─────────────────────────────────────────────

    /** Build a RawGPSData stub at a fixed coordinate (Hungaroring pit straight). */
    private fun gps(speed: Float, lat: Double = 47.5789, lon: Double = 19.2486) =
        RawGPSData(
            id = 0,
            sessionid = 1L,
            timestamp = 0L,
            latitude = lat,
            longitude = lon,
            speed = speed,
            altitude = 0.0,
            fixQuality = 0
        )

    /** Feed a speed sequence sampled at `intervalMs` intervals starting from t0. */
    private fun feedSequence(
        speeds: List<Float>,
        intervalMs: Long = 100L
    ): DragMetrics {
        var lastMetrics = DragMetrics()
        speeds.forEachIndexed { i, speed ->
            lastMetrics = calc.processRealtimeGPS(
                gps(speed),
                t0 + i * intervalMs
            )
        }
        return lastMetrics
    }

    // ─────────────────────────────────────────────
    // 1. Initial state
    // ─────────────────────────────────────────────

    @Test
    fun `initial metrics are all null or zero`() {
        val m = calc.getCurrentMetrics()
        assertNull(m.time0to60)
        assertNull(m.time0to100)
        assertNull(m.time0to160)
        assertNull(m.time0to200)
        assertNull(m.time50to150)
        assertNull(m.time100to200)
        assertNull(m.quarterMileTime)
        assertNull(m.quarterMileSpeed)
        assertNull(m.halfMileTime)
        assertEquals(0f, m.maxSpeed, 0f)
        assertEquals(0f, m.totalDistance, 0f)
    }

    // ─────────────────────────────────────────────
    // 2. Standing-start run trigger
    // ─────────────────────────────────────────────

    @Test
    fun `run does not start until speed crosses zero threshold`() {
        // Feed only moving data without a standing start → no 0-60 recorded
        val speeds = List(20) { 30f + it * 3f }   // 30, 33, 36 … already moving
        feedSequence(speeds)
        assertNull(calc.getCurrentMetrics().time0to60)
    }

    @Test
    fun `run starts after vehicle is stationary then accelerates`() {
        // 0 km/h for 3 samples, then ramp up
        val speeds = listOf(0f, 0f, 0f) + (1..70).map { it.toFloat() }
        feedSequence(speeds)
        assertNotNull(calc.getCurrentMetrics().time0to60)
    }

    // ─────────────────────────────────────────────
    // 3. 0-60 / 0-100 timing accuracy
    // ─────────────────────────────────────────────

    @Test
    fun `0-60 time is recorded when speed first reaches 60`() {
        // Stand still → then 10 km/h per 100 ms step
        // Step 0: 0 km/h (t0)
        // Steps 1–6: 10, 20, 30, 40, 50, 60  → run starts at step 1 (t0+100 ms)
        //                                       60 reached at step 6 (t0+600 ms) → elapsed = 0.5 s
        val speeds = listOf(0f, 10f, 20f, 30f, 40f, 50f, 60f)
        feedSequence(speeds, intervalMs = 100L)

        val m = calc.getCurrentMetrics()
        assertNotNull(m.time0to60)
        assertEquals(0.5, m.time0to60!!, 0.01)   // 5 * 100 ms = 500 ms = 0.5 s
    }

    @Test
    fun `0-100 is null while max speed is below 100`() {
        val speeds = listOf(0f, 20f, 40f, 60f, 80f)
        feedSequence(speeds)
        assertNull(calc.getCurrentMetrics().time0to100)
    }

    @Test
    fun `0-100 time is recorded correctly`() {
        // Stand still → 10 km/h per 200 ms step
        // Run start at step 1 (t0+200); 100 km/h at step 10 (t0+2000) → elapsed = 1.8 s
        val speeds = listOf(0f) + (1..10).map { it * 10f }
        feedSequence(speeds, intervalMs = 200L)

        val m = calc.getCurrentMetrics()
        assertNotNull(m.time0to100)
        assertEquals(1.8, m.time0to100!!, 0.01)
    }

    @Test
    fun `0-60 result is not overwritten by subsequent higher speeds`() {
        val speeds = listOf(0f) + (1..100).map { it.toFloat() }
        feedSequence(speeds, intervalMs = 100L)

        val firstCapture = calc.getCurrentMetrics().time0to60
        // Feed more data
        calc.processRealtimeGPS(gps(150f), t0 + 200_000L)

        assertEquals(firstCapture, calc.getCurrentMetrics().time0to60)
    }

    // ─────────────────────────────────────────────
    // 4. 50-150 rolling metric
    // ─────────────────────────────────────────────

    @Test
    fun `50-150 is recorded when speed goes through 50 then 150`() {
        // 50 km/h at step 0 (t0), 150 km/h at step 10 (t0 + 1000 ms) → 1.0 s
        val speeds = listOf(50f) + (1..9).map { 50f + it * 10f } + listOf(150f)
        feedSequence(speeds, intervalMs = 100L)

        val m = calc.getCurrentMetrics()
        assertNotNull(m.time50to150)
        assertEquals(1.0, m.time50to150!!, 0.02)
    }

    @Test
    fun `50-150 timer resets when speed drops below 45 before 150`() {
        // Rise to 60, drop below 45, rise again through 50 to 150
        val speedsPhase1 = listOf(50f, 60f, 40f)          // drops to 40 → reset
        val speedsPhase2 = listOf(50f, 100f, 150f)        // new attempt
        val speeds = speedsPhase1 + speedsPhase2

        var time = t0
        speeds.forEachIndexed { i, speed ->
            calc.processRealtimeGPS(gps(speed), time)
            time += 1000L
        }

        // The result should be measured from the SECOND time 50 was hit
        assertNotNull(calc.getCurrentMetrics().time50to150)
        // Phase 2: 50 at index 3 (t0+3s), 150 at index 5 (t0+5s) → 2 s
        assertEquals(2.0, calc.getCurrentMetrics().time50to150!!, 0.05)
    }

    @Test
    fun `50-150 is null when speed never reaches 150`() {
        val speeds = listOf(0f, 50f, 80f, 120f)
        feedSequence(speeds)
        assertNull(calc.getCurrentMetrics().time50to150)
    }

    // ─────────────────────────────────────────────
    // 5. 100-200 rolling metric
    // ─────────────────────────────────────────────

    @Test
    fun `100-200 is recorded correctly`() {
        // 100 km/h at t0, 200 km/h at t0 + 5000 ms
        calc.processRealtimeGPS(gps(100f), t0)
        calc.processRealtimeGPS(gps(150f), t0 + 2500L)
        calc.processRealtimeGPS(gps(200f), t0 + 5000L)

        val m = calc.getCurrentMetrics()
        assertNotNull(m.time100to200)
        assertEquals(5.0, m.time100to200!!, 0.01)
    }

    @Test
    fun `100-200 timer resets if speed drops below 95 before 200`() {
        calc.processRealtimeGPS(gps(105f), t0)
        calc.processRealtimeGPS(gps(90f), t0 + 1000L)   // drops below 95 → reset
        calc.processRealtimeGPS(gps(100f), t0 + 2000L)  // new start
        calc.processRealtimeGPS(gps(200f), t0 + 4000L)  // 200 km/h reached

        val m = calc.getCurrentMetrics()
        assertNotNull(m.time100to200)
        assertEquals(2.0, m.time100to200!!, 0.01)
    }

    // ─────────────────────────────────────────────
    // 6. Max speed tracking
    // ─────────────────────────────────────────────

    @Test
    fun `maxSpeed tracks the highest speed seen`() {
        calc.processRealtimeGPS(gps(50f), t0)
        calc.processRealtimeGPS(gps(180f), t0 + 1000L)
        calc.processRealtimeGPS(gps(120f), t0 + 2000L)

        assertEquals(180f, calc.getCurrentMetrics().maxSpeed, 0f)
    }

    @Test
    fun `maxSpeed stays zero when no GPS is processed`() {
        assertEquals(0f, calc.getCurrentMetrics().maxSpeed, 0f)
    }

    // ─────────────────────────────────────────────
    // 7. Distance accumulation
    // ─────────────────────────────────────────────

    @Test
    fun `totalDistance is zero for a single GPS point`() {
        calc.processRealtimeGPS(gps(0f, lat = 47.0, lon = 19.0), t0)
        assertEquals(0f, calc.getCurrentMetrics().totalDistance, 0f)
    }

    @Test
    fun `totalDistance increases with each subsequent point`() {
        calc.processRealtimeGPS(gps(0f, lat = 47.0000, lon = 19.0000), t0)
        calc.processRealtimeGPS(gps(50f, lat = 47.0010, lon = 19.0000), t0 + 1000L)
        calc.processRealtimeGPS(gps(80f, lat = 47.0020, lon = 19.0000), t0 + 2000L)

        assertTrue(calc.getCurrentMetrics().totalDistance > 0f)
    }

    // ─────────────────────────────────────────────
    // 8. Quarter-mile
    // ─────────────────────────────────────────────

    @Test
    fun `quarterMileTime is null when distance is below 402m`() {
        // Feed a few close-together points: no 402 m covered
        calc.processRealtimeGPS(gps(0f, 47.0, 19.0), t0)
        calc.processRealtimeGPS(gps(60f, 47.0001, 19.0), t0 + 1000L)
        assertNull(calc.getCurrentMetrics().quarterMileTime)
    }

    // ─────────────────────────────────────────────
    // 9. Reset
    // ─────────────────────────────────────────────

    @Test
    fun `resetRealtimeTracking clears all state`() {
        // Prime with some data
        feedSequence(listOf(0f, 50f, 100f, 150f, 200f), intervalMs = 1000L)
        calc.resetRealtimeTracking()

        val m = calc.getCurrentMetrics()
        assertNull(m.time0to60)
        assertNull(m.time0to100)
        assertNull(m.time50to150)
        assertNull(m.time100to200)
        assertEquals(0f, m.maxSpeed, 0f)
        assertEquals(0f, m.totalDistance, 0f)
    }

    @Test
    fun `after reset a new standing-start run can be recorded`() {
        feedSequence(listOf(0f, 50f, 100f), intervalMs = 1000L)
        calc.resetRealtimeTracking()

        val speeds = listOf(0f) + (1..7).map { it * 10f }
        feedSequence(speeds, intervalMs = 500L)

        assertNotNull(calc.getCurrentMetrics().time0to60)
    }

    // ─────────────────────────────────────────────
    // 10. calculateFullSessionMetrics
    // ─────────────────────────────────────────────

    @Test
    fun `calculateFullSessionMetrics resets state and processes sorted data`() {
        // Put some stale state in first
        feedSequence(listOf(0f, 200f), intervalMs = 1000L)

        val sessionData = buildList {
            add(gps(0f).copy(timestamp = t0))
            (1..10).forEach { i ->
                add(gps(i * 10f).copy(timestamp = t0 + i * 1000L))
            }
        }

        val result = calc.calculateFullSessionMetrics(sessionData)
        // 0-60: hit at step 6 (t0+6000 ms), run start at step 1 (t0+1000 ms) → 5 s
        assertNotNull(result.time0to60)
        assertEquals(5.0, result.time0to60!!, 0.1)
    }

    @Test
    fun `calculateFullSessionMetrics handles unsorted input by sorting on timestamp`() {
        val data = listOf(
            gps(60f).copy(timestamp = t0 + 6000L),
            gps(0f).copy(timestamp = t0),
            gps(30f).copy(timestamp = t0 + 3000L)
        )
        // Should not throw; 0-60 must be captured from sorted processing
        val result = calc.calculateFullSessionMetrics(data)
        assertNotNull(result.time0to60)
    }

    // ─────────────────────────────────────────────
    // 11. totalDistance (standalone helper)
    // ─────────────────────────────────────────────

    @Test
    fun `totalDistance returns zero for empty list`() {
        assertEquals(0.0, calc.totalDistance(emptyList()), 0.0)
    }

    @Test
    fun `totalDistance returns zero for single point`() {
        assertEquals(0.0, calc.totalDistance(listOf(LatLonOffset(47.0, 19.0))), 0.0)
    }

    @Test
    fun `totalDistance returns positive value for two different points`() {
        val points = listOf(
            LatLonOffset(47.0000, 19.0000),
            LatLonOffset(47.0010, 19.0000)
        )
        val dist = calc.totalDistance(points)
        assertTrue("Expected distance > 0 but was $dist", dist > 0.0)
    }

    @Test
    fun `totalDistance is approximately 111m per 0_001 degree latitude`() {
        // Roughly 0.001° latitude ≈ 111 m
        val points = listOf(
            LatLonOffset(47.0000, 19.0000),
            LatLonOffset(47.0010, 19.0000)
        )
        val dist = calc.totalDistance(points)
        assertEquals(111.0, dist, 5.0)   // ±5 m tolerance
    }

    @Test
    fun `totalDistance accumulates over multiple segments`() {
        val twoSeg = listOf(
            LatLonOffset(47.0000, 19.0000),
            LatLonOffset(47.0010, 19.0000),
            LatLonOffset(47.0020, 19.0000)
        )
        val oneSeg = listOf(
            LatLonOffset(47.0000, 19.0000),
            LatLonOffset(47.0010, 19.0000)
        )
        assertTrue(calc.totalDistance(twoSeg) > calc.totalDistance(oneSeg))
    }
}