package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCareerTest {

    private val ring = TrackMainData(trackId = 1, trackName = "Hungaroring", totalLength = 4.381, country = "Hungary", type = "Circuit")
    private val kakucs = TrackMainData(trackId = 2, trackName = "Kakucs Ring", totalLength = null, country = "Hungary", type = "Circuit")

    private fun car(id: Long) = VehicleInformationData(
        vehicleId = id, manufacturer = "Porsche", model = "Car $id", year = 2020, engineType = "F6",
        horsepower = 400, torque = null, weight = 1400.0, topSpeed = null, acceleration = null,
        drivetrain = "RWD", fuelType = "Petrol", tireType = "Slick", fuelCapacity = null,
        transmission = "PDK", suspensionType = null,
    )

    private fun session(id: Long, track: Long?, vehicle: Long?, start: Long, voided: Boolean = false) =
        SessionData(id = id, startTime = start, endTime = start + 1, eventType = "s$id", vehicleId = vehicle, trackId = track, voided = voided)

    private fun lap(id: Long, session: Long, time: String, gap: Boolean = false) =
        LapTimeData(id = id, sessionid = session, lapnumber = id.toInt(), laptime = time, signalGap = gap)

    @Test
    fun `nothing recorded is an empty sheet, not an error`() {
        val career = LocalCareer.compute(emptyList(), emptyList(), listOf(ring), listOf(car(1)))

        assertTrue(career.isEmpty)
        assertEquals(1, career.vehicleCount)
        assertTrue(career.bests.isEmpty())
    }

    @Test
    fun `voided sessions, signal gaps and unfinished laps never count`() {
        val career = LocalCareer.compute(
            sessions = listOf(
                session(1, track = 1, vehicle = 1, start = 100),
                session(2, track = 1, vehicle = 1, start = 200, voided = true),
            ),
            laps = listOf(
                lap(1, 1, "01:50.00"),
                lap(2, 1, "01:40.00", gap = true),
                lap(3, 1, "IN PROGRESS"),
                lap(4, 2, "01:30.00"),
            ),
            tracks = listOf(ring),
            vehicles = listOf(car(1)),
        )

        assertEquals(1, career.sessionCount)
        assertEquals(1, career.lapCount)
        assertEquals(110_000L, career.bests.single().bestLapMs)
        assertEquals(4.4, career.distanceKm!!, 0.0001)
    }

    @Test
    fun `a personal best per track, with the car it was set in`() {
        val career = LocalCareer.compute(
            sessions = listOf(
                session(1, track = 1, vehicle = 1, start = 100),
                session(2, track = 1, vehicle = 2, start = 200),
                session(3, track = 2, vehicle = 2, start = 300),
            ),
            laps = listOf(
                lap(1, 1, "01:50.00"),
                lap(2, 2, "01:48.50"),
                lap(3, 3, "00:59.10"),
            ),
            tracks = listOf(ring, kakucs),
            vehicles = listOf(car(1), car(2)),
        )

        val ringBest = career.bests.first { it.trackId == 1L }
        assertEquals(108_500L, ringBest.bestLapMs)
        assertEquals(2L, ringBest.vehicleId)
        assertEquals(2, ringBest.lapCount)
        assertEquals(2, career.trackCount)
        assertEquals(2L, career.mainVehicleId)
        // Kakucs has no known length, so only the ring's two laps count toward distance.
        assertEquals(8.8, career.distanceKm!!, 0.0001)
    }

    @Test
    fun `drag runs count as sessions but not as tracks or laps`() {
        val career = LocalCareer.compute(
            sessions = listOf(session(1, track = null, vehicle = 1, start = 100), session(2, track = -1, vehicle = 1, start = 150)),
            laps = emptyList(),
            tracks = listOf(ring),
            vehicles = listOf(car(1)),
        )

        assertEquals(2, career.sessionCount)
        assertEquals(0, career.trackCount)
        assertEquals(0, career.lapCount)
        assertNull(career.distanceKm)
        assertEquals(1L, career.mainVehicleId)
    }
}
