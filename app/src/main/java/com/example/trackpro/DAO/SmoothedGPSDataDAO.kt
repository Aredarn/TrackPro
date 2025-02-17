package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trackpro.DataClasses.SmoothedGPSData


@Dao
interface SmoothedGPSDataDAO {
    @Insert
    suspend fun insertSmoothedGPSDataDAO(smoothedGPSData: SmoothedGPSData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<SmoothedGPSData>)

    @Query("SELECT * FROM smoothed_gps_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSmoothedGPSDataBySession(sessionId:Long):List<SmoothedGPSData>
}