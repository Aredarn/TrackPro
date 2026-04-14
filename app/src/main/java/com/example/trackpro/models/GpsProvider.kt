package com.example.trackpro.models

import com.example.trackpro.dataClasses.RawGPSData
import kotlinx.coroutines.flow.StateFlow

interface GpsProvider {
    val gpsFlow: StateFlow<RawGPSData?>
    val connectionStatus: StateFlow<Boolean>
    fun start()
    fun stop()
}