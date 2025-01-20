package com.example.trackpro

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.trackpro.DAO.DerivedDataDao
import com.example.trackpro.DAO.RawGPSDataDao
import com.example.trackpro.DAO.SessionDataDao
import com.example.trackpro.DAO.SmoothedGPSDataDAO
import com.example.trackpro.DataClasses.DerivedData
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.DataClasses.SmoothedGPSData

@Database(entities = [SessionData::class, RawGPSData::class, DerivedData::class, SmoothedGPSData::class], version = 1, exportSchema = false)
abstract class ESPDatabase : RoomDatabase() {
    abstract fun sessionDataDao(): SessionDataDao
    abstract fun rawGPSDataDao(): RawGPSDataDao
    abstract fun derivedDataDao(): DerivedDataDao
    abstract fun smoothedDataDao() : SmoothedGPSDataDAO


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
                    .fallbackToDestructiveMigration() // Optional: handles schema changes
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


