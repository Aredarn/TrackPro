package com.example.trackpro.managerClasses

import android.util.Log
import com.example.trackpro.dao.SessionDataDao
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.managerClasses.utilities.WeatherService


class SessionManager private constructor(
    private val sessionDataDao: SessionDataDao,

) {

    private var currentSessionId: Long? = null

    suspend fun startSession(eventType: String, vehicleId: Long?, trackId: Long? = null) : Long {
        val session = SessionData(
            eventType = eventType,
            startTime = System.currentTimeMillis(),
            endTime = null ,// Active session,
            vehicleId = vehicleId,
            trackId = trackId,
            )
        currentSessionId = sessionDataDao.insertSession(session) // Insert session
        Log.d("SessionManager", "Inserted session with ID: $currentSessionId")

        return currentSessionId ?: throw Exception("Failed to insert session")
    }

    /**
     * Looks up the conditions at [latitude]/[longitude] and stores them against [sessionId].
     *
     * Call this *after* the session has been created and launched off the critical path -
     * it makes a network request, and starting a recording must never wait on (or fail
     * because of) the network. A failed lookup just leaves the weather columns null.
     */
    suspend fun captureWeather(sessionId: Long, latitude: Double, longitude: Double) {
        val conditions = WeatherService.fetchCurrent(latitude, longitude)
        if (conditions == null) {
            Log.d("SessionManager", "No weather captured for session $sessionId")
            return
        }
        sessionDataDao.updateSessionWeather(
            sessionId = sessionId,
            tempC = conditions.temperatureC,
            humidityPct = conditions.humidityPct,
            precipitationMm = conditions.precipitationMm,
            weatherCode = conditions.weatherCode,
            windKph = conditions.windKph,
            windDirDeg = conditions.windDirectionDeg,
            pressureHpa = conditions.pressureHpa
        )
        Log.d(
            "SessionManager",
            "Captured weather for session $sessionId: ${conditions.temperatureC}C, " +
                    WeatherService.describeCode(conditions.weatherCode)
        )
    }

    // End the current session
    suspend fun endSession() {
        currentSessionId?.let { sessionId ->
            val session = sessionDataDao.getSessionById(sessionId)
            session?.let {
                val updatedSession = it.copy(endTime = System.currentTimeMillis())
                sessionDataDao.updateSession(updatedSession) // Update session
            }
        }
        currentSessionId = null
    }

    // Get the current session ID
    fun getCurrentSessionId(): Long? = currentSessionId

    companion object {
        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(database: ESPDatabase): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionManager(
                    database.sessionDataDao(),
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
