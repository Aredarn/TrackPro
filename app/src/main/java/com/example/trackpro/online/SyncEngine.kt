package com.example.trackpro.online

import com.example.trackpro.dao.SyncDao
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_PREMADE_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_SESSION
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import java.util.UUID

/** A reason one record could not be synced, already phrased for the driver. */
class SyncProblem(message: String) : Exception(message)

/**
 * Mirrors the driver's leaderboard-eligible sessions onto TrackBoard, and nothing else.
 *
 * What goes up is decided fresh on every run from local data, so every path — first sync,
 * a voided session, an unpublished track, sharing switched off, a deleted session — is the
 * same comparison of "what should be online" against "what was sent last time":
 *
 * - A session is posted when sharing is on, it recorded its GPS source, it has a timed lap,
 *   and its track is either premade or published by the driver.
 * - Premade tracks are linked to the one shared server track every install derives the same
 *   id for; the first driver to post on one creates it.
 * - Anything online that no longer qualifies is withdrawn.
 *
 * A problem with one record never stops the others. A lost connection stops the run — the
 * rest would fail the same way — and WorkManager tries again later.
 */
class SyncEngine(
    private val api: TrackBoardApi,
    private val auth: AuthRepository,
    private val dao: SyncDao,
    private val premade: PremadeTracks,
    private val sharingEnabled: () -> Boolean,
    private val appVersion: String?,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    /** The garage backup. Null in tests that only exercise leaderboard sync. */
    private val garage: GarageSync? = null,
) {
    private class TrackInfo(
        val track: TrackMainData,
        val points: List<TrackCoordinatesData>,
        /** Non-null when this is an unmodified premade track. */
        val premadeId: String?,
    )

    private class Plan(val session: SessionData, val track: TrackInfo, val laps: List<LapTimeData>)

    private class Tally {
        var uploaded = 0
        var unchanged = 0
        var withdrawn = 0
        var failed = 0
        var problem: String? = null

        fun fail(message: String?) {
            failed++
            if (problem == null) problem = message ?: "Something could not be synced."
        }
    }

    suspend fun run(): SyncReport {
        if (!auth.isSignedIn) return SyncReport(clock(), problem = "Not signed in to TrackBoard.")

        val tally = Tally()
        val garageTally = GarageTally()
        try {
            val published = dao.getPublishedTrackIds().toSet()
            val tracks = mutableMapOf<Long, TrackInfo?>()
            suspend fun track(id: Long) = tracks.getOrPut(id) { loadTrack(id) }

            val plans = if (sharingEnabled()) planSessions(published, ::track) else emptyList()
            val wanted = plans.mapTo(mutableSetOf()) { it.session.id }

            // Withdrawals first: a session leaving the leaderboard should not queue behind uploads.
            for (link in dao.getLinks(KIND_SESSION)) {
                if (link.localId !in wanted) attempt(tally) { withdrawSession(link, tally) }
            }

            // After withdrawals, so a car deleted together with its sessions is no longer
            // held on the account by them; before uploads, so sessions find their car linked.
            garage?.run(garageTally)

            syncUserTracks(published, ::track, tally)

            for (plan in plans) attempt(tally) { uploadSession(plan, tally) }
        } catch (e: NetworkException) {
            return tally.report(garageTally, offline = true, problem = e.message)
        } catch (e: ApiException) {
            // Only a 401 escapes attempt(): the refresh failed and the driver is now signed out.
            return tally.report(garageTally, problem = e.message)
        }
        return tally.report(garageTally)
    }

    // ── Planning ──

    private suspend fun planSessions(
        published: Set<Long>,
        track: suspend (Long) -> TrackInfo?,
    ): List<Plan> = dao.getFinishedTrackSessions().mapNotNull { session ->
        if (PayloadMapper.gpsSource(session.gpsSource) == null) return@mapNotNull null
        val info = session.trackId?.let { track(it) } ?: return@mapNotNull null
        if (info.premadeId == null && info.track.trackId !in published) return@mapNotNull null

        val laps = dao.getLaps(session.id)
        // Same rules as the real upload, so "wanted" and "sendable" can never disagree.
        val sendable = PayloadMapper.session(session, laps, emptyList(), "planning", null, null) != null
        if (sendable) Plan(session, info, laps) else null
    }

    private suspend fun loadTrack(trackId: Long): TrackInfo? {
        val track = dao.getTrack(trackId) ?: return null
        val points = dao.getTrackPoints(trackId)
        return TrackInfo(track, points, premade.remoteIdFor(track, points))
    }

    // ── Sessions ──

    private suspend fun uploadSession(plan: Plan, tally: Tally) {
        val trackRemoteId = if (plan.track.premadeId != null) {
            ensurePremadeTrack(plan.track)
        } else {
            ensureUserTrack(plan.track, ApiTrackVisibility.Published)
        }

        // A vehicle that cannot be sent costs the session its vehicle, not the session itself.
        val vehicleRemoteId = plan.session.vehicleId?.let { ensureVehicle(it) }

        val sectors = dao.getSectors(plan.laps.map { it.id })
        val body = PayloadMapper.session(plan.session, plan.laps, sectors, trackRemoteId, vehicleRemoteId, appVersion)
            ?: return
        val hash = PayloadMapper.hash(body)
        val link = dao.getLink(KIND_SESSION, plan.session.id)

        if (link != null && link.uploadedHash == hash) {
            link.lastError?.let { throw SyncProblem(it) }
            tally.unchanged++
            return
        }

        val remoteId = link?.remoteId ?: newId()
        put(KIND_SESSION, plan.session.id, remoteId, hash) { token -> api.putSession(token, remoteId, body) }
        tally.uploaded++
    }

    private suspend fun withdrawSession(link: RemoteLink, tally: Tally) {
        auth.authorized { token -> api.deleteSession(token, link.remoteId) }
        dao.deleteLink(KIND_SESSION, link.localId)
        tally.withdrawn++
    }

    // ── Tracks ──

    private suspend fun syncUserTracks(
        published: Set<Long>,
        track: suspend (Long) -> TrackInfo?,
        tally: Tally,
    ) {
        for (trackId in published) {
            val info = track(trackId) ?: continue
            // Premade tracks are shared automatically; publishing one is not a thing.
            if (info.premadeId != null) continue
            attempt(tally) { ensureUserTrack(info, ApiTrackVisibility.Published) }
        }

        for (link in dao.getLinks(KIND_TRACK)) {
            if (link.localId in published) continue
            val info = track(link.localId)
            attempt(tally) {
                if (info == null) {
                    // Deleted on the phone. If other drivers have laps on it the server keeps
                    // it for them, which is correct; either way it is no longer ours to track.
                    try {
                        auth.authorized { token -> api.deleteTrack(token, link.remoteId) }
                    } catch (e: ApiException) {
                        if (e.status == 401 || e.code != "TrackInUse") throw e
                    }
                    dao.deleteLink(KIND_TRACK, link.localId)
                } else {
                    ensureUserTrack(info, ApiTrackVisibility.Private)
                }
            }
        }
    }

    /** Uploads the driver's own track at [visibility] if it changed, and returns its server id. */
    private suspend fun ensureUserTrack(info: TrackInfo, visibility: ApiTrackVisibility): String {
        val name = info.track.trackName
        val body = PayloadMapper.track(info.track, info.points, visibility)
            ?: throw SyncProblem("\"$name\" can't be published: it needs at least two valid track points.")
        val hash = PayloadMapper.hash(body)
        val link = dao.getLink(KIND_TRACK, info.track.trackId)

        if (link != null && link.uploadedHash == hash) {
            link.lastError?.let { throw SyncProblem(it) }
            return link.remoteId
        }

        val remoteId = link?.remoteId ?: newId()
        put(KIND_TRACK, info.track.trackId, remoteId, hash) { token -> api.putTrack(token, remoteId, body) }
        return remoteId
    }

    /**
     * Links a premade track to its shared server id, creating the server track if this driver
     * is the first to post on it. Never re-uploaded afterwards: the shared copy is not this
     * driver's to change, and a local sector re-slice must not try to.
     */
    private suspend fun ensurePremadeTrack(info: TrackInfo): String {
        val remoteId = info.premadeId!!
        val trackId = info.track.trackId
        val link = dao.getLink(KIND_PREMADE_TRACK, trackId)
        if (link != null && link.remoteId == remoteId && link.lastError == null) return remoteId

        val existing = auth.authorized { token -> api.getTrack(token, remoteId) }
        if (existing == null) {
            val body = PayloadMapper.track(info.track, info.points, ApiTrackVisibility.Published)
                ?: throw SyncProblem("\"${info.track.trackName}\" can't be shared: its bundled geometry is incomplete.")
            try {
                auth.authorized { token -> api.putTrack(token, remoteId, body) }
            } catch (e: ApiException) {
                if (e.status == 401) throw e
                // 403: another driver created it between our check and our write. Theirs is
                // identical by construction, so linking to it is exactly right — if we can see it.
                val createdByAnother = e.status == 403 &&
                    auth.authorized { token -> api.getTrack(token, remoteId) } != null
                if (!createdByAnother) {
                    throw SyncProblem("\"${info.track.trackName}\" isn't available on TrackBoard right now.")
                }
            }
        }

        dao.putLink(RemoteLink(KIND_PREMADE_TRACK, trackId, remoteId, uploadedHash = "shared", uploadedAt = clock()))
        return remoteId
    }

    // ── Vehicles ──

    private suspend fun ensureVehicle(localId: Long): String? {
        val vehicle = dao.getVehicle(localId) ?: return null
        val body = PayloadMapper.vehicle(vehicle) ?: return null
        val hash = PayloadMapper.hash(body)
        val link = dao.getLink(KIND_VEHICLE, localId)

        if (link != null && link.uploadedHash == hash) {
            return link.remoteId.takeIf { link.lastError == null }
        }

        val remoteId = link?.remoteId ?: newId()
        return try {
            put(KIND_VEHICLE, localId, remoteId, hash) { token -> api.putVehicle(token, remoteId, body) }
            remoteId
        } catch (e: SyncProblem) {
            null
        }
    }

    // ── Plumbing ──

    /**
     * Sends one record and remembers the outcome either way. A rejection is stored against the
     * payload's hash, so the same rejected payload is not re-sent every sync.
     */
    private suspend fun put(
        kind: String,
        localId: Long,
        remoteId: String,
        hash: String,
        send: suspend (token: String) -> Unit,
    ) {
        try {
            auth.authorized { token -> send(token) }
            dao.putLink(RemoteLink(kind, localId, remoteId, hash, clock(), lastError = null))
        } catch (e: ApiException) {
            if (e.status == 401) throw e
            val message = e.message ?: "The server rejected it."
            dao.putLink(RemoteLink(kind, localId, remoteId, hash, uploadedAt = null, lastError = message))
            throw SyncProblem(message)
        }
    }

    /** Runs one record's sync; a problem is tallied and the run carries on. */
    private suspend fun attempt(tally: Tally, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: SyncProblem) {
            tally.fail(e.message)
        } catch (e: ApiException) {
            if (e.status == 401) throw e
            tally.fail(e.message)
        }
    }

    private fun Tally.report(
        garage: GarageTally,
        offline: Boolean = false,
        problem: String? = this.problem ?: garage.problem,
    ) = SyncReport(
        finishedAt = clock(),
        uploaded = uploaded + garage.uploaded,
        unchanged = unchanged,
        withdrawn = withdrawn,
        downloaded = garage.downloaded,
        failed = failed + garage.failed,
        problem = problem,
        offline = offline,
    )
}
