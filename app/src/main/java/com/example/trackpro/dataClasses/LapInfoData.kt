package com.example.trackpro.dataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "lap_info_data",
    foreignKeys = [ForeignKey(
        entity = LapTimeData::class,
        parentColumns = ["id"],
        childColumns = ["lapid"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class LapInfoData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lapid: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double?,
    val spd: Float?,
    val latgforce: Double?,
    val longforce: Double?
)