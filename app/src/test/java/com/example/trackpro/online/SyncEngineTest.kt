package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_PREMADE_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_SESSION
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE
import com.example.trackpro.dataClasses.SectorTimeData
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

    private fun engine(store: TokenStore = signedInStore(), dao: FakeSyncDao = this.dao) = SyncEngine(
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

    /** A second phone signed in to the same account: its own database, the same server. */
    private val otherPhone = FakeSyncDao()

    private fun runOtherPhone() = runBlocking { engine(dao = otherPhone).run() }

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
    fun `a deleted track other drivers still use is let go, and not restored`() {
        userTrack(); session()
        run()
        api.onDeleteTrack = { throw ApiException(409, "Other drivers have sessions on this track.", "TrackInUse") }
        dao.tracks.clear(); dao.points.clear(); dao.sessions.clear(); dao.laps.clear(); dao.published.clear()

        val report = run()
        api.calls.clear()
        val again = run()

        assertEquals(0, report.failed)
        assertEquals(GarageSync.DELETED_HERE, dao.links[KIND_TRACK to 3L]!!.uploadedHash)
        assertTrue("the account still has it, but it stays deleted here", dao.tracks.isEmpty())
        assertEquals(0, again.downloaded)
        assertTrue("and it is not asked to delete it again", api.calls.none { it.startsWith("DELETE") })
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

    // ── Restoring onto another phone ──

    /** This phone posts a session with a car, sectors and weather on its own published track. */
    private fun postFromThisPhone() {
        userTrack(); vehicle(); session(vehicleId = 1, times = listOf("01:30.00", "01:28.50"))
        dao.sessions.replaceAll { it.copy(weatherTempC = 21.5, weatherCode = 3) }
        dao.sectors += SectorTimeData(id = 1, lapid = 70, sectorIndex = 0, splitTimeMs = 41_000)
        run()
    }

    /** The other phone has the same car, backed up to the same account. */
    private fun sameCarOnOtherPhone() {
        otherPhone.vehicles += dao.vehicles.single().copy(vehicleId = 5)
        otherPhone.links[KIND_VEHICLE to 5L] = dao.links[KIND_VEHICLE to 1L]!!.copy(localId = 5)
    }

    @Test
    fun `a new phone gets the published track and shared sessions back`() {
        postFromThisPhone()
        sameCarOnOtherPhone()

        val report = runOtherPhone()

        val track = otherPhone.tracks.single()
        assertEquals("My track", track.trackName)
        assertEquals(2, otherPhone.points.count { it.trackId == track.trackId })
        assertTrue("still published, so its sessions stay on the leaderboard", track.trackId in otherPhone.published)

        val session = otherPhone.sessions.single()
        assertEquals(track.trackId, session.trackId)
        assertEquals(5L, session.vehicleId)
        assertEquals("WIFI", session.gpsSource)
        assertEquals(1_700_000_000_000, session.startTime)
        assertEquals(1_700_000_600_000, session.endTime)
        assertEquals(21.5, session.weatherTempC!!, 0.0)
        assertEquals(listOf("01:30.00", "01:28.50"), otherPhone.laps.sortedBy { it.lapnumber }.map { it.laptime })
        assertEquals(41_000L, otherPhone.sectors.single().splitTimeMs)

        assertEquals(2, report.downloaded)
        assertEquals(0, report.failed)
    }

    @Test
    fun `what was restored is not sent back`() {
        postFromThisPhone()
        sameCarOnOtherPhone()
        runOtherPhone()
        api.calls.clear()

        val report = runOtherPhone()

        assertTrue(api.calls.none { it.startsWith("PUT") || it.startsWith("DELETE") || it.startsWith("GET session") })
        assertEquals(1, report.unchanged)
        assertEquals(0, report.downloaded)
    }

    @Test
    fun `a restored session edited on the new phone updates the same record`() {
        postFromThisPhone()
        sameCarOnOtherPhone()
        runOtherPhone()
        otherPhone.sessions.replaceAll { it.copy(voided = true) }

        val report = runOtherPhone()

        assertEquals(1, report.uploaded)
        assertTrue(api.sessions.values.single().voided)
        assertEquals(
            "the car it was posted with stays with it",
            dao.links[KIND_VEHICLE to 1L]!!.remoteId,
            api.sessions.values.single().vehicleId
        )
    }

    @Test
    fun `a restored session brings its car when the new phone does not have it`() {
        postFromThisPhone()
        val remoteCar = dao.links[KIND_VEHICLE to 1L]!!.remoteId

        val report = runOtherPhone()
        api.calls.clear()
        runOtherPhone()

        val car = otherPhone.vehicles.single()
        assertEquals("Porsche", car.manufacturer)
        assertEquals(car.vehicleId, otherPhone.sessions.single().vehicleId)
        assertEquals(remoteCar, otherPhone.links[KIND_VEHICLE to car.vehicleId]!!.remoteId)
        assertEquals("track, car and session", 3, report.downloaded)
        assertTrue("nothing goes back up", api.calls.none { it.startsWith("PUT") })
    }

    @Test
    fun `a car deleted on the new phone comes back when a restored session was driven in it`() {
        postFromThisPhone()
        val remoteCar = dao.links[KIND_VEHICLE to 1L]!!.remoteId
        // Deleted there once, while the account had to keep it for this session.
        otherPhone.links[KIND_VEHICLE to 3L] = RemoteLink(KIND_VEHICLE, 3, remoteCar, GarageSync.DELETED_HERE, 1L)

        runOtherPhone()

        val car = otherPhone.vehicles.single()
        assertEquals(car.vehicleId, otherPhone.sessions.single().vehicleId)
        assertNull("no longer remembered as deleted", otherPhone.links[KIND_VEHICLE to 3L])
        assertEquals(remoteCar, otherPhone.links[KIND_VEHICLE to car.vehicleId]!!.remoteId)
    }

    @Test
    fun `a session restored without its car gets it back, without being sent again`() {
        postFromThisPhone()
        // What the first version of restore did: the car was not found, the session came alone.
        api.hideVehicles = true
        runOtherPhone()
        assertNull(otherPhone.sessions.single().vehicleId)
        api.hideVehicles = false

        runOtherPhone()
        api.calls.clear()
        val report = runOtherPhone()

        val car = otherPhone.vehicles.single()
        assertEquals(car.vehicleId, otherPhone.sessions.single().vehicleId)
        assertTrue(api.calls.none { it.startsWith("PUT") })
        assertEquals(1, report.unchanged)
    }

    @Test
    fun `a change made to a carless session still goes up, now with its car`() {
        postFromThisPhone()
        api.hideVehicles = true
        runOtherPhone()
        api.hideVehicles = false
        otherPhone.sessions.replaceAll { it.copy(voided = true) }

        runOtherPhone()

        val online = api.sessions.values.single()
        assertTrue(online.voided)
        assertEquals(dao.links[KIND_VEHICLE to 1L]!!.remoteId, online.vehicleId)
    }

    @Test
    fun `with sharing off only tracks come back, and nothing is withdrawn`() {
        postFromThisPhone()
        sharing = false

        val report = runOtherPhone()

        assertEquals(1, otherPhone.tracks.size)
        assertTrue(otherPhone.sessions.isEmpty())
        assertEquals(1, api.sessions.size)
        assertEquals(1, report.downloaded)
    }

    @Test
    fun `a session on a premade track lands on the phone's own copy of it`() {
        premadeTrack(); session(trackId = 4)
        run()
        // Every install seeds the premade tracks, under whatever local id.
        otherPhone.tracks += dao.tracks.single().copy(trackId = 9)
        otherPhone.points += dao.points.map { it.copy(id = it.id + 1000, trackId = 9) }
        premadeIds[9] = "premade-hungaroring"

        runOtherPhone()

        assertEquals("the shared track is not copied as the driver's own", 1, otherPhone.tracks.size)
        assertEquals(9L, otherPhone.sessions.single().trackId)
        assertTrue(otherPhone.links.keys.none { it.first == KIND_TRACK })
    }

    @Test
    fun `a session already on the phone is linked, not doubled`() {
        postFromThisPhone()
        // The same session and track, on a phone that lost its links.
        otherPhone.tracks += dao.tracks; otherPhone.points += dao.points; otherPhone.published += dao.published
        otherPhone.sessions += dao.sessions.map { it.copy(vehicleId = null) }; otherPhone.laps += dao.laps

        runOtherPhone()

        assertEquals(1, otherPhone.tracks.size)
        assertEquals(1, otherPhone.sessions.size)
        assertEquals(dao.links[KIND_SESSION to 7L]!!.remoteId, otherPhone.links[KIND_SESSION to 7L]!!.remoteId)
        assertEquals(dao.links[KIND_TRACK to 3L]!!.remoteId, otherPhone.links[KIND_TRACK to 3L]!!.remoteId)
        assertEquals("one record online, updated from this phone", 1, api.sessions.size)
    }

    @Test
    fun `a session on a track the new phone does not share stays online, untouched`() {
        postFromThisPhone()
        // Restored once, then the track unpublished on the new phone and its session gone.
        runOtherPhone()
        otherPhone.sessions.clear(); otherPhone.laps.clear(); otherPhone.sectors.clear()
        otherPhone.links.keys.removeAll { it.first == KIND_SESSION }
        otherPhone.published.clear()

        runOtherPhone()

        assertTrue(otherPhone.sessions.isEmpty())
    }

    @Test
    fun `an account that cannot be read costs the restore, not the uploads`() {
        val failing = object : FakeApi() {
            override suspend fun listSessions(accessToken: String, page: Int, pageSize: Int): SessionPage =
                throw ApiException(500, "The server had a problem.")
        }
        val engine = SyncEngine(
            api = failing, auth = AuthRepository(failing, signedInStore()), dao = dao,
            premade = { _, _ -> null }, sharingEnabled = { true }, appVersion = "1.1",
        )
        userTrack(); session()

        val report = runBlocking { engine.run() }

        assertEquals(1, report.uploaded)
        assertEquals(1, report.failed)
        assertTrue(report.problem!!.contains("sessions"))
    }

    // ── The account holding one session twice ──

    /** Posts session 7, then gives the account a second record of it, as another phone would. */
    private fun postTwice(): String {
        userTrack(); session()
        run()
        val first = dao.links[KIND_SESSION to 7L]!!.remoteId
        api.sessions["second-record"] = api.sessions.getValue(first)
        return first
    }

    @Test
    fun `a session the account holds twice is not brought down a second time`() {
        postTwice()

        val report = run()

        assertEquals(1, dao.sessions.size)
        assertEquals(0, report.downloaded)
        assertTrue("both records stay online", api.sessions.containsKey("second-record"))
    }

    @Test
    fun `a second copy an earlier restore made is removed again, here only`() {
        postTwice()
        // What the first version of restore did with the second record.
        dao.sessions += dao.sessions.single().copy(id = 8)
        dao.laps += dao.laps.map { it.copy(id = it.id + 100, sessionid = 8) }
        dao.links[KIND_SESSION to 8L] = dao.links[KIND_SESSION to 7L]!!.copy(localId = 8, remoteId = "second-record")
        api.calls.clear()

        run()

        assertEquals(listOf(7L), dao.sessions.map { it.id })
        assertTrue(dao.laps.none { it.sessionid == 8L })
        assertNull(dao.links[KIND_SESSION to 8L])
        assertTrue("nothing is withdrawn for it", api.calls.none { it.startsWith("DELETE") })
        assertEquals(2, api.sessions.size)
    }

    @Test
    fun `a same-start session with its own GPS trace is never taken for a copy`() {
        postTwice()
        dao.sessions += dao.sessions.single().copy(id = 8)
        dao.laps += dao.laps.map { it.copy(id = it.id + 100, sessionid = 8) }
        dao.links[KIND_SESSION to 8L] = dao.links[KIND_SESSION to 7L]!!.copy(localId = 8, remoteId = "second-record")
        dao.withGpsTrace += 8L

        run()

        assertEquals(listOf(7L, 8L), dao.sessions.map { it.id })
    }
}
