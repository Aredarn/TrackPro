package com.example.trackpro.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.managerClasses.InterruptedSessions
import com.example.trackpro.managerClasses.utilities.LapStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sessions a dead process left open are closed on the next start, the way
 * SessionManager.endSession would have closed them, and then count as finished for sync.
 */
@RunWith(AndroidJUnit4::class)
class InterruptedSessionsTest {

    @get:Rule
    val database = InMemoryDatabaseRule()
    private val db get() = database.db

    private val start = 1_700_000_000_000L
    private val processStart = start + 3_600_000L

    private suspend fun newVehicle(): Long = db.vehicleInformationDAO().insertVehicle(vehicle())

    private fun point(lapId: Long, timestamp: Long?) = LapInfoData(
        lapid = lapId, lat = 47.0, lon = 19.0, alt = null, spd = 120f,
        latgforce = null, longforce = null, timestamp = timestamp
    )

    @Test
    fun circuitSession_endsAtItsLastPoint_andLosesOnlyTheUnfinishedLap() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle(), trackId = 3L, startTime = start))
        val done = db.lapTimeDataDAO().insert(lap(sessionId, 1, "01:42.35"))
        val cut = db.lapTimeDataDAO().insert(lap(sessionId, 2, LapStatus.IN_PROGRESS))
        db.lapInfoDataDAO().insert(point(done, start + 100_000))
        db.lapInfoDataDAO().insert(point(cut, start + 150_000))

        val closed = InterruptedSessions(db).closeAll(startedBefore = processStart)

        assertEquals(1, closed)
        // The cut-off lap's points still mark how long the session ran.
        assertEquals(start + 150_000, db.sessionDataDao().getSessionById(sessionId)!!.endTime)
        val laps = db.lapTimeDataDAO().getLapsForSession(sessionId)
        assertEquals(listOf(1), laps.map { it.lapnumber })
        assertTrue("an interrupted session is no longer treated as live",
            db.syncDao().getRunningSessionIds().isEmpty())
    }

    @Test
    fun dragSession_endsAtItsLastFix() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle(), startTime = start))
        db.rawGPSDataDao().insertAll(listOf(gpsFix(sessionId, start + 1_000), gpsFix(sessionId, start + 9_500)))

        InterruptedSessions(db).closeAll(startedBefore = processStart)

        assertEquals(start + 9_500, db.sessionDataDao().getSessionById(sessionId)!!.endTime)
    }

    @Test
    fun sessionWithNothingStored_endsAtItsStart() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle(), startTime = start))

        InterruptedSessions(db).closeAll(startedBefore = processStart)

        assertEquals(start, db.sessionDataDao().getSessionById(sessionId)!!.endTime)
    }

    @Test
    fun sessionStartedSinceTheProcessStarted_isLeftRecording() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle(), startTime = processStart + 1))
        val lapId = db.lapTimeDataDAO().insert(lap(sessionId, 1, LapStatus.IN_PROGRESS))

        val closed = InterruptedSessions(db).closeAll(startedBefore = processStart)

        assertEquals(0, closed)
        assertNull(db.sessionDataDao().getSessionById(sessionId)!!.endTime)
        assertEquals(lapId, db.lapTimeDataDAO().getLapsForSession(sessionId).single().id)
    }

    @Test
    fun finishedSession_isNotTouched() = runBlocking<Unit> {
        val sessionId = db.sessionDataDao().insertSession(session(newVehicle(), startTime = start))
        db.sessionDataDao().markSessionEnded(sessionId, start + 42_000)
        db.lapTimeDataDAO().insert(lap(sessionId, 1, LapStatus.IN_PROGRESS))

        val closed = InterruptedSessions(db).closeAll(startedBefore = processStart)

        assertEquals(0, closed)
        assertEquals(start + 42_000, db.sessionDataDao().getSessionById(sessionId)!!.endTime)
        assertEquals(1, db.lapTimeDataDAO().getLapCountForSession(sessionId))
    }
}
