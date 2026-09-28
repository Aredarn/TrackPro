package com.example.trackpro.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackpro.dataClasses.SectorTimeData
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
 * Upgrading an installed database through every schema version.
 *
 * Schema export was only switched on at version 5, so there is no `1.json`..`4.json` for
 * [MigrationTestHelper.createDatabase] to build the old versions from. Instead the v1
 * database is created here from the DDL Room generated for the v1 entities (the v5 export
 * with everything the four migrations add taken back out), seeded with a track day's worth
 * of rows, and then migrated two ways:
 *
 *  - [migratingFromV1_producesExactlyTheExportedLatestSchema] runs the production migration
 *    list through the helper, which validates every table, index and foreign key against
 *    the newest `schemas/.../N.json` and fails on any table left behind.
 *  - [migratingFromV1_keepsEveryRow_andTheNewSchemaWorks] opens the same v1 file with the
 *    real [ESPDatabase] class - Room's own generated validation, the one a user's phone
 *    runs - and reads everything back through the DAOs.
 *
 * Adding a migration in future: bump the version and [LATEST_VERSION], build once so the new
 * `schemas/.../N.json` is written, commit it, add the Migration to [ESPDatabase.MIGRATIONS],
 * and add a test here that calls `helper.createDatabase(TEST_DB, N - 1)` then
 * `helper.runMigrationsAndValidate(TEST_DB, N, true, *ESPDatabase.MIGRATIONS)` - from
 * version 5 onwards the exported schemas make that the whole test, as
 * [migrating5to6_addsTheGpsGapFlag_andExistingLapsReadAsNoGap] shows.
 */
@RunWith(AndroidJUnit4::class)
class EspDatabaseMigrationTest {

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
    fun migratingFromV1_producesExactlyTheExportedLatestSchema() {
        createVersion1Database()

        // Migrates from the file's own version (1) through every step to the current version
        // and validates the result against that version's export. validateDroppedTables =
        // true also fails if the scratch table from the 2 -> 3 rebuild
        // (track_coordinates_data_new) were still around.
        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, LATEST_VERSION, true, *ESPDatabase.MIGRATIONS
        )

        assertEquals(LATEST_VERSION, migrated.version)
        migrated.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrating6to7_timestampsTracePoints_andExistingPointsHaveNone() = runBlocking<Unit> {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(V1_ROWS[0]) // vehicle
            execSQL("INSERT INTO `session_data` (`id`, `startTime`, `endTime`, `eventType`, `vehicleId`, `trackId`) VALUES (1, 1700000000000, NULL, 'Test', 1, NULL)")
            execSQL("INSERT INTO `lap_time_data` (`id`, `sessionid`, `lapnumber`, `laptime`, `signalGap`) VALUES (1, 1, 1, '01:42.35', 0)")
            execSQL("INSERT INTO `lap_info_data` (`id`, `lapid`, `lat`, `lon`, `alt`, `spd`, `latgforce`, `longforce`) VALUES (1, 1, 47.0, 19.0, NULL, 100.0, NULL, NULL)")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, *ESPDatabase.MIGRATIONS).close()

