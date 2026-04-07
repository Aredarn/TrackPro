package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_data",
    foreignKeys = [androidx.room.ForeignKey(
        entity = VehicleInformationData::class,
        parentColumns = ["vehicleId"],
        childColumns = ["vehicleId"],
        onDelete = androidx.room.ForeignKey.CASCADE
    )]

)
data class SessionData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val eventType: String,
    val vehicleId: Long,
    val trackId: Long? = null  // null for drag sessions
)
