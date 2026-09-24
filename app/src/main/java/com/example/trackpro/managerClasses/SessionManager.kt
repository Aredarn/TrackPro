package com.example.trackpro.managerClasses

import android.util.Log
import com.example.trackpro.dao.SessionDataDao
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.managerClasses.utilities.WeatherService


/**
 * Opens and closes recording sessions.
 *
 * Deliberately holds no notion of "the current session". It used to keep the id of whichever
 * session was started last, and [endSession] closed that one - but this is a singleton shared
 * by the drag screen and the time attack screen, so with both in the back stack the wrong
 * session could be closed. Time attack had already grown its own copy of the logic to route
 * around it. Every caller now names the session it means, and each screen owns its own id.
 */
class SessionManager private constructor(
    private val sessionDataDao: SessionDataDao,

) {

    /** Creates a session and returns its id. The caller owns that id from here on. */
    suspend fun startSession(eventType: String, vehicleId: Long?, trackId: Long? = null) : Long {
        val session = SessionData(
            eventType = eventType,
            startTime = System.currentTimeMillis(),
            endTime = null ,// Active session,
            vehicleId = vehicleId,
            trackId = trackId,
            )
        val sessionId = sessionDataDao.insertSession(session)
        Log.d("SessionManager", "Inserted session with ID: $sessionId")

        return sessionId
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

    /**
     * Closes [sessionId] by stamping its endTime.
     *
     * Without this a session stays "active" forever and the detail screen reports it as
     * zero-length. A one-column write, so it cannot undo a weather lookup that lands at the
     * same moment - see SessionDataDao.markSessionEnded.
     */
    suspend fun endSession(sessionId: Long) {
        if (sessionId < 0) return
        sessionDataDao.markSessionEnded(sessionId, System.currentTimeMillis())
        Log.d("SessionManager", "Ended session $sessionId")
    }

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
