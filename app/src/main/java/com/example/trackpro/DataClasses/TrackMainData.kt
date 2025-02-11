package com.example.trackpro.DataClasses

import android.health.connect.datatypes.units.Length
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "track_main_data")
data class TrackMainData (
    @PrimaryKey(autoGenerate = true)
    val trackId: Int,
    val trackName: String,
    val totalLength: Length,
    val country: String
)