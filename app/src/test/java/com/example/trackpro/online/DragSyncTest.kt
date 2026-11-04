package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_SESSION
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.calculationClasses.DragBoardMetric
import com.example.trackpro.managerClasses.calculationClasses.DragBoardResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragSyncTest {

    private val api = FakeApi()
    private val dao = FakeSyncDao()
    private var sharing = true
    private var nextId = 0

    private fun run() = runBlocking {
        SyncEngine(
            api = api,
            auth = AuthRepository(api, signedInStore()),
            dao = dao,
            premade = PremadeTracks { _, _ -> null },
            sharingEnabled = { sharing },
            appVersion = "1.1",
            newId = { "id-${++nextId}" },
            clock = { 1_000L },
        ).run()
    }

    private fun vehicle(id: Long = 1) {
        dao.vehicles += VehicleInformationData(
            vehicleId = id, manufacturer = "Porsche", model = "911", year = 2023, engineType = "F6",
            horsepower = 500, torque = null, weight = 1400.0, topSpeed = null, acceleration = null,
            drivetrain = "RWD", fuelType = "Petrol", tireType = "Slick", fuelCapacity = null,
            transmission = "PDK", suspensionType = null,
        )
    }

    /** A recorded drag session with one 0-100 of about 4 s. */
    private fun dragSession(
        id: Long = 7,
        vehicleId: Long? = 1,
        gps: String? = "WIFI",
        voided: Boolean = false,
        posted: Boolean = true,
        kmhPerSecond: Double = 25.0,
    ) {
        dao.sessions.removeAll { it.id == id }
        dao.sessions += SessionData(
            id = id, startTime = 1_700_000_000_000, endTime = 1_700_000_600_000, eventType = "Drag $id",
            vehicleId = vehicleId, trackId = null, voided = voided, gpsSource = gps,
        )
        if (posted) dao.publishedDrags += id

        dao.rawGps.removeAll { it.sessionid == id }
        var t = 1_700_000_000_000
        fun fix(kmh: Double) {
            dao.rawGps += RawGPSData(
                sessionid = id, latitude = 47.0, longitude = 19.0, altitude = 200.0,
                timestamp = t, speed = kmh.toFloat(), fixQuality = 1,
            )
            t += 100
        }
        repeat(10) { fix(0.0) }
        var kmh = 0.0
        while (kmh < 110) { kmh += kmhPerSecond / 10; fix(kmh) }
        while (kmh > 0) { kmh = (kmh - 4).coerceAtLeast(0.0); fix(kmh) }
        repeat(10) { fix(0.0) }
    }

    private fun uploaded() = api.sessions.values.single()

    @Test
    fun `a posted run goes up with no track, no laps and its car`() {
        vehicle(); dragSession()

        run()

        val body = uploaded()
        assertNull(body.trackId)
        assertTrue(body.laps.isEmpty())
        assertEquals(api.vehicles.keys.single(), body.vehicleId)
        assertEquals(ApiSessionVisibility.Ranked, body.visibility)
        assertEquals(ApiDragMetric.ZeroToHundred, body.dragResults.single().metric)
        assertEquals(4_000.0, body.dragResults.single().timeMs.toDouble(), 150.0)
    }

    @Test
    fun `a run the driver did not post stays on the phone`() {
        vehicle(); dragSession(posted = false)

        run()

        assertTrue(api.sessions.isEmpty())
    }

    @Test
    fun `nothing goes up with sharing off`() {
        vehicle(); dragSession()
        sharing = false

        run()

        assertTrue(api.sessions.isEmpty())
    }

    @Test
    fun `a run without a car or a recorded GPS source is not sent`() {
        vehicle()
        dragSession(id = 7, vehicleId = null)
        dragSession(id = 8, gps = null)

        run()

        assertTrue(api.sessions.isEmpty())
    }

    @Test
    fun `an unchanged run is not sent twice`() {
        vehicle(); dragSession()

        run()
        val report = run()

        assertEquals(1, api.calls.count { it.startsWith("PUT session") })
        assertEquals(1, report.unchanged)
    }

    @Test
    fun `withdrawing a post takes it back down`() {
        vehicle(); dragSession()
        run()
        val remoteId = dao.getLinkBlocking(7)

        dao.publishedDrags -= 7L
        run()

        assertTrue("DELETE session $remoteId" in api.calls)
        assertNull(dao.links[KIND_SESSION to 7L])
    }

    @Test
    fun `voiding a posted run sends it voided, for the server to hide`() {
        vehicle(); dragSession()
        run()

        dragSession(voided = true)
        run()

        assertTrue(uploaded().voided)
    }

    @Test
    fun `a run with no clean result is withdrawn`() {
        vehicle(); dragSession()
        run()

        dao.rawGps.clear()
        run()

        assertTrue(api.sessions.isEmpty())
    }

    @Test
    fun `a car that cannot be sent fails the run rather than sending it unranked`() {
        dao.vehicles += VehicleInformationData(
            vehicleId = 1, manufacturer = "", model = "911", year = 2023, engineType = "F6",
            horsepower = 500, torque = null, weight = 1400.0, topSpeed = null, acceleration = null,
            drivetrain = "RWD", fuelType = "Petrol", tireType = "Slick", fuelCapacity = null,
            transmission = "PDK", suspensionType = null,
        )
        dragSession()

        val report = run()

        assertEquals(1, report.failed)
        assertTrue(api.sessions.isEmpty())
    }

    @Test
    fun `drag sessions and lap sessions go up side by side`() {
        vehicle(); dragSession()
        dao.tracks += com.example.trackpro.dataClasses.TrackMainData(
            trackId = 3, trackName = "Ring", totalLength = 1.0, country = "Hungary", type = "Circuit",
        )
        dao.points += listOf(
            com.example.trackpro.dataClasses.TrackCoordinatesData(id = 1, trackId = 3, latitude = 47.0, longitude = 19.0, altitude = null, isStartPoint = true),
            com.example.trackpro.dataClasses.TrackCoordinatesData(id = 2, trackId = 3, latitude = 47.1, longitude = 19.1, altitude = null),
        )
        dao.published += 3L
        dao.sessions += SessionData(
            id = 9, startTime = 1, endTime = 2, eventType = "Laps", vehicleId = null, trackId = 3, gpsSource = "WIFI",
        )
        dao.laps += LapTimeData(id = 90, sessionid = 9, lapnumber = 1, laptime = "01:30.00")

        run()

        assertEquals(2, api.sessions.size)
    }

    // ── Payload ──

    private val session = SessionData(
        id = 1, startTime = 1_700_000_000_000, endTime = 1_700_000_600_000, eventType = "  ",
        vehicleId = 1, trackId = null, gpsSource = "PHONE_GPS",
    )

    @Test
    fun `times become milliseconds and trap speed one decimal`() {
        val body = PayloadMapper.dragSession(
            session,
            listOf(DragBoardResult(DragBoardMetric.QuarterMile, 12.3456, 186.47f)),
            "car",
            "1.1",
        )!!

        assertEquals("Drag session", body.name)
        assertEquals(12_346, body.dragResults.single().timeMs)
        assertEquals(186.5, body.dragResults.single().trapSpeedKmh!!, 0.0001)
    }

    @Test
    fun `a time under the server's floor is dropped, not sent to sink the session`() {
        val body = PayloadMapper.dragSession(
            session,
            listOf(
                DragBoardResult(DragBoardMetric.ZeroToHundred, 0.8),
                DragBoardResult(DragBoardMetric.HundredToTwoHundred, 6.0),
            ),
            "car",
            null,
        )

        assertNotNull(body)
        assertEquals(listOf(ApiDragMetric.HundredToTwoHundred), body!!.dragResults.map { it.metric })
    }

    @Test
    fun `no sendable result means no session`() {
        val only = listOf(DragBoardResult(DragBoardMetric.ZeroToHundred, 0.8))

        assertNull(PayloadMapper.dragSession(session, only, "car", null))
        assertNull(PayloadMapper.dragSession(session.copy(gpsSource = null), emptyList(), "car", null))
    }

    @Test
    fun `a lap session's encoded body does not gain the drag field`() {
        val lap = SessionWrite(
            name = "Laps", startedAt = "2026-01-01T00:00:00Z", gpsSource = ApiGpsSource.Wifi,
            visibility = ApiSessionVisibility.Ranked, laps = listOf(LapWrite(1, 90_000)),
        )

        val encoded = OkHttpTrackBoardApi.json.encodeToString(SessionWrite.serializer(), lap)

        assertFalse("Would change every stored upload hash", "dragResults" in encoded)
    }

    private fun FakeSyncDao.getLinkBlocking(id: Long) = links[KIND_SESSION to id]!!.remoteId
}
