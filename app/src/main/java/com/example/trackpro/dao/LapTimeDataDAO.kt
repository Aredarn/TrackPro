package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.dataClasses.LapTimeData
import kotlinx.coroutines.flow.Flow

@Dao
interface LapTimeDataDAO {

    @Insert
    suspend fun insert(lapTimeData: LapTimeData): Long

    @Update
    suspend fun update(lapTimeData: LapTimeData)

    @Delete
    suspend fun delete(lapTimeData: LapTimeData)

    // Get all laps for a specific session
    @Query("SELECT * FROM lap_time_data WHERE sessionid = :sessionId")
    suspend fun getLapsForSession(sessionId: Long): List<LapTimeData>

    // Get a single lap by its ID
    @Query("SELECT * FROM lap_time_data WHERE id = :lapId LIMIT 1")
    suspend fun getLapById(lapId: Long): LapTimeData?

    // Get best lap for a session (smallest time)
    @Query("SELECT * FROM lap_time_data WHERE sessionid = :sessionId ORDER BY laptime ASC LIMIT 1")
    suspend fun getBestLapForSession(sessionId: Long): LapTimeData?

    // Get total number of laps in a session
    @Query("SELECT COUNT(*) FROM lap_time_data WHERE sessionid = :sessionId")
    suspend fun getLapCountForSession(sessionId: Long): Int


    @Query("UPDATE lap_time_data SET laptime = :time WHERE id = :lapId")
    suspend fun updateLapTime(lapId: Long, time: String)


    @Query("""
    SELECT * FROM lap_time_data 
    WHERE sessionid = :sessionId 
    AND laptime != 'IN PROGRESS' 
    AND laptime != 'INVALID'
    ORDER BY lapnumber ASC
""")
    fun getCompletedLapsForSession(sessionId: Long): Flow<List<LapTimeData>>



}