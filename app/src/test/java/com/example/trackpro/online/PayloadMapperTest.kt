package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.utilities.LapStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PayloadMapperTest {

    private fun session(
        gpsSource: String? = "WIFI",
        start: Long = 1_700_000_000_000,
        end: Long? = 1_700_000_900_000,
        pressure: Double? = 1013.0,
        voided: Boolean = false,
    ) = SessionData(
        id = 7, startTime = start, endTime = end, eventType = "Hungaroring - today",
        vehicleId = 1, trackId = 3, weatherTempC = 21.5, weatherPressureHpa = pressure,
        voided = voided, gpsSource = gpsSource,
    )

    private fun lap(id: Long, number: Int, time: String, gap: Boolean = false) =
        LapTimeData(id = id, sessionid = 7, lapnumber = number, laptime = time, signalGap = gap)

    private fun map(
        s: SessionData = session(),
        laps: List<LapTimeData> = listOf(lap(1, 1, "01:42.35")),
        sectors: List<SectorTimeData> = emptyList(),
    ) = PayloadMapper.session(s, laps, sectors, remoteTrackId = "track-id", remoteVehicleId = "veh-id", appVersion = "1.1")

    // ── Sessions ──

    @Test
    fun `stored lap times become milliseconds`() {
        val body = map()!!
        assertEquals(102_350, body.laps.single().timeMs)
        assertEquals(1, body.laps.single().lapNumber)
    }

    @Test
    fun `laps still in progress or invalid are never sent`() {
        val body = map(laps = listOf(
            lap(1, 1, "01:42.35"),
            lap(2, 2, LapStatus.IN_PROGRESS),
            lap(3, 3, LapStatus.INVALID),
        ))!!
        assertEquals(listOf(1), body.laps.map { it.lapNumber })
    }

    @Test
    fun `a session with no timed lap has nothing to post`() {
        assertNull(map(laps = listOf(lap(1, 1, LapStatus.IN_PROGRESS))))
        assertNull(map(laps = emptyList()))
    }

    @Test
    fun `a session with no recorded gps source is never posted`() {
        // Sessions from before the source was captured: guessing it would be a lie.
        assertNull(map(s = session(gpsSource = null)))
        assertNull(map(s = session(gpsSource = "SOMETHING_ELSE")))
    }

    @Test
    fun `each gps source maps to its contract value`() {
        assertEquals(ApiGpsSource.Wifi, map(s = session(gpsSource = "WIFI"))!!.gpsSource)
        assertEquals(ApiGpsSource.Bluetooth, map(s = session(gpsSource = "BLUETOOTH"))!!.gpsSource)
        assertEquals(ApiGpsSource.PhoneGps, map(s = session(gpsSource = "PHONE_GPS"))!!.gpsSource)
    }

    @Test
    fun `a repeated lap number keeps only the first, since the server rejects duplicates`() {
        val body = map(laps = listOf(lap(1, 1, "01:42.35"), lap(2, 1, "01:40.00"), lap(3, 2, "01:41.00")))!!
        assertEquals(listOf(1, 2), body.laps.map { it.lapNumber })
        assertEquals(102_350, body.laps.first().timeMs)
    }

    @Test
    fun `sectors are per lap, ordered, and out-of-range ones dropped`() {
        val body = map(
            laps = listOf(lap(1, 1, "01:42.35")),
            sectors = listOf(
                SectorTimeData(id = 1, lapid = 1, sectorIndex = 1, splitTimeMs = 60_000),
                SectorTimeData(id = 2, lapid = 1, sectorIndex = 0, splitTimeMs = 42_350),
                SectorTimeData(id = 3, lapid = 1, sectorIndex = 60, splitTimeMs = 1_000),
                SectorTimeData(id = 4, lapid = 99, sectorIndex = 0, splitTimeMs = 5_000),
            ),
        )!!
        assertEquals(listOf(0 to 42_350, 1 to 60_000), body.laps.single().sectors.map { it.sectorIndex to it.splitMs })
    }

    @Test
    fun `an out-of-range weather value is dropped rather than costing the session`() {
        // Surface pressure at altitude can sit below the server's 800 hPa floor.
        val weather = map(s = session(pressure = 750.0))!!.weather!!
        assertNull(weather.pressureHpa)
        assertEquals(21.5, weather.tempC!!, 0.0)
    }

    @Test
    fun `timestamps are ISO-8601 UTC and an end before the start is dropped`() {
        val body = map()!!
        assertEquals("2023-11-14T22:13:20Z", body.startedAt)
        assertEquals("2023-11-14T22:28:20Z", body.endedAt)
        assertNull(map(s = session(end = 1_600_000_000_000))!!.endedAt)
    }

    @Test
    fun `sessions post as ranked and carry the voided flag and the remote ids`() {
        val body = map(s = session(voided = true))!!
        assertEquals(ApiSessionVisibility.Ranked, body.visibility)
        assertEquals(true, body.voided)
        assertEquals("track-id", body.trackId)
        assertEquals("veh-id", body.vehicleId)
    }

    // ── Tracks ──

    private fun point(id: Long, lat: Double, lon: Double, start: Boolean = false, sector: Boolean = false, index: Int? = null) =
        TrackCoordinatesData(id = id, trackId = 3, latitude = lat, longitude = lon, altitude = 120.0,
            isStartPoint = start, isSectorPoint = sector, sectorIndex = index)

    private val loop = listOf(
        point(10, 47.0, 19.0, start = true),
        point(11, 47.001, 19.0, sector = true, index = 0),
        point(12, 47.001, 19.001),
    )

    @Test
    fun `a track keeps its point order and converts km to metres`() {
        val track = TrackMainData(trackId = 3, trackName = "Hungaroring", totalLength = 4.381, country = "Hungary", type = "Circuit")
        val body = PayloadMapper.track(track, loop, ApiTrackVisibility.Published)!!
        assertEquals(listOf(0, 1, 2), body.points.map { it.seq })
        assertEquals(4381.0, body.lengthMeters!!, 0.001)
        assertEquals(ApiTrackType.Circuit, body.type)
        assertEquals(true, body.points[1].isSectorPoint)
    }

    @Test
    fun `a track with no country or a sprint layout still maps`() {
        val track = TrackMainData(trackId = 3, trackName = "Hill", totalLength = 0.0, country = " ", type = "Sprint")
        val body = PayloadMapper.track(track, loop, ApiTrackVisibility.Private)!!
        assertEquals("Unknown", body.country)
        assertEquals(ApiTrackType.Sprint, body.type)
        assertNull(body.lengthMeters)
    }

    @Test
    fun `a track with fewer than two points cannot be sent`() {
        val track = TrackMainData(trackId = 3, trackName = "Stub", totalLength = 1.0, country = "HU", type = "Circuit")
        assertNull(PayloadMapper.track(track, loop.take(1), ApiTrackVisibility.Published))
    }

    @Test
    fun `the timing fingerprint ignores sector flags but not geometry`() {
        val resliced = loop.map { it.copy(isSectorPoint = false, sectorIndex = null) }
        assertEquals(PayloadMapper.timingFingerprint(loop), PayloadMapper.timingFingerprint(resliced))

        val moved = loop.mapIndexed { i, p -> if (i == 2) p.copy(latitude = 47.002) else p }
        assertNotEquals(PayloadMapper.timingFingerprint(loop), PayloadMapper.timingFingerprint(moved))

        val newStart = loop.mapIndexed { i, p -> p.copy(isStartPoint = i == 1) }
        assertNotEquals(PayloadMapper.timingFingerprint(loop), PayloadMapper.timingFingerprint(newStart))
    }

    @Test
    fun `premade ids are stable across installs and change with the geometry`() {
        val fp = PayloadMapper.timingFingerprint(loop)
        assertEquals(PayloadMapper.premadeTrackId("Hungaroring", fp), PayloadMapper.premadeTrackId("Hungaroring", fp))
        assertNotEquals(PayloadMapper.premadeTrackId("Hungaroring", fp), PayloadMapper.premadeTrackId("Hungaroring", fp + "x"))
        assertNotEquals(PayloadMapper.premadeTrackId("Hungaroring", fp), PayloadMapper.premadeTrackId("Mugello", fp))
    }

    // ── Vehicles ──

    private fun vehicle(manufacturer: String = "Porsche", torque: Int? = 470, topSpeed: Double? = 3000.0) =
        VehicleInformationData(
            vehicleId = 1, manufacturer = manufacturer, model = "911 GT3", year = 2023, engineType = "F6",
            horsepower = 510, torque = torque, weight = 1418.0, topSpeed = topSpeed, acceleration = 3.4,
            drivetrain = "RWD", fuelType = "Petrol", tireType = "Slick", fuelCapacity = 64.0,
            transmission = "PDK", suspensionType = null,
        )

    @Test
    fun `a vehicle maps and an out-of-range optional value is dropped`() {
        val body = PayloadMapper.vehicle(vehicle())!!
        assertEquals("Porsche", body.manufacturer)
        assertEquals(470, body.torque)
        assertNull("3000 km/h is outside the server's range", body.topSpeed)
    }

    @Test
    fun `a vehicle missing a required field is not sent`() {
        assertNull(PayloadMapper.vehicle(vehicle(manufacturer = "  ")))
    }

    // ── Change detection ──

    @Test
    fun `the hash changes when anything posted changes`() {
        val a = PayloadMapper.hash(map()!!)
        assertEquals(a, PayloadMapper.hash(map()!!))
        assertNotEquals(a, PayloadMapper.hash(map(s = session(voided = true))!!))
        assertNotNull(a)
    }
}
