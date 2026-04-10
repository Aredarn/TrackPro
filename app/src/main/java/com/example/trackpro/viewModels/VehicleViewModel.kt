package com.example.trackpro.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.models.VehiclePair
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


class VehicleViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _vehicles = MutableStateFlow<List<VehiclePair>>(emptyList())
    val vehicles = _vehicles.asStateFlow()

    private val _loadingState = MutableStateFlow(true)
    val loadingState = _loadingState.asStateFlow()

    fun fetchVehicles() {
        viewModelScope.launch {
            _loadingState.value = true
            try {
                // If you want live updates, change .first() to .collect { ... }
                database.vehicleInformationDAO().getPairVehicles().collect { fetchedVehicles ->
                    _vehicles.value = fetchedVehicles
                    _loadingState.value = false
                }
            } catch (e: Exception) {
                Log.e("VehicleViewModel", "Error: ${e.message}")
                _loadingState.value = false
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