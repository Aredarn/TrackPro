package com.example.trackpro.dataClasses

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lap_time_data",
    foreignKeys = [ForeignKey(
        entity = SessionData::class,
        parentColumns = ["id"],
        childColumns = ["sessionid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionid")]
)

data class LapTimeData (
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionid: Long,
    val lapnumber: Int,
    val laptime: String,
    /**
     * GPS went quiet for part of this lap - see CompletedLap.signalGap for what that can
     * and cannot mean. Declared with a database default so the v5 -> v6 migration can add it
     * with a plain ALTER and still match what Room expects: every lap recorded before the
     * column existed reads as having had no gap, which is all anyone could know about them.
     */
    @ColumnInfo(defaultValue = "0")
    val signalGap: Boolean = false
)