package com.example.trackpro.dataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lap_info_data",
    foreignKeys = [ForeignKey(
        entity = LapTimeData::class,
        parentColumns = ["id"],
        childColumns = ["lapid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lapid")]
)
data class LapInfoData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lapid: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double?,
    val spd: Float?,
    val latgforce: Double?,
    val longforce: Double?,
    /**
     * When this point's fix arrived (wall-clock ms). Lets an old lap be replayed as a live
     * delta reference exactly. Null for laps recorded before this column existed; those are
     * rebuilt assuming the fixes were evenly spaced across the lap's known time instead.
     */
    val timestamp: Long? = null
)