package com.example.trackpro.dataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sector_time_data",
    foreignKeys = [ForeignKey(
        entity = LapTimeData::class,
        parentColumns = ["id"],
        childColumns = ["lapid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("lapid")]
)
data class SectorTimeData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lapid: Long,
    val sectorIndex: Int,
    val splitTimeMs: Long
)
