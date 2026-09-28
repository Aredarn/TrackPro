package com.example.trackpro.online

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** What the last sync did, for the Settings screen. */
@Serializable
data class SyncReport(
    val finishedAt: Long,
    val uploaded: Int = 0,
    val unchanged: Int = 0,
    val withdrawn: Int = 0,
    val failed: Int = 0,
    /** The first problem, phrased for the driver. */
    val problem: String? = null,
    /** The server was unreachable; the worker retries later on its own. */
    val offline: Boolean = false,
)

/**
 * TrackBoard preferences, persisted like the rest of TrackProApp's settings: a
 * SharedPreferences file mirrored into StateFlows the UI collects.
 *
 * Everything online is opt-in. With no server address, or sharing switched off, the app
 * behaves exactly as it did before TrackBoard existed.
 */
class OnlineSettings(context: Context) {

    private val prefs = context.getSharedPreferences("trackboard_prefs", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER, "") ?: "")
    /** No default: there is no public TrackBoard deployment to point at yet. */
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _sharing = MutableStateFlow(prefs.getBoolean(KEY_SHARING, false))
    /** Whether finished sessions are posted to track leaderboards. Off by default. */
    val sharingEnabled: StateFlow<Boolean> = _sharing.asStateFlow()

    private val _lastSync = MutableStateFlow(
        prefs.getString(KEY_LAST_SYNC, null)?.let {
            runCatching { OkHttpTrackBoardApi.json.decodeFromString(SyncReport.serializer(), it) }.getOrNull()
        }
    )
    val lastSync: StateFlow<SyncReport?> = _lastSync.asStateFlow()

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER, url.trim()).apply()
        _serverUrl.value = url.trim()
    }

    fun setSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHARING, enabled).apply()
        _sharing.value = enabled
    }

    fun recordSync(report: SyncReport) {
        prefs.edit()
            .putString(KEY_LAST_SYNC, OkHttpTrackBoardApi.json.encodeToString(SyncReport.serializer(), report))
            .apply()
        _lastSync.value = report
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_SHARING = "sharing_enabled"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
