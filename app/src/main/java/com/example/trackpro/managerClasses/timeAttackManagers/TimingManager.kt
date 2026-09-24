package com.example.trackpro.managerClasses.timeAttackManagers

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.trackpro.dataClasses.RawGPSData

sealed class TimingMode {
    object Circuit : TimingMode()
    object Sprint : TimingMode()
}

/**
 * A lap - or, in Sprint mode, a run - that has closed.
 *
 * Kept in memory for the whole session so the HUD can list recent history without a
 * database round trip, at full millisecond precision. [splits] are the sector splits driven
 * on that lap in driving order, including the final sector closed by the finish line;
 * empty for sprints and for tracks with no marked sectors.
 *
 * [signalGap] means GPS went quiet for longer than [TimingManager.SIGNAL_GAP_MS] at some
 * point during the lap. The time may still be right - both line crossings can be seen either
 * side of a short gap - but if the gap covered the finish line, the crossing was never seen
 * and this "lap" is really two merged into one. There is no telling which from here, so the
 * lap is flagged rather than discarded, and the driver judges.
 */
data class CompletedLap(
    val number: Int,
    val timeMs: Long,
    val splits: List<SectorSplit> = emptyList(),
    val signalGap: Boolean = false
)

abstract class TimingManager {
    protected val _currentTime = MutableStateFlow("00:00.00")
    protected val _bestTime = MutableStateFlow("--:--.--")
    protected val _lastTime = MutableStateFlow("--:--.--")
    protected val _delta = MutableStateFlow(0.0)
    protected val _eventCount = MutableStateFlow(0)
    protected val _stintStart = MutableStateFlow(SystemClock.elapsedRealtime())
    protected val _completedLaps = MutableStateFlow<List<CompletedLap>>(emptyList())

    val currentTime get() = _currentTime
    val bestTime get() = _bestTime
    val lastTime get() = _lastTime
    val delta get() = _delta
    val eventCount get() = _eventCount
    val stintStart get() = _stintStart
    /** Every lap closed this session, oldest first. */
    val completedLaps get() = _completedLaps

    abstract fun handleGpsUpdate(prev: RawGPSData?, current: RawGPSData)

    /**
     * Whether two consecutive fixes are far enough apart that fixes were lost between them.
     * Measured on the fixes' own receipt times, so it catches every cause alike: a dropped
     * link, a reconnect, or phone GPS throttled while the app was in the background.
     */
    protected fun isSignalGap(prev: RawGPSData, current: RawGPSData): Boolean =
        current.timestamp - prev.timestamp > SIGNAL_GAP_MS

    abstract fun reset()
    abstract fun startNewEvent()

    protected fun formatTime(millis: Long) = String.format(
        "%02d:%02d.%02d",
        millis / 60000,
        (millis % 60000) / 1000,
        (millis % 1000) / 10
    )

    companion object {
        /**
         * Silence between fixes that counts as lost signal. Every supported rate is 1 Hz or
         * faster, so this is at least one whole missed fix at the slowest and twenty at 10 Hz.
         */
        const val SIGNAL_GAP_MS = 2_000L
    }
}