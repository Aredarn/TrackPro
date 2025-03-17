package com.example.trackpro.DAO

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.Models.DragSessionWithVehicle
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

    //Get DRAG sessions with vehicle info
    @Query("""
    SELECT 
        session_data.id as sessionId, 
        vehicle_information_data.manufacturer as manufacturer, 
        vehicle_information_data.model as model, 
        vehicle_information_data.year as year, 
        session_data.startTime as startTime, 
        session_data.endTime as endTime, 
        session_data.eventType as eventType 
    FROM session_data
    INNER JOIN vehicle_information_data 
    ON session_data.vehicleId = vehicle_information_data.vehicleId
""")
    fun getAllSessionsWithVehicles(): Flow<List<DragSessionWithVehicle>>

}




