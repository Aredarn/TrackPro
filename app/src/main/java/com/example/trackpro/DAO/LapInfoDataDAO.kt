package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import com.example.trackpro.DataClasses.LapInfoData

@Dao
interface LapInfoDataDAO {

    @Insert
    suspend fun insert(lapInfoData: LapInfoData)
}