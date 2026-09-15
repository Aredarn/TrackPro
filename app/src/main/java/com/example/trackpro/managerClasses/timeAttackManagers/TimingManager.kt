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
 */
data class CompletedLap(
    val number: Int,
    val timeMs: Long,
    val splits: List<SectorSplit> = emptyList()
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
    abstract fun reset()
    abstract fun startNewEvent()

    protected fun formatTime(millis: Long) = String.format(
        "%02d:%02d.%02d",
        millis / 60000,
        (millis % 60000) / 1000,
        (millis % 1000) / 10
    )
}