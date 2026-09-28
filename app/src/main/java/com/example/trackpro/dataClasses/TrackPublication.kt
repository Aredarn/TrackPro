package com.example.trackpro.dataClasses

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A user-built track the driver has chosen to publish. The row existing is the whole signal.
 *
 * Premade tracks never appear here: every install carries identical geometry for them, so
 * they share one server track automatically and need no decision from the driver.
 *
 * Kept out of [TrackMainData] so publishing is a sharing preference, not part of the track.
 * Deleting the track deletes this row with it.
 */
@Entity(
    tableName = "track_publication",
    foreignKeys = [ForeignKey(
        entity = TrackMainData::class,
        parentColumns = ["trackId"],
        childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TrackPublication(
    @PrimaryKey val trackId: Long,
    val publishedAt: Long
)
