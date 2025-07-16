// TimeAttackViewModel.kt (complete)
package com.example.trackpro

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.trackpro.DataClasses.LapInfoData
import com.example.trackpro.DataClasses.LapTimeData
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData
import com.example.trackpro.ManagerClasses.SessionManager
import com.example.trackpro.ManagerClasses.TimeAttackManagers.CircuitTimingManager
import com.example.trackpro.ManagerClasses.TimeAttackManagers.SprintTimingManager
import com.example.trackpro.ManagerClasses.TimeAttackManagers.TimingManager
import com.example.trackpro.ManagerClasses.TimeAttackManagers.TimingMode
import com.example.trackpro.ManagerClasses.TimeAttackManagers.TrackGeometry
import com.example.trackpro.ManagerClasses.TimeAttackManagers.TrackGeometry.calculateFinishLine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Add these imports at the top of the file
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class TimeAttackViewModel(
    private val database: ESPDatabase,
    context: Context
) : ViewModel() {
    private var tcpClient: ESPTcpClient? = null
    private val config = JsonReader.loadConfig(context)
    private val ip = config.first
    private val port = config.second

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
        startTcpClient()
        startLapDataConsumer()
    }

    private fun startTcpClient() = viewModelScope.launch {
        runCatching {
            val client = ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data -> handleGpsUpdate(data) },
                onConnectionStatusChanged = { connected ->
                    if (!connected) timingManager?.reset()
                }
            )
            tcpClient = client
            client.connect()
        }.onFailure {
            Log.e(TAG, "TCP connection failed", it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tcpClient?.disconnect()
        timingManager?.reset()
        Log.d(TAG, "ViewModel cleared")
    }

    //WORKS
    // Updated ViewModel section
    fun loadTrack(trackId: Long, mode: TimingMode) {
        _timingMode.value = mode
        viewModelScope.launch {
            database.trackCoordinatesDao().getCoordinatesOfTrack(trackId).collect { coords ->
                _fullTrack.value = coords

                when (mode) {
                    TimingMode.Circuit -> {
                        _finishLine.value = calculateFinishLine(coords)
                        _startLine.value = emptyList()

                        val manager = CircuitTimingManager(_finishLine.value)
                        timingManager = manager

                        viewModelScope.launch {
                            manager.lapCompletedChannel.consumeAsFlow().collect {
                                handleCompletedLap(it)
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
                            manager.sprintCompletedChannel.consumeAsFlow().collect {
                                handleCompletedSprint(it)
                            }
                        }
                    }
                }
            }
        }
    }


    //WORKS
    private fun handleGpsUpdate(current: RawGPSData) {
        timingManager?.handleGpsUpdate(previousGPSData, current)
        _driverPosition.value = LatLonOffset(lat = current.latitude, lon = current.longitude)
        previousGPSData = current
        processLapData(current)
    }

    //WORKS
    private fun processLapData(current: RawGPSData) {
        if (_lapId == -1L) return

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

    //WORKS
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
        viewModelScope.launch {
            if (_sessionId == -1L) {
                Log.e(TAG, "Session not created, cannot save lap")
                return@launch
            }

            val lapTimeData = LapTimeData(
                sessionid = _sessionId,
                lapnumber = eventCount.value,
                laptime = formatLapTime(lapMs)
            )

            _lapId = withContext(Dispatchers.IO) {
                database.lapTimeDataDAO().insert(lapTimeData)
            }

            Log.d(TAG, "Lap time saved: $lapTimeData")
        }
    }

    private fun handleCompletedSprint(sprintMs: Long) {
        viewModelScope.launch {
            if (_sessionId == -1L) {
                Log.e(TAG, "Session not created, cannot save sprint")
                return@launch
            }

            val sprintData = LapTimeData(
                sessionid = _sessionId,
                lapnumber = eventCount.value,
                laptime = formatLapTime(sprintMs)
            )

            _lapId = withContext(Dispatchers.IO) {
                database.lapTimeDataDAO().insert(sprintData)
            }

            Log.d(TAG, "Sprint time saved: $sprintData")
        }
    }

    private fun formatLapTime(millis: Long) = String.format(
        "%02d:%02d.%02d",
        millis / 60000,
        (millis % 60000) / 1000,
        (millis % 1000) / 10
    )

    //WORKS
    suspend fun createSession(trackId: Long, vehicleId: Long) {
        withContext(Dispatchers.IO) {
            val sessionManager = SessionManager.getInstance(database)
            val track =
                database.trackMainDao().getTrack(trackId).firstOrNull() ?: return@withContext
            val trackName = track.trackName
            val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
            val eventType = "$trackName - $todayFormatted"

            Log.d("createSession", "eventType: $eventType, vehicleId: $vehicleId")

            val existingSession = database.sessionDataDao().getAllSessions().first().find {
                it.eventType == eventType && it.vehicleId == vehicleId
            }


            _sessionId = existingSession?.id ?: run {
                sessionManager.startSession(
                    eventType = eventType,
                    vehicleId = vehicleId,
                    description = "${timingMode.value} session"
                )
                sessionManager.getCurrentSessionId()!!
            }
            _lapId = trackId


            Log.d("createSession", "Session created with id: $_sessionId")

        }
    }

    fun resetSession() {
        timingManager?.reset()
        _lapId = -1
    }

    companion object {
        private const val TAG = "TimeAttackViewModel"
    }
}


// TimeAttackScreen.kt (complete composable)
@Composable
fun TimeAttackScreenView(
    database: ESPDatabase,
    trackId: Long?,
    vehicleId: Long?,
    // timingMode: TimingMode, Passed from navigation
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: TimeAttackViewModel = viewModel(
        factory = TimeAttackViewModelFactory(context, database)
    )

    // Collect state
    val currentTime by vm.currentTime.collectAsState()
    val bestTime by vm.bestTime.collectAsState()
    val lastTime by vm.lastTime.collectAsState()
    val delta by vm.delta.collectAsState()
    val eventCount by vm.eventCount.collectAsState()
    val stintStart by vm.stintStart.collectAsState()
    val fullTrack by vm.fullTrack.collectAsState()
    val finishLine by vm.finishLine.collectAsState()
    val startLine by vm.startLine.collectAsState()
    val driver by vm.driverPosition.collectAsState()
    val timingMode by vm.timingMode.collectAsState()

    // Calculate lines to show based on mode
    val linesToShow = remember {
        if (timingMode is TimingMode.Sprint) {
            startLine + finishLine
        } else {
            finishLine
        }
    }

    // Initialize track and session
    LaunchedEffect(trackId) {
        if (trackId != null && vehicleId != null) {
            vm.loadTrack(trackId, timingMode)
            vm.createSession(trackId, vehicleId)
        }
    }

    // UI based on orientation
    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> LandscapeLayout(
            timingMode = timingMode,
            currentTime = currentTime,
            delta = delta,
            bestTime = bestTime,
            eventCount = eventCount,
            stintStart = stintStart
        )

        else -> PortraitLayout(
            timingMode = timingMode,
            currentTime = currentTime,
            delta = delta,
            bestTime = bestTime,
            eventCount = eventCount,
            stintStart = stintStart,
            gpsPoints = fullTrack + linesToShow,
            driver = driver ?: LatLonOffset(0.0, 0.0)
        )
    }
}

