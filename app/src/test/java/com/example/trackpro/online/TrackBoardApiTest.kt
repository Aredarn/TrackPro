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
