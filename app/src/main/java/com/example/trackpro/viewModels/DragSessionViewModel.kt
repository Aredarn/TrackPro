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

class DragSessionViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _dragSessions = MutableStateFlow<List<DragSessionWithVehicle>>(emptyList())
    val dragSessions = _dragSessions.asStateFlow()

    init {
        fetchDragSessions()
    }

    private fun fetchDragSessions() {
        viewModelScope.launch {
            // Only fetch sessions where trackId is null or -1 (drag sessions)
            database.sessionDataDao().getAllDragSessionsWithVehicles().collect { _dragSessions.value = it }
        }
    }

    fun deleteSession(session: DragSessionWithVehicle) {
        viewModelScope.launch {
            database.sessionDataDao().deleteDragSession(session.sessionId)
        }
    }
}

class DragSessionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DragSessionViewModel(ESPDatabase.getInstance(context.applicationContext)) as T
    }
}