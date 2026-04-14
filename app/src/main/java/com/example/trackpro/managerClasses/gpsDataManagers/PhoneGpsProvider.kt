package com.example.trackpro.managerClasses.gpsDataManagers

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.models.GpsProvider
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PhoneGpsProvider(
    context: Context,
) : GpsProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _gpsFlow = MutableStateFlow<RawGPSData?>(null)
    override val gpsFlow = _gpsFlow.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val connectionStatus = _isConnected.asStateFlow()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _gpsFlow.value = RawGPSData(
                sessionid = 0,
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                speed = loc.speed * 3.6f, // CRITICAL: Convert m/s to km/h
                fixQuality = if (loc.accuracy < 10) 3 else 1,
                timestamp = loc.time
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        _isConnected.value = true
    }

    override fun stop() {
        client.removeLocationUpdates(callback)
        _isConnected.value = false
    }
}