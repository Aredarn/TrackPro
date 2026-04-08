package com.example.trackpro.models

import androidx.room.ColumnInfo

data class VehiclePair(
    val vehicleId: Long,

    // Use @ColumnInfo to specify the column name to map the concatenated result
    @ColumnInfo(name = "manufacturer || ' ' || model")
    val manufacturerAndModel: String
)