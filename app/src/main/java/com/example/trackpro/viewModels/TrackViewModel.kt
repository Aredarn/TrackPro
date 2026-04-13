package com.example.trackpro.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.managerClasses.ESPDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//ViewModel to handle track data retrieval
class TrackViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _tracks = MutableStateFlow<List<TrackMainData>>(emptyList())
    val tracks = _tracks.asStateFlow()

    init {
        fetchTracks()
    }

    private fun fetchTracks() {
        viewModelScope.launch {
            database.trackMainDao().getAllTrack().collect { trackList ->
                _tracks.value = trackList
            }
        }
    }
}

class TrackViewModelFactory(private val database: ESPDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrackViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrackViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}