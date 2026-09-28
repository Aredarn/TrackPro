package com.example.trackpro.online

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.TrackPublication
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.TrackSeeder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * The app and a real TrackBoard server, end to end: real HTTP, real JSON, the real sync engine,
 * a real Room database. The unit tests check each side against a reading of the contract; this
 * checks the two sides against each other.
 *
 * Needs a running server, so it is skipped unless one is named:
 *
 *     gradlew connectedDebugAndroidTest
 *       -Pandroid.testInstrumentationRunnerArguments.trackboardUrl=http://10.0.2.2:5000
 *
 * (10.0.2.2 is the emulator's alias for the host machine.)
 */
@RunWith(AndroidJUnit4::class)
class TrackBoardEndToEndTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val serverUrl: String? =
        InstrumentationRegistry.getArguments().getString("trackboardUrl")

    private val databases = mutableListOf<ESPDatabase>()

    @Before
    fun requireServer() {
        assumeTrue("No trackboardUrl instrumentation argument; skipping end-to-end tests.", serverUrl != null)
    }

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
    }

    private class MemoryTokens : TokenStore {
        var session: StoredSession? = null
        override fun load() = session
        override fun save(session: StoredSession) { this.session = session }
        override fun clear() { session = null }
    }

    /** One driver: their own phone database, account and sync engine. */
    private inner class Driver(name: String) {
        val db = Room.inMemoryDatabaseBuilder(context, ESPDatabase::class.java).build().also { databases += it }
        val api = OkHttpTrackBoardApi(baseUrl = { serverUrl })
        val auth = AuthRepository(api, MemoryTokens())
        val engine = SyncEngine(
            api = api,
            auth = auth,
            dao = db.syncDao(),
            premade = BundledPremadeTracks(context),
            sharingEnabled = { true },
            appVersion = "e2e",
        )

        init {
            runBlocking {
                auth.register("e2e-${UUID.randomUUID()}@example.com", name, "correct-horse-battery-staple")
            }
        }
    }

    private suspend fun ESPDatabase.recordSession(
        trackId: Long,
        vehicleId: Long?,
        lapTimes: List<String>,
        gpsSource: String = "PHONE_GPS",
    ): Long {
        val sessionId = sessionDataDao().insertSession(
            SessionData(
                startTime = System.currentTimeMillis() - 3_600_000,
                endTime = System.currentTimeMillis() - 1_800_000,
                eventType = "E2E session",
                vehicleId = vehicleId,
                trackId = trackId,
                gpsSource = gpsSource,
            )
        )
        lapTimes.forEachIndexed { i, time ->
            val lapId = lapTimeDataDAO().insert(LapTimeData(sessionid = sessionId, lapnumber = i + 1, laptime = time))
            sectorTimeDataDAO().insert(SectorTimeData(lapid = lapId, sectorIndex = 0, splitTimeMs = 40_000))
        }
        return sessionId
    }

    private suspend fun ESPDatabase.addVehicle(): Long = vehicleInformationDAO().insertVehicle(
        VehicleInformationData(
            manufacturer = "Porsche", model = "911 GT3", year = 2023, engineType = "F6", horsepower = 510,
            torque = 470, weight = 1418.0, topSpeed = 318.0, acceleration = 3.4, drivetrain = "RWD",
            fuelType = "Petrol", tireType = "Slick", fuelCapacity = 64.0, transmission = "PDK", suspensionType = null
        )
    )

    @Test
    fun aSessionOnAPublishedTrackReachesTheLeaderboard_andVoidingTakesItDown() = runBlocking<Unit> {
        val driver = Driver("E2E Driver")
        val db = driver.db

        val trackId = db.trackMainDao().insertTrackMainDataDAO(
            TrackMainData(trackName = "E2E Loop ${UUID.randomUUID()}".take(40), totalLength = 1.2, country = "Hungary", type = "Circuit")
        )
        db.trackCoordinatesDao().insertTrack(listOf(
            TrackCoordinatesData(trackId = trackId, latitude = 47.1, longitude = 19.1, altitude = 120.0, isStartPoint = true),
            TrackCoordinatesData(trackId = trackId, latitude = 47.101, longitude = 19.1, altitude = 121.0, isSectorPoint = true, sectorIndex = 0),
            TrackCoordinatesData(trackId = trackId, latitude = 47.101, longitude = 19.101, altitude = 122.0),
        ))
        db.syncDao().publish(TrackPublication(trackId, System.currentTimeMillis()))
        val vehicleId = db.addVehicle()
        val sessionId = db.recordSession(trackId, vehicleId, listOf("01:35.00", "01:31.50", "IN PROGRESS"))

        val first = driver.engine.run()
        assertEquals("problem: ${first.problem}", 0, first.failed)
        assertEquals(1, first.uploaded)

        val remoteTrack = db.syncDao().getLink(RemoteLink.KIND_TRACK, trackId)!!.remoteId
        val board = driver.api.getLeaderboard(null, remoteTrack, 10)!!
        val entry = board.entries.single()
        assertEquals("E2E Driver", entry.displayName)
        assertEquals(91_500, entry.lapTimeMs)
        assertEquals(ApiGpsSource.PhoneGps, entry.gpsSource)
        assertEquals("911 GT3", entry.vehicle!!.model)
        assertEquals(40_000, entry.sectors.single().splitMs)

        // Nothing changed: nothing is re-sent.
        assertEquals(1, driver.engine.run().unchanged)

        // Voiding re-uploads the session, and the server drops it from the board.
        db.sessionDataDao().setVoided(sessionId, true)
        assertEquals(1, driver.engine.run().uploaded)
        assertTrue(driver.api.getLeaderboard(null, remoteTrack, 10)!!.entries.isEmpty())
    }

    @Test
    fun twoDriversOnAPremadeTrackShareOneLeaderboard() = runBlocking<Unit> {
        val alice = Driver("E2E Alice")
        val bob = Driver("E2E Bob")

        // Each phone seeds the bundled tracks itself, exactly as the app does on launch.
        TrackSeeder.syncPremadeTracks(context, alice.db)
        TrackSeeder.syncPremadeTracks(context, bob.db)
        val aliceTrack = alice.db.idOf("Hungaroring")
        val bobTrack = bob.db.idOf("Hungaroring")

        alice.db.recordSession(aliceTrack, null, listOf("01:48.20"), gpsSource = "BLUETOOTH")
        bob.db.recordSession(bobTrack, null, listOf("01:47.90"), gpsSource = "PHONE_GPS")

        assertEquals(0, alice.engine.run().failed)
        assertEquals(0, bob.engine.run().failed)

        val aliceRemote = alice.db.syncDao().getLink(RemoteLink.KIND_PREMADE_TRACK, aliceTrack)!!.remoteId
        val bobRemote = bob.db.syncDao().getLink(RemoteLink.KIND_PREMADE_TRACK, bobTrack)!!.remoteId
        assertEquals("both installs must derive the same server track", aliceRemote, bobRemote)

        val board = alice.api.getLeaderboard(alice.auth.currentAccessTokenOrNull(), aliceRemote, 50)!!
        val names = board.entries.map { it.displayName }
        assertTrue(names.toString(), "E2E Alice" in names && "E2E Bob" in names)
        // Bob's phone lap is faster, and both GPS sources share one board.
        assertTrue(names.indexOf("E2E Bob") < names.indexOf("E2E Alice"))
        assertNotNull(board.me)
    }

    private suspend fun ESPDatabase.idOf(trackName: String): Long =
        syncDao().let { dao ->
            (1L..50L).first { id -> dao.getTrack(id)?.trackName == trackName }
        }
}
