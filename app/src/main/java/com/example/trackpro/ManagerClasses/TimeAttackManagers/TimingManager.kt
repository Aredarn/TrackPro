package com.example.trackpro.ManagerClasses.TimeAttackManagers

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

sealed class TimingMode {
    object Circuit : TimingMode()
    object Sprint : TimingMode()
}

abstract class TimingManager {
    protected val _currentTime = MutableStateFlow("00:00.00")
    protected val _bestTime = MutableStateFlow("--:--.--")
    protected val _lastTime = MutableStateFlow("--:--.--")
    protected val _delta = MutableStateFlow(0.0)
    protected val _eventCount = MutableStateFlow(0)
    protected val _stintStart = MutableStateFlow(SystemClock.elapsedRealtime())

    val currentTime get() = _currentTime
    val bestTime get() = _bestTime
    val lastTime get() = _lastTime
    val delta get() = _delta
    val eventCount get() = _eventCount
    val stintStart get() = _stintStart

    abstract fun handleGpsUpdate(prev: com.example.trackpro.ManagerClasses.RawGPSData?, current: com.example.trackpro.ManagerClasses.RawGPSData)
    abstract fun reset()
    abstract fun startNewEvent()

    protected fun formatTime(millis: Long) = String.format(
        "%02d:%02d.%02d",
        millis / 60000,
        (millis % 60000) / 1000,
        (millis % 1000) / 10
    )
}