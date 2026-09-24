package com.example.trackpro

import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.calculationClasses.DragMetrics
import com.example.trackpro.managerClasses.calculationClasses.DragSpeedScale
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
        calc = DragTimeCalculation(
            session = 1L,
            database = mockDatabase,
            scale = DragSpeedScale.Metric
        )
    }

    /** The seconds recorded for a split, by the label its scale gives it. */
    private fun DragMetrics.split(label: String): Double? =
        (standing + rolling).firstOrNull { it.label == label }?.seconds

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
        assertEquals(
            listOf("0-60", "0-100", "0-160", "0-200"),
            m.standing.map { it.label }
        )
        assertEquals(listOf("50-150", "100-200"), m.rolling.map { it.label })
        assertTrue((m.standing + m.rolling).all { it.seconds == null })
        assertNull(m.quarterMileTime)
        assertNull(m.quarterMileSpeed)
        assertNull(m.halfMileTime)
        assertEquals(0f, m.maxSpeed, 0f)
        assertEquals(0f, m.totalDistance, 0f)
        assertEquals(0, m.runCount)
    }

    // ─────────────────────────────────────────────
    // 2. Standing-start run trigger
    // ─────────────────────────────────────────────

    @Test
    fun `run does not start until speed crosses zero threshold`() {
        // Feed only moving data without a standing start → no 0-60 recorded
        val speeds = List(20) { 30f + it * 3f }   // 30, 33, 36 … already moving
        feedSequence(speeds)
        assertNull(calc.getCurrentMetrics().split("0-60"))
    }

    @Test
    fun `run starts after vehicle is stationary then accelerates`() {
        // 0 km/h for 3 samples, then ramp up
        val speeds = listOf(0f, 0f, 0f) + (1..70).map { it.toFloat() }
        feedSequence(speeds)
        assertNotNull(calc.getCurrentMetrics().split("0-60"))
    }

    // ─────────────────────────────────────────────
    // 3. 0-60 / 0-100 timing accuracy
    // ─────────────────────────────────────────────

    @Test
    fun `0-60 time is recorded when speed first reaches 60`() {
        // Stand still → then 10 km/h per 100 ms step
        // Step 0: 0 km/h (t0)
        // Steps 1–6: 10, 20, 30, 40, 50, 60
        // The launch is when speed passed the 2 km/h stopped threshold, interpolated between
        // steps 0 and 1: t0+20 ms. 60 is reached exactly on step 6: t0+600 ms. → 0.58 s
        val speeds = listOf(0f, 10f, 20f, 30f, 40f, 50f, 60f)
        feedSequence(speeds, intervalMs = 100L)

        val m = calc.getCurrentMetrics()
        assertNotNull(m.split("0-60"))
        assertEquals(0.58, m.split("0-60")!!, 0.001)
    }

    @Test
    fun `0-100 is null while max speed is below 100`() {
        val speeds = listOf(0f, 20f, 40f, 60f, 80f)
        feedSequence(speeds)
        assertNull(calc.getCurrentMetrics().split("0-100"))
    }

    @Test
    fun `0-100 time is recorded correctly`() {
        // Stand still → 10 km/h per 200 ms step
        // Launch at 2 km/h, interpolated: t0+40 ms. 100 km/h exactly at step 10: t0+2000. → 1.96 s
        val speeds = listOf(0f) + (1..10).map { it * 10f }
        feedSequence(speeds, intervalMs = 200L)

        val m = calc.getCurrentMetrics()
        assertNotNull(m.split("0-100"))
        assertEquals(1.96, m.split("0-100")!!, 0.001)
    }

    @Test
    fun `0-60 result is not overwritten by subsequent higher speeds`() {
        val speeds = listOf(0f) + (1..100).map { it.toFloat() }
        feedSequence(speeds, intervalMs = 100L)

        val firstCapture = calc.getCurrentMetrics().split("0-60")
        // Feed more data
        calc.processRealtimeGPS(gps(150f), t0 + 200_000L)

        assertEquals(firstCapture, calc.getCurrentMetrics().split("0-60"))
    }

    // ─────────────────────────────────────────────
    // 3a. Interpolation between samples
    // ─────────────────────────────────────────────

    @Test
    fun `a split is timed where the threshold was crossed, not at the next sample`() {
        // 0 -> 30 -> 90 km/h at one-second samples. Snapped to samples: launch at 1 s, 60 at
        // 2 s, so 1.0 s. Interpolated: 2 km/h passed at 66.7 ms, 60 at 1500 ms.
        listOf(0f to 0L, 30f to 1000L, 90f to 2000L)
            .forEach { (speed, offset) -> calc.processRealtimeGPS(gps(speed), t0 + offset) }

        assertEquals(1.4333, calc.getCurrentMetrics().split("0-60")!!, 0.001)
    }

    @Test
    fun `quarter mile and trap speed are taken at the line, not the sample after it`() {
        // Standstill, then two ~222 m jumps north: the 402 m line falls 81% of the way through
        // the second jump, between 100 and 120 km/h.
        calc.processRealtimeGPS(gps(0f, lat = 47.000, lon = 19.0), t0)
        calc.processRealtimeGPS(gps(100f, lat = 47.000, lon = 19.0), t0 + 1000L)
        calc.processRealtimeGPS(gps(100f, lat = 47.002, lon = 19.0), t0 + 2000L)
        calc.processRealtimeGPS(gps(120f, lat = 47.004, lon = 19.0), t0 + 3000L)

        val m = calc.getCurrentMetrics()
        assertEquals(2.789, m.quarterMileTime!!, 0.001)
        assertEquals(116.18f, m.quarterMileSpeed!!, 0.01f)
    }

    // ─────────────────────────────────────────────
    // 3b. Multiple runs in one session
    // ─────────────────────────────────────────────

    /** Stop, then launch at 10 km/h per step until [topSpeed]. */
    private fun launch(topSpeed: Float): List<Float> =
        listOf(0f, 0f) + generateSequence(10f) { it + 10f }.takeWhile { it <= topSpeed }.toList()

    @Test
    fun `a second run in the same session is timed`() {
        // The regression: hasStartedRun latched on the first launch, so every later run in
        // a session was ignored entirely.
        feedSequence(launch(60f), intervalMs = 1000L)     // slow first run
        val afterFirst = calc.getCurrentMetrics()
        assertNotNull(afterFirst.split("0-60"))
        assertEquals(1, afterFirst.runCount)

        feedSequence(launch(60f), intervalMs = 100L)      // quicker second run
        val afterSecond = calc.getCurrentMetrics()

        assertEquals(2, afterSecond.runCount)
        assertTrue(
            "second run should have improved 0-60",
            afterSecond.split("0-60")!! < afterFirst.split("0-60")!!
        )
    }

    @Test
    fun `a slower later run does not replace a quicker one`() {
        feedSequence(launch(60f), intervalMs = 100L)      // quick first run
        val quick = calc.getCurrentMetrics().split("0-60")!!

        feedSequence(launch(60f), intervalMs = 1000L)     // slow second run

        assertEquals(quick, calc.getCurrentMetrics().split("0-60")!!, 0.001)
    }

    @Test
    fun `a rollout does not own the session`() {
        // A crawl out of the paddock that reaches 60 slowly used to take the 0-60 slot for
        // the whole session with no way to redo it.
        feedSequence(launch(60f), intervalMs = 2000L)
        val rollout = calc.getCurrentMetrics().split("0-60")!!

        feedSequence(launch(60f), intervalMs = 100L)

        assertTrue(calc.getCurrentMetrics().split("0-60")!! < rollout)
    }

    @Test
    fun `each run is timed from its own launch, not from the session start`() {
        // Run 1 at 1 s per 10 km/h, a long pause, then run 2 at 100 ms per 10 km/h.
        feedSequence(launch(60f), intervalMs = 1000L)
        calc.processRealtimeGPS(gps(0f), t0 + 600_000L)   // parked for ten minutes

        var time = t0 + 700_000L
        listOf(0f, 10f, 20f, 30f, 40f, 50f, 60f).forEach { speed ->
            calc.processRealtimeGPS(gps(speed), time)
            time += 100L
        }

        // 0.58 s (see the single-run test for why not 0.5), not ten minutes and change.
        assertEquals(0.58, calc.getCurrentMetrics().split("0-60")!!, 0.001)
    }

    @Test
    fun `runCount ignores GPS noise around a standstill`() {
        // Parked, with speed flickering either side of the 2 km/h stopped threshold.
        listOf(0f, 3f, 0f, 4f, 1f, 3f, 0f).forEachIndexed { i, speed ->
            calc.processRealtimeGPS(gps(speed), t0 + i * 1000L)
        }

        assertEquals(0, calc.getCurrentMetrics().runCount)
    }

    // ─────────────────────────────────────────────
    // 3c. Imperial scale
    // ─────────────────────────────────────────────

    @Test
    fun `imperial splits are labelled for the milestones they measure`() {
        val imperial = DragTimeCalculation(1L, mockDatabase, DragSpeedScale.Imperial)

        val m = imperial.getCurrentMetrics()

        assertEquals(listOf("0-30", "0-60", "0-100", "0-120"), m.standing.map { it.label })
        assertEquals(listOf("40-100", "60-130"), m.rolling.map { it.label })
    }

    @Test
    fun `imperial 0-60 means 60 mph, not 60 kmh`() {
        // The bug: thresholds were hardcoded km/h while the tile said "0-60", so an imperial
        // driver was shown 0-37 mph as their 0-60. 60 mph is 96.6 km/h.
        val imperial = DragTimeCalculation(1L, mockDatabase, DragSpeedScale.Imperial)

        // Stand still, then hold 90 km/h (56 mph) - past 60 km/h, short of 60 mph.
        listOf(0f, 90f, 90f, 90f).forEachIndexed { i, speed ->
            imperial.processRealtimeGPS(gps(speed), t0 + i * 1000L)
        }
        assertNull("56 mph must not satisfy 0-60 mph", imperial.getCurrentMetrics().split("0-60"))

        // 97 km/h is just over 60 mph.
        imperial.processRealtimeGPS(gps(97f), t0 + 4000L)
        assertNotNull(imperial.getCurrentMetrics().split("0-60"))
    }

    @Test
    fun `metric 0-60 still means 60 kmh`() {
        feedSequence(listOf(0f, 30f, 61f), intervalMs = 1000L)

        assertNotNull(calc.getCurrentMetrics().split("0-60"))
    }

    @Test
    fun `imperial rolling interval uses mph bounds`() {
        // 60-130 mph is 96.6-209.2 km/h.
        val imperial = DragTimeCalculation(1L, mockDatabase, DragSpeedScale.Imperial)

        listOf(0f to 0L, 97f to 1000L, 200f to 3000L)
            .forEach { (speed, offset) -> imperial.processRealtimeGPS(gps(speed), t0 + offset) }
        assertNull("200 km/h is short of 130 mph", imperial.getCurrentMetrics().split("60-130"))

        imperial.processRealtimeGPS(gps(210f), t0 + 4000L)
        // Both ends interpolated: 60 mph passed 995.5 ms in (0 -> 97 km/h over a second),
        // 130 mph passed 3921.5 ms in (200 -> 210 km/h). Snapped to samples this was 3.0.
        assertEquals(2.926, imperial.getCurrentMetrics().split("60-130")!!, 0.001)
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
        assertNotNull(m.split("50-150"))
        assertEquals(1.0, m.split("50-150")!!, 0.02)
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
        assertNotNull(calc.getCurrentMetrics().split("50-150"))
        // Phase 2: 50 at index 3 (t0+3s), 150 at index 5 (t0+5s) → 2 s
        assertEquals(2.0, calc.getCurrentMetrics().split("50-150")!!, 0.05)
    }

    @Test
    fun `50-150 is measured on every pull and keeps the quickest`() {
        // First pull: 50 at t0, 150 at t0+4s. Drop back to 40, then a 2 s pull.
        listOf(50f to 0L, 100f to 2000L, 150f to 4000L, 40f to 6000L,
               50f to 7000L, 100f to 8000L, 150f to 9000L)
            .forEach { (speed, offset) -> calc.processRealtimeGPS(gps(speed), t0 + offset) }

        assertEquals(2.0, calc.getCurrentMetrics().split("50-150")!!, 0.05)
    }

    @Test
    fun `50-150 does not re-open at speed after being recorded`() {
        // Without requiring a drop back below the reset speed, clearing the timer at 150
        // immediately re-started it from there and recorded an instant second interval.
        listOf(50f to 0L, 150f to 4000L, 160f to 5000L, 170f to 6000L)
            .forEach { (speed, offset) -> calc.processRealtimeGPS(gps(speed), t0 + offset) }

        assertEquals(4.0, calc.getCurrentMetrics().split("50-150")!!, 0.05)
    }

    @Test
    fun `50-150 is not timed when the recording joins above the band`() {
        // Starting a session already at 140 km/h must not produce an instant 50-150.
        listOf(140f to 0L, 150f to 1000L, 160f to 2000L)
            .forEach { (speed, offset) -> calc.processRealtimeGPS(gps(speed), t0 + offset) }

        assertNull(calc.getCurrentMetrics().split("50-150"))
    }

    @Test
    fun `50-150 is null when speed never reaches 150`() {
        val speeds = listOf(0f, 50f, 80f, 120f)
        feedSequence(speeds)
        assertNull(calc.getCurrentMetrics().split("50-150"))
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
        assertNotNull(m.split("100-200"))
        assertEquals(5.0, m.split("100-200")!!, 0.01)
    }

    @Test
    fun `100-200 timer resets if speed drops below 95 before 200`() {
        calc.processRealtimeGPS(gps(105f), t0)
        calc.processRealtimeGPS(gps(90f), t0 + 1000L)   // drops below 95 → reset
        calc.processRealtimeGPS(gps(100f), t0 + 2000L)  // new start
        calc.processRealtimeGPS(gps(200f), t0 + 4000L)  // 200 km/h reached

        val m = calc.getCurrentMetrics()
        assertNotNull(m.split("100-200"))
        assertEquals(2.0, m.split("100-200")!!, 0.01)
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
        assertNull(m.split("0-60"))
        assertNull(m.split("0-100"))
        assertNull(m.split("50-150"))
        assertNull(m.split("100-200"))
        assertEquals(0f, m.maxSpeed, 0f)
        assertEquals(0f, m.totalDistance, 0f)
        assertEquals(0, m.runCount)
    }

    @Test
    fun `after reset a new standing-start run can be recorded`() {
        feedSequence(listOf(0f, 50f, 100f), intervalMs = 1000L)
        calc.resetRealtimeTracking()

        val speeds = listOf(0f) + (1..7).map { it * 10f }
        feedSequence(speeds, intervalMs = 500L)

        assertNotNull(calc.getCurrentMetrics().split("0-60"))
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
        // 0-60: hit exactly at step 6 (t0+6000 ms); launch at 2 km/h, interpolated between
        // steps 0 and 1 (t0+200 ms) → 5.8 s
        assertNotNull(result.split("0-60"))
        assertEquals(5.8, result.split("0-60")!!, 0.001)
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
        assertNotNull(result.split("0-60"))
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