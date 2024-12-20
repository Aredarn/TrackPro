package com.example.trackpro.DataClasses


import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_gps_data",
    foreignKeys = [ForeignKey(
        entity = SessionData::class,
        parentColumns = ["id"],
        childColumns = ["sessionid"],
        onDelete = ForeignKey.CASCADE
    )]

)
data class RawGPSData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionid: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?, // Optional: altitude in meters
    val timestamp: Long, // Timestamp in milliseconds
    val speed: Float?, // Optional: speed provided by GNSS (in m/s or km/h)
    val fixQuality: Int? // Optional: GNSS signal quality, e.g., number of satellites
)
