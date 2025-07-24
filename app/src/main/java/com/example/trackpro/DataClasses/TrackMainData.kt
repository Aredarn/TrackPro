package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "track_main_data")
data class TrackMainData (
    @PrimaryKey(autoGenerate = true)
    val trackId: Long = 0,
    val trackName: String,
    val totalLength: Double? = null,
    val country: String,
    val type: String
)