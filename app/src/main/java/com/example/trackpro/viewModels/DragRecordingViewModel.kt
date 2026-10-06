package com.example.trackpro.viewModels

import com.example.trackpro.R
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.RecordingService
import com.example.trackpro.managerClasses.calculationClasses.DragMetrics
import com.example.trackpro.managerClasses.calculationClasses.DragSpeedScale
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Owns a drag recording: the run in progress, its trace, and its derived metrics.
 *
 * This lives in a ViewModel rather than in the screen because a recording has to outlive the
 * composition. The drag screen used to hold all of it in `remember`, and the activity has no
 * `configChanges` in the manifest - so rotating the phone mid-run destroyed the buffered
 * trace, the calculator and the start time, while the two `rememberSaveable` flags survived.
 * The screen came back believing it was still recording, with nothing recorded, and the
 * disposal handler had already ended the session in the database on the way out. A ViewModel
 * scoped to the nav entry survives exactly that, and [onCleared] then fires only when the
 * screen is genuinely finished with.
 */
class DragRecordingViewModel(context: Context) : ViewModel() {

    private val app = context.applicationContext as TrackProApp
    private val database = app.database
    private val sessionManager = app.sessionManager

    /**
     * Built for the unit system in force when a run starts, so an imperial driver is timed
     * to 0-60 mph rather than to 0-60 km/h under an mph label. Replaced on each start rather
     * than reset, so a unit change between runs is picked up; not replaced mid-run, so a run
     * always reports the milestones it was actually measured against.
     */
    private var calculator = newCalculator()

    /** Guards itself. Written by the GPS collector, drained by [stop] and [onCleared]. */
    private val buffer = mutableListOf<RawGPSData>()

    private var gpsJob: Job? = null
    private var sessionId: Long = -1L
    private var startedAtMillis: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _metrics = MutableStateFlow(calculator.getCurrentMetrics())
    val metrics: StateFlow<DragMetrics> = _metrics.asStateFlow()

    private val _elapsedTime = MutableStateFlow(ZERO_ELAPSED)
    val elapsedTime: StateFlow<String> = _elapsedTime.asStateFlow()

    /**
     * The speed trace behind the live chart, newest last, capped to the most recent
     * [MAX_CHART_SAMPLES]. Speeds only - the x axis is position in this window, so the chart
     * does not need the ever-growing sample counter the screen used to keep.
     */
    private val _speedSamples = MutableStateFlow<List<Float>>(emptyList())
    val speedSamples: StateFlow<List<Float>> = _speedSamples.asStateFlow()

    /**
     * The car this run is recorded against. Held here rather than in the screen so it
     * survives recreation too - otherwise a rotation mid-run left the button reading
     * "Select Vehicle First" while a session was running.
     */
    /**
     * The session the last Stop closed, and whether writing it worked.
     *
     * Both exist so the screen can show a summary instead of vanishing silently, and so a
     * failed write is visible rather than only in logcat. Held until the summary is
     * dismissed; the id is what VOID stamps.
     */
    private val _lastRunSessionId = MutableStateFlow<Long?>(null)
    val lastRunSessionId: StateFlow<Long?> = _lastRunSessionId.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<Long?>(null)
    val selectedVehicleId: StateFlow<Long?> = _selectedVehicleId.asStateFlow()

    fun selectVehicle(vehicleId: Long) {
        _selectedVehicleId.value = vehicleId
    }

    /**
     * Opens a session and begins recording.
     *
     * [weatherAnchor] is the current GPS fix, if there is one. A drag session has no track to
     * anchor conditions to, so this is the only position available; without a fix the session
     * simply has no weather.
     */
    fun start(weatherAnchor: RawGPSData?) {
        if (_isRecording.value) return
        val vehicleId = _selectedVehicleId.value ?: return

        viewModelScope.launch {
            val eventType = "Drag - ${LocalDateTime.now().format(EVENT_TIME_FORMAT)}"
            val createdId = runCatching {
                withContext(Dispatchers.IO) {
                    sessionManager.startSession(eventType = eventType, vehicleId = vehicleId, trackId = null)
                }
            }.getOrElse { error ->
                Log.e(TAG, "Could not start drag session", error)
                return@launch
            }

            // Everything cleared before the collector can see it, so a run never inherits
            // the previous one.
            sessionId = createdId
            calculator = newCalculator()
            _metrics.value = calculator.getCurrentMetrics()
            _speedSamples.value = emptyList()
            _elapsedTime.value = ZERO_ELAPSED
            synchronized(buffer) { buffer.clear() }

            startedAtMillis = System.currentTimeMillis()
            _isRecording.value = true
            startGpsCollection()
            RecordingService.acquire(app, RECORDING_HOLDER)

            weatherAnchor?.let { fix ->
                app.applicationScope.launch(Dispatchers.IO) {
                    sessionManager.captureWeather(createdId, fix.latitude, fix.longitude)
                }
            }
        }
    }

