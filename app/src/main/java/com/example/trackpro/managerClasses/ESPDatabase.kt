package com.example.trackpro.managerClasses

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.trackpro.dao.DerivedDataDao
import com.example.trackpro.dao.LapInfoDataDAO
import com.example.trackpro.dao.LapTimeDataDAO
import com.example.trackpro.dao.RawGPSDataDao
import com.example.trackpro.dao.SectorTimeDataDAO
import com.example.trackpro.dao.SessionDataDao
import com.example.trackpro.dao.SmoothedGPSDataDAO
import com.example.trackpro.dao.TrackCoordinatesDataDAO
import com.example.trackpro.dao.TrackMainDataDAO
import com.example.trackpro.dao.VehicleInformationDAO
import com.example.trackpro.dataClasses.DerivedData
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.SmoothedGPSData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData

@Database(entities =
[
    SessionData::class,
    RawGPSData::class,
    DerivedData::class,
    SmoothedGPSData::class,
    TrackMainData::class,
    TrackCoordinatesData::class,
    VehicleInformationData::class,
    LapTimeData::class,
    LapInfoData::class,
    SectorTimeData::class
], version = 5, exportSchema = true)
abstract class ESPDatabase : RoomDatabase() {
    abstract fun sessionDataDao(): SessionDataDao
    abstract fun rawGPSDataDao(): RawGPSDataDao
    abstract fun derivedDataDao(): DerivedDataDao
    abstract fun smoothedDataDao() : SmoothedGPSDataDAO
    abstract fun trackMainDao(): TrackMainDataDAO
    abstract fun trackCoordinatesDao(): TrackCoordinatesDataDAO
    abstract fun vehicleInformationDAO(): VehicleInformationDAO
    abstract fun lapTimeDataDAO(): LapTimeDataDAO
    abstract fun lapInfoDataDAO(): LapInfoDataDAO
    abstract fun sectorTimeDataDAO(): SectorTimeDataDAO

    companion object {
        @Volatile
        private var INSTANCE: ESPDatabase? = null

        /**
         * v1 -> v2: adds the foreign-key/filter indices.
         *
         * Index names must match what Room generates ("index_<table>_<column>") or the
         * post-migration schema validation fails even though the indices are functionally
         * identical.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "index_derived_data_sessionid" to "derived_data(`sessionid`)",
                    "index_lap_info_data_lapid" to "lap_info_data(`lapid`)",
                    "index_lap_time_data_sessionid" to "lap_time_data(`sessionid`)",
                    "index_raw_gps_data_sessionid" to "raw_gps_data(`sessionid`)",
                    "index_session_data_vehicleId" to "session_data(`vehicleId`)",
                    "index_session_data_trackId" to "session_data(`trackId`)",
                    "index_smoothed_gps_data_sessionid" to "smoothed_gps_data(`sessionid`)",
                    "index_track_coordinates_data_trackId" to "track_coordinates_data(`trackId`)"
                ).forEach { (name, target) ->
                    db.execSQL("CREATE INDEX IF NOT EXISTS `$name` ON $target")
                }
            }
        }

        /**
         * v2 -> v3: adds sector splits - the sector marker columns on track points, and the
         * sector_time_data table.
         *
         * track_coordinates_data is rebuilt rather than ALTERed because isSectorPoint is
         * non-null, and SQLite requires a DEFAULT on any NOT NULL column added via ALTER
         * TABLE. That default would then be baked into the stored schema, while the entity
         * declares none - a difference Room's schema validation can reject. Recreating the
         * table produces a column that matches the entity exactly.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_coordinates_data_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitude` REAL,
                        `isStartPoint` INTEGER NOT NULL,
                        `isSectorPoint` INTEGER NOT NULL,
                        `sectorIndex` INTEGER,
                        FOREIGN KEY(`trackId`) REFERENCES `track_main_data`(`trackId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `track_coordinates_data_new`
                        (`id`, `trackId`, `latitude`, `longitude`, `altitude`,
                         `isStartPoint`, `isSectorPoint`, `sectorIndex`)
                    SELECT `id`, `trackId`, `latitude`, `longitude`, `altitude`,
                           `isStartPoint`, 0, NULL
                    FROM `track_coordinates_data`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `track_coordinates_data`")
                db.execSQL("ALTER TABLE `track_coordinates_data_new` RENAME TO `track_coordinates_data`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_track_coordinates_data_trackId` " +
                            "ON `track_coordinates_data` (`trackId`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sector_time_data` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `lapid` INTEGER NOT NULL,
                        `sectorIndex` INTEGER NOT NULL,
                        `splitTimeMs` INTEGER NOT NULL,
                        FOREIGN KEY(`lapid`) REFERENCES `lap_time_data`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sector_time_data_lapid` " +
                            "ON `sector_time_data` (`lapid`)"
                )
            }
        }

        /** v3 -> v4: adds RawGPSData.valid (nullable, so a plain ALTER is enough). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `raw_gps_data` ADD COLUMN `valid` INTEGER")
            }
        }

        /**
         * v4 -> v5: adds the automatically-captured session weather columns.
         *
         * All nullable with no default, so existing rows simply read as "no weather
         * captured" and a plain ALTER matches the entity exactly.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "weatherTempC REAL",
                    "weatherHumidityPct INTEGER",
                    "weatherPrecipitationMm REAL",
                    "weatherCode INTEGER",
                    "weatherWindKph REAL",
                    "weatherWindDirDeg INTEGER",
                    "weatherPressureHpa REAL"
                ).forEach { column ->
                    db.execSQL("ALTER TABLE session_data ADD COLUMN $column")
                }
            }
        }

        /**
         * Every migration, in order. This is the single list the production database is
         * built with; the instrumented migration tests run exactly the same array so a
         * migration can't be tested but forgotten here, or vice versa.
         */
        internal val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5
        )

        fun getInstance(context: Context): ESPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ESPDatabase::class.java,
                    "esp_database"
                )
                    .addMigrations(*MIGRATIONS)
                    // Last-resort backstop, now that every released version has a real path
                    // above. It only fires for a version with no migration route at all -
                    // in practice an ad-hoc dev build - and when it fires it DROPS EVERYTHING.
                    //
                    // Two rules for any future schema change:
                    //   1. Bump the version AND add a Migration above. Relying on this
                    //      fallback instead silently destroys the user's entire history.
                    //   2. Never change an entity without bumping the version. This fallback
                    //      does not cover that case at all - Room sees a matching version
                    //      number with a mismatched schema and simply throws on first access.
                    .fallbackToDestructiveMigration()
                    .build()

                // Premade tracks (res/raw/tracks.json) are synced separately on every app
                // start via TrackSeeder, called from TrackProApp.onCreate() - not here, since
                // this factory can be called from a background thread and seeding is its own
                // idempotent, name-deduped operation rather than a one-time DB-creation hook.

                INSTANCE = instance
                instance
            }
        }
    }
}