package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trackpro.DataClasses.LapInfoData

@Dao
interface LapInfoDataDAO {

    @Insert
    suspend fun insert(lapInfoData: LapInfoData)

    @Query("SELECT * FROM lap_info_data WHERE lapid = :lapId")
    suspend fun getLapData(lapId: Long): List<LapInfoData>
}