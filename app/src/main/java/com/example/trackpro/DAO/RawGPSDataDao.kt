package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trackpro.DataClasses.RawGPSData

@Dao
interface RawGPSDataDao {
    @Insert
    suspend fun insert(rawGPSData: RawGPSData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<RawGPSData>)

    @Query("SELECT * FROM raw_gps_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getGPSDataBySession(sessionId: Long?): List<RawGPSData>

    @Query("DELETE FROM raw_gps_data WHERE sessionId = :sessionId")
    suspend fun deleteGPSDataBySession(sessionId: Int)
}
