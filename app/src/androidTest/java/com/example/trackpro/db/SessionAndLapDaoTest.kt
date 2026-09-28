package com.example.trackpro.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.managerClasses.utilities.LapStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The session -> lap -> sector/GPS chain: the rows a track day produces and the queries the
 * session list, lap detail and timing screens run over them.
 */
@RunWith(AndroidJUnit4::class)
class SessionAndLapDaoTest {

    @get:Rule
    val database = InMemoryDatabaseRule()
    private val db get() = database.db

    private suspend fun newVehicle(): Long = db.vehicleInformationDAO().insertVehicle(vehicle())

    // ─────────────────────────────────────────────
    // Sessions
    // ─────────────────────────────────────────────

    @Test
    fun session_roundTrips_withNoWeatherUntilCaptured() = runBlocking<Unit> {
        val vehicleId = newVehicle()
        val id = db.sessionDataDao().insertSession(session(vehicleId, trackId = 7L))

        val loaded = db.sessionDataDao().getSessionById(id)

        assertNotNull(loaded)
        assertEquals(vehicleId, loaded!!.vehicleId)
        assertEquals(7L, loaded.trackId)
        assertNull("a session that hasn't ended has no endTime", loaded.endTime)
        assertNull("weather is only present once captured", loaded.weatherTempC)
        assertNull(loaded.weatherCode)
    }

    @Test
    fun updateSessionWeather_leavesTheRestOfTheRowAlone() = runBlocking<Unit> {
        // Weather arrives asynchronously and can land right as a session is closed. Both
        // writes are targeted at their own columns precisely so neither can clobber the
        // other; this pins that the endTime survives a weather write landing after it.
        val id = db.sessionDataDao().insertSession(session(newVehicle()))
        val started = db.sessionDataDao().getSessionById(id)!!
        db.sessionDataDao().markSessionEnded(id, 1_700_003_600_000L)

        db.sessionDataDao().updateSessionWeather(
            sessionId = id,
            tempC = 21.5,
            humidityPct = 40,
            precipitationMm = 0.0,
            weatherCode = 1,
            windKph = 12.0,
            windDirDeg = 180,
            pressureHpa = 1013.2
        )

        val loaded = db.sessionDataDao().getSessionById(id)!!
        assertEquals(1_700_003_600_000L, loaded.endTime)
        assertEquals(21.5, loaded.weatherTempC!!, 1e-9)
        assertEquals(40, loaded.weatherHumidityPct)
        assertEquals(0.0, loaded.weatherPrecipitationMm!!, 1e-9)
        assertEquals(1, loaded.weatherCode)
        assertEquals(12.0, loaded.weatherWindKph!!, 1e-9)
        assertEquals(180, loaded.weatherWindDirDeg)
        assertEquals(1013.2, loaded.weatherPressureHpa!!, 1e-9)
        assertEquals("nothing else on the row changed", started.eventType, loaded.eventType)
    }

    @Test
    fun markSessionEnded_doesNotClobberWeatherWrittenBeforeIt() = runBlocking<Unit> {
        // The other order, which is the one that used to lose data: closing a session read
        // the whole row and wrote it back, so a weather lookup that landed after that read
        // was overwritten with nulls. Both writers now touch only their own columns.
        val id = db.sessionDataDao().insertSession(session(newVehicle()))
        db.sessionDataDao().updateSessionWeather(id, 18.0, 55, 0.2, 61, 20.0, 270, 1008.0)

        db.sessionDataDao().markSessionEnded(id, 1_700_003_600_000L)

        val loaded = db.sessionDataDao().getSessionById(id)!!
        assertEquals(1_700_003_600_000L, loaded.endTime)
        assertEquals("weather survived the session being closed", 18.0, loaded.weatherTempC!!, 1e-9)
        assertEquals(61, loaded.weatherCode)
    }

    @Test
    fun markSessionEnded_closesOnlyTheNamedSession() = runBlocking<Unit> {
        // SessionManager used to close whichever session started last, which with a drag
        // screen and a time attack screen both in the back stack could be the wrong one.
        val vehicleId = newVehicle()
        val first = db.sessionDataDao().insertSession(session(vehicleId))
        val second = db.sessionDataDao().insertSession(session(vehicleId))

        db.sessionDataDao().markSessionEnded(first, 1_700_003_600_000L)

        assertEquals(1_700_003_600_000L, db.sessionDataDao().getSessionById(first)!!.endTime)
        assertNull(
            "the session that was not named stays open",
            db.sessionDataDao().getSessionById(second)!!.endTime
        )
    }

