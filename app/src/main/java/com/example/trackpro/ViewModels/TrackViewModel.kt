package com.example.trackpro.ViewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.DataClasses.TrackMainData
import com.example.trackpro.ESPDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


//ViewModel to handle track data retrieval
class TrackViewModel(private val database: ESPDatabase): ViewModel()
{
    private var _tracks = MutableStateFlow<List<TrackMainData>>(emptyList())
    val tracks = _tracks.asStateFlow()

    init {
        fetchTracks()
    }

    private fun fetchTracks()
    {
        viewModelScope.launch(Dispatchers.IO){
            database.trackMainDao().getAllTrack().collect { trackList ->
                _tracks.value = trackList
            }
        }
    }
}

class TrackViewModelFactory(private val activity: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TrackViewModel(ESPDatabase.getInstance(activity.applicationContext)) as T
    }
}
