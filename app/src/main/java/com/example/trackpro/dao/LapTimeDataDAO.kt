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

    // Every lap row of a session, including ones with no time yet (IN PROGRESS / INVALID) -
    // ending a session needs those to delete them. Anything *analysing* a session wants
    // List<LapTimeData>.timed() instead, which drops them.
    //
    // Ordered explicitly: callers present this as the lap-by-lap breakdown and derive
    // first-half/second-half trends from its order, which SQLite only happens to give for
    // an unordered query.
    @Query("SELECT * FROM lap_time_data WHERE sessionid = :sessionId ORDER BY lapnumber ASC")
    suspend fun getLapsForSession(sessionId: Long): List<LapTimeData>

    // Get a single lap by its ID
    @Query("SELECT * FROM lap_time_data WHERE id = :lapId LIMIT 1")
    suspend fun getLapById(lapId: Long): LapTimeData?

    // Best lap of a session. Excludes laps with no time the same way getBestLapForTrack
    // does - without that filter a session whose only rows are unfinished returns one of
    // them as its "best".
    //
    // Ordering by the time *string* is only correct because the stored format is
    // zero-padded to a fixed width ("MM:SS.hh"), so lexical order matches chronological.
    @Query("""
    SELECT * FROM lap_time_data
    WHERE sessionid = :sessionId
    AND laptime != 'IN PROGRESS'
    AND laptime != 'INVALID'
    ORDER BY laptime ASC
    LIMIT 1
    """)
    suspend fun getBestLapForSession(sessionId: Long): LapTimeData?

    // Get best completed lap across all sessions recorded on a track
    @Query("""
    SELECT lap_time_data.* FROM lap_time_data
    INNER JOIN session_data ON lap_time_data.sessionid = session_data.id
    WHERE session_data.trackId = :trackId
    AND lap_time_data.laptime != 'IN PROGRESS'
    AND lap_time_data.laptime != 'INVALID'
    ORDER BY lap_time_data.laptime ASC
    LIMIT 1
    """)
    suspend fun getBestLapForTrack(trackId: Long): LapTimeData?

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