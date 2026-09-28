package com.example.trackpro.online

import android.content.Context
import android.net.Uri
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** The account side of the Profile tab, as last fetched. Persisted so it shows offline. */
@Serializable
data class AccountSnapshot(
    val userId: String,
    val profile: ProfileResponse,
    val stats: ProfileStats? = null,
    /** Local copy of the avatar, so it renders offline. */
    val avatarFile: String? = null,
    /** The avatar URL [avatarFile] was downloaded from; a different URL means a new avatar. */
    val avatarSource: String? = null,
    val fetchedAt: Long,
)

/**
 * Everything the Profile tab reads and does.
 *
 * The career sheet is always the phone's own numbers ([career]); signing in adds the account
 * layer on top ([account]): the profile fields, the avatar, and leaderboard positions only the
 * server can know. Refreshing never blanks what is already on screen — a failed fetch leaves
 * the last snapshot in place and reports why in [problem].
 */
class ProfileRepository(
    context: Context,
    private val api: TrackBoardAccountApi,
    private val auth: AuthRepository,
    private val database: ESPDatabase,
    private val photos: PhotoStore,
) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("trackboard_profile", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(loadSnapshot())
    val account: StateFlow<AccountSnapshot?> = _account.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)
    val problem: StateFlow<String?> = _problem.asStateFlow()

    val career: Flow<LocalCareer> = database.careerDao().let { dao ->
        combine(dao.observeSessions(), dao.observeLaps(), dao.observeTracks(), dao.observeVehicles()) { s, l, t, v ->
            LocalCareer.compute(s, l, t, v)
        }.flowOn(Dispatchers.Default)
    }

    /** The snapshot, but only if it belongs to whoever is signed in now. */
    fun snapshotFor(state: AccountState): AccountSnapshot? =
        (state as? AccountState.SignedIn)?.let { signedIn -> _account.value?.takeIf { it.userId == signedIn.userId } }

    suspend fun refresh() {
        val signedIn = auth.state.value as? AccountState.SignedIn ?: return
        _refreshing.value = true
        try {
            val profile = auth.authorized { api.getProfile(it) }
            val stats = auth.authorized { api.getStats(it) }
            val previous = _account.value?.takeIf { it.userId == signedIn.userId }
            val (avatarFile, avatarSource) = resolveAvatar(previous, profile.avatarUrl)
            store(AccountSnapshot(signedIn.userId, profile, stats, avatarFile, avatarSource, System.currentTimeMillis()))
            if (profile.displayName != signedIn.displayName) auth.renamed(profile.displayName)
            _problem.value = null
        } catch (e: NetworkException) {
            _problem.value = "Offline. Showing what was last synced."
        } catch (e: ApiException) {
            _problem.value = if (e.status == 404) {
                "This server has no profiles yet. Showing this phone's records."
            } else {
                e.message
            }
        } finally {
            _refreshing.value = false
        }
    }

    /** Throws [ApiException] or [NetworkException] with a message fit to show. */
    suspend fun update(displayName: String?, bio: String?, country: String?) {
        val signedIn = auth.state.value as? AccountState.SignedIn ?: return
        val profile = auth.authorized { api.updateProfile(it, UpdateProfileRequest(displayName, bio, country)) }
        val previous = _account.value?.takeIf { it.userId == signedIn.userId }
        store(
            previous?.copy(profile = profile, fetchedAt = System.currentTimeMillis())
                ?: AccountSnapshot(signedIn.userId, profile, fetchedAt = System.currentTimeMillis())
        )
        auth.renamed(profile.displayName)
    }

    /** Re-encodes the picture, uploads it and makes it the avatar. Needs a connection. */
    suspend fun setAvatar(uri: Uri) {
        val signedIn = auth.state.value as? AccountState.SignedIn ?: return
        val name = photos.import(uri, "avatar") ?: throw ApiException(400, "That file could not be read as a picture.")
        try {
            val bytes = photos.bytes(name) ?: throw ApiException(400, "That file could not be read as a picture.")
            val target = auth.authorized { api.createUpload(it, UploadRequest(MediaKind.Avatar, contentType = "image/jpeg")) }
            api.uploadBytes(target.uploadUrl, bytes, "image/jpeg")
            val profile = auth.authorized { api.setAvatar(it, target.path) }
            val previous = _account.value?.takeIf { it.userId == signedIn.userId }
            previous?.avatarFile?.let(photos::delete)
            store(
                (previous ?: AccountSnapshot(signedIn.userId, profile, fetchedAt = System.currentTimeMillis()))
                    .copy(profile = profile, avatarFile = name, avatarSource = profile.avatarUrl)
            )
        } catch (e: Exception) {
            photos.delete(name)
            throw e
        }
    }

    suspend fun removeAvatar() {
        val signedIn = auth.state.value as? AccountState.SignedIn ?: return
        val profile = auth.authorized { api.setAvatar(it, null) }
        val previous = _account.value?.takeIf { it.userId == signedIn.userId }
        previous?.avatarFile?.let(photos::delete)
        store(
            (previous ?: AccountSnapshot(signedIn.userId, profile, fetchedAt = System.currentTimeMillis()))
                .copy(profile = profile, avatarFile = null, avatarSource = null)
        )
    }

    /** Writes the server's export document to [target], a file the driver picked. */
    suspend fun export(target: Uri) {
        val document = auth.authorized { api.exportAccount(it) }
        withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(target)?.use { it.write(document.toByteArray(Charsets.UTF_8)) }
                ?: throw NetworkException("Could not write to the chosen file.")
        }
    }

    /**
     * Deletes the account on the server, then everything on this phone that pointed at it.
     * The phone's own records — cars, tracks, sessions — stay: they were never the account's.
     */
    suspend fun deleteAccount() {
        auth.authorized { api.deleteAccount(it) }
        database.syncDao().deleteAllLinks()
        _account.value?.avatarFile?.let(photos::delete)
        clear()
        SyncScheduler.cancelAll(appContext)
        auth.forgetLocally("Your TrackBoard account was deleted. Everything on this phone is still here.")
    }

    /** Drops the cached account layer, e.g. after signing out. */
    fun clear() {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
        _account.value = null
        _problem.value = null
    }

    private suspend fun resolveAvatar(previous: AccountSnapshot?, url: String?): Pair<String?, String?> {
        if (url == null) {
            previous?.avatarFile?.let(photos::delete)
            return null to null
        }
        if (previous?.avatarSource == url && photos.file(previous.avatarFile) != null) {
            return previous.avatarFile to url
        }
        val bytes = runCatching { api.download(url) }.getOrNull() ?: return previous?.avatarFile to previous?.avatarSource
        val name = photos.importBytes(bytes, "avatar")
        previous?.avatarFile?.let(photos::delete)
        return name to url
    }

    private fun store(snapshot: AccountSnapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, OkHttpTrackBoardApi.json.encodeToString(AccountSnapshot.serializer(), snapshot)).apply()
        _account.value = snapshot
    }

    private fun loadSnapshot(): AccountSnapshot? =
        prefs.getString(KEY_SNAPSHOT, null)?.let {
            runCatching { OkHttpTrackBoardApi.json.decodeFromString(AccountSnapshot.serializer(), it) }.getOrNull()
        }

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
    }
}
