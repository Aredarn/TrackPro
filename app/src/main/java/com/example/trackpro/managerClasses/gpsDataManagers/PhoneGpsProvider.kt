package com.example.trackpro.managerClasses.gpsDataManagers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
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
    private val context: Context,
) : GpsProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _gpsFlow = MutableStateFlow<RawGPSData?>(null)
    override val gpsFlow = _gpsFlow.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val connectionStatus = _isConnected.asStateFlow()

    override val hasFix = MutableStateFlow(false).asStateFlow()

    private var lastStamp = 0L

    private fun fixWallTime(loc: android.location.Location): Long {
        val ageMs = (android.os.SystemClock.elapsedRealtime() - loc.elapsedRealtimeNanos / 1_000_000L)
            .coerceIn(0L, 2_000L)
        // Never earlier than the previous fix, so a clock adjustment cannot run time backwards.
        return (System.currentTimeMillis() - ageMs).coerceAtLeast(lastStamp + 1).also { lastStamp = it }
    }

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
                // The moment the fix was taken, on the wall clock the rest of the app uses.
                // Receipt time lags the fix by a variable delivery delay, which lands directly
                // in every split; the fix carries its own monotonic time, so its age is known.
                timestamp = fixWallTime(loc)
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            Log.w("PhoneGpsProvider", "Location permission not granted, cannot start phone GPS")
            _isConnected.value = false
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        _isConnected.value = true
    }

    override fun stop() {
        client.removeLocationUpdates(callback)
        _isConnected.value = false
    }
}