    /** Closes the session and writes the trace. Safe to call when nothing is recording. */
    fun stop() {
        if (!_isRecording.value) return
        // Before draining the buffer: the collector re-checks this under the buffer lock, so
        // a fix arriving right now is either included below or refused, never appended to an
        // already-drained buffer where it would surface in the next run.
        _isRecording.value = false
        gpsJob?.cancel()
        gpsJob = null
        RecordingService.release(app, RECORDING_HOLDER)
        val (endedId, points) = drainRun()
        _lastRunSessionId.value = endedId
        _saveError.value = null
        persist(endedId, points)
    }

    /**
     * Consumes the GPS flow directly rather than through Compose state.
     *
     * collectAsState conflates - it keeps only the newest value - so a fix arriving while the
     * previous frame was still rendering was dropped, and at 10 Hz the live chart redraw puts
     * frames over the sample interval routinely. A dropped fix is a missing row in the trace
     * and a short measured distance, not a dropped frame. On the default dispatcher for the
     * same reason: a collector on the main thread cannot run mid-frame, and a suspended
     * collector is precisely when a StateFlow drops values.
     */
    private fun startGpsCollection() {
        gpsJob?.cancel()
        gpsJob = viewModelScope.launch(Dispatchers.Default) {
            app.gpsManager.activeGpsFlow.collect { fix -> record(fix) }
        }
    }

    private fun record(fix: RawGPSData?) {
        if (fix == null || !_isRecording.value) return
        val now = System.currentTimeMillis()

        _elapsedTime.value = formatElapsedTime(now - startedAtMillis)
        // The fix's own timestamp, not now: splits are differences between samples, and the
        // wait before each one is processed varies. The review screen already replays the
        // stored trace this way, so live and review now agree.
        _metrics.value = calculator.processRealtimeGPS(fix, fix.timestamp)

        synchronized(buffer) {
            if (!_isRecording.value) return
            buffer.add(fix.copy(sessionid = sessionId))
        }

        _speedSamples.value = (_speedSamples.value + (fix.speed ?: 0f)).takeLast(MAX_CHART_SAMPLES)
    }

    /**
     * Takes the finished run: its session id and its points, clearing both.
     *
     * The id is read and cleared under the buffer lock rather than beside it, because the
     * collector reads it while holding the same lock - that is what gives the two threads a
     * happens-before relationship on it. The other fields a run sets up are published by the
     * [_isRecording] write that follows them in [start] and precedes every read of them.
     */
    private fun drainRun(): Pair<Long, List<RawGPSData>> = synchronized(buffer) {
        val endedId = sessionId
        sessionId = -1L
        endedId to buffer.toList().also { buffer.clear() }
    }

    /**
     * Always on the application scope, never [viewModelScope]: this runs as the ViewModel is
     * being cleared, so work started on its own scope would be cancelled before the insert
     * completed and the run would be lost with no error.
     */
    private fun persist(endedSessionId: Long, points: List<RawGPSData>) {
        app.applicationScope.launch(Dispatchers.IO) {
            try {
                sessionManager.endSession(endedSessionId)
                if (points.isNotEmpty()) database.rawGPSDataDao().insertAll(points)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save drag run", e)
                _saveError.value = e.message ?: app.getString(R.string.drag_save_failed)
            }
        }
    }

    /**
     * Stamps the last run discarded. Application scope: the driver taps this and leaves in
     * the same breath, and a write cancelled by navigation is the bug that lost runs here
     * in the first place.
     */
    fun voidLastRun() {
        val id = _lastRunSessionId.value ?: return
        app.applicationScope.launch(Dispatchers.IO) {
            runCatching { database.sessionDataDao().setVoided(id, true) }
                .onFailure { Log.e(TAG, "Could not void session $id", it) }
        }
        _lastRunSessionId.value = null
    }

    /** Dismisses the summary without changing what was saved. */
    fun clearLastRun() {
        _lastRunSessionId.value = null
        _saveError.value = null
    }

    private fun newCalculator() = DragTimeCalculation(
        session = null,
        database = database,
        scale = DragSpeedScale.of(app.useMetricUnits.value)
    )

    /**
     * Fires when the screen is finished with for good - popped off the back stack - and not
     * on a configuration change, which is the whole reason this state lives here. An
     * in-progress run is saved rather than discarded: leaving the screen is a plausible way
     * to end a run, and the alternative is losing it.
     */
    override fun onCleared() {
        super.onCleared()
        gpsJob?.cancel()
        gpsJob = null
        if (_isRecording.value) {
            _isRecording.value = false
            RecordingService.release(app, RECORDING_HOLDER)
            val (endedId, points) = drainRun()
            persist(endedId, points)
        }
    }

    private companion object {
        const val TAG = "DragRecording"
        /** This screen's claim on RecordingService; see RecordingService.acquire. */
        const val RECORDING_HOLDER = "drag"
        const val ZERO_ELAPSED = "00:00.00"
        const val MAX_CHART_SAMPLES = 500
        val EVENT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
    }
}

/** Running time of the current run, as "MM:SS.hh". */
fun formatElapsedTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 60000) % 60
    val centiseconds = (millis % 1000) / 10
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
}

class DragRecordingViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DragRecordingViewModel(context) as T
    }
}
