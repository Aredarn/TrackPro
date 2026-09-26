package com.example.trackpro.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.models.DragSessionWithVehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDataDao {

    // Insert a session
    @Insert
    suspend fun insertSession(sessionData: SessionData): Long

    /**
     * Replaces a whole session row.
     *
     * Currently unused, and a hazard to reach for: a session is written by more than one
     * party - the screen that closes it and the background weather lookup - so a
     * read-modify-write here silently reverts whatever the other one did in between. That
     * exact bug is why [markSessionEnded] and [updateSessionWeather] exist. If a future
     * caller needs to change some other field, give it a targeted UPDATE too.
     */
    @Update
    suspend fun updateSession(sessionData: SessionData)

    /** Stamps a session discarded. The rows stay; see SessionData.voided. */
    @Query("UPDATE session_data SET voided = :voided WHERE id = :sessionId")
    suspend fun setVoided(sessionId: Long, voided: Boolean)

    @Query("DELETE FROM session_data WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    /**
     * Closes a session by stamping its endTime.
     *
     * Targeted for the same reason as the weather backfill below, and against the same other
     * write: closing the session used to read the whole row and write it back, so a weather
     * lookup that landed in between was read as absent and written back as null. Two writers
     * touching disjoint columns of one row must both write only their own columns.
     */
    @Query("UPDATE session_data SET endTime = :endTime WHERE id = :sessionId")
    suspend fun markSessionEnded(sessionId: Long, endTime: Long)

    /**
     * Backfills only the weather columns. Deliberately a targeted UPDATE rather than a
     * read-modify-write via [updateSession]: weather arrives asynchronously a second or two
     * after the session starts, which can overlap with endSession() writing endTime on the
     * same row - a whole-row write from either side would clobber the other's change.
     */
    @Query("""
        UPDATE session_data SET
            weatherTempC = :tempC,
            weatherHumidityPct = :humidityPct,
            weatherPrecipitationMm = :precipitationMm,
            weatherCode = :weatherCode,
            weatherWindKph = :windKph,
            weatherWindDirDeg = :windDirDeg,
            weatherPressureHpa = :pressureHpa
        WHERE id = :sessionId
    """)
    suspend fun updateSessionWeather(
        sessionId: Long,
        tempC: Double?,
        humidityPct: Int?,
        precipitationMm: Double?,
        weatherCode: Int?,
        windKph: Double?,
        windDirDeg: Int?,
        pressureHpa: Double?
    )

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
        session_data.eventType as eventType,
        session_data.vehicleId as vehicleId,
        session_data.trackId as trackId,
        session_data.voided as voided
    FROM session_data
    INNER JOIN vehicle_information_data 
    ON session_data.vehicleId = vehicle_information_data.vehicleId
    WHERE session_data.trackId IS NULL OR session_data.trackId = -1
""")
    fun getAllDragSessionsWithVehicles(): Flow<List<DragSessionWithVehicle>>

    @Query("""
    SELECT 
        session_data.id as sessionId, 
        vehicle_information_data.manufacturer as manufacturer, 
        vehicle_information_data.model as model, 
        vehicle_information_data.year as year, 
        session_data.startTime as startTime, 
        session_data.endTime as endTime, 
        session_data.eventType as eventType,
        session_data.vehicleId as vehicleId,
        session_data.trackId as trackId,
        session_data.voided as voided
    FROM session_data
    INNER JOIN vehicle_information_data 
    ON session_data.vehicleId = vehicle_information_data.vehicleId
    WHERE session_data.trackId IS NOT NULL AND session_data.trackId != -1
""")
    fun getAllTrackSessionsWithVehicles(): Flow<List<DragSessionWithVehicle>>




}
