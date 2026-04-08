package com.example.trackpro.screens

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackpro.DataClasses.LapInfoData
import com.example.trackpro.DataClasses.LapTimeData
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.ManagerClasses.ESPDatabase
import com.example.trackpro.ExtrasForUI.LatLonOffset
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
import com.example.trackpro.ManagerClasses.toDataClass
import com.example.trackpro.TrackProApp
import com.example.trackpro.theme.TrackProColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.concurrent.formatDuration
import org.maplibre.android.maps.MapView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.maplibre.android.maps.Style
class TimeAttackViewModel(
    context: Context
) : ViewModel() {
    private var tcpClient: ESPTcpClient? = null
    private val config = JsonReader.loadConfig(context)
    private val ip = config.first
    private val port = config.second

    val app = context.applicationContext as TrackProApp
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
        GlobalScope.launch(Dispatchers.IO) {
            endSession(database)
            Log.d(TAG, "endSession completed")
        }

        Log.d(TAG, "ViewModel cleared")
    }

    // Updated ViewModel section
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
    }

    //WORKS
    private fun handleGpsUpdate(current: RawGPSData) {
        timingManager?.handleGpsUpdate(previousGPSData, current.toDataClass() )
        _driverPosition.value = LatLonOffset(lat = current.latitude, lon = current.longitude)
        previousGPSData = current
        processLapData(current)
    }

    //WORKS
    private fun processLapData(current: RawGPSData) {
        if (_sessionId == -1L) return

        val lapInfoData = LapInfoData(
            lapid = if (_lapId == -1L) 0 else _lapId, // Placeholder to handle nulls
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
            if (_sessionId == -1L || _lapId == -1L) return@launch

            val lapTimeStr = formatLapTime(lapMs)

            withContext(Dispatchers.IO) {
                // 1. "Close" the current lap with the final time
                database.lapTimeDataDAO().updateLapTime(_lapId, lapTimeStr)

                Log.d("TimeAttack", "Updated Lap ID $_lapId with time $lapTimeStr")
            }

            // 2. Immediately start the next lap so GPS points have a new ID to latch onto
            // eventCount.value + 1 provides the next lap number
            startNewLap(eventCount.value + 1)
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
            val track = database.trackMainDao().getTrack(trackId).firstOrNull() ?: return@withContext
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
                    trackId = trackId
                )
                sessionManager.getCurrentSessionId()!!
            }
            _lapId = -1L


            Log.d("createSession", "Session created with id: $_sessionId")
            startNewLap(lapNumber = 1)
        }
    }

    private fun startNewLap(lapNumber: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val lapTimeData = LapTimeData(
                sessionid = _sessionId,
                lapnumber = lapNumber,
                laptime = "IN PROGRESS"
            )
            _lapId = database.lapTimeDataDAO().insert(lapTimeData)
            Log.d("TimeAttack", "Started recording for Lap $lapNumber with ID $_lapId")
        }
    }

    suspend fun endSession(database: ESPDatabase) {
        Log.d("In end", "In end")
        val sessionManager = SessionManager.getInstance(database)

        withContext(Dispatchers.IO) {
            Log.d("In end", sessionManager.getCurrentSessionId().toString())
            sessionManager.endSession()
        }
    }


    companion object {
        private const val TAG = "TimeAttackViewModel"
    }
}


// TimeAttackScreen.kt (complete composable)
// Replace everything from TimeAttackScreenView downward

@Composable
fun TimeAttackScreenView(
    database: ESPDatabase,
    trackId: Long?,
    vehicleId: Long?,
) {
    val context = LocalContext.current
    val vm: TimeAttackViewModel = viewModel(factory = TimeAttackViewModelFactory(context))

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

    val linesToShow by remember(timingMode, startLine, finishLine) {
        derivedStateOf {
            if (timingMode is TimingMode.Sprint) startLine + finishLine else finishLine
        }
    }

    LaunchedEffect(trackId) {
        if (trackId != null && vehicleId != null) {
            val track = withContext(Dispatchers.IO) {
                database.trackMainDao().getTrack(trackId).firstOrNull()
            }
            val mode = when (track?.type?.lowercase()) {
                "sprint" -> TimingMode.Sprint
                else -> TimingMode.Circuit
            }
            vm.loadTrack(trackId, mode)
            vm.createSession(trackId, vehicleId)
        }
    }

    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> LandscapeLayout(
            timingMode = timingMode,
            currentTime = currentTime,
            bestTime = bestTime,
            lastTime = lastTime,
            delta = delta,
            eventCount = eventCount,
            stintStart = stintStart,
            gpsPoints = fullTrack + linesToShow,
            driver = driver ?: LatLonOffset(0.0, 0.0)
        )
        else -> PortraitLayout(
            timingMode = timingMode,
            currentTime = currentTime,
            bestTime = bestTime,
            lastTime = lastTime,
            delta = delta,
            eventCount = eventCount,
            stintStart = stintStart,
            gpsPoints = fullTrack + linesToShow,
            driver = driver ?: LatLonOffset(0.0, 0.0)
        )
    }
}


