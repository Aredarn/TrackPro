package com.example.trackpro.viewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.models.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleFULLViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _vehicles = MutableStateFlow<List<VehicleInformationData>>(emptyList())
    val vehicles = _vehicles.asStateFlow()

    // Loading until the database actually answers; an empty list before that is
    // 'not looked yet', not 'nothing here'.
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    val loadState = _loadState.asStateFlow()

    init {
        fetchVehicles()
    }

    private fun fetchVehicles() {
        viewModelScope.launch {
            try {
                database.vehicleInformationDAO().getAllVehicles().collect {
                    _vehicles.value = it
                    _loadState.value = LoadState.Ready
                }
            } catch (e: Exception) {
                _loadState.value = LoadState.Failed("Could not read the garage")
            }
        }
    }
}

class VehicleFULLViewModelFactory(private val activity: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VehicleFULLViewModel(ESPDatabase.getInstance(activity.applicationContext)) as T
    }
}