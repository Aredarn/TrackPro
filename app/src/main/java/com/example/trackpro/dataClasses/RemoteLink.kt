package com.example.trackpro.dataClasses

import androidx.room.Entity

/**
 * Ties a local row to its copy on the TrackBoard server.
 *
 * The server identifies records by UUIDs the app generates, so a retried upload lands on the
 * same row instead of creating a duplicate. That UUID has to survive restarts, and it has to
 * live somewhere other than the entity tables: keeping it here means the recording path, and
 * every existing query, never learn that syncing exists.
 *
 * No foreign key, because [localId] points into a different table per [kind]. A link whose
 * local row has gone is how sync notices a deletion it has to mirror.
 */
@Entity(tableName = "remote_link", primaryKeys = ["kind", "localId"])
data class RemoteLink(
    /** One of [KIND_VEHICLE], [KIND_TRACK], [KIND_PREMADE_TRACK], [KIND_SESSION]. */
    val kind: String,
    val localId: Long,
    val remoteId: String,
    /**
     * Fingerprint of the payload last sent. Sync compares against it to skip anything
     * unchanged, which is what keeps a background sync from re-uploading every session every
     * time. When [lastError] is set, this is the payload the server *rejected*, so the same
     * rejected payload is not re-sent on every sync until something about it changes.
     */
    val uploadedHash: String?,
    val uploadedAt: Long?,
    /** Why the last attempt failed, shown to the user; null once an upload succeeds. */
    val lastError: String? = null
) {
    companion object {
        const val KIND_VEHICLE = "vehicle"
        /** A track this driver built and published; they own it on the server. */
        const val KIND_TRACK = "track"
        /**
         * A premade track, shared by every install. Kept apart from [KIND_TRACK] so that
         * deleting the local copy can never delete the shared track other drivers' laps are on.
         */
        const val KIND_PREMADE_TRACK = "premade_track"
        const val KIND_SESSION = "session"
    }
}
