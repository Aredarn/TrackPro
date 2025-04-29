package com.example.trackpro.ViewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.ESPDatabase
import com.example.trackpro.Models.VehiclePair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


class VehicleViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _vehicles = MutableStateFlow<List<VehiclePair>>(emptyList())
    val vehicles: StateFlow<List<VehiclePair>> = _vehicles

    private val _loadingState = MutableStateFlow(true) // Track loading state
    val loadingState: StateFlow<Boolean> = _loadingState

    fun fetchVehicles() {
        viewModelScope.launch {
            _loadingState.value = true // Set loading state to true before fetching
            Log.d("ViewModel", "Fetching vehicles...")

            try {
                val fetchedVehicles = database.vehicleInformationDAO().getPairVehicles().first() // Collect the first value
                _vehicles.value = fetchedVehicles
                Log.d("ViewModel", "Fetched vehicles: ${_vehicles.value}")
            } catch (e: Exception) {
                Log.e("Error", "Fetching vehicles failed: ${e.message}")
            } finally {
                _loadingState.value = false // Set loading state to false after fetching
                Log.d("ViewModel", "Loading state: ${_loadingState.value}")
            }
        }
    }
}

class VehicleViewModelFactory(private val database: ESPDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VehicleViewModel::class.java)) {
            // Ensure the return type is correct (casting it to T)
            @Suppress("UNCHECKED_CAST")
            return VehicleViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}