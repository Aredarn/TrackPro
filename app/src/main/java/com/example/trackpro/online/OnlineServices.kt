package com.example.trackpro.online

import android.content.Context
import androidx.room.withTransaction
import android.util.Log
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.managerClasses.ESPDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.trackpro.managerClasses.utilities.PhotoStore

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

    /** Same client as [api]; the account endpoints are a second interface on it. */
    val accountApi: TrackBoardAccountApi = api as TrackBoardAccountApi

    val photos = PhotoStore(context)

    val profile: ProfileRepository by lazy {
        ProfileRepository(context, accountApi, auth, database, photos)
    }

    /** Same client again: the track-day endpoints. */
    val eventsApi: TrackBoardEventsApi = api as TrackBoardEventsApi

    val events: EventRepository by lazy {
        EventRepository(
            context = context,
            api = eventsApi,
            auth = auth,
            dao = database.syncDao(),
            remoteTrackIdFor = ::remoteTrackIdFor,
            transaction = { block -> database.withTransaction { block() } },
        )
    }

    /**
     * One upload at a time: the background sync and the per-lap live push share links, and two
     * writers at once could each post the same session under a different id.
     */
    val syncLock = Mutex()

    /**
     * Called after every completed lap. Sends the running session at once when it belongs to a
     * joined event, so the event board moves within seconds of the lap.
     */
    fun onLapCompleted(sessionId: Long, scope: CoroutineScope) {
        if (!auth.isSignedIn || events.joined.value.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            runCatching { syncLock.withLock { syncEngine().pushLive(sessionId) } }
                .onFailure { Log.w("TrackBoard", "Live event upload failed; the end-of-session sync retries", it) }
        }
    }

    fun syncEngine(): SyncEngine = SyncEngine(
        api = api,
        auth = auth,
        dao = database.syncDao(),
        premade = premadeTracks,
        sharingEnabled = { settings.sharingEnabled.value },
        appVersion = appVersion,
        garage = GarageSync(accountApi, api, auth, database.syncDao(), photos),
        transaction = { block -> database.withTransaction { block() } },
        events = { events.joined.value },
    )

    /** The server id a local track's leaderboard lives under, or null if it has none yet. */
    suspend fun remoteTrackIdFor(localTrackId: Long): String? {
        val dao = database.syncDao()
        val track = dao.getTrack(localTrackId) ?: return null
        premadeTracks.remoteIdFor(track, dao.getTrackPoints(localTrackId))?.let { return it }
        dao.getLink(RemoteLink.KIND_SHARED_TRACK, localTrackId)?.let { return it.remoteId }
        return dao.getLink(RemoteLink.KIND_TRACK, localTrackId)
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
