package com.example.trackpro

import android.content.Context
import android.location.Location.distanceBetween
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.example.trackpro.DataClasses.LatLon
import com.example.trackpro.DataClasses.TrackJson
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val inputStream = context.resources.openRawResource(R.raw.tracks) // your JSON file
                                    val jsonString = inputStream.bufferedReader().use { it.readText() }

                                    val tracks: List<TrackJson> = Gson().fromJson(
                                        jsonString,
                                        object : TypeToken<List<TrackJson>>() {}.type
                                    )

                                    val db = getInstance(context)

                                    for (track in tracks) {
                                        val trackId = db.trackMainDao().insertTrackMainDataDAO(
                                            TrackMainData(
                                                trackName = track.trackName,
                                                totalLength = track.totalLength,
                                                country = track.country,
                                                type = track.type
                                            )
                                        )

                                        val coords = track.coordinates.mapIndexed { index, coord ->
                                            TrackCoordinatesData(
                                                trackId = trackId,
                                                latitude = coord.lat,
                                                longitude = coord.lon,
                                                altitude = null,
                                                isStartPoint = index == 0
                                            )
                                        }

                                        db.trackCoordinatesDao().insertTrack(coords)

                                        Log.d("DB_INIT", "Inserted ${coords.size} points for ${track.trackName}")
                                    }

                                } catch (e: Exception) {
                                    Log.e("DB_INIT", "Error inserting tracks", e)
                                }
                            }

                        }
                    })
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}


