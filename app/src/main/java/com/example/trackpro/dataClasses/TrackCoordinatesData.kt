package com.example.trackpro.dataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "track_coordinates_data",
    foreignKeys = [ForeignKey(
        entity = TrackMainData::class,
        parentColumns = ["trackId"],
        childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("trackId")]
)
data class TrackCoordinatesData(
    @PrimaryKey(autoGenerate = true)
    val id : Long = 0,
    val trackId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val isStartPoint: Boolean = false,
    val isSectorPoint: Boolean = false,
    val sectorIndex: Int? = null
)
