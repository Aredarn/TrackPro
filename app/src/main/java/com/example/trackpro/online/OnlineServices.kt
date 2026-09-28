package com.example.trackpro.online

import android.content.Context
import com.example.trackpro.managerClasses.ESPDatabase

/**
 * Everything TrackBoard, reached through `TrackProApp.online` the same way the rest of the
 * app reaches its database and GPS manager.
 */
class OnlineServices(private val context: Context, private val database: ESPDatabase) {

    val settings = OnlineSettings(context)

    val api: TrackBoardApi = OkHttpTrackBoardApi(baseUrl = { settings.serverUrl.value })

    val auth = AuthRepository(api, KeystoreTokenStore(context))

    private val premadeTracks: PremadeTracks = BundledPremadeTracks(context)

    private val appVersion: String? by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    fun syncEngine(): SyncEngine = SyncEngine(
        api = api,
        auth = auth,
        dao = database.syncDao(),
        premade = premadeTracks,
        sharingEnabled = { settings.sharingEnabled.value },
        appVersion = appVersion,
    )

    /** The server id a local track's leaderboard lives under, or null if it has none yet. */
    suspend fun remoteTrackIdFor(localTrackId: Long): String? {
        val dao = database.syncDao()
        val track = dao.getTrack(localTrackId) ?: return null
        premadeTracks.remoteIdFor(track, dao.getTrackPoints(localTrackId))?.let { return it }
        return dao.getLink(com.example.trackpro.dataClasses.RemoteLink.KIND_TRACK, localTrackId)
            ?.takeIf { it.lastError == null }
            ?.remoteId
    }

    /** True for an unmodified premade track, which is shared automatically and never "published". */
    suspend fun isPremade(localTrackId: Long): Boolean {
        val dao = database.syncDao()
        val track = dao.getTrack(localTrackId) ?: return false
        return premadeTracks.remoteIdFor(track, dao.getTrackPoints(localTrackId)) != null
    }
}
