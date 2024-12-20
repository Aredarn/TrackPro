package com.example.trackpro.DAO

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trackpro.DataClasses.RawGPSData

@Dao
interface RawGPSDataDao {
    @Insert
    suspend fun insert(rawGPSData: RawGPSData)
    {
        Log.d("Database", "Inserted RawGPSData: $rawGPSData")
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<RawGPSData>)

    @Query("SELECT * FROM raw_gps_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getGPSDataBySession(sessionId: Int): List<RawGPSData>

    @Query("DELETE FROM raw_gps_data WHERE sessionId = :sessionId")
    suspend fun deleteGPSDataBySession(sessionId: Int)
}
