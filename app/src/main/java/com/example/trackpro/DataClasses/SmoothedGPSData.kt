package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "smoothed_gps_data" ,
    foreignKeys = [ForeignKey(
        entity = SessionData::class,
        parentColumns = ["id"],
        childColumns = ["sessionid"],
        onDelete = androidx.room.ForeignKey.CASCADE
    )]
)

data class SmoothedGPSData (
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionid: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val timestamp: Long,
    val smoothedSpeed: Float?,
)