@Composable
private fun LandscapeLayout(
    timingMode: TimingMode,
    currentTime: String,
    delta: Double,
    bestTime: String,
    eventCount: Int,
    stintStart: Long
) {
    val deltaColor = if (delta < 0) Color.Green else Color.Red
    val eventName = if (timingMode is TimingMode.Circuit) "Laps" else "Runs"

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ModeIndicator(timingMode)
            LapTimeDisplay(time = currentTime, color = deltaColor, size = 68.sp)
            DeltaDisplay(delta = delta, size = 28.sp)
            ReferenceTimeDisplay(bestTime = bestTime)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigDeltaDisplay(delta = delta)
            StintInfoDisplay(
                stintStart = stintStart,
                eventCount = eventCount,
                eventName = eventName
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    timingMode: TimingMode,
    currentTime: String,
    delta: Double,
    bestTime: String,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset
) {
    val deltaColor = if (delta < 0) Color.Green else Color.Red
    val eventName = if (timingMode is TimingMode.Circuit) "Laps" else "Runs"

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            ModeIndicator(timingMode)
            LapTimeDisplay(time = currentTime, color = deltaColor, size = 56.sp)
            DeltaDisplay(delta = delta, size = 24.sp)
            ReferenceTimeDisplay(bestTime = bestTime)
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                StintInfoDisplay(
                    stintStart = stintStart,
                    eventCount = eventCount,
                    eventName = eventName
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTrack(gpsPoints, 32f, driver)
            }
        }
    }
}

@Composable
private fun ModeIndicator(timingMode: TimingMode) {
    val (text, color) = when (timingMode) {
        is TimingMode.Circuit -> "CIRCUIT MODE" to Color.Blue
        is TimingMode.Sprint -> "SPRINT MODE" to Color.Red
    }

    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun LapTimeDisplay(
    time: String,
    color: Color,
    size: TextUnit
) {
    Text(
        text = time,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
private fun DeltaDisplay(
    delta: Double,
    size: TextUnit
) {
    Text(
        text = "Δ ${String.format("%+.2f", delta)}s",
        fontSize = size,
        fontWeight = FontWeight.Bold,
        color = if (delta < 0) Color.Green else Color.Red
    )
}

@Composable
private fun BigDeltaDisplay(
    delta: Double
) {
    Text(
        text = "Δ ${String.format("%+.1f", delta)}s",
        fontSize = 84.sp,
        fontWeight = FontWeight.Black,
        color = if (delta < 0) Color.Green else Color.Red,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReferenceTimeDisplay(
    bestTime: String
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(text = "BEST TIME", color = Color.Gray, fontSize = 18.sp)
        Text(text = bestTime, fontSize = 32.sp, color = Color.LightGray)
    }
}

@Composable
private fun StintInfoDisplay(
    stintStart: Long,
    eventCount: Int,
    eventName: String
) {
    var stintTime by remember { mutableStateOf("00:00:00") }

    LaunchedEffect(stintStart) {
        while (true) {
            delay(100)
            stintTime = formatDuration(SystemClock.elapsedRealtime() - stintStart)
        }
    }

    Column {
        Text(text = "Stint: $stintTime", fontSize = 20.sp, color = Color.DarkGray)
        Text(text = "$eventName: $eventCount", fontSize = 20.sp, color = Color.DarkGray)
    }
}

private fun formatDuration(millis: Long): String = String.format(
    "%02d:%02d:%02d",
    millis / 3600000,
    (millis % 3600000) / 60000,
    (millis % 60000) / 1000
)

// ViewModel Factory
class TimeAttackViewModelFactory(
    private val context: Context,
    private val database: ESPDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeAttackViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimeAttackViewModel(database, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}