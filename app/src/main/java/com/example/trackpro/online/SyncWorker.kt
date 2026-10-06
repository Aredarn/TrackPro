package com.example.trackpro.online

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.trackpro.TrackProApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Runs one sync in the background. WorkManager keeps it alive through process death and holds
 * it until there is a network, which matters at a circuit: the session that just ended goes
 * up whenever the phone next sees signal, with no one having to remember.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val online = (applicationContext as TrackProApp).online

        // Keeps the phone's list of joined events current, so their sessions are planned
        // below. Best effort: offline, the list the phone already has stands.
        runCatching { online.events.refresh() }

        if (online.settings.serverUrl.value.isBlank()) {
            online.settings.recordSync(SyncReport(System.currentTimeMillis(), problem = "No TrackBoard server is set."))
            return Result.success()
        }

        val report = online.syncLock.withLock { online.syncEngine().run() }
        online.settings.recordSync(report)

        // Offline is the one outcome worth retrying: every other problem needs a change on
        // the phone first, and retrying unchanged would only fail the same way.
        return if (report.offline) Result.retry() else Result.success()
    }
}

object SyncScheduler {
    private const val ONE_OFF = "trackboard-sync"
    private const val PERIODIC = "trackboard-sync-periodic"

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Sync as soon as there is a network. A request made mid-sync queues one more run after it. */
    fun syncSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(needsNetwork)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** A safety net for anything the event triggers miss. Idempotent: safe on every launch. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stops background syncing, for signing out. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(ONE_OFF)
            cancelUniqueWork(PERIODIC)
        }
    }

    /** True while a triggered sync is queued or running, for the "Sync now" control. */
    fun isSyncing(context: Context): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ONE_OFF).map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
}
