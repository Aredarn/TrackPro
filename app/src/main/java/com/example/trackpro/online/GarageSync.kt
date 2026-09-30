package com.example.trackpro.online

import com.example.trackpro.dao.SyncDao
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE_PHOTO
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.utilities.PhotoFiles
import java.util.UUID

/** What garage sync did in one run. Folded into the [SyncReport] the Settings screen shows. */
class GarageTally {
    var uploaded = 0
    var downloaded = 0
    var failed = 0
    var problem: String? = null

    fun fail(message: String?) {
        failed++
        if (problem == null) problem = message ?: "A vehicle could not be synced."
    }
}

/**
 * The driver's garage, backed up to their TrackBoard account.
 *
 * The phone stays the place a car lives: everything here is a comparison of local rows
 * against the account, run on every sync, and a car works offline whatever happens to it.
 * Unlike leaderboard sync this does not depend on sharing being switched on — it is a
 * backup of the driver's own data, so being signed in is the consent.
 *
 * Per car, against the fingerprint of what was last agreed with the server:
 * - changed here → uploaded (the phone wins a simultaneous edit; it is where you are looking)
 * - changed only on the account (another phone) → pulled down
 * - on the account but not here → imported, which is how a new phone gets the garage back
 * - deleted here → deleted from the account, unless uploaded sessions still need it
 * - deleted from the account elsewhere → kept here and marked local-only, never silently
 *   destroyed; the driver can put it back from the car's page
 *
 * Photos follow the same rules, compared by content hash.
 */
