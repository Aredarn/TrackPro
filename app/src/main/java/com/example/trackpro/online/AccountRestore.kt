package com.example.trackpro.online

import com.example.trackpro.dao.SyncDao
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_PREMADE_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_SESSION
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_TRACK
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.TrackPublication
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.models.GpsProviderType
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/** What restoring did in one run. Folded into the [SyncReport] the Settings screen shows. */
class RestoreTally {
    var downloaded = 0
    var failed = 0
    var problem: String? = null

    fun fail(message: String?) {
        failed++
        if (problem == null) problem = message ?: "Something could not be restored."
    }
}

/**
 * Brings the driver's own tracks and sessions from their TrackBoard account onto this phone:
 * how a new phone or a reinstall gets its history back, and how a session driven with another
 * phone shows up on this one.
 *
 * Everything restored is linked to the record it came from, with the fingerprint the upload
 * side computes for it. To the rest of sync it then looks exactly like something this phone
 * sent itself: unchanged, it is never sent back; edited here, it goes up as an update to the
 * same record, never as a copy.
 *
 * The account holds less than the phone recorded. A session is there only if it was shared
 * to a leaderboard, and it carries laps, sectors and weather but not the GPS trace, which
 * never leaves the phone - so a restored session has its times but no map or speed data.
 *
 * Nothing here deletes a local row. A session or track that disappears from the account is
 * left alone on the phone, as the garage does with cars.
 */
