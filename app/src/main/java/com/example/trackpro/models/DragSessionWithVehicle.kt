package com.example.trackpro.models

data class DragSessionWithVehicle(
    val sessionId: Long,
    val manufacturer: String,
    val model: String,
    val year: Int,
    val startTime: Long,
    val endTime: Long?,
    val eventType: String,
    val vehicleId: Long,
    val trackId: Long?,
    /** See SessionData.voided - a run the driver discarded but kept. */
    val voided: Boolean = false
)
