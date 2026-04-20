package com.example.trackpro.viewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.models.DragSessionWithVehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TrackSessionViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _trackSessions = MutableStateFlow<List<DragSessionWithVehicle>>(emptyList())
    val trackSessions = _trackSessions.asStateFlow()

    init {
        fetchTrackSessions()
    }

    private fun fetchTrackSessions() {
        viewModelScope.launch {
            // Only fetch sessions where trackId is NOT null and NOT -1 (track sessions)
            database.sessionDataDao().getAllTrackSessionsWithVehicles()
                .collect { _trackSessions.value = it }
        }
    }

    fun deleteSession(session: DragSessionWithVehicle) {
        viewModelScope.launch {
            database.sessionDataDao().deleteDragSession(session.sessionId)
        }
    }
}

class TrackSessionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TrackSessionViewModel(ESPDatabase.getInstance(context.applicationContext)) as T
    }
}