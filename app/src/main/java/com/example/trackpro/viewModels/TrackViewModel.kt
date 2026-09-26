package com.example.trackpro.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.models.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

//ViewModel to handle track data retrieval
class TrackViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _tracks = MutableStateFlow<List<TrackMainData>>(emptyList())
    val tracks = _tracks.asStateFlow()

    // Loading until the database actually answers; an empty list before that is
    // 'not looked yet', not 'nothing here'.
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState = _loadState.asStateFlow()

    init {
        fetchTracks()
    }

    private fun fetchTracks() {
        viewModelScope.launch {
            try {
                database.trackMainDao().getAllTrack().collect { trackList ->
                    _tracks.value = trackList
                    _loadState.value = LoadState.Ready
                }
            } catch (e: Exception) {
                _loadState.value = LoadState.Failed("Could not read tracks")
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