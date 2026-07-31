package com.example.trackpro.managerClasses

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
], version = 3, exportSchema = false)
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

        fun getInstance(context: Context): ESPDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ESPDatabase::class.java,
                    "esp_database"
                )
                    // No migrations exist yet; without this, any future (or this) schema
                    // change throws IllegalStateException on every existing install instead
                    // of recovering. Replace with real Migration objects once the schema
                    // needs to be preserved across upgrades.
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