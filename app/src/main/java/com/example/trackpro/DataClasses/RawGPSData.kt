package com.example.trackpro.DataClasses


import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionid: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val timestamp: Long,
    val speed: Float?,
    val fixQuality: Int?
)

