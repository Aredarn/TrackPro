package com.example.trackpro.DAO

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.DataClasses.SessionData
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDataDao {

    // Insert a session
    @Insert
    suspend fun insertSession(sessionData: SessionData): Long

    // Update a session
    @Update
    suspend fun updateSession(sessionData: SessionData)

    // Get session by ID
    @Query("SELECT * FROM session_data WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionData?

    // Get all sessions
    @Query("SELECT * FROM session_data")
    fun getAllSessions(): Flow<List<SessionData>>
}




