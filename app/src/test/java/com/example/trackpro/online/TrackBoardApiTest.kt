package com.example.trackpro.online

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Response bodies below are shaped like TrackBoard's real ones, extra fields included. */
class TrackBoardApiTest {

    private lateinit var server: MockWebServer
    private var baseUrl: String? = null
    private lateinit var api: OkHttpTrackBoardApi

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        // A trailing slash on the configured address must not produce "//api".
        baseUrl = server.url("/").toString()
        api = OkHttpTrackBoardApi(baseUrl = { baseUrl })
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun session() = SessionWrite(
        name = "Test", startedAt = "2026-09-28T10:00:00Z", trackId = "t-1",
        gpsSource = ApiGpsSource.PhoneGps, visibility = ApiSessionVisibility.Ranked,
        laps = listOf(LapWrite(lapNumber = 1, timeMs = 90_000)),
    )

    @Test
    fun `a put targets the v1 route with the token and contract-cased enums`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":"s-1","laps":[]}"""))

        val created = api.putSession("access-token", "s-1", session())

        val request = server.takeRequest()
        assertTrue(created)
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/sessions/s-1", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"gpsSource\":\"PhoneGps\""))
        assertTrue(body, body.contains("\"visibility\":\"Ranked\""))
    }

    @Test
    fun `200 means replaced rather than created`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertFalse(api.putSession("t", "s-1", session()))
    }

    @Test
    fun `a conflict surfaces its stable code and the server's explanation`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(409).setHeader("Content-Type", "application/problem+json").setBody(
                """{"type":"https://tools.ietf.org/html/rfc9110#section-15.5.10","title":"Request conflicts with the current state",
                   "status":409,"detail":"This track already has ranked laps, so its points can no longer change.",
                   "instance":"PUT /api/v1/tracks/x","code":"TrackGeometryLocked","traceId":"00-abc"}"""
            )
        )
        try {
            api.putTrack("t", "x", TrackWrite("T", "HU", ApiTrackType.Circuit, null, ApiTrackVisibility.Published, emptyList()))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(409, e.status)
            assertEquals("TrackGeometryLocked", e.code)
            assertEquals("This track already has ranked laps, so its points can no longer change.", e.message)
        }
    }

    @Test
    fun `a validation failure names the first rejected field`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"title":"One or more validation errors occurred.","status":400,
                   "errors":{"Password":["Passwords must be at least 12 characters."]}}"""
            )
        )
        try {
            api.register(RegisterRequest("a@b.c", "A", "short"))
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.status)
            assertEquals("Passwords must be at least 12 characters.", e.message)
        }
    }

    @Test
    fun `an unknown or unpublished leaderboard reads as none`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":404}"""))
        assertNull(api.getLeaderboard(null, "t-1", 50))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `a leaderboard parses, ignoring fields the app does not use`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"trackId":"t-1","trackName":"Hungaroring","someFutureField":true,
                   "entries":[{"rank":1,"userId":"u-1","displayName":"Alice","lapTimeMs":91500,"gapToLeaderMs":0,
                     "sectors":[{"sectorIndex":0,"splitMs":42000}],
                     "vehicle":{"manufacturer":"Porsche","model":"911 GT3","year":2023},
                     "setAt":"2026-09-28T10:00:00+00:00","gpsSource":"Bluetooth"}],
                   "me":null}"""
            )
        )
        val board = api.getLeaderboard("t", "t-1", 10)!!
        assertEquals("/api/v1/tracks/t-1/leaderboard?limit=10", server.takeRequest().path)
        assertEquals("Hungaroring", board.trackName)
        assertEquals(ApiGpsSource.Bluetooth, board.entries.single().gpsSource)
        assertEquals(42_000, board.entries.single().sectors.single().splitMs)
        assertNull(board.me)
    }

    @Test
    fun `a session reads back with its laps, ignoring the leaderboard fields`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id":"s-1","name":"Time Attack","startedAt":"2026-09-28T10:00:00.25+00:00",
                   "endedAt":"2026-09-28T10:20:00+00:00","trackId":"t-1","trackName":"Kakucs","vehicleId":null,
                   "gpsSource":"Wifi","visibility":"Ranked","voided":false,"lapCount":1,"bestLapMs":41910,
                   "weather":{"tempC":18.5,"humidityPct":60,"precipitationMm":null,"weatherCode":2,
                     "windKph":null,"windDirDeg":null,"pressureHpa":null},"appVersion":"1.1",
                   "laps":[{"lapNumber":1,"timeMs":41910,"signalGap":false,
                     "sectors":[{"sectorIndex":0,"splitMs":20000}],"countsForLeaderboard":true,"leaderboardRank":3}],
                   "createdAt":"2026-09-28T10:21:00+00:00","updatedAt":"2026-09-28T10:21:00+00:00"}"""
            )
        )

        val session = api.getSession("t", "s-1")!!

        assertEquals("/api/v1/sessions/s-1", server.takeRequest().path)
        assertEquals(ApiGpsSource.Wifi, session.gpsSource)
        assertEquals(18.5, session.weather!!.tempC!!, 0.0)
        assertEquals(20_000, session.laps.single().sectors.single().splitMs)
        // .NET's offset form, which Instant.parse alone rejects before API 34.
        assertEquals(1_790_589_600_250L, AccountRestore.parseInstant(session.startedAt))
    }

    @Test
    fun `a session that is gone reads as none`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":404}"""))
        assertNull(api.getSession("t", "s-1"))
    }

    @Test
    fun `listings ask for the driver's own records, a page at a time`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"items":[],"page":2,"pageSize":100,"totalCount":0}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"items":[{"id":"s-1","name":"x","startedAt":"2026-09-28T10:00:00+00:00","trackId":"t-1",
                   "vehicleId":null,"gpsSource":"Wifi","visibility":"Ranked","voided":false,"lapCount":4,"bestLapMs":1}],
                   "page":1,"pageSize":100,"totalCount":1,"totalPages":1,"hasNext":false,"hasPrevious":false}"""
            )
        )

        api.listMyTracks("t", 2, 100)
        val sessions = api.listSessions("t", 1, 100)

        assertEquals("/api/v1/tracks?mine=true&page=2&pageSize=100", server.takeRequest().path)
        assertEquals("/api/v1/sessions?page=1&pageSize=100", server.takeRequest().path)
        assertEquals(4, sessions.items.single().lapCount)
    }

    @Test
    fun `deleting something already gone succeeds`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        api.deleteSession("t", "gone")
    }

    @Test
    fun `no configured server is a network problem, not a crash`() = runBlocking {
        baseUrl = "  "
        try {
            api.getLeaderboard(null, "t-1", 10)
            fail("expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals("No TrackBoard server is set. Add one in Settings.", e.message)
        }
    }

    @Test
    fun `an unreachable server is a network problem`() = runBlocking {
        server.shutdown()
        try {
            api.getLeaderboard(null, "t-1", 10)
            fail("expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals("Could not reach the TrackBoard server.", e.message)
        }
    }
}
