package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "derived_data",
        foreignKeys = [androidx.room.ForeignKey(
    entity = SessionData::class,
    parentColumns = ["id"],
    childColumns = ["sessionid"],
    onDelete = androidx.room.ForeignKey.CASCADE
)])

data class DerivedData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionid: Int, // Links to the session that produced this data
    val startSpeed: Float?, // Starting speed in km/h
    val endSpeed: Float?, // Ending speed in km/h
    val elapsedTime: Float, // Time taken for the interval in seconds
    val startLatitude: Double?, // Latitude at start
    val startLongitude: Double?, // Longitude at start
    val endLatitude: Double?, // Latitude at end
    val endLongitude: Double? // Longitude at end
)
