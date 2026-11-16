package com.example.trackpro.managerClasses

import android.util.Log
import androidx.room.withTransaction
import com.example.trackpro.managerClasses.utilities.LapStatus

/**
 * Closes sessions that a dead process left open.
 *
 * A recording lives in the screens' ViewModels, and RecordingService is not sticky, so when
 * the process dies mid-session (a crash, an OEM battery killer, a reboot) nothing gets to call
 * SessionManager.endSession. The row keeps `endTime = NULL` for good: sync never posts a
 * session without an end and treats every open one as live, and the lap that was being driven
 * stays behind as IN PROGRESS.
 *
 * Run once per process start, before anything can begin recording. Only sessions that started
 * before the process did are touched, so a recording started in the meantime is never closed.
 *
 * Each repaired session is closed the way SessionManager.endSession would have closed it: the
 * unfinished lap is dropped, and the end is the last moment anything was recorded - the
 * session did run until then. A session with nothing stored (a drag run keeps its fixes in
 * memory until it ends) is closed at its start, as a drag run with no points already is.
 */
class InterruptedSessions(private val database: ESPDatabase) {

    /** Closes every open session that started before [startedBefore]. Returns how many. */
    suspend fun closeAll(startedBefore: Long): Int {
        val sessions = database.sessionDataDao()
        val laps = database.lapTimeDataDAO()
        val open = sessions.getOpenSessionsStartedBefore(startedBefore)
        for (session in open) {
            database.withTransaction {
                // Read before the unfinished lap goes: its points are part of the session.
                val last = sessions.lastRecordedAt(session.id)
                val dropped = laps.deleteLapsWithTime(session.id, LapStatus.IN_PROGRESS)
                sessions.markSessionEnded(session.id, endTimeFor(session.startTime, last))
                Log.i(TAG, "Closed interrupted session ${session.id}; dropped $dropped unfinished lap(s)")
            }
        }
        return open.size
    }

    companion object {
        private const val TAG = "InterruptedSessions"

        /** The last recorded moment, never before the start (clocks can disagree). */
        fun endTimeFor(startTime: Long, lastRecordedAt: Long?): Long =
            maxOf(startTime, lastRecordedAt ?: startTime)
    }
}
