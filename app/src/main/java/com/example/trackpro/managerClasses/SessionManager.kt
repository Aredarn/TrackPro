package com.example.trackpro.managerClasses

import android.util.Log
import com.example.trackpro.dao.SessionDataDao
import com.example.trackpro.dataClasses.SessionData


class SessionManager private constructor(
    private val sessionDataDao: SessionDataDao,

) {

    private var currentSessionId: Long? = null

    suspend fun startSession(eventType: String, vehicleId: Long, trackId: Long? = null) {
        val session = SessionData(
            eventType = eventType,
            startTime = System.currentTimeMillis(),
            endTime = null ,// Active session,
            vehicleId = vehicleId,
            trackId = trackId,
            )
        currentSessionId = sessionDataDao.insertSession(session) // Insert session
        Log.e("SessionManager", "Inserted session with ID: $currentSessionId")
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