        withProductionDatabase { db ->
            val points = db.lapInfoDataDAO().getLapData(1L)
            assertEquals(1, points.size)
            assertNull("a point recorded before the column existed has no timestamp", points[0].timestamp)

            db.lapInfoDataDAO().insert(
                com.example.trackpro.dataClasses.LapInfoData(
                    lapid = 1L, lat = 47.001, lon = 19.0, alt = null, spd = 101f,
                    latgforce = null, longforce = null, timestamp = 1_700_000_000_100L
                )
            )
            assertEquals(1_700_000_000_100L, db.lapInfoDataDAO().getLapData(1L)[1].timestamp)
        }
    }

    @Test
    fun migrating5to6_addsTheGpsGapFlag_andExistingLapsReadAsNoGap() = runBlocking<Unit> {
        // Built from the committed 5.json - the version every current install is on.
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(V1_ROWS[0]) // vehicle
            execSQL("INSERT INTO `session_data` (`id`, `startTime`, `endTime`, `eventType`, `vehicleId`, `trackId`) VALUES (1, 1700000000000, NULL, 'Test', 1, NULL)")
            execSQL("INSERT INTO `lap_time_data` (`id`, `sessionid`, `lapnumber`, `laptime`) VALUES (1, 1, 1, '01:42.35')")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 6, true, *ESPDatabase.MIGRATIONS).close()

        withProductionDatabase { db ->
            val lap = db.lapTimeDataDAO().getLapById(1L)!!
            assertEquals("01:42.35", lap.laptime)
            assertFalse("a lap recorded before the flag existed reads as no gap", lap.signalGap)

            // And the new column takes a write.
            val next = db.lapTimeDataDAO().insert(
                com.example.trackpro.dataClasses.LapTimeData(sessionid = 1L, lapnumber = 2, laptime = "IN PROGRESS")
            )
            db.lapTimeDataDAO().completeLap(next, "01:55.10", signalGap = true)
            assertTrue(db.lapTimeDataDAO().getLapById(next)!!.signalGap)
        }
    }

    @Test
    fun migratingFromV1_keepsEveryRow_andTheNewSchemaWorks() = runBlocking<Unit> {
        createVersion1Database()

        withProductionDatabase { db ->
            // ── Rows recorded at v1 read back through the v5 entities ──
            val session = db.sessionDataDao().getSessionById(1L)!!
            assertEquals(1_700_000_000_000L, session.startTime)
            assertEquals(1_700_003_600_000L, session.endTime)
            assertEquals(1L, session.vehicleId)
            assertEquals(1L, session.trackId)
            assertEquals("Pannonia-ring - 2023.11.14", session.eventType)
            assertNull("a pre-weather session reads as 'no weather captured'", session.weatherTempC)
            assertNull(session.weatherCode)

            assertEquals("Mazda", db.vehicleInformationDAO().getVehicle(1L).first().manufacturer)

            val laps = db.lapTimeDataDAO().getLapsForSession(1L).sortedBy { it.lapnumber }
            assertEquals(listOf("01:42.35", "01:43.02"), laps.map { it.laptime })
            assertEquals("01:42.35", db.lapTimeDataDAO().getBestLapForSession(1L)?.laptime)
            assertEquals(1, db.lapInfoDataDAO().getLapData(laps[0].id).size)

            val points = db.trackCoordinatesDao().getCoordinatesOfTrack(1L).first()
            assertEquals(listOf(1L, 2L, 3L), points.map { it.id })
            assertTrue(points[0].isStartPoint)
            assertEquals(120.5, points[1].altitude!!, 1e-9)
            assertTrue("no v1 point is a sector marker", points.none { it.isSectorPoint })
            assertTrue(points.all { it.sectorIndex == null })

            val fixes = db.rawGPSDataDao().getGPSDataBySession(1L)
            assertEquals(listOf(1_700_000_000_100L, 1_700_000_000_200L), fixes.map { it.timestamp })
            assertEquals(listOf(100f, 101.5f), fixes.map { it.speed })
            assertTrue("v1 fixes have no validity flag", fixes.all { it.valid == null })

            // ── What each migration added is usable, not just present ──
            // 2 -> 3: sector table, with its foreign key onto the migrated laps.
            db.sectorTimeDataDAO().insert(SectorTimeData(lapid = laps[0].id, sectorIndex = 0, splitTimeMs = 32_410))
            assertEquals(1, db.sectorTimeDataDAO().getSectorTimesForLap(laps[0].id).size)

            // 2 -> 3: sector markers on the rebuilt points table.
            db.trackCoordinatesDao().updateTrackCoordinates(
                listOf(points[1].copy(isSectorPoint = true, sectorIndex = 0))
            )
            val marked = db.trackCoordinatesDao().getCoordinatesOfTrack(1L).first().single { it.isSectorPoint }
            assertEquals(2L, marked.id)
            assertEquals(0, marked.sectorIndex)

            // 2 -> 3: the rebuilt table's id sequence carries on from the copied rows, so a
            // newly recorded point can never collide with an old one.
            db.trackCoordinatesDao().insertTrackPart(listOf(trackPoint(1L, 47.003, 19.003)))
            assertEquals(
                listOf(1L, 2L, 3L, 4L),
                db.trackCoordinatesDao().getCoordinatesOfTrack(1L).first().map { it.id }
            )

            // 4 -> 5: weather columns take a write.
            db.sessionDataDao().updateSessionWeather(1L, 18.0, 55, 0.2, 61, 20.0, 270, 1008.0)
            assertEquals(18.0, db.sessionDataDao().getSessionById(1L)!!.weatherTempC!!, 1e-9)

            // 5 -> 6: laps from v1 carry no gap flag, and the flag takes a write.
            assertTrue(laps.none { it.signalGap })
            db.lapTimeDataDAO().completeLap(laps[1].id, "01:43.02", signalGap = true)
            assertTrue(db.lapTimeDataDAO().getLapById(laps[1].id)!!.signalGap)

            // ── Cascades survived the rebuild ──
            db.trackMainDao().deleteTrack(1L)
            assertTrue(db.trackCoordinatesDao().getCoordinatesOfTrack(1L).first().isEmpty())

            db.sessionDataDao().deleteSessionById(1L)
            assertTrue(db.lapTimeDataDAO().getLapsForSession(1L).isEmpty())
            assertTrue(db.sectorTimeDataDAO().getSectorTimesForLap(laps[0].id).isEmpty())
            assertTrue(db.rawGPSDataDao().getGPSDataBySession(1L).isEmpty())
        }
    }

    @Test
    fun exportedSchema_opensWithTheCompiledDatabase() = runBlocking<Unit> {
        // Builds the database purely from the newest export and then opens it with the
        // compiled ESPDatabase. Room refuses to open a file whose stored identity hash
        // doesn't match the entities it was compiled with, so this fails whenever the
        // committed export and the code disagree - i.e. someone changed an entity and did
        // not rebuild/commit the export - and it proves the asset wiring the migration
        // tests depend on.
        helper.createDatabase(TEST_DB, LATEST_VERSION).close()

        withProductionDatabase { db ->
            val vehicleId = db.vehicleInformationDAO().insertVehicle(vehicle())
            assertTrue(vehicleId > 0)
        }
    }

    // ─────────────────────────────────────────────
    // Plumbing
    // ─────────────────────────────────────────────

    /**
     * Opens [TEST_DB] the way the app does - with the production migration list and no
     * destructive fallback, so a missing migration path fails the test instead of quietly
     * recreating the schema - then closes and deletes it.
     */
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

    /** Creates [TEST_DB] at schema version 1, seeded with [V1_ROWS]. */
    private fun createVersion1Database() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DB)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    V1_TABLES.forEach { db.execSQL(it) }
                    V1_ROWS.forEach { db.execSQL(it) }
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            assertEquals("fixture must start at schema version 1", 1, openHelper.writableDatabase.version)
        } finally {
            openHelper.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** ESPDatabase's current version. Bump with it, alongside the new schemas/N.json. */
        const val LATEST_VERSION = 8

        /**
         * The v1 schema as Room generated it: the v5 export minus the four migrations - no
         * indices, no sector columns or table, no `valid`, no weather. Room's own
         * `room_master_table` is deliberately absent; Room creates it itself once the
         * migrations have run, exactly as it does for a pre-populated database.
         */
        val V1_TABLES = listOf(
            "CREATE TABLE IF NOT EXISTS `vehicle_information_data` (`vehicleId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `manufacturer` TEXT NOT NULL, `model` TEXT NOT NULL, `year` INTEGER NOT NULL, `engineType` TEXT NOT NULL, `horsepower` INTEGER NOT NULL, `torque` INTEGER, `weight` REAL NOT NULL, `topSpeed` REAL, `acceleration` REAL, `drivetrain` TEXT NOT NULL, `fuelType` TEXT NOT NULL, `tireType` TEXT NOT NULL, `fuelCapacity` REAL, `transmission` TEXT NOT NULL, `suspensionType` TEXT)",
            "CREATE TABLE IF NOT EXISTS `session_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `eventType` TEXT NOT NULL, `vehicleId` INTEGER, `trackId` INTEGER, FOREIGN KEY(`vehicleId`) REFERENCES `vehicle_information_data`(`vehicleId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `raw_gps_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionid` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitude` REAL, `timestamp` INTEGER NOT NULL, `speed` REAL, `fixQuality` INTEGER, FOREIGN KEY(`sessionid`) REFERENCES `session_data`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `derived_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionid` INTEGER NOT NULL, `startSpeed` REAL, `endSpeed` REAL, `elapsedTime` REAL NOT NULL, `startLatitude` REAL, `startLongitude` REAL, `endLatitude` REAL, `endLongitude` REAL, FOREIGN KEY(`sessionid`) REFERENCES `session_data`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `smoothed_gps_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionid` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitude` REAL, `timestamp` INTEGER NOT NULL, `smoothedSpeed` REAL, FOREIGN KEY(`sessionid`) REFERENCES `session_data`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `track_main_data` (`trackId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackName` TEXT NOT NULL, `totalLength` REAL, `country` TEXT NOT NULL, `type` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `track_coordinates_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackId` INTEGER NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitude` REAL, `isStartPoint` INTEGER NOT NULL, FOREIGN KEY(`trackId`) REFERENCES `track_main_data`(`trackId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `lap_time_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionid` INTEGER NOT NULL, `lapnumber` INTEGER NOT NULL, `laptime` TEXT NOT NULL, FOREIGN KEY(`sessionid`) REFERENCES `session_data`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `lap_info_data` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lapid` INTEGER NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `alt` REAL, `spd` REAL, `latgforce` REAL, `longforce` REAL, FOREIGN KEY(`lapid`) REFERENCES `lap_time_data`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        /** One car, one track with three points, one session with two laps and a short trace. */
        val V1_ROWS = listOf(
            "INSERT INTO `vehicle_information_data` (`vehicleId`, `manufacturer`, `model`, `year`, `engineType`, `horsepower`, `torque`, `weight`, `topSpeed`, `acceleration`, `drivetrain`, `fuelType`, `tireType`, `fuelCapacity`, `transmission`, `suspensionType`) VALUES (1, 'Mazda', 'MX-5', 2019, 'NA', 181, 205, 1060.0, 219.0, 6.5, 'RWD', 'Petrol', 'Street', 45.0, 'Manual', NULL)",
            "INSERT INTO `track_main_data` (`trackId`, `trackName`, `totalLength`, `country`, `type`) VALUES (1, 'Pannonia-ring', 4.74, 'HU', 'circuit')",
            "INSERT INTO `track_coordinates_data` (`id`, `trackId`, `latitude`, `longitude`, `altitude`, `isStartPoint`) VALUES (1, 1, 47.0, 19.0, NULL, 1), (2, 1, 47.001, 19.001, 120.5, 0), (3, 1, 47.002, 19.002, NULL, 0)",
            "INSERT INTO `session_data` (`id`, `startTime`, `endTime`, `eventType`, `vehicleId`, `trackId`) VALUES (1, 1700000000000, 1700003600000, 'Pannonia-ring - 2023.11.14', 1, 1)",
            "INSERT INTO `lap_time_data` (`id`, `sessionid`, `lapnumber`, `laptime`) VALUES (1, 1, 1, '01:42.35'), (2, 1, 2, '01:43.02')",
            "INSERT INTO `lap_info_data` (`id`, `lapid`, `lat`, `lon`, `alt`, `spd`, `latgforce`, `longforce`) VALUES (1, 1, 47.0, 19.0, NULL, 100.0, NULL, NULL)",
            "INSERT INTO `raw_gps_data` (`id`, `sessionid`, `latitude`, `longitude`, `altitude`, `timestamp`, `speed`, `fixQuality`) VALUES (1, 1, 47.0, 19.0, NULL, 1700000000100, 100.0, 1), (2, 1, 47.001, 19.001, NULL, 1700000000200, 101.5, 1)"
        )
    }
}
