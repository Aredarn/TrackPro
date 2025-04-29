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
    val id: Long  = 0,
    val startTime: Long, // Session start time in milliseconds
    val endTime: Long?, // Session end time (null if ongoing)
    val eventType: String,
    val vehicleId: Long
)
