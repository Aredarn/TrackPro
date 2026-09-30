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
    /** Cars, photos, tracks and sessions restored from the account onto this phone. */
    val downloaded: Int = 0,
    val failed: Int = 0,
    /** The first problem, phrased for the driver. */
    val problem: String? = null,
    /** The server was unreachable; the worker retries later on its own. */
    val offline: Boolean = false,
)

/** One line for the driver about the last sync, e.g. "3 sent · 1 restored · 14:02". */
fun describe(report: SyncReport?): String {
    if (report == null) return "Not synced yet"
    val at = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(report.finishedAt))
    if (report.offline) return "Offline at $at · will retry"

    val parts = buildList {
        if (report.uploaded > 0) add("${report.uploaded} sent")
        if (report.downloaded > 0) add("${report.downloaded} restored")
        if (report.withdrawn > 0) add("${report.withdrawn} withdrawn")
        if (report.unchanged > 0) add("${report.unchanged} up to date")
        if (report.failed > 0) add("${report.failed} failed")
    }
    val summary = parts.ifEmpty { listOf("Everything up to date") }.joinToString(" · ")
    return listOfNotNull("$summary · $at", report.problem).joinToString("\n")
}

/**
 * TrackBoard preferences, persisted like the rest of TrackProApp's settings: a
 * SharedPreferences file mirrored into StateFlows the UI collects.
 *
 * Everything online is opt-in. With no server address, or sharing switched off, the app
 * behaves exactly as it did before TrackBoard existed.
 */
class OnlineSettings(context: Context) {

    private val prefs = context.getSharedPreferences("trackboard_prefs", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(storedServerUrl() ?: DEFAULT_SERVER)
    /**
     * Defaults to the public TrackBoard deployment. A driver who typed their own address keeps
     * it; clearing the field falls back to this default on the next launch. An address of a
     * retired TrackBoard deployment is dropped, so those phones move to the current one.
     */
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

    private fun storedServerUrl(): String? {
        val stored = prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: return null
        if (stored.trim().trimEnd('/').lowercase() in RETIRED_SERVERS) {
            prefs.edit().remove(KEY_SERVER).apply()
            return null
        }
        return stored
    }

    companion object {
        const val DEFAULT_SERVER = "https://trackboard-backend.onrender.com"

        /** Former public deployments. Same accounts and data; the host alone changed. */
        private val RETIRED_SERVERS = setOf("https://trackboard-u9uj.onrender.com")

        private const val KEY_SERVER = "server_url"
        private const val KEY_SHARING = "sharing_enabled"
        private const val KEY_LAST_SYNC = "last_sync"
    }
}
