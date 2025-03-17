package com.example.trackpro.ViewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.ESPDatabase
import com.example.trackpro.Models.DragSessionWithVehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ViewModel to handle session data retrieval
class SessionViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _sessions = MutableStateFlow<List<SessionData>>(emptyList())
    private val _sessionsWithVehicles = MutableStateFlow<List<DragSessionWithVehicle>>(emptyList())

    val sessions = _sessions.asStateFlow()
    val sessionsWithVehicle = _sessionsWithVehicles.asStateFlow()

    init {
        fetchSessions()
    }

    private fun fetchSessions() {
        viewModelScope.launch {
            database.sessionDataDao().getAllSessions().collect { sessionList ->
                _sessions.value = sessionList
            }
        }

        viewModelScope.launch {
            database.sessionDataDao().getAllSessionsWithVehicles().collect { sessionsWithVehicle ->
                _sessionsWithVehicles.value = sessionsWithVehicle
            }
        }
    }
}


class SessionViewModelFactory(private val activity: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SessionViewModel(ESPDatabase.getInstance(activity.applicationContext)) as T
    }
}
