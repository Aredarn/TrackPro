package com.example.trackpro.dataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A drag session the driver has chosen to post to the drag leaderboard. The row existing is the
 * whole signal, so runs recorded before this existed stay on the phone until the driver says
 * otherwise.
 *
 * Kept out of [SessionData] for the same reason as [TrackPublication]: posting is a sharing
 * preference, not part of the run. Deleting the session deletes this row with it.
 */
@Entity(
    tableName = "drag_publication",
    foreignKeys = [ForeignKey(
        entity = SessionData::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class DragPublication(
    @PrimaryKey val sessionId: Long,
    val publishedAt: Long
)