@Composable
private fun PortraitLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset
) {
    val deltaColor = if (delta <= 0) TrackProColors.DeltaGood else TrackProColors.DeltaBad
    val eventName = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeColor = if (timingMode is TimingMode.Circuit) TrackProColors.AccentRed else TrackProColors.AccentAmber

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top mode bar ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(modeColor)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                val modeLabel = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"
                Text(
                    text = "● $modeLabel MODE",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            }

            // ── Main timer ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.BgCard)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column {
                    Text(
                        text = "CURRENT $eventName",
                        color = TrackProColors.TextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentTime,
                        color = deltaColor,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        lineHeight = 68.sp
                    )
                    // Delta pill
                    val deltaText = String.format("%+.3f", delta) + "s"
                    Box(
                        modifier = Modifier
                            .background(
                                deltaColor.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Δ $deltaText",
                            color = deltaColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Lap counter — top right
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(text = eventName, color = TrackProColors.TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(
                        text = "$eventCount",
                        color = TrackProColors.TextPrimary,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Divider(color = TrackProColors.SectorLine, thickness = 1.dp)

            // ── Best / Last row ───────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.BgElevated)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimeCell(label = "BEST", value = bestTime, valueColor = TrackProColors.DeltaGood)
                VerticalDivider()
                TimeCell(label = "LAST", value = lastTime, valueColor = TrackProColors.TextPrimary)
                VerticalDivider()
                StintCell(stintStart = stintStart)
            }

            Divider(color = TrackProColors.SectorLine, thickness = 1.dp)

            // ── Map ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TrackProColors.BgCard)
            ) {
                if (gpsPoints.isNotEmpty()) {
                    MapLibreTrackView(
                        gpsPoints = gpsPoints,
                        driverPosition = driver,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("AWAITING GPS SIGNAL", color = TrackProColors.TextMuted, fontSize = 12.sp, letterSpacing = 2.sp)
                    }
                }

                // Mode watermark overlay
                Text(
                    text = if (timingMode is TimingMode.Circuit) "◎" else "▶",
                    color = modeColor.copy(alpha = 0.08f),
                    fontSize = 180.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun LandscapeLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset
) {
    val deltaColor = if (delta <= 0) TrackProColors.DeltaGood else TrackProColors.DeltaBad
    val eventName = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeColor = if (timingMode is TimingMode.Circuit) TrackProColors.AccentRed else TrackProColors.AccentAmber

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        // Left panel — telemetry
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxSize()
                .background(TrackProColors.BgCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(modeColor)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                val modeLabel = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"
                Text("● $modeLabel", color = Color.Black, fontSize = 10.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "CURRENT $eventName", color = TrackProColors.TextMuted, fontSize = 9.sp, letterSpacing = 2.sp)
                Text(
                    text = currentTime,
                    color = deltaColor,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Box(
                    modifier = Modifier
                        .background(deltaColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Δ ${String.format("%+.3f", delta)}s",
                        color = deltaColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = TrackProColors.SectorLine)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    TimeCell(label = "BEST", value = bestTime, valueColor = TrackProColors.DeltaGood)
                    TimeCell(label = "LAST", value = lastTime, valueColor = TrackProColors.TextPrimary)
                }

                Spacer(Modifier.height(16.dp))
                Divider(color = TrackProColors.SectorLine)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StintCell(stintStart = stintStart)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(eventName, color = TrackProColors.TextMuted, fontSize = 9.sp, letterSpacing = 2.sp)
                        Text(
                            "$eventCount",
                            color = modeColor,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Right panel — map
        Box(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxSize()
                .background(TrackProColors.BgDeep)
        ) {
            if (gpsPoints.isNotEmpty()) {
                MapLibreTrackView(
                    gpsPoints = gpsPoints,
                    driverPosition = driver,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("AWAITING GPS", color = TrackProColors.TextMuted, fontSize = 11.sp, letterSpacing = 2.sp)
                }
            }
        }
    }
}

// ── Reusable sub-components ────────────────────────────────

@Composable
private fun TimeCell(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, color = TrackProColors.TextMuted, fontSize = 9.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StintCell(stintStart: Long) {
    var stintTime by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(stintStart) {
        while (true) {
            delay(100)
            stintTime = formatDuration(SystemClock.elapsedRealtime() - stintStart)
        }
    }
    Column {
        Text(text = "STINT", color = TrackProColors.TextMuted, fontSize = 9.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(text = stintTime, color = TrackProColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(TrackProColors.SectorLine)
    )
}

@Composable
fun MapLibreTrackView(
    gpsPoints: List<TrackCoordinatesData>,
    driverPosition: LatLonOffset,
    modifier: Modifier = Modifier
) {
    val driverSource = remember { mutableStateOf<org.maplibre.android.style.sources.GeoJsonSource?>(null) }
    val mapReady = remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }

    // 1. Only update the Driver Source (No Camera Movement)
    LaunchedEffect(driverPosition) {
        val src = driverSource.value ?: return@LaunchedEffect
        if (driverPosition.lat == 0.0 && driverPosition.lon == 0.0) return@LaunchedEffect

        val geojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${driverPosition.lon},${driverPosition.lat}]},"properties":{}}"""
        src.setGeoJson(geojson)
    }

    // 2. Separate Effect to fit the camera to the track whenever the track data changes
    LaunchedEffect(gpsPoints) {
        val map = mapReady.value ?: return@LaunchedEffect
        if (gpsPoints.isNotEmpty()) {
            fitCameraToTrack(map, gpsPoints)
        }
    }

    AndroidView(
        factory = { ctx ->
            org.maplibre.android.MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapReady.value = map
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        // Disable gestures so the user doesn't accidentally move away from the track
                        map.uiSettings.setAllGesturesEnabled(false)

                        if (gpsPoints.isNotEmpty()) {
                            drawTrackOnStyle(style, gpsPoints)
                            fitCameraToTrack(map, gpsPoints) // Initial fit
                        }

                        val src = org.maplibre.android.style.sources.GeoJsonSource(
                            "driver-src",
                            """{"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]},"properties":{}}"""
                        )
                        style.addSource(src)
                        style.addLayer(
                            org.maplibre.android.style.layers.CircleLayer("driver-layer", "driver-src").apply {
                                setProperties(
                                    org.maplibre.android.style.layers.PropertyFactory.circleColor("#FFFFFF"),
                                    org.maplibre.android.style.layers.PropertyFactory.circleRadius(6f),
                                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#E8001C"),
                                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(2f)
                                )
                            }
                        )
                        driverSource.value = src
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun fitCameraToTrack(map: org.maplibre.android.maps.MapLibreMap, points: List<TrackCoordinatesData>) {
    if (points.isEmpty()) return

    val boundsBuilder = org.maplibre.android.geometry.LatLngBounds.Builder()
    points.forEach {
        boundsBuilder.include(org.maplibre.android.geometry.LatLng(it.latitude, it.longitude))
    }

    map.easeCamera(
        org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
            boundsBuilder.build(),
            100 // Padding in pixels from the edges of the view
        ), 1000 // Animation duration
    )
}


private fun drawTrackOnStyle(
    style: Style,
    points: List<TrackCoordinatesData>
) {
    val coords = com.google.gson.JsonArray().apply {
        points.forEach { pt ->
            add(com.google.gson.JsonArray().apply {
                add(pt.longitude)
                add(pt.latitude)
            })
        }
    }
    val geojson = """{"type":"Feature","geometry":{"type":"LineString","coordinates":$coords},"properties":{}}"""

    style.addSource(org.maplibre.android.style.sources.GeoJsonSource("track-src", geojson))
    style.addLayer(
        org.maplibre.android.style.layers.LineLayer("track-layer", "track-src").apply {
            setProperties(
                org.maplibre.android.style.layers.PropertyFactory.lineColor("#E8001C"),
                org.maplibre.android.style.layers.PropertyFactory.lineWidth(3f),
                org.maplibre.android.style.layers.PropertyFactory.lineCap(
                    org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                ),
                org.maplibre.android.style.layers.PropertyFactory.lineJoin(
                    org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND
                )
            )
        }
    )
}


class TimeAttackViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeAttackViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimeAttackViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}