package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.ForeignKey


@Entity(
    tableName = "track_coordinates_data",
    foreignKeys = [ForeignKey(
        entity = TrackMainData::class,
        parentColumns = ["trackId"],
        childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TrackCoordinatesData(
    val trackId: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?
)