package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_PREMADE_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_SESSION
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    private val api = FakeApi()
    private val dao = FakeSyncDao()
    private var sharing = true
    private val premadeIds = mutableMapOf<Long, String>()
    private var nextId = 0

    private fun engine(store: TokenStore = signedInStore()) = SyncEngine(
        api = api,
        auth = AuthRepository(api, store),
        dao = dao,
        premade = PremadeTracks { track, _ -> premadeIds[track.trackId] },
        sharingEnabled = { sharing },
        appVersion = "1.1",
        newId = { "id-${++nextId}" },
        clock = { 1_000L },
    )

    private fun run(store: TokenStore = signedInStore()) = runBlocking { engine(store).run() }

    // ── Fixtures ──

    private fun track(id: Long, name: String) {
        dao.tracks += TrackMainData(trackId = id, trackName = name, totalLength = 4.4, country = "HU", type = "Circuit")
        dao.points += listOf(
            TrackCoordinatesData(id = id * 100, trackId = id, latitude = 47.0, longitude = 19.0, altitude = null, isStartPoint = true),
            TrackCoordinatesData(id = id * 100 + 1, trackId = id, latitude = 47.001, longitude = 19.0, altitude = null),
        )
    }

    private fun userTrack(id: Long = 3, published: Boolean = true) {
        track(id, "My track")
        if (published) dao.published += id
    }

    private fun premadeTrack(id: Long = 4, remoteId: String = "premade-hungaroring") {
        track(id, "Hungaroring")
        premadeIds[id] = remoteId
    }

    private fun vehicle(id: Long = 1, manufacturer: String = "Porsche") {
        dao.vehicles += VehicleInformationData(
            vehicleId = id, manufacturer = manufacturer, model = "911", year = 2023, engineType = "F6",
            horsepower = 500, torque = null, weight = 1400.0, topSpeed = null, acceleration = null,
            drivetrain = "RWD", fuelType = "Petrol", tireType = "Slick", fuelCapacity = null,
            transmission = "PDK", suspensionType = null,
        )
    }

    private fun session(
        id: Long = 7,
        trackId: Long = 3,
        gps: String? = "WIFI",
        voided: Boolean = false,
        vehicleId: Long? = null,
        times: List<String> = listOf("01:30.00"),
    ) {
        dao.sessions.removeAll { it.id == id }
        dao.sessions += SessionData(
            id = id, startTime = 1_700_000_000_000, endTime = 1_700_000_600_000, eventType = "Session $id",
            vehicleId = vehicleId, trackId = trackId, voided = voided, gpsSource = gps,
        )
        dao.laps.removeAll { it.sessionid == id }
        times.forEachIndexed { i, t -> dao.laps += LapTimeData(id = id * 10 + i, sessionid = id, lapnumber = i + 1, laptime = t) }
    }

    // ── Gating ──

    @Test
    fun `nothing happens for a driver who is not signed in`() {
        userTrack(); session()

        val report = run(store = InMemoryTokenStore())

        assertTrue(api.calls.isEmpty())
        assertNotNull(report.problem)
    }

    @Test
    fun `a session with no recorded gps source is never posted`() {
        userTrack(); session(gps = null)

        run()

        assertTrue(api.puts("session").isEmpty())
    }

    @Test
    fun `a session on a private track the driver built is never posted`() {
        userTrack(published = false); session()

        run()

        assertTrue(api.calls.none { it.startsWith("PUT") })
    }

    // ── Uploading ──

    @Test
    fun `a session on a published track goes up with its track and vehicle`() {
        userTrack(); vehicle(); session(vehicleId = 1)

        val report = run()

        assertEquals(1, report.uploaded)
        val trackId = dao.links[KIND_TRACK to 3L]!!.remoteId
        val vehicleId = dao.links[KIND_VEHICLE to 1L]!!.remoteId
        val posted = api.sessions.getValue(dao.links[KIND_SESSION to 7L]!!.remoteId)
        assertEquals(trackId, posted.trackId)
        assertEquals(vehicleId, posted.vehicleId)
        assertEquals(ApiTrackVisibility.Published, api.tracks.getValue(trackId).visibility)
        assertEquals(90_000, posted.laps.single().timeMs)
    }

    @Test
    fun `an unchanged session is not uploaded again`() {
        userTrack(); session()
        run()
        api.calls.clear()

        val report = run()

        assertTrue(api.calls.none { it.startsWith("PUT") })
        assertEquals(1, report.unchanged)
        assertEquals(0, report.uploaded)
    }

    @Test
    fun `voiding a session re-uploads it so its laps leave the leaderboard`() {
        userTrack(); session()
        run()
        session(voided = true)

        val report = run()

        assertEquals(1, report.uploaded)
        assertTrue(api.sessions.values.single().voided)
    }

    // ── Premade tracks ──

    @Test
    fun `the first driver on a premade track creates the shared server track`() {
        premadeTrack(); session(trackId = 4)

        run()

        assertTrue(api.calls.contains("PUT track premade-hungaroring Published"))
        assertEquals("premade-hungaroring", api.sessions.values.single().trackId)
        assertNotNull(dao.links[KIND_PREMADE_TRACK to 4L])
    }

    @Test
    fun `a later driver links to the existing shared track instead of writing it`() {
        premadeTrack(); session(trackId = 4)
        api.foreignTracks += "premade-hungaroring"

        val report = run()

        assertTrue(api.puts("track").isEmpty())
        assertEquals("premade-hungaroring", api.sessions.values.single().trackId)
        assertEquals(0, report.failed)
    }

    @Test
    fun `a premade track is never re-uploaded, even after a local sector change`() {
        premadeTrack(); session(trackId = 4)
        run()
        dao.points.replaceAll { if (it.trackId == 4L) it.copy(isSectorPoint = true, sectorIndex = 0) else it }
        api.calls.clear()

        run()

        assertTrue(api.puts("track").isEmpty())
    }

    // ── Withdrawing ──

    @Test
    fun `switching sharing off takes posted sessions back down`() {
        userTrack(); session()
        run()
        sharing = false

        val report = run()

        assertEquals(1, report.withdrawn)
        assertTrue(api.sessions.isEmpty())
        assertNull(dao.links[KIND_SESSION to 7L])
    }

    @Test
    fun `unpublishing a track withdraws its sessions and makes it private`() {
        userTrack(); session()
        run()
        dao.published -= 3L

        run()

        assertTrue(api.sessions.isEmpty())
        val trackRemote = dao.links[KIND_TRACK to 3L]!!.remoteId
        assertEquals(ApiTrackVisibility.Private, api.tracks.getValue(trackRemote).visibility)
    }

    @Test
    fun `deleting a track and its sessions on the phone deletes them online`() {
        userTrack(); session()
        run()
        val trackRemote = dao.links[KIND_TRACK to 3L]!!.remoteId
        dao.tracks.clear(); dao.points.clear(); dao.sessions.clear(); dao.laps.clear(); dao.published.clear()

        run()

        assertTrue(api.calls.contains("DELETE track $trackRemote"))
        assertTrue(api.sessions.isEmpty())
        assertTrue(dao.links.isEmpty())
    }

    @Test
    fun `a deleted track other drivers still use is simply let go`() {
        userTrack(); session()
        run()
        api.onDeleteTrack = { throw ApiException(409, "Other drivers have sessions on this track.", "TrackInUse") }
        dao.tracks.clear(); dao.points.clear(); dao.sessions.clear(); dao.laps.clear(); dao.published.clear()

        val report = run()

        assertEquals(0, report.failed)
        assertNull(dao.links[KIND_TRACK to 3L])
    }

    // ── Failures ──

    @Test
    fun `a rejected session is reported and not re-sent until it changes`() {
        userTrack(); session()
        api.onPutSession = { throw ApiException(400, "Lap number 1 appears more than once.") }

        val first = run()
        api.calls.clear()
        val second = run()

        assertEquals(1, first.failed)
        assertEquals("Lap number 1 appears more than once.", first.problem)
        assertTrue("the same rejected payload is not re-sent", api.puts("session").isEmpty())
        assertEquals("still reported, since it is still not posted", 1, second.failed)
    }

    @Test
    fun `one bad session does not stop the others`() {
        userTrack()
        session(id = 7); session(id = 8)
        api.onPutSession = { if (it.name == "Session 7") throw ApiException(400, "bad") }

        val report = run()

        assertEquals(1, report.failed)
        assertEquals(1, report.uploaded)
    }

    @Test
    fun `losing the network stops the run and marks it for retry`() {
        userTrack(); session(id = 7); session(id = 8)
        api.onPutSession = { throw NetworkException("offline") }

        val report = run()

        assertTrue(report.offline)
        assertEquals("the rest would fail the same way", 1, api.puts("session").size)
    }

    @Test
    fun `a vehicle the server rejects costs the session only its vehicle`() {
        userTrack(); vehicle(); session(vehicleId = 1)
        api.onPutVehicle = { throw ApiException(400, "The field Weight must be between 0.1 and 10000.") }

        val report = run()

        assertEquals(1, report.uploaded)
        assertNull(api.sessions.values.single().vehicleId)
        assertFalse(report.offline)
    }
}
