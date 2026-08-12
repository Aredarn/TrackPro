package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.dataClasses.SectorTimeData

@Dao
interface SectorTimeDataDAO {

    @Insert
    suspend fun insert(sectorTimeData: SectorTimeData): Long

    @Query("SELECT * FROM sector_time_data WHERE lapid = :lapId ORDER BY sectorIndex ASC")
    suspend fun getSectorTimesForLap(lapId: Long): List<SectorTimeData>
}
