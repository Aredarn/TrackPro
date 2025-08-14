package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.trackpro.DataClasses.LapTimeData

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