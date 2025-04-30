package com.example.trackpro.ViewModels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.ESPDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleFULLViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _vehicles = MutableStateFlow<List<VehicleInformationData>>(emptyList())
    val vehicles = _vehicles.asStateFlow()

    init {
        fetchVehicles()
    }

    private fun fetchVehicles() {

        viewModelScope.launch(Dispatchers.IO){
            database.vehicleInformationDAO().getAllVehicles().collect { vehicleList ->
                _vehicles.value = vehicleList
            }
        }
    }
}

class VehicleFULLViewModelFactory(private val activity: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VehicleFULLViewModel(ESPDatabase.getInstance(activity.applicationContext)) as T
    }
}