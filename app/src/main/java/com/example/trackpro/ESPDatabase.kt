package com.example.trackpro

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.trackpro.DAO.DerivedDataDao
import com.example.trackpro.DAO.LapInfoDataDAO
import com.example.trackpro.DAO.LapTimeDataDAO
import com.example.trackpro.DAO.RawGPSDataDao
import com.example.trackpro.DAO.SessionDataDao
import com.example.trackpro.DAO.SmoothedGPSDataDAO
import com.example.trackpro.DAO.TrackCoordinatesDataDAO
import com.example.trackpro.DAO.TrackMainDataDAO
import com.example.trackpro.DAO.VehicleInformationDAO
import com.example.trackpro.DataClasses.DerivedData
import com.example.trackpro.DataClasses.LapTimeData
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.DataClasses.TrackMainData
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.DataClasses.LapInfoData
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
    LapInfoData::class
], version = 1, exportSchema = false)
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
                    .fallbackToDestructiveMigration()
                    .setJournalMode(JournalMode.TRUNCATE) // Forces immediate writes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


