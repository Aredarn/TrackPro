package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "lap_time_data",
    foreignKeys = [ForeignKey(
        entity = SessionData::class,
        parentColumns = ["id"],
        childColumns = ["sessionid"],
        onDelete = ForeignKey.CASCADE
    )]
)

data class LapTimeData (
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionid: Long,
    val lapnumber: Int,
    val laptime: String
)