package com.example.trackpro.ManagerClasses

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.DAO.DerivedDataDao
import com.example.trackpro.DAO.RawGPSDataDao
import com.example.trackpro.DAO.SessionDataDao
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.ESPDatabase


class SessionManager private constructor(
    private val sessionDataDao: SessionDataDao,
    private val rawGPSDataDao: RawGPSDataDao,
    private val derivedDataDao: DerivedDataDao
) {

    private var currentSessionId: Long? = null

    suspend fun startSession(eventType: String, description: String, vehicleId: Long) {
        val session = SessionData(
            eventType = eventType,
            startTime = System.currentTimeMillis(),
            endTime = null ,// Active session,
            vehicleId = vehicleId
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
                    database.rawGPSDataDao(),
                    database.derivedDataDao()
                )
                INSTANCE = instance
                instance
            }
        }
    }
}
