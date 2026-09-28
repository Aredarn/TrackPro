package com.example.trackpro.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.TrackPublication
import com.example.trackpro.managerClasses.ESPDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v8 -> v9, the TrackBoard sync migration: a GPS-source column on sessions and two new tables.
 *
 * Built from the committed `schemas/.../8.json` — the version every current install is on —
 * validated against `9.json`, then reopened with the production [ESPDatabase] class, which is
 * the validation a driver's phone actually runs.
 */
@RunWith(AndroidJUnit4::class)
class Migration8To9Test {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ESPDatabase::class.java
    )

    @Before
    fun startWithoutADatabaseFile() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrating8to9_producesExactlyTheExportedSchema() {
        createVersion8Database()

        helper.runMigrationsAndValidate(TEST_DB, 9, true, *ESPDatabase.MIGRATIONS).close()
    }

    @Test
    fun migrating8to9_keepsEveryRow_andExistingSessionsHaveNoGpsSource() = runBlocking<Unit> {
        createVersion8Database()
        helper.runMigrationsAndValidate(TEST_DB, 9, true, *ESPDatabase.MIGRATIONS).close()

        withProductionDatabase { db ->
            val session = db.sessionDataDao().getSessionById(1L)!!
            assertEquals("Hungaroring - old", session.eventType)
            assertEquals(21.5, session.weatherTempC!!, 0.0)
            // The honest reading of a session recorded before the source was captured.
            assertNull(session.gpsSource)

            val sync = db.syncDao()
            assertEquals(listOf(1L), sync.getFinishedTrackSessions().map { it.id })
            assertEquals("01:42.35", sync.getLaps(1L).single().laptime)
            assertEquals(2, sync.getTrackPoints(1L).size)
        }
    }

    @Test
    fun theNewTablesWork_andDeletingATrackUnpublishesIt() = runBlocking<Unit> {
        createVersion8Database()
        helper.runMigrationsAndValidate(TEST_DB, 9, true, *ESPDatabase.MIGRATIONS).close()

        withProductionDatabase { db ->
            val sync = db.syncDao()

            sync.putLink(RemoteLink(RemoteLink.KIND_SESSION, 1L, "remote-1", "hash-1", 123L))
            assertEquals("remote-1", sync.getLink(RemoteLink.KIND_SESSION, 1L)!!.remoteId)

            sync.publish(TrackPublication(trackId = 1L, publishedAt = 1L))
            assertTrue(sync.observeIsPublished(1L).first())

            // The publication has a cascading foreign key: it cannot outlive its track.
            db.trackMainDao().deleteTrack(1L)
            assertFalse(sync.observeIsPublished(1L).first())
            assertTrue(sync.getPublishedTrackIds().isEmpty())
        }
    }

    @Test
    fun aNewSessionRecordsItsGpsSource() = runBlocking<Unit> {
        createVersion8Database()
        helper.runMigrationsAndValidate(TEST_DB, 9, true, *ESPDatabase.MIGRATIONS).close()

        withProductionDatabase { db ->
            val id = db.sessionDataDao().insertSession(
                com.example.trackpro.dataClasses.SessionData(
                    startTime = 1_800_000_000_000, endTime = null, eventType = "New",
                    vehicleId = 1L, trackId = 1L, gpsSource = "BLUETOOTH"
                )
            )
            assertEquals("BLUETOOTH", db.sessionDataDao().getSessionById(id)!!.gpsSource)
        }
    }

    private fun createVersion8Database() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(
                "INSERT INTO `vehicle_information_data` (`vehicleId`, `manufacturer`, `model`, `year`, " +
                    "`engineType`, `horsepower`, `torque`, `weight`, `topSpeed`, `acceleration`, `drivetrain`, " +
                    "`fuelType`, `tireType`, `fuelCapacity`, `transmission`, `suspensionType`) VALUES " +
                    "(1, 'Porsche', '911 GT3', 2023, 'F6', 510, 470, 1418.0, 318.0, 3.4, 'RWD', 'Petrol', " +
                    "'Slick', 64.0, 'PDK', NULL)"
            )
            execSQL(
                "INSERT INTO `track_main_data` (`trackId`, `trackName`, `totalLength`, `country`, `type`) " +
                    "VALUES (1, 'Hungaroring', 4.381, 'Hungary', 'Circuit')"
            )
            execSQL(
                "INSERT INTO `track_coordinates_data` (`id`, `trackId`, `latitude`, `longitude`, `altitude`, " +
                    "`isStartPoint`, `isSectorPoint`, `sectorIndex`) VALUES " +
                    "(1, 1, 47.5789, 19.2486, NULL, 1, 0, NULL), (2, 1, 47.5800, 19.2500, NULL, 0, 1, 0)"
            )
            execSQL(
                "INSERT INTO `session_data` (`id`, `startTime`, `endTime`, `eventType`, `vehicleId`, `trackId`, " +
                    "`weatherTempC`, `voided`) VALUES (1, 1700000000000, 1700000900000, 'Hungaroring - old', 1, 1, 21.5, 0)"
            )
            execSQL(
                "INSERT INTO `lap_time_data` (`id`, `sessionid`, `lapnumber`, `laptime`, `signalGap`) " +
                    "VALUES (1, 1, 1, '01:42.35', 0)"
            )
            close()
        }
    }

    private suspend fun withProductionDatabase(block: suspend (ESPDatabase) -> Unit) {
        val db = Room.databaseBuilder(context, ESPDatabase::class.java, TEST_DB)
            .addMigrations(*ESPDatabase.MIGRATIONS)
            .build()
        try {
            block(db)
        } finally {
            db.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    private companion object {
        const val TEST_DB = "migration-8-9-test.db"
    }
}
