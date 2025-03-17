package com.example.trackpro.Models

import androidx.room.Embedded

data class DragSessionWithVehicle(
    val sessionId: Long,
    val manufacturer: String,
    val model: String,
    val year: Int,
    val startTime: Long,
    val endTime: Long?,
    val eventType: String
)