    @Test
    fun dragAndTrackSessionLists_partitionOnTrackId() = runBlocking<Unit> {
        // Drag sessions have no track; historically some were written with trackId = -1
        // rather than null, and both must still land on the drag side.
        val vehicleId = newVehicle()
        val dragNull = db.sessionDataDao().insertSession(session(vehicleId, trackId = null))
        val dragMinusOne = db.sessionDataDao().insertSession(session(vehicleId, trackId = -1L))
        val onTrack = db.sessionDataDao().insertSession(session(vehicleId, trackId = 5L))

        val dragSessions = db.sessionDataDao().getAllDragSessionsWithVehicles().first()
        val trackSessions = db.sessionDataDao().getAllTrackSessionsWithVehicles().first()

        assertEquals(setOf(dragNull, dragMinusOne), dragSessions.map { it.sessionId }.toSet())
        assertEquals(listOf(onTrack), trackSessions.map { it.sessionId })
        assertEquals("joined vehicle columns come through", "Mazda", dragSessions.first().manufacturer)
        assertEquals("MX-5", trackSessions.first().model)
    }

    // ─────────────────────────────────────────────
    // Laps
    // ─────────────────────────────────────────────

    @Test
    fun updateLapTime_closesAnInProgressLap() = runBlocking<Unit> {
        // The timing screen inserts a lap as IN PROGRESS when it starts and stamps the time
        // when the finish line is crossed.
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        val lapId = db.lapTimeDataDAO().insert(lap(sessionId, number = 1, time = LapStatus.IN_PROGRESS))

        db.lapTimeDataDAO().updateLapTime(lapId, "01:42.35")

        assertEquals("01:42.35", db.lapTimeDataDAO().getLapById(lapId)?.laptime)
        assertEquals(1, db.lapTimeDataDAO().getLapCountForSession(sessionId))
    }

    @Test
    fun completeLap_stampsTheTimeAndTheGapFlagTogether() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        val clean = db.lapTimeDataDAO().insert(lap(sessionId, 1, LapStatus.IN_PROGRESS))
        val gappy = db.lapTimeDataDAO().insert(lap(sessionId, 2, LapStatus.IN_PROGRESS))

        db.lapTimeDataDAO().completeLap(clean, "01:42.35", signalGap = false)
        db.lapTimeDataDAO().completeLap(gappy, "03:21.70", signalGap = true)

