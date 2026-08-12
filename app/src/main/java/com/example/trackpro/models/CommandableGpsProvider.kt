package com.example.trackpro.models

import kotlinx.coroutines.flow.StateFlow

interface CommandableGpsProvider : GpsProvider {
    val confirmedRateHz: StateFlow<Int?>
    fun sendCommand(command: String)
}