class AccountRestore(
    private val api: TrackBoardApi,
    private val auth: AuthRepository,
    private val dao: SyncDao,
    private val premade: PremadeTracks,
    private val appVersion: String?,
    /** Runs a block atomically: Room's withTransaction in the app, a plain call in tests. */
    private val transaction: suspend (suspend () -> Unit) -> Unit,
    private val clock: () -> Long,
) {
    private class LocalTrack(val track: TrackMainData, val points: List<TrackCoordinatesData>) {
        val fingerprint by lazy { PayloadMapper.timingFingerprint(points) }
    }

    /** Every local track with its points, read once per run and only if something needs it. */
    private var localTracks: List<LocalTrack>? = null

    private suspend fun localTracks(): List<LocalTrack> =
        localTracks ?: dao.getTracks().map { LocalTrack(it, dao.getTrackPoints(it.trackId)) }.also { localTracks = it }

    // ── Tracks ──

    /**
     * The driver's own tracks, published and private. Runs before [restoreSessions], which
     * needs the tracks to put sessions on.
     */
    suspend fun restoreTracks(tally: RestoreTally) {
        localTracks = null
        val remote = listing(tally, "your tracks") { token, page ->
            api.listMyTracks(token, page, PAGE_SIZE).let { it.items to it.totalCount }
        } ?: return

        // Linked either way already, including tracks deleted here that the account had to
        // keep for other drivers' laps: those must not come back.
        val known = (dao.getLinks(KIND_TRACK) + dao.getLinks(KIND_PREMADE_TRACK)).mapTo(mutableSetOf()) { it.remoteId }
        val missing = remote.filter { it.id !in known && !isPremadeId(it.id) }
        if (missing.isEmpty()) return

        // A premade track created by this driver lists as theirs. Every install has it
        // already, bundled, and it is not theirs to copy.
        val bundled = localTracks().mapNotNullTo(mutableSetOf()) { premade.remoteIdFor(it.track, it.points) }
        for (summary in missing) {
            if (summary.id in bundled) continue
            attempt(tally) {
                val detail = auth.authorized { token -> api.getTrackDetail(token, summary.id) } ?: return@attempt
                restoreTrack(detail, tally)
            }
        }
    }

    private suspend fun restoreTrack(detail: TrackDetail, tally: RestoreTally) {
        val points = detail.points.sortedBy { it.seq }.map { p ->
            TrackCoordinatesData(
                trackId = 0,
                latitude = p.latitude,
                longitude = p.longitude,
                altitude = p.altitude,
                isStartPoint = p.isStartPoint,
                isSectorPoint = p.isSectorPoint,
                sectorIndex = p.sectorIndex,
            )
        }
        // Nothing a timer could use; the server never accepts fewer, but be sure.
        if (points.size < 2) return
        val published = detail.visibility == ApiTrackVisibility.Published

        // The same track built by hand on both phones: link the local one rather than doubling it.
        val linked = dao.getLinks(KIND_TRACK).mapTo(mutableSetOf()) { it.localId }
        val fingerprint = PayloadMapper.timingFingerprint(points)
        val twin = localTracks().firstOrNull {
            it.track.trackId !in linked &&
                it.track.trackName == detail.name &&
                it.fingerprint == fingerprint &&
                premade.remoteIdFor(it.track, it.points) == null
        }
        if (twin != null) {
            transaction {
                if (published) dao.publish(TrackPublication(twin.track.trackId, clock()))
                // Not yet compared: the next upload settles any difference, the phone's copy winning.
                dao.putLink(RemoteLink(KIND_TRACK, twin.track.trackId, detail.id, PAIRED, clock()))
            }
            return
        }

        transaction {
            val trackId = dao.insertTrack(
                TrackMainData(
                    trackName = detail.name,
                    // The contract carries metres; the app stores kilometres.
                    totalLength = detail.lengthMeters?.let { it / 1000 },
                    country = detail.country,
                    type = if (detail.type == ApiTrackType.Sprint) "Sprint" else "Circuit",
                )
            )
            dao.insertTrackPoints(points.map { it.copy(trackId = trackId) })
            if (published) dao.publish(TrackPublication(trackId, clock()))

            // Fingerprinted from the rows as stored, exactly as an upload would read them.
            val visibility = if (published) ApiTrackVisibility.Published else ApiTrackVisibility.Private
            val stored = dao.getTrack(trackId)?.let { PayloadMapper.track(it, dao.getTrackPoints(trackId), visibility) }
            dao.putLink(RemoteLink(KIND_TRACK, trackId, detail.id, stored?.let(PayloadMapper::hash), clock()))
        }
        localTracks = null
        tally.downloaded++
    }

    // ── Sessions ──

    /**
     * The driver's sessions. Only called while leaderboard sharing is on: the account's
     * sessions are exactly the shared ones, and restoring them with sharing off would have the
     * next sync withdraw them from the leaderboard - on every phone at once.
     */
    suspend fun restoreSessions(tally: RestoreTally) {
        val remote = listing(tally, "your sessions") { token, page ->
            api.listSessions(token, page, PAGE_SIZE).let { it.items to it.totalCount }
        } ?: return

        // Linked already, including sessions deleted here whose withdrawal has not gone
        // through yet: those must not come back either.
        val links = dao.getLinks(KIND_SESSION)
        val cars = carIndex()
        reattachCars(remote, links, cars, tally)

        val known = links.mapTo(mutableSetOf()) { it.remoteId }
        val missing = remote.filter { it.id !in known && it.trackId != null && it.lapCount > 0 }
        if (missing.isEmpty()) return

        val tracks = trackIndex()
        // The sessions here by the millisecond they started, which no two different sessions
        // share. The account can hold one session twice - a phone that lost its links and
        // posted it again, or two phones with the same history - so a record is matched
        // against every session here, linked or not, before anything is added.
        val here = dao.getFinishedTrackSessions().groupBy { it.startTime }
        val linkedHere = links.mapTo(mutableSetOf()) { it.localId }

        for (summary in missing) {
            val startTime = parseInstant(summary.startedAt) ?: continue
            val copies = here[startTime]
            if (copies != null) {
                attempt(tally) { pair(copies, summary.id, linkedHere) }
                continue
            }

            // A track this phone does not have, or one it has but does not share: a session
            // restored onto it would be withdrawn again by the next sync, so it stays online.
            val localTrackId = tracks[summary.trackId] ?: continue
            attempt(tally) {
                val detail = auth.authorized { token -> api.getSession(token, summary.id) } ?: return@attempt
                if (restoreSession(detail, summary.trackId!!, localTrackId, cars, tally)) tally.downloaded++
            }
        }
    }

    /**
     * The account's record of a session this phone already has. A copy here without a link -
     * the links were lost, say - takes the record, so its next upload updates it rather than
     * posting yet another. A copy already linked to a different record means the account holds
     * the session twice; that is left as it is, and nothing is added here.
     */
    private suspend fun pair(copies: List<SessionData>, remoteId: String, linkedHere: MutableSet<Long>) {
        val twin = copies.firstOrNull { it.id !in linkedHere } ?: return
        // Only a copy this phone could post itself, or the next sync would withdraw the
        // account's record for it. The planner's own test, so the two can never disagree.
        val sendable = PayloadMapper.session(twin, dao.getLaps(twin.id), emptyList(), "planning", null, null) != null
        if (!sendable) return
        dao.putLink(RemoteLink(KIND_SESSION, twin.id, remoteId, PAIRED, clock()))
        linkedHere += twin.id
    }

    /**
     * Removes what the first version of this restore did when the account held a session
     * twice: it brought the second record down as a second copy. Such a copy is unmistakable -
     * the same start as another session here, linked, and without a GPS trace, which no
     * restored session has - and dropping it with its link loses nothing: the account keeps
     * both records, and the copy kept here stays linked to one of them.
     *
     * Only ever local. Nothing is withdrawn from the account. Run before sync plans its
     * uploads, or a copy dropped mid-run would be posted as a new record.
     */
    suspend fun dropRestoredDuplicates() {
        val linked = dao.getLinks(KIND_SESSION).mapTo(mutableSetOf()) { it.localId }
        val groups = dao.getFinishedTrackSessions().groupBy { it.startTime }.values.filter { it.size > 1 }
        for (copies in groups) {
            // The oldest row stays: the original, or at least the first copy made.
            for (extra in copies.sortedBy { it.id }.drop(1)) {
                if (extra.id !in linked || dao.hasGpsTrace(extra.id)) continue
                transaction {
                    dao.deleteSession(extra.id)
                    dao.deleteLink(KIND_SESSION, extra.id)
                }
            }
        }
    }

    private suspend fun restoreSession(
        detail: SessionDetail,
        remoteTrackId: String,
        localTrackId: Long,
        cars: MutableMap<String, Long>,
        tally: RestoreTally,
    ): Boolean {
        val startTime = parseInstant(detail.startedAt) ?: return false
        val laps = detail.laps
            .filter { it.lapNumber >= 1 && it.timeMs > 0 }
            .distinctBy { it.lapNumber }
            .sortedBy { it.lapNumber }
        if (laps.isEmpty()) return false

        // Sync only posts finished sessions, so there always is an end; should one be missing,
        // the laps back to back stand in, since a session without an end is never synced again.
        val endTime = detail.endedAt?.let(::parseInstant)?.takeIf { it >= startTime }
            ?: (startTime + laps.sumOf { it.timeMs.toLong() })
        val vehicleId = detail.vehicleId?.let { carFor(it, cars, tally) }
        val weather = detail.weather

        transaction {
            val sessionId = dao.insertSession(
                SessionData(
                    startTime = startTime,
                    endTime = endTime,
                    eventType = detail.name,
                    vehicleId = vehicleId,
                    trackId = localTrackId,
                    weatherTempC = weather?.tempC,
                    weatherHumidityPct = weather?.humidityPct,
                    weatherPrecipitationMm = weather?.precipitationMm,
                    weatherCode = weather?.weatherCode,
                    weatherWindKph = weather?.windKph,
                    weatherWindDirDeg = weather?.windDirDeg,
                    weatherPressureHpa = weather?.pressureHpa,
                    voided = detail.voided,
                    gpsSource = detail.gpsSource.toLocal().name,
                )
            )
            for (lap in laps) {
                val lapId = dao.insertLap(
                    LapTimeData(
                        sessionid = sessionId,
                        lapnumber = lap.lapNumber,
                        laptime = lap.timeMs.toLong().toLapTimeString(),
                        signalGap = lap.signalGap,
                    )
                )
                if (lap.sectors.isNotEmpty()) {
                    dao.insertSectors(lap.sectors.map { SectorTimeData(lapid = lapId, sectorIndex = it.sectorIndex, splitTimeMs = it.splitMs.toLong()) })
                }
            }

            // Fingerprinted from the rows as stored, with the ids an upload would use: the
            // account's own track, and its car - absent only if the account no longer has it.
            val session = dao.getSession(sessionId)
            val storedLaps = dao.getLaps(sessionId)
            val stored = session?.let {
                PayloadMapper.session(
                    it, storedLaps, dao.getSectors(storedLaps.map { lap -> lap.id }),
                    remoteTrackId, detail.vehicleId.takeIf { vehicleId != null }, appVersion,
                )
            }
            dao.putLink(RemoteLink(KIND_SESSION, sessionId, detail.id, stored?.let(PayloadMapper::hash), clock()))
        }
        return true
    }

    /**
     * Server track id to local track id, for the tracks a restored session would stay online
     * on: premade ones, and the driver's own published ones.
     */
    private suspend fun trackIndex(): Map<String, Long> {
        val index = mutableMapOf<String, Long>()
        val published = dao.getPublishedTrackIds().toSet()
        for (link in dao.getLinks(KIND_TRACK)) {
            if (link.localId in published) index[link.remoteId] = link.localId
        }
        for (local in localTracks()) {
            premade.remoteIdFor(local.track, local.points)?.let { index.putIfAbsent(it, local.track.trackId) }
        }
        return index
    }

    /** Server car id to local car id, for the cars still on this phone. */
    private suspend fun carIndex(): MutableMap<String, Long> =
        dao.getLinks(KIND_VEHICLE)
            .filter { dao.getVehicle(it.localId) != null }
            .associateTo(mutableMapOf()) { it.remoteId to it.localId }

    /**
     * The local car for an account car, brought down if this phone does not have it - even a
     * car deleted here, which the garage otherwise leaves on the account alone. A session
     * driven in it is on this phone now: without its car it reads "Unknown car", and the first
     * edit made to it here would post it without the car and take the car off it online too.
     *
     * Null only when the account no longer has the car.
     */
    private suspend fun carFor(remoteId: String, cars: MutableMap<String, Long>, tally: RestoreTally): Long? {
        cars[remoteId]?.let { return it }
        val remote = auth.authorized { token -> api.getVehicle(token, remoteId) } ?: return null

        var localId = 0L
        transaction {
            localId = dao.insertVehicle(remote.applyTo(null))
            // A car deleted here is remembered under its old local id, and the garage would
            // keep treating it as deleted; it is here again now.
            for (stale in dao.getLinks(KIND_VEHICLE).filter { it.remoteId == remoteId }) {
                dao.deleteLink(KIND_VEHICLE, stale.localId)
            }
            // The same fingerprint the garage gives a car it imports, so it takes over from
            // here - its photo included - as if it had brought the car down itself.
            dao.putLink(RemoteLink(KIND_VEHICLE, localId, remoteId, PayloadMapper.hash(remote.toWrite()), clock()))
        }
        cars[remoteId] = localId
        tally.downloaded++
        return localId
    }

    /**
     * Gives back their car to sessions brought down without it. The first version of this
     * restore left a session's car out whenever the car was not on this phone; the account
     * still names it, so the car comes down now and the session gets it again.
     */
    private suspend fun reattachCars(
        remote: List<SessionSummary>,
        links: List<RemoteLink>,
        cars: MutableMap<String, Long>,
        tally: RestoreTally,
    ) {
        val byRemote = links.associateBy { it.remoteId }
        for (summary in remote) {
            val remoteCar = summary.vehicleId ?: continue
            val remoteTrack = summary.trackId ?: continue
            val link = byRemote[summary.id] ?: continue
            val session = dao.getSession(link.localId) ?: continue
            if (session.vehicleId != null) continue

            attempt(tally) {
                val carId = carFor(remoteCar, cars, tally) ?: return@attempt
                val laps = dao.getLaps(session.id)
                val sectors = dao.getSectors(laps.map { it.id })
                val withoutCar = PayloadMapper.session(session, laps, sectors, remoteTrack, null, appVersion)
                transaction {
                    dao.setSessionVehicle(session.id, carId)
                    // In step with the account before, so in step now: the account had the car
                    // all along. A change made here and not yet sent keeps its old fingerprint,
                    // and goes up at the next upload - now with its car.
                    if (withoutCar != null && PayloadMapper.hash(withoutCar) == link.uploadedHash) {
                        val withCar = PayloadMapper.session(session.copy(vehicleId = carId), laps, sectors, remoteTrack, remoteCar, appVersion)
                        dao.putLink(link.copy(uploadedHash = withCar?.let(PayloadMapper::hash)))
                    }
                }
            }
        }
    }

    // ── Plumbing ──

    /**
     * Every page of a listing. Null when the server refused it: that costs this run the
     * restore, not the uploads that follow.
     */
    private suspend fun <T> listing(
        tally: RestoreTally,
        what: String,
        fetch: suspend (token: String, page: Int) -> Pair<List<T>, Long>,
    ): List<T>? {
        val all = mutableListOf<T>()
        var page = 1
        while (true) {
            val (items, total) = try {
                auth.authorized { token -> fetch(token, page) }
            } catch (e: ApiException) {
                if (e.status == 401) throw e
                tally.fail("Could not read $what from TrackBoard: ${e.message}")
                return null
            }
            all += items
            if (items.isEmpty() || all.size >= total) return all
            page++
        }
    }

    private suspend fun attempt(tally: RestoreTally, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: SyncProblem) {
            tally.fail(e.message)
        } catch (e: ApiException) {
            if (e.status == 401) throw e
            tally.fail(e.message)
        }
    }

    companion object {
        private const val PAGE_SIZE = 100

        /** Linked to a matching local record that has not been compared yet. */
        const val PAIRED = "paired"

        /**
         * Premade track ids are name-based (version 3) UUIDs, derived the same on every
         * install; everything the app creates itself is random (version 4).
         */
        fun isPremadeId(id: String): Boolean =
            runCatching { UUID.fromString(id).version() == 3 }.getOrDefault(false)

        /** .NET may send an offset rather than "Z", which Instant.parse rejects before API 34. */
        fun parseInstant(value: String): Long? =
            runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()

        private fun ApiGpsSource.toLocal(): GpsProviderType = when (this) {
            ApiGpsSource.Wifi -> GpsProviderType.WIFI
            ApiGpsSource.Bluetooth -> GpsProviderType.BLUETOOTH
            ApiGpsSource.PhoneGps -> GpsProviderType.PHONE_GPS
        }
    }
}