        assertEquals("01:42.35", db.lapTimeDataDAO().getLapById(clean)?.laptime)
        assertEquals(false, db.lapTimeDataDAO().getLapById(clean)?.signalGap)
        assertEquals("03:21.70", db.lapTimeDataDAO().getLapById(gappy)?.laptime)
        assertEquals(true, db.lapTimeDataDAO().getLapById(gappy)?.signalGap)
    }

    @Test
    fun fastestCleanLaps_areThisTracksCompletedGapFreeLaps_fastestFirst() = runBlocking<Unit> {
        // What the live delta can use as a track best: nothing unfinished, nothing with a GPS
        // gap in it, nothing from another track.
        val vehicleId = newVehicle()
        val trackId = db.trackMainDao().insertTrackMainDataDAO(track("Home"))
        val otherTrackId = db.trackMainDao().insertTrackMainDataDAO(track("Away"))
        val here = db.sessionDataDao().insertSession(session(vehicleId, trackId))
        val there = db.sessionDataDao().insertSession(session(vehicleId, otherTrackId))

        db.lapTimeDataDAO().insert(lap(here, 1, "01:44.00"))
        db.lapTimeDataDAO().insert(lap(here, 2, "01:42.35"))
        db.lapTimeDataDAO().insert(lap(here, 3, LapStatus.IN_PROGRESS))
        val gappy = db.lapTimeDataDAO().insert(lap(here, 4, LapStatus.IN_PROGRESS))
        db.lapTimeDataDAO().completeLap(gappy, "01:30.00", signalGap = true)
        db.lapTimeDataDAO().insert(lap(there, 1, "01:10.00"))

        val fastest = db.lapTimeDataDAO().getFastestCleanLapsForTrack(trackId, limit = 10)

        assertEquals(listOf("01:42.35", "01:44.00"), fastest.map { it.laptime })
        assertEquals(1, db.lapTimeDataDAO().getFastestCleanLapsForTrack(trackId, limit = 1).size)
    }

    @Test
    fun newLaps_haveNoGapFlagByDefault() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        val lapId = db.lapTimeDataDAO().insert(lap(sessionId, 1, "01:42.35"))

        assertEquals(false, db.lapTimeDataDAO().getLapById(lapId)?.signalGap)
    }

    @Test
    fun completedLaps_excludeUnfinishedAndInvalid_inLapOrder() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        db.lapTimeDataDAO().insert(lap(sessionId, 3, "01:44.00"))
        db.lapTimeDataDAO().insert(lap(sessionId, 1, "01:42.35"))
        db.lapTimeDataDAO().insert(lap(sessionId, 2, LapStatus.IN_PROGRESS))
        db.lapTimeDataDAO().insert(lap(sessionId, 4, LapStatus.INVALID))

        val completed = db.lapTimeDataDAO().getCompletedLapsForSession(sessionId).first()

        assertEquals(listOf(1, 3), completed.map { it.lapnumber })
    }

    @Test
    fun bestLapForTrack_spansSessions_andIgnoresUnfinishedLaps() = runBlocking<Unit> {
        val vehicleId = newVehicle()
        val trackId = db.trackMainDao().insertTrackMainDataDAO(track("Home"))
        val otherTrackId = db.trackMainDao().insertTrackMainDataDAO(track("Away"))
        val first = db.sessionDataDao().insertSession(session(vehicleId, trackId))
        val second = db.sessionDataDao().insertSession(session(vehicleId, trackId))
        val elsewhere = db.sessionDataDao().insertSession(session(vehicleId, otherTrackId))

        db.lapTimeDataDAO().insert(lap(first, 1, "01:43.02"))
        db.lapTimeDataDAO().insert(lap(first, 2, LapStatus.IN_PROGRESS))
        db.lapTimeDataDAO().insert(lap(second, 1, "01:41.93"))
        db.lapTimeDataDAO().insert(lap(second, 2, LapStatus.INVALID))
        // Faster, but on a different track - must not be picked.
        db.lapTimeDataDAO().insert(lap(elsewhere, 1, "01:30.00"))

        val best = db.lapTimeDataDAO().getBestLapForTrack(trackId)

        assertEquals("01:41.93", best?.laptime)
        assertEquals(second, best?.sessionid)
    }

    @Test
    fun bestLapForSession_ignoresLapsWithNoTime() = runBlocking<Unit> {
        // Without the filter these sort after any real time but still win when they are the
        // only rows - a session interrupted before its first lap closed would report one of
        // them as its best.
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        db.lapTimeDataDAO().insert(lap(sessionId, 1, LapStatus.IN_PROGRESS))
        db.lapTimeDataDAO().insert(lap(sessionId, 2, LapStatus.INVALID))

        assertNull(db.lapTimeDataDAO().getBestLapForSession(sessionId))

        db.lapTimeDataDAO().insert(lap(sessionId, 3, "01:42.35"))
        assertEquals("01:42.35", db.lapTimeDataDAO().getBestLapForSession(sessionId)?.laptime)
    }

    @Test
    fun bestLap_ordersByTheFixedWidthTimeString() = runBlocking<Unit> {
        // Lap times are stored as "MM:SS.hh" text and the best-lap queries ORDER BY that
        // text. That only works because the format is zero-padded to a fixed width - a
        // 10-minute lap must not sort ahead of a 9-minute one the way "10" < "9" would.
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        db.lapTimeDataDAO().insert(lap(sessionId, 1, "10:00.00"))
        db.lapTimeDataDAO().insert(lap(sessionId, 2, "09:59.99"))

        assertEquals("09:59.99", db.lapTimeDataDAO().getBestLapForSession(sessionId)?.laptime)
    }

    // ─────────────────────────────────────────────
    // Cascades
    // ─────────────────────────────────────────────

    @Test
    fun deletingASession_removesItsLapsSectorsTraceAndGps() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        val lapId = db.lapTimeDataDAO().insert(lap(sessionId, 1, "01:42.35"))
        db.sectorTimeDataDAO().insert(SectorTimeData(lapid = lapId, sectorIndex = 0, splitTimeMs = 32_410))
        db.lapInfoDataDAO().insert(
            LapInfoData(lapid = lapId, lat = 47.0, lon = 19.0, alt = null, spd = 100f, latgforce = null, longforce = null)
        )
        db.rawGPSDataDao().insertAll(listOf(gpsFix(sessionId, 1L), gpsFix(sessionId, 2L)))

        db.sessionDataDao().deleteSessionById(sessionId)

        assertNull(db.sessionDataDao().getSessionById(sessionId))
        assertTrue(db.lapTimeDataDAO().getLapsForSession(sessionId).isEmpty())
        assertTrue(db.sectorTimeDataDAO().getSectorTimesForLap(lapId).isEmpty())
        assertTrue(db.lapInfoDataDAO().getLapData(lapId).isEmpty())
        assertTrue(db.rawGPSDataDao().getGPSDataBySession(sessionId).isEmpty())
    }

    @Test
    fun deletingAVehicle_removesItsSessions() = runBlocking<Unit> {
        // This is why the garage's delete dialog warns about it.
        val vehicleId = newVehicle()
        val sessionId = db.sessionDataDao().insertSession(session(vehicleId))

        db.vehicleInformationDAO().deleteVehicle(vehicleId)

        assertNull(db.sessionDataDao().getSessionById(sessionId))
    }

    @Test
    fun sectorSplits_comeBackInSectorOrder() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle()))
        val lapId = db.lapTimeDataDAO().insert(lap(sessionId, 1, "01:42.35"))
        db.sectorTimeDataDAO().insert(SectorTimeData(lapid = lapId, sectorIndex = 1, splitTimeMs = 35_100))
        db.sectorTimeDataDAO().insert(SectorTimeData(lapid = lapId, sectorIndex = 0, splitTimeMs = 32_410))

        val splits = db.sectorTimeDataDAO().getSectorTimesForLap(lapId)

        assertEquals(listOf(0, 1), splits.map { it.sectorIndex })
        assertEquals(listOf(32_410L, 35_100L), splits.map { it.splitTimeMs })
    }
}
