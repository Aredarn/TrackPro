package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey


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
    @PrimaryKey(autoGenerate = true)
    val id : Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val isStartPoint: Boolean = false
)