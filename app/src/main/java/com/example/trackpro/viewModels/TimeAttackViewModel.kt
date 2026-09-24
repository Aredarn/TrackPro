package com.example.trackpro.viewModels

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.managerClasses.timeAttackManagers.CircuitTimingManager
import com.example.trackpro.managerClasses.timeAttackManagers.CompletedLap
import com.example.trackpro.managerClasses.timeAttackManagers.SectorSplit
import com.example.trackpro.managerClasses.timeAttackManagers.SprintTimingManager
import com.example.trackpro.managerClasses.timeAttackManagers.TimingManager
import com.example.trackpro.managerClasses.timeAttackManagers.TimingMode
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry.calculateFinishLine
import com.example.trackpro.managerClasses.RecordingService
import com.example.trackpro.managerClasses.utilities.LapStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.collections.emptyList

class TimeAttackViewModel(
    context: Context
) : ViewModel() {
    private val app = context.applicationContext as TrackProApp
    val database = app.database

    // Timing state
    private var timingManager: TimingManager? = null
    private val _timingMode = MutableStateFlow<TimingMode>(TimingMode.Circuit)
    val timingMode: StateFlow<TimingMode> = _timingMode.asStateFlow()

    // Position tracking
    private val _driverPosition = MutableStateFlow<LatLonOffset?>(null)
    private val _fullTrack = MutableStateFlow<List<TrackCoordinatesData>>(emptyList())
    private val _startLine = MutableStateFlow<List<TrackCoordinatesData>>(emptyList())
    private val _finishLine = MutableStateFlow<List<TrackCoordinatesData>>(emptyList())
    private val _sectorLines = MutableStateFlow<List<List<TrackCoordinatesData>>>(emptyList())

    // Session state
    private var _sessionId: Long = -1
    private var _lapId: Long = -1

    // The screen re-runs its init effect every time it enters composition - which
    // includes every device rotation, since this screen has separate portrait and
    // landscape layouts. The ViewModel outlives those recompositions, so it is the only
    // place that can tell "the screen was rebuilt" apart from "the driver started a new
    // session". These guards are what keep one run from becoming ten sessions.
    private val sessionMutex = Mutex()
    private var trackLoaded = false
    private var gpsJob: Job? = null
    private var previousGPSData: RawGPSData? = null
    private val lapDataChannel = Channel<LapInfoData>(Channel.UNLIMITED)
    private val sessionManager = app.sessionManager

    // Expose state to UI
    val driverPosition: StateFlow<LatLonOffset?> = _driverPosition.asStateFlow()
    val fullTrack: StateFlow<List<TrackCoordinatesData>> = _fullTrack.asStateFlow()
    val startLine: StateFlow<List<TrackCoordinatesData>> = _startLine.asStateFlow()
    val finishLine: StateFlow<List<TrackCoordinatesData>> = _finishLine.asStateFlow()
    val sectorLines: StateFlow<List<List<TrackCoordinatesData>>> = _sectorLines.asStateFlow()

    // Sector splits for the lap currently in progress (Circuit mode only; empty for Sprint
    // or tracks with no marked sector points).
    val currentLapSplits: StateFlow<List<SectorSplit>>
        get() = (timingManager as? CircuitTimingManager)?.currentLapSplits
            ?: MutableStateFlow<List<SectorSplit>>(emptyList()).asStateFlow()

    // Continuously-updating delta vs. the session's best lap, tracked by distance into the
    // lap rather than only once at the finish line. Null until a best lap reference exists
    // (i.e. before the first lap of the session has completed) or in Sprint mode.
    val liveDelta: StateFlow<Double?>
        get() = (timingManager as? CircuitTimingManager)?.liveDelta
            ?: MutableStateFlow<Double?>(null).asStateFlow()

    // Every lap (or sprint run) closed this session, oldest first, with its sector splits.
    // Held by the timing manager rather than read back from the database so the HUD lists
    // them at full precision and without waiting on the lap-completion write.
    val completedLaps: StateFlow<List<CompletedLap>>
        get() = timingManager?.completedLaps
            ?: MutableStateFlow<List<CompletedLap>>(emptyList()).asStateFlow()

    // Expose timing state
    val currentTime: StateFlow<String>
        get() = timingManager?.currentTime ?: MutableStateFlow("00:00.00").asStateFlow()
    val bestTime: StateFlow<String>
        get() = timingManager?.bestTime ?: MutableStateFlow("--:--.--").asStateFlow()
    val lastTime: StateFlow<String>
        get() = timingManager?.lastTime ?: MutableStateFlow("--:--.--").asStateFlow()
    val delta: StateFlow<Double> get() = timingManager?.delta ?: MutableStateFlow(0.0).asStateFlow()
    val eventCount: StateFlow<Int>
        get() = timingManager?.eventCount ?: MutableStateFlow(0).asStateFlow()
    val stintStart: StateFlow<Long>
        get() = timingManager?.stintStart ?: MutableStateFlow(
            SystemClock.elapsedRealtime()
        ).asStateFlow()

    init {
        startLapDataConsumer()
    }

    override fun onCleared() {
        super.onCleared()
        gpsJob?.cancel()
        gpsJob = null
        RecordingService.release(app, RECORDING_HOLDER)
        timingManager?.reset()
        app.applicationScope.launch(Dispatchers.IO) {
            endSession()
        }
    }

    fun loadTrack(trackId: Long, mode: TimingMode) {
        // getCoordinatesOfTrack returns a Flow that never completes, so calling this
        // twice would leave two live collectors racing to set the same state.
        if (trackLoaded) return
        trackLoaded = true
        _timingMode.value = mode
        viewModelScope.launch {
            database.trackCoordinatesDao().getCoordinatesOfTrack(trackId)
                .collect { coords ->
                    _fullTrack.value = coords

                    // Only init timing manager ONCE
                    if (timingManager == null) {
                        when (mode) {
                            TimingMode.Circuit -> {
                                _finishLine.value = calculateFinishLine(coords)
                                _startLine.value = emptyList()
                                _sectorLines.value = TrackGeometry.calculateSectorLines(coords)
                                val manager = CircuitTimingManager(_finishLine.value, _sectorLines.value)
                                timingManager = manager
                                viewModelScope.launch {
                                    manager.lapCompletedChannel.consumeAsFlow().collect { lap ->
                                        handleCompletedLap(lap)
                                    }
                                }
                                viewModelScope.launch {
                                    manager.sectorCompletedChannel.consumeAsFlow().collect { split ->
                                        handleCompletedSector(split)
                                    }
                                }
                            }
                            TimingMode.Sprint -> {
                                val (start, finish) = TrackGeometry.calculateSprintLines(coords)
                                _startLine.value = start
                                _finishLine.value = finish
                                val manager = SprintTimingManager(start, finish)
                                timingManager = manager
                                viewModelScope.launch {
                                    manager.sprintCompletedChannel.consumeAsFlow().collect { run ->
                                        handleCompletedSprint(run)
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    internal fun handleGpsUpdate(current: RawGPSData) {
        Log.d("TimeAttackViewModel", "handleGpsUpdate START - lat=${current.latitude}, lon=${current.longitude}")

        timingManager?.handleGpsUpdate(previousGPSData, current)

        Log.d("TimeAttackViewModel", "Setting driver position to lat=${current.latitude}, lon=${current.longitude}")
        _driverPosition.value = LatLonOffset(lat = current.latitude, lon = current.longitude)
        Log.d("TimeAttackViewModel", "Driver position set. Current value: ${_driverPosition.value}")

        previousGPSData = current
        processLapData(current)

        Log.d("TimeAttackViewModel", "handleGpsUpdate END")
    }

    private fun processLapData(current: RawGPSData) {
        if (_sessionId == -1L || _lapId == -1L) return

        val lapInfoData = LapInfoData(
            lapid = _lapId,
            lat = current.latitude,
            lon = current.longitude,
            spd = current.speed,
            alt = current.altitude,
            latgforce = null,
            longforce = null
        )

        lapDataChannel.trySend(lapInfoData).onFailure {
            Log.e("LapInsert", "Failed to queue lap data: ${it?.message}")
        }
    }

    private fun startLapDataConsumer() {
        viewModelScope.launch(Dispatchers.IO) {
            for (lapData in lapDataChannel) {
                try {
                    database.lapInfoDataDAO().insert(lapData)
                } catch (e: Exception) {
                    Log.e("LapInsert", "Failed to insert lap data", e)
                }
            }
        }
    }

    private fun handleCompletedLap(lap: CompletedLap) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_sessionId == -1L || _lapId == -1L) {
                Log.w("TimeAttack", "Cannot complete lap: session or lap ID invalid")
                return@launch
            }

            val lapTimeStr = formatLapTime(lap.timeMs)

            try {
                // 1. Mark the current lap as COMPLETED, with whether GPS dropped out during it
                database.lapTimeDataDAO().completeLap(_lapId, lapTimeStr, lap.signalGap)
                Log.d("TimeAttack", "Lap $_lapId COMPLETED with time $lapTimeStr" +
                        if (lap.signalGap) " (GPS gap during lap)" else "")

                // 2. For circuits, immediately start the next lap
                if (_timingMode.value is TimingMode.Circuit) {
                    val nextLapNumber = eventCount.value + 1
                    startNewLap(nextLapNumber)
                    Log.d("TimeAttack", "Started next lap: $nextLapNumber")
                }
            } catch (e: Exception) {
                Log.e("TimeAttack", "Error completing lap: ${e.message}", e)
            }
        }
    }

    private fun handleCompletedSector(split: SectorSplit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_lapId == -1L) {
                Log.w("TimeAttack", "Cannot record sector split: no active lap")
                return@launch
            }
            try {
                database.sectorTimeDataDAO().insert(
                    SectorTimeData(
                        lapid = _lapId,
                        sectorIndex = split.sectorIndex,
                        splitTimeMs = split.splitMs
                    )
                )
            } catch (e: Exception) {
                Log.e("TimeAttack", "Error recording sector split: ${e.message}", e)
            }
        }
    }

    private fun handleCompletedSprint(run: CompletedLap) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_sessionId == -1L || _lapId == -1L) {
                Log.w("TimeAttack", "Cannot complete sprint: session or lap ID invalid")
                return@launch
            }

            val sprintTimeStr = formatLapTime(run.timeMs)

            try {
                // Mark the current sprint as COMPLETED, with whether GPS dropped out during it
                database.lapTimeDataDAO().completeLap(_lapId, sprintTimeStr, run.signalGap)
                Log.d("TimeAttack", "Sprint $_lapId COMPLETED with time $sprintTimeStr")

                // Don't create a new lap for sprints - user manually starts each run
            } catch (e: Exception) {
                Log.e("TimeAttack", "Error completing sprint: ${e.message}", e)
            }
        }
    }

    private fun formatLapTime(millis: Long) = String.format(
        "%02d:%02d.%02d",
        millis / 60000,
        (millis % 60000) / 1000,
        (millis % 1000) / 10
    )

    /**
     * Creates the session for this screen, or does nothing if one is already running.
     *
     * SessionManager.startSession always INSERTs, so every call here produces another row
     * in the session list. The mutex closes the window where two rapid calls could both
     * see _sessionId == -1 and each insert one.
     */
    suspend fun ensureSession(trackId: Long, vehicleId: Long) {
        sessionMutex.withLock {
            if (_sessionId != -1L) {
                Log.d("TimeAttack", "Session $_sessionId already active - not creating another")
            } else {
                withContext(Dispatchers.IO) {
                    val track = database.trackMainDao().getTrack(trackId).firstOrNull() ?: return@withContext
                    val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
                    val eventType = "${track.trackName} - $todayFormatted"

                    _sessionId = sessionManager.startSession(
                        eventType = eventType,
                        vehicleId = vehicleId,
                        trackId = trackId
                    )

                    Log.d("TimeAttack", "Session created: $_sessionId")

                    // Start the first lap
                    startNewLap(lapNumber = 1)

                    // Held until onCleared, which is when this session ends.
                    RecordingService.acquire(app, RECORDING_HOLDER)

                    // Capture conditions in the background. The track's own coordinates are a
                    // better location source here than the live GPS fix: they're definitionally
                    // where the session happens and are available immediately, rather than
                    // waiting for a fix to arrive. Queried directly rather than read off
                    // _fullTrack, which loadTrack() may not have populated yet.
                    val createdSessionId = _sessionId
                    val anchor = database.trackCoordinatesDao()
                        .getCoordinatesOfTrack(trackId)
                        .firstOrNull()
                        ?.firstOrNull()
                    if (anchor != null) {
                        app.applicationScope.launch(Dispatchers.IO) {
                            sessionManager.captureWeather(
                                sessionId = createdSessionId,
                                latitude = anchor.latitude,
                                longitude = anchor.longitude
                            )
                        }
                    }
                }
            }
        }
        // Idempotent, and deliberately outside the branch above: it has to run even when
        // the session already existed, so a rebuilt screen re-attaches to GPS.
        startGpsCollection()
    }

    /**
     * Consumes GPS straight from the source flow.
     *
     * This deliberately does not go through the UI. collectAsState conflates - it keeps
     * only the newest value - so routing fixes through Compose state dropped any sample
     * that arrived faster than the next recomposition. Line-crossing detection compares
     * *consecutive* fixes, so a dropped sample is a missed lap, and a dropped run of them
     * is a lap trace with a hole in it.
     */
    private fun startGpsCollection() {
        if (gpsJob != null) return
        gpsJob = viewModelScope.launch {
            app.gpsManager.activeGpsFlow.collect { fix ->
                fix?.let { handleGpsUpdate(it) }
            }
        }
    }

    private suspend fun startNewLap(lapNumber: Int) {
        val lapTimeData = LapTimeData(
            sessionid = _sessionId,
            lapnumber = lapNumber,
            laptime = LapStatus.IN_PROGRESS
        )

        withContext(Dispatchers.IO) {
            _lapId = database.lapTimeDataDAO().insert(lapTimeData)
            Log.d("TimeAttack", "Started Lap $lapNumber with ID $_lapId (status: ${LapStatus.IN_PROGRESS})")
        }
    }

    private companion object {
        /** This screen's claim on RecordingService; see RecordingService.acquire. */
        const val RECORDING_HOLDER = "timeAttack"
    }

    suspend fun endSession() {
        if (_sessionId == -1L) {
            Log.d("TimeAttack", "No active session to end")
            return
        }

        withContext(Dispatchers.IO) {
            try {

                val allLapsInSession = database.lapTimeDataDAO()
                    .getLapsForSession(_sessionId)

                val inProgressLaps = allLapsInSession.filter { it.laptime == LapStatus.IN_PROGRESS }

                inProgressLaps.forEach { lap ->
                    database.lapTimeDataDAO().delete(lap)
                }

                // SessionManager.endSession now takes the session to close, so this no
                // longer has to stamp endTime itself to avoid its old shared "current
                // session" field. The read-modify-write this replaces could also drop a
                // weather lookup that landed between its read and its write.
                sessionManager.endSession(_sessionId)

                Log.d("TimeAttack", "Session $_sessionId ended. Deleted ${inProgressLaps.size} incomplete laps.")

                _sessionId = -1L
                _lapId = -1L

            } catch (e: Exception) {
                Log.e("TimeAttack", "Error ending session: ${e.message}", e)
            }
        }
    }
}