package com.example.trackpro.viewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.models.DragSessionWithVehicle
import com.example.trackpro.models.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _sessions = MutableStateFlow<List<SessionData>>(emptyList())
    val sessions = _sessions.asStateFlow()

    // Loading until the database actually answers; an empty list before that is
    // 'not looked yet', not 'nothing here'.
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState = _loadState.asStateFlow()

    private val _sessionsWithVehicles = MutableStateFlow<List<DragSessionWithVehicle>>(emptyList())
    val sessionsWithVehicle = _sessionsWithVehicles.asStateFlow()

    init {
        fetchSessions()
    }

    private fun fetchSessions() {
        viewModelScope.launch {
            try {
                database.sessionDataDao().getAllSessions().collect {
                    _sessions.value = it
                    _loadState.value = LoadState.Ready
                }
            } catch (e: Exception) {
                _loadState.value = LoadState.Failed("Could not read sessions")
            }
        }
        viewModelScope.launch {
            database.sessionDataDao().getAllTrackSessionsWithVehicles().collect {
                _sessionsWithVehicles.value = it
            }
        }
    }

    fun deleteSession(session: SessionData) {
        viewModelScope.launch {
            database.sessionDataDao().deleteSessionById(session.id)
        }
    }
}


class SessionViewModelFactory(private val activity: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SessionViewModel(ESPDatabase.getInstance(activity.applicationContext)) as T
    }
}
