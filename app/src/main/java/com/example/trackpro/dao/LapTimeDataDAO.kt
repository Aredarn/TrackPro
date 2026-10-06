package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.models.SessionBestLap
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

    /**
     * Best completed lap across every session on a track.
     *
     * Voided sessions are excluded here rather than filtered in the UI: this is the query
     * the whole app asks for "the record", and a discarded run must not be able to hold one
     * from any caller. That exclusion is the only thing that makes VOID mean anything
     * beyond a badge.
     */
    @Query("""
    SELECT lap_time_data.* FROM lap_time_data
    INNER JOIN session_data ON lap_time_data.sessionid = session_data.id
    WHERE session_data.trackId = :trackId
    AND session_data.voided = 0
    AND lap_time_data.laptime != 'IN PROGRESS'
    AND lap_time_data.laptime != 'INVALID'
    ORDER BY lap_time_data.laptime ASC
    LIMIT 1
    """)
    suspend fun getBestLapForTrack(trackId: Long): LapTimeData?

    // Every session's fastest completed lap, for listing and ordering sessions by pace. MIN
    // over the stored "MM:SS.hh" text is MIN over time because the format is fixed-width;
    // the unfinished and invalid markers are excluded, as they are wherever laps are ranked.
    @Query("""
    SELECT sessionid AS sessionId, MIN(laptime) AS bestLap FROM lap_time_data
    WHERE laptime != 'IN PROGRESS'
    AND laptime != 'INVALID'
    GROUP BY sessionid
    """)
    fun getBestLapPerSession(): Flow<List<SessionBestLap>>

    // The fastest laps on a track that are fit to be a live-delta reference, fastest first:
    // completed, and with no GPS gap - a gap is a straight chord through the lap's trace,
    // which would skew every delta measured against it. Several rather than one, because
    // the caller still has to find the fastest in each direction the track is driven.
    @Query("""
    SELECT lap_time_data.* FROM lap_time_data
    INNER JOIN session_data ON lap_time_data.sessionid = session_data.id
    WHERE session_data.trackId = :trackId
    AND lap_time_data.laptime != 'IN PROGRESS'
    AND lap_time_data.laptime != 'INVALID'
    AND lap_time_data.signalGap = 0
    ORDER BY lap_time_data.laptime ASC
    LIMIT :limit
    """)
    suspend fun getFastestCleanLapsForTrack(trackId: Long, limit: Int): List<LapTimeData>

    /**
     * Every clean lap time on a track from sessions that count: not voided, no GPS gap, and
     * actually finished. Parsed by the caller, which is why it returns the raw strings.
     * Read once when a session starts, it is the personal best the session has to beat.
     */
    @Query("""
    SELECT lap_time_data.laptime FROM lap_time_data
    INNER JOIN session_data ON lap_time_data.sessionid = session_data.id
    WHERE session_data.trackId = :trackId
    AND session_data.voided = 0
    AND lap_time_data.signalGap = 0
    AND lap_time_data.laptime != 'IN PROGRESS'
    AND lap_time_data.laptime != 'INVALID'
    """)
    suspend fun getCountedLapTimesForTrack(trackId: Long): List<String>

    // Get total number of laps in a session
    @Query("SELECT COUNT(*) FROM lap_time_data WHERE sessionid = :sessionId")
    suspend fun getLapCountForSession(sessionId: Long): Int


    @Query("UPDATE lap_time_data SET laptime = :time WHERE id = :lapId")
    suspend fun updateLapTime(lapId: Long, time: String)

    // Closes an in-progress lap: its time, and whether GPS dropped out during it. One write,
    // so a lap is never left timed but unflagged.
    @Query("UPDATE lap_time_data SET laptime = :time, signalGap = :signalGap WHERE id = :lapId")
    suspend fun completeLap(lapId: Long, time: String, signalGap: Boolean)


    @Query("""
    SELECT * FROM lap_time_data 
    WHERE sessionid = :sessionId 
    AND laptime != 'IN PROGRESS' 
    AND laptime != 'INVALID'
    ORDER BY lapnumber ASC
""")
    fun getCompletedLapsForSession(sessionId: Long): Flow<List<LapTimeData>>



}