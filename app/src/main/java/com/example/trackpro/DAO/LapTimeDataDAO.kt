package com.example.trackpro.DAO

import androidx.room.Dao
import androidx.room.Insert
import com.example.trackpro.DataClasses.LapTimeData

@Dao
interface LapTimeDataDAO {

    @Insert
    suspend fun insert(laptimedata: LapTimeData)



}