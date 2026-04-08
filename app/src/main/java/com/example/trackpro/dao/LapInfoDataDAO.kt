package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.dataClasses.LapInfoData

@Dao
interface LapInfoDataDAO {

    @Insert
    suspend fun insert(lapInfoData: LapInfoData)

    @Query("SELECT * FROM lap_info_data WHERE lapid = :lapId")
    suspend fun getLapData(lapId: Long): List<LapInfoData>
}