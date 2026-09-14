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
], version = 5, exportSchema = false)
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
         * Adds the automatically-captured session weather columns.
         *
         * This is 4 -> 5, not 3 -> 4: the schema was already at version 4 before the weather
         * columns existed. Adding columns without bumping the version leaves Room comparing
         * an on-device v4 schema against a different compiled v4 schema, which throws
         * ("Room cannot verify the data integrity") on the first DB access rather than
         * migrating - and the destructive fallback below does NOT catch that case, because
         * it only handles version changes with no migration path, not same-version drift.
         *
         * Written as a real migration rather than letting the fallback handle it, because
         * that fallback would drop every existing session, lap and GPS trace on upgrade.
         * All columns are nullable with no default, so existing rows simply read as
         * "no weather captured".
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

        fun getInstance(context: Context): ESPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ESPDatabase::class.java,
                    "esp_database"
                )
                    .addMigrations(MIGRATION_4_5)
                    // Backstop for version jumps with no migration path (e.g. upgrading from
                    // an older dev build). Any new schema change must bump the version AND add
                    // a real Migration above - relying on this instead silently wipes the
                    // user's entire history, and changing the schema *without* bumping the
                    // version isn't covered by it at all, it just crashes.
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