class GarageSync(
    private val api: TrackBoardAccountApi,
    private val core: TrackBoardApi,
    private val auth: AuthRepository,
    private val dao: SyncDao,
    private val photos: PhotoFiles,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Photo storage answered 503 once this run; the rest would too. */
    private var photosUnavailable = false

    suspend fun run(tally: GarageTally) {
        photosUnavailable = false
        val remote = listRemote()
        val remoteById = remote.associateBy { it.id }
        val links = dao.getLinks(KIND_VEHICLE).associateBy { it.localId }
        val linkedRemoteIds = links.values.mapTo(mutableSetOf()) { it.remoteId }
        var locals = dao.getVehicles()
        val localIds = locals.mapTo(mutableSetOf()) { it.vehicleId }

        // ── Deleted on this phone ──
        for (link in links.values) {
            if (link.localId in localIds || link.uploadedHash == DELETED_HERE) continue
            attempt(tally) { deleteRemote(link) }
        }

        // ── First contact: pair identical cars instead of duplicating them ──
        // A reinstall, or a second phone where the same car was typed in by hand. Pairing is
        // by make, model and year; the local copy then wins like any other local edit.
        val unlinkedRemote = remote.filter { it.id !in linkedRemoteIds }.toMutableList()
        for (local in locals.filter { it.vehicleId !in links }) {
            val match = unlinkedRemote.firstOrNull { it.sameCarAs(local) } ?: continue
            unlinkedRemote.remove(match)
            dao.putLink(RemoteLink(KIND_VEHICLE, local.vehicleId, match.id, uploadedHash = PAIRED, uploadedAt = clock()))
        }

        // ── Imports: on the account, not on this phone ──
        for (vehicle in unlinkedRemote) {
            attempt(tally) { import(vehicle, tally) }
        }

        // ── Everything present here ──
        locals = dao.getVehicles()
        for (local in locals) {
            val link = dao.getLink(KIND_VEHICLE, local.vehicleId)
            attempt(tally) {
                when {
                    link == null -> uploadNew(local, remoteId = newId(), tally)
                    link.uploadedHash == REMOTE_GONE || link.uploadedHash == DELETED_HERE -> Unit
                    link.remoteId in remoteById -> reconcile(local, link, remoteById.getValue(link.remoteId), tally)
                    // Linked, but the account no longer has it. Only a car that really was
                    // on the account counts as deleted there: a rejected upload never arrived.
                    link.uploadedAt != null && link.lastError == null -> markRemoteGone(link)
                    else -> uploadNew(local, link.remoteId, tally)
                }
            }
        }
    }

    // ── Vehicles ──

    private suspend fun listRemote(): List<VehicleResponse> {
        val all = mutableListOf<VehicleResponse>()
        var page = 1
        while (true) {
            val batch = auth.authorized { token -> api.listVehicles(token, page, PAGE_SIZE) }
            all += batch.items
            if (batch.items.isEmpty() || all.size >= batch.totalCount) return all
            page++
        }
    }

    private suspend fun upload(local: VehicleInformationData, remoteId: String, tally: GarageTally) {
        val body = PayloadMapper.vehicle(local)
            ?: throw SyncProblem("${local.label()} is missing details the account needs, so it stays on this phone.")
        val hash = PayloadMapper.hash(body)
        val link = dao.getLink(KIND_VEHICLE, local.vehicleId)
        if (link != null && link.uploadedHash == hash && link.remoteId == remoteId) {
            link.lastError?.let { throw SyncProblem(it) }
            return
        }
        try {
            auth.authorized { token -> core.putVehicle(token, remoteId, body) }
        } catch (e: ApiException) {
            if (e.status == 401) throw e
            dao.putLink(RemoteLink(KIND_VEHICLE, local.vehicleId, remoteId, hash, uploadedAt = null, lastError = e.message))
            throw SyncProblem(e.message ?: "The account refused ${local.label()}.")
        }
        dao.putLink(RemoteLink(KIND_VEHICLE, local.vehicleId, remoteId, hash, clock()))
        tally.uploaded++
    }

    /** A car the account does not have yet: it has no photo there either. */
    private suspend fun uploadNew(local: VehicleInformationData, remoteId: String, tally: GarageTally) {
        upload(local, remoteId, tally)
        syncPhoto(local, remoteId, remotePhotoUrl = null, tally)
    }

    private suspend fun reconcile(
        local: VehicleInformationData,
        link: RemoteLink,
        remote: VehicleResponse,
        tally: GarageTally,
    ) {
        val localBody = PayloadMapper.vehicle(local)
        val localHash = localBody?.let(PayloadMapper::hash)
        val remoteHash = PayloadMapper.hash(remote.toWrite())

        // The listing's photo URL stays valid through the data upload below: the vehicle
        // upsert never touches the photo. The photo is reconciled exactly once, after.
        var current = local
        when {
            // Just paired: the phone's copy is the one the driver is looking at.
            link.uploadedHash == PAIRED -> upload(local, link.remoteId, tally)
            localHash != null && localHash != link.uploadedHash -> upload(local, link.remoteId, tally)
            remoteHash != link.uploadedHash -> {
                current = remote.applyTo(local)
                dao.updateVehicle(current)
                dao.putLink(link.copy(uploadedHash = remoteHash, uploadedAt = clock(), lastError = null))
                tally.downloaded++
            }
        }
        syncPhoto(current, link.remoteId, remote.photoUrl, tally)
    }

    private suspend fun import(remote: VehicleResponse, tally: GarageTally) {
        val localId = dao.insertVehicle(remote.applyTo(null))
        dao.putLink(RemoteLink(KIND_VEHICLE, localId, remote.id, PayloadMapper.hash(remote.toWrite()), clock()))
        tally.downloaded++
        remote.photoUrl?.let { url -> download(dao.getVehicle(localId) ?: return, url) }
    }

    private suspend fun deleteRemote(link: RemoteLink) {
        try {
            auth.authorized { token -> api.deleteVehicle(token, link.remoteId) }
        } catch (e: ApiException) {
            if (e.status == 401 || e.code != "VehicleInUse") throw e
            // Leaderboard sessions still name this car, so the account keeps it for them.
            // Remember that, or the next sync would import it straight back.
            dao.putLink(link.copy(uploadedHash = DELETED_HERE, lastError = null))
            dao.deleteLink(KIND_VEHICLE_PHOTO, link.localId)
            return
        }
        dao.deleteLink(KIND_VEHICLE, link.localId)
        dao.deleteLink(KIND_VEHICLE_PHOTO, link.localId)
    }

    private suspend fun markRemoteGone(link: RemoteLink) {
        dao.putLink(
            link.copy(
                uploadedHash = REMOTE_GONE,
                lastError = "Removed from your account on another device. Kept on this phone.",
            )
        )
        dao.deleteLink(KIND_VEHICLE_PHOTO, link.localId)
    }

    // ── Photos ──

    private suspend fun syncPhoto(
        local: VehicleInformationData,
        remoteId: String,
        remotePhotoUrl: String?,
        tally: GarageTally,
    ) {
        if (photosUnavailable) return
        val link = dao.getLink(KIND_VEHICLE_PHOTO, local.vehicleId)
        val localHash = photos.hash(local.photoFile)

        try {
            when {
                localHash != null && localHash != link?.uploadedHash -> uploadPhoto(local, remoteId, localHash, tally)
                localHash != null && remotePhotoUrl != link?.remoteId -> {
                    // Unchanged here, changed on the account.
                    if (remotePhotoUrl == null) removeLocalPhoto(local) else download(local, remotePhotoUrl)
                }
                localHash == null && link != null -> {
                    // Removed on this phone after it had been synced.
                    auth.authorized { token -> api.setVehiclePhoto(token, remoteId, null) }
                    dao.deleteLink(KIND_VEHICLE_PHOTO, local.vehicleId)
                }
                localHash == null && remotePhotoUrl != null -> download(local, remotePhotoUrl)
            }
        } catch (e: ApiException) {
            if (e.status == 401) throw e
            if (e.status == 503) {
                photosUnavailable = true
                tally.fail("This server does not store photos, so they stay on this phone.")
                return
            }
            throw SyncProblem("The photo of ${local.label()} could not be synced: ${e.message}")
        }
    }

    private suspend fun uploadPhoto(local: VehicleInformationData, remoteId: String, hash: String, tally: GarageTally) {
        val bytes = photos.bytes(local.photoFile) ?: return
        val target = auth.authorized { token ->
            api.createUpload(token, UploadRequest(MediaKind.VehiclePhoto, remoteId, CONTENT_TYPE))
        }
        api.uploadBytes(target.uploadUrl, bytes, CONTENT_TYPE)
        val attached = auth.authorized { token -> api.setVehiclePhoto(token, remoteId, target.path) }
        dao.putLink(RemoteLink(KIND_VEHICLE_PHOTO, local.vehicleId, attached.photoUrl ?: target.publicUrl, hash, clock()))
        tally.uploaded++
    }

    private suspend fun download(local: VehicleInformationData, url: String) {
        val bytes = api.download(url) ?: return
        val name = photos.importBytes(bytes, "vehicle-${local.vehicleId}")
        dao.updateVehicle(local.copy(photoFile = name))
        photos.delete(local.photoFile)
        dao.putLink(RemoteLink(KIND_VEHICLE_PHOTO, local.vehicleId, url, photos.hash(name), clock()))
    }

    private suspend fun removeLocalPhoto(local: VehicleInformationData) {
        dao.updateVehicle(local.copy(photoFile = null))
        photos.delete(local.photoFile)
        dao.deleteLink(KIND_VEHICLE_PHOTO, local.vehicleId)
    }

    // ── Plumbing ──

    private suspend fun attempt(tally: GarageTally, block: suspend () -> Unit) {
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
        private const val CONTENT_TYPE = "image/jpeg"

        /** Paired with an identical account car on first contact; not yet reconciled. */
        const val PAIRED = "paired"

        /** The account deleted this car elsewhere; the phone keeps it as local-only. */
        const val REMOTE_GONE = "remote-gone"

        /** Deleted on this phone, but the account had to keep it for uploaded sessions. */
        const val DELETED_HERE = "deleted-here"

        private fun VehicleInformationData.label() = "$manufacturer $model".trim()

        private fun VehicleResponse.sameCarAs(local: VehicleInformationData) =
            manufacturer.trim().equals(local.manufacturer.trim(), ignoreCase = true) &&
                model.trim().equals(local.model.trim(), ignoreCase = true) &&
                year == local.year
    }
}

/** The account's copy as a local row, keeping the local id and photo when there is one. */
internal fun VehicleResponse.applyTo(local: VehicleInformationData?) = VehicleInformationData(
    vehicleId = local?.vehicleId ?: 0,
    manufacturer = manufacturer,
    model = model,
    year = year,
    engineType = engineType,
    horsepower = horsepower,
    torque = torque,
    weight = weight,
    topSpeed = topSpeed,
    acceleration = acceleration,
    drivetrain = drivetrain,
    fuelType = fuelType,
    tireType = tireType,
    fuelCapacity = fuelCapacity,
    transmission = transmission,
    suspensionType = suspensionType,
    photoFile = local?.photoFile,
)
