package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.dataClasses.LapInfoData

@Dao
interface LapInfoDataDAO {

    @Insert
    suspend fun insert(lapInfoData: LapInfoData)

    // In recorded order, which is id order. Everything reading this treats it as a path -
    // the lap map, the heatmap, and rebuilding a lap as a delta reference - and SQLite only
    // happens to return insertion order for an unordered query.
    @Query("SELECT * FROM lap_info_data WHERE lapid = :lapId ORDER BY id ASC")
    suspend fun getLapData(lapId: Long): List<LapInfoData>
}