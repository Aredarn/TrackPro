package com.example.trackpro.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The raw GPS trace behind every drag run and session review. */
@RunWith(AndroidJUnit4::class)
class RawGpsDaoTest {

    @get:Rule
    val database = InMemoryDatabaseRule()
    private val db get() = database.db

    private suspend fun newSession(): Long {
        val vehicleId = db.vehicleInformationDAO().insertVehicle(vehicle())
        return db.sessionDataDao().insertSession(session(vehicleId))
    }

    @Test
    fun fixes_comeBackInTimestampOrder_regardlessOfInsertOrder() = runBlocking<Unit> {
        // Everything downstream - 0-60 detection, the speed chart, the map trace - walks
        // this list assuming time only moves forward.
        val sessionId = newSession()
        db.rawGPSDataDao().insertAll(
            listOf(
                gpsFix(sessionId, timestamp = 300L, speed = 30f),
                gpsFix(sessionId, timestamp = 100L, speed = 10f),
                gpsFix(sessionId, timestamp = 200L, speed = 20f)
            )
        )

        val fixes = db.rawGPSDataDao().getGPSDataBySession(sessionId)

        assertEquals(listOf(100L, 200L, 300L), fixes.map { it.timestamp })
        assertEquals(listOf(10f, 20f, 30f), fixes.map { it.speed })
    }

    @Test
    fun validFlag_roundTripsAsNullableBoolean() = runBlocking<Unit> {
        // `valid` was added in schema v3 -> v4 as a nullable column; fixes recorded before
        // then, and from sources that don't report it, have no value rather than false.
        val sessionId = newSession()
        db.rawGPSDataDao().insert(gpsFix(sessionId, timestamp = 1L, valid = true))
        db.rawGPSDataDao().insert(gpsFix(sessionId, timestamp = 2L, valid = false))
        db.rawGPSDataDao().insert(gpsFix(sessionId, timestamp = 3L, valid = null))

        val fixes = db.rawGPSDataDao().getGPSDataBySession(sessionId)

        assertEquals(true, fixes[0].valid)
        assertEquals(false, fixes[1].valid)
        assertNull(fixes[2].valid)
    }

    @Test
    fun deleteGPSDataBySession_leavesOtherSessionsAlone() = runBlocking<Unit> {
        val cleared = newSession()
        val kept = newSession()
        db.rawGPSDataDao().insertAll(listOf(gpsFix(cleared, 1L), gpsFix(cleared, 2L)))
        db.rawGPSDataDao().insertAll(listOf(gpsFix(kept, 1L)))

        db.rawGPSDataDao().deleteGPSDataBySession(cleared)

        assertTrue(db.rawGPSDataDao().getGPSDataBySession(cleared).isEmpty())
        assertEquals(1, db.rawGPSDataDao().getGPSDataBySession(kept).size)
    }
}
