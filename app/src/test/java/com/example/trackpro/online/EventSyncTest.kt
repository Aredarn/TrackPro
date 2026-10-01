package com.example.trackpro.online

import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_SESSION
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_SHARED_TRACK
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How joined track-day events change what sync sends. */
class EventSyncTest {

    private val api = FakeApi()
    private val dao = FakeSyncDao()
    private var sharing = false
    private val premadeIds = mutableMapOf<Long, String>()
    private val events = mutableListOf<JoinedEvent>()
    private var nextId = 0

    private val start = 1_700_000_000_000L

    private fun engine() = SyncEngine(
        api = api,
        auth = AuthRepository(api, signedInStore()),
        dao = dao,
        premade = PremadeTracks { track, _ -> premadeIds[track.trackId] },
        sharingEnabled = { sharing },
        appVersion = "1.1",
        newId = { "id-${++nextId}" },
        clock = { 1_000L },
        events = { events },
    )

    private fun run() = runBlocking { engine().run() }

    private fun track(id: Long, name: String) {
        dao.tracks += TrackMainData(trackId = id, trackName = name, totalLength = 1.05, country = "HU", type = "Circuit")
        dao.points += listOf(
            TrackCoordinatesData(id = id * 100, trackId = id, latitude = 47.0, longitude = 19.0, altitude = null, isStartPoint = true),
            TrackCoordinatesData(id = id * 100 + 1, trackId = id, latitude = 47.001, longitude = 19.0, altitude = null),
        )
    }

    private fun premadeKakucs(id: Long = 4) {
        track(id, "Kakucs ring")
        premadeIds[id] = "premade-kakucs"
    }

    private fun joined(trackId: String = "premade-kakucs", from: Long = start - 3_600_000, to: Long = start + 3_600_000) {
        events += JoinedEvent("event-1", "Club day", trackId, "Kakucs ring", from, to)
    }

    private fun session(id: Long = 7, trackId: Long = 4, startTime: Long = start, running: Boolean = false, times: List<String> = listOf("00:41.91")) {
        dao.sessions.removeAll { it.id == id }
        dao.sessions += SessionData(
            id = id, startTime = startTime, endTime = if (running) null else startTime + 600_000, eventType = "Track day",
            vehicleId = null, trackId = trackId, voided = false, gpsSource = "WIFI",
        )
        dao.laps.removeAll { it.sessionid == id }
        times.forEachIndexed { i, t -> dao.laps += LapTimeData(id = id * 10 + i, sessionid = id, lapnumber = i + 1, laptime = t) }
    }

    @Test
    fun `with sharing off, a session in a joined event still goes up, as private`() {
        premadeKakucs(); joined(); session()

        val report = run()

        assertEquals(1, report.uploaded)
        assertEquals(ApiSessionVisibility.Private, api.sessions.values.single().visibility)
    }

    @Test
    fun `with sharing on, an event session is ranked as usual`() {
        sharing = true
        premadeKakucs(); joined(); session()

        run()

        assertEquals(ApiSessionVisibility.Ranked, api.sessions.values.single().visibility)
    }

    @Test
    fun `a session outside the event window stays on the phone when sharing is off`() {
        premadeKakucs(); joined(); session(startTime = start + 2 * 3_600_000)

        run()

        assertTrue(api.sessions.isEmpty())
    }

    @Test
    fun `a running event session goes up after a lap and a full sync does not withdraw it`() {
        premadeKakucs(); joined(); session(running = true)

        val pushed = runBlocking { engine().pushLive(7) }
        assertTrue(pushed)
        val remoteId = dao.links[KIND_SESSION to 7L]!!.remoteId
        assertEquals(null, api.sessions.getValue(remoteId).endedAt)

        // A periodic sync mid-session: not finished, so not planned, but it must stay up.
        val report = run()

        assertEquals(0, report.withdrawn)
        assertTrue(remoteId in api.sessions)

        // The next lap goes up to the same record.
        session(running = true, times = listOf("00:41.91", "00:41.20"))
        runBlocking { engine().pushLive(7) }
        assertEquals(2, api.sessions.getValue(remoteId).laps.size)
    }

    @Test
    fun `pushLive ignores a session that is not in a joined event`() {
        premadeKakucs(); session(running = true)

        val pushed = runBlocking { engine().pushLive(7) }

        assertFalse(pushed)
        assertTrue(api.sessions.isEmpty())
    }

    @Test
    fun `a track downloaded for an event is timed against, never uploaded as the driver's own`() {
        track(9, "Host's own track")
        dao.putLinkBlocking(RemoteLink(KIND_SHARED_TRACK, 9, "hosts-track", "shared", 1L))
        api.foreignTracks += "hosts-track"
        joined(trackId = "hosts-track")
        session(trackId = 9)

        run()

        assertTrue(api.puts("track").isEmpty())
        assertEquals("hosts-track", api.sessions.values.single().trackId)
        assertNotNull(dao.links[KIND_SESSION to 7L])
    }
}

private fun FakeSyncDao.putLinkBlocking(link: RemoteLink) = runBlocking { putLink(link) }
