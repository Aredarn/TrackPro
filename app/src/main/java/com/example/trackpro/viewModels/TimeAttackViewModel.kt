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
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.managerClasses.gpsDataManagers.ESPTcpClient
import com.example.trackpro.managerClasses.timeAttackManagers.CircuitTimingManager
import com.example.trackpro.managerClasses.timeAttackManagers.SprintTimingManager
import com.example.trackpro.managerClasses.timeAttackManagers.TimingManager
import com.example.trackpro.managerClasses.timeAttackManagers.TimingMode
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry.calculateFinishLine
import kotlinx.coroutines.Dispatchers
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
    private var tcpClient: ESPTcpClient? = null

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

    // Session state
    private var _sessionId: Long = -1
    private var _lapId: Long = -1
    private var previousGPSData: RawGPSData? = null
    private val lapDataChannel = Channel<LapInfoData>(Channel.UNLIMITED)
    private val sessionManager = app.sessionManager

    // Expose state to UI
    val driverPosition: StateFlow<LatLonOffset?> = _driverPosition.asStateFlow()
    val fullTrack: StateFlow<List<TrackCoordinatesData>> = _fullTrack.asStateFlow()
    val startLine: StateFlow<List<TrackCoordinatesData>> = _startLine.asStateFlow()
    val finishLine: StateFlow<List<TrackCoordinatesData>> = _finishLine.asStateFlow()

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
        tcpClient?.disconnect()
        timingManager?.reset()
        app.applicationScope.launch(Dispatchers.IO) {
            endSession()
        }
    }

    fun loadTrack(trackId: Long, mode: TimingMode) {
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
                                val manager = CircuitTimingManager(_finishLine.value)
                                timingManager = manager
                                viewModelScope.launch {
                                    manager.lapCompletedChannel.consumeAsFlow().collect { lapMs ->
                                        handleCompletedLap(lapMs)
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
                                    manager.sprintCompletedChannel.consumeAsFlow().collect { sprintMs ->
                                        handleCompletedSprint(sprintMs)
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
        if (_sessionId == -1L) return

        val lapInfoData = LapInfoData(
            lapid = if (_lapId == -1L) 0 else _lapId,
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

    private fun handleCompletedLap(lapMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_sessionId == -1L || _lapId == -1L) {
                Log.w("TimeAttack", "Cannot complete lap: session or lap ID invalid")
                return@launch
            }

            val lapTimeStr = formatLapTime(lapMs)

            try {
                // 1. Mark the current lap as COMPLETED
                database.lapTimeDataDAO().updateLapTime(_lapId, lapTimeStr)
                Log.d("TimeAttack", "Lap $_lapId COMPLETED with time $lapTimeStr")

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

    private fun handleCompletedSprint(sprintMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_sessionId == -1L || _lapId == -1L) {
                Log.w("TimeAttack", "Cannot complete sprint: session or lap ID invalid")
                return@launch
            }

            val sprintTimeStr = formatLapTime(sprintMs)

            try {
                // Mark the current sprint as COMPLETED
                database.lapTimeDataDAO().updateLapTime(_lapId, sprintTimeStr)
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

    suspend fun createSession(trackId: Long, vehicleId: Long) {
        withContext(Dispatchers.IO) {
            val track = database.trackMainDao().getTrack(trackId).firstOrNull() ?: return@withContext
            val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
            val eventType = "${track.trackName} - $todayFormatted"

            val existingSession = database.sessionDataDao().getAllSessions().first().find {
                it.eventType == eventType && it.vehicleId == vehicleId
            }

            _sessionId = existingSession?.id ?: run {
                sessionManager.startSession(
                    eventType = eventType,
                    vehicleId = vehicleId,
                    trackId = trackId
                )
                sessionManager.getCurrentSessionId()!!
            }

            Log.d("TimeAttack", "Session created/resumed: $_sessionId")

            // Start the first lap
            startNewLap(lapNumber = 1)
        }
    }

    private fun startNewLap(lapNumber: Int) {
        val lapTimeData = LapTimeData(
            sessionid = _sessionId,
            lapnumber = lapNumber,
            laptime = "IN PROGRESS"
        )

        viewModelScope.launch(Dispatchers.IO) {
            _lapId = database.lapTimeDataDAO().insert(lapTimeData)
            Log.d("TimeAttack", "Started Lap $lapNumber with ID $_lapId (status: IN PROGRESS)")
        }
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

                val inProgressLaps = allLapsInSession.filter { it.laptime == "IN PROGRESS" }

                inProgressLaps.forEach { lap ->
                    database.lapTimeDataDAO().delete(lap)
                }

                Log.d("TimeAttack", "Session $_sessionId ended. Deleted ${inProgressLaps.size} incomplete laps.")

                _sessionId = -1L
                _lapId = -1L

            } catch (e: Exception) {
                Log.e("TimeAttack", "Error ending session: ${e.message}", e)
            }
        }
    }
}