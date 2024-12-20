package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "session_data")
data class SessionData(
    @PrimaryKey(autoGenerate = true)
    val id: Long  = 0,
    val startTime: Long, // Session start time in milliseconds
    val endTime: Long?, // Session end time (null if ongoing)
    val eventType: String
)
