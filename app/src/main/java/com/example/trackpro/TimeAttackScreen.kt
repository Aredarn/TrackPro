// TimeAttackScreen.kt
package com.example.trackpro

import android.content.ContentValues.TAG
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackpro.DataClasses.SessionData
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData
import com.example.trackpro.ManagerClasses.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

// Data and enums
private enum class CrossingDirection { ENTERING, EXITING }
private data class CrossingResult(val isValid: Boolean, val direction: CrossingDirection)
private data class Point(val x: Double, val y: Double)

// ViewModel
class TimeAttackViewModel(
    private val database: ESPDatabase,
    context: Context
) : ViewModel() {
    private var tcpClient: ESPTcpClient? = null

    private val config = JsonReader.loadConfig(context)
    private val ip = config.first
    private val port = config.second
    private var hasStarted = false

    private val _driverPosition = MutableStateFlow<LatLonOffset?>(null)
    val driverPosition: StateFlow<LatLonOffset?> = _driverPosition.asStateFlow()

    // Finish line state
    private val _finishLine = MutableStateFlow<List<TrackCoordinatesData>>(emptyList())
    val finishLine: StateFlow<List<TrackCoordinatesData>> = _finishLine.asStateFlow()

    private val _fullTrack = MutableStateFlow<List<TrackCoordinatesData>>(emptyList())
    val fullTrack: StateFlow<List<TrackCoordinatesData>> = _fullTrack.asStateFlow()


    // Timing state
    private var lapStartTime = SystemClock.elapsedRealtime()
    private var lastCrossTime = 0L
    private var bestLapSeconds = Double.POSITIVE_INFINITY
    private var previousGPSData: RawGPSData? = null

    // UI state
    private val _currentLapTime = MutableStateFlow("00:00.00")
    val currentLapTime: StateFlow<String> = _currentLapTime.asStateFlow()
    private val _bestLap = MutableStateFlow("--:--.--")
    val bestLap: StateFlow<String> = _bestLap.asStateFlow()
    private val _lastLap = MutableStateFlow("--:--.--")
    val lastLap: StateFlow<String> = _lastLap.asStateFlow()
    private val _delta = MutableStateFlow(0.0)
    val delta: StateFlow<Double> = _delta.asStateFlow()
    private val _lapCount = MutableStateFlow(0)
    val lapCount: StateFlow<Int> = _lapCount.asStateFlow()
    private val _stintStart = MutableStateFlow(lapStartTime)
    val stintStart: StateFlow<Long> = _stintStart.asStateFlow()


    fun loadTrack(trackId: Long) {
        viewModelScope.launch {
            database.trackCoordinatesDao().getCoordinatesOfTrack(trackId).collect { coords ->
                _fullTrack.value = coords
                _finishLine.value = calculateFinishLine(coords)
            }
        }
    }

    init {
        startTcpClient()
    }

    private fun startTcpClient() = viewModelScope.launch {
        runCatching {
            val client = ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data -> handleGpsUpdate(data) },
                onConnectionStatusChanged = { connected -> if (!connected) resetTiming() }
            )
            tcpClient = client
            client.connect()
        }.onFailure {
            Log.e(TAG, "TCP connection failed", it)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tcpClient?.disconnect()  // Add this method to your ESPTcpClient if needed
        resetTiming()
        Log.d(TAG, "TimeAttackViewModel cleared, TCP connection closed")
    }


    private fun handleGpsUpdate(current: RawGPSData) {
        val now = SystemClock.elapsedRealtime()

        previousGPSData?.let { prev ->
            checkFinishLineCrossing(prev, current)?.let { crossing ->
                Log.d(
                    TAG,
                    "Crossing detected: valid=${crossing.isValid}, dir=${crossing.direction}"
                )

                if (crossing.isValid && now - lastCrossTime > 5000) {
                    if (!hasStarted) {
                        hasStarted = true
                        Log.d(
                            TAG,
                            "First valid crossing detected, marking session started but NOT counting lap."
                        )
                        lastCrossTime = now
                        lapStartTime = now
                        return  // Don't count the first lap
                    }

                    val lapMs = now - lapStartTime
                    Log.d(TAG, "Lap crossed: duration=${lapMs}ms")
                    updateLapTimes(lapMs)
                    lastCrossTime = now
                    lapStartTime = now
                    _lapCount.value += 1
                }
            }
        }

        _currentLapTime.value = formatLapTime(now - lapStartTime)
        _driverPosition.value = LatLonOffset(lat = current.latitude, lon = current.longitude)
        previousGPSData = current
    }

    private fun updateLapTimes(lapMs: Long) {
        val seconds = lapMs / 1000.0
        _delta.value = if (bestLapSeconds.isFinite()) seconds - bestLapSeconds else 0.0
        if (seconds < bestLapSeconds) {
            bestLapSeconds = seconds
            _bestLap.value = formatLapTime(lapMs)
        }
        _lastLap.value = formatLapTime(lapMs)
    }

    private fun resetTiming() {
        lapStartTime = SystemClock.elapsedRealtime()
        lastCrossTime = 0L
        hasStarted = false  // Reset session state
        _stintStart.value = lapStartTime
        _lapCount.value = 0
    }

    private fun formatLapTime(millis: Long) = String.format(
        "%02d:%02d.%02d",
        (millis / 60000),
        ((millis % 60000) / 1000),
        ((millis % 1000) / 10)
    )

    private fun checkFinishLineCrossing(prev: RawGPSData, curr: RawGPSData): CrossingResult? {
        val finishLine = _finishLine.value
        if (finishLine.size < 2) return null

        // Convert to vectors for easier calculations
        val prevPos = Vector(prev.latitude, prev.longitude)
        val currPos = Vector(curr.latitude, curr.longitude)
        val lineStart = Vector(finishLine[0].latitude, finishLine[0].longitude)
        val lineEnd = Vector(finishLine[1].latitude, finishLine[1].longitude)

        // Check for intersection
        val intersection = findIntersection(prevPos, currPos, lineStart, lineEnd)

        if (intersection != null) {
            // Determine crossing direction (which side of the line we're crossing to)
            val crossingDirection = determineCrossingDirection(prevPos, currPos, lineStart, lineEnd)
            return CrossingResult(true, crossingDirection)
        }

        return null
    }

    private fun calculateFinishLine(track: List<TrackCoordinatesData>): List<TrackCoordinatesData> {
        val startPoint = track.find { it.isStartPoint } ?: run {
            Log.w(TAG, "No start point found, using first two points as fallback")
            return if (track.size >= 2) listOf(track[0], track[1]) else track
        }

        // Find nearby points to determine track direction
        val nearbyPoints = track.filter {
            it.id in (startPoint.id - 5)..(startPoint.id + 5) && it.id != startPoint.id
        }.take(10)

        if (nearbyPoints.isEmpty()) {
            Log.w(TAG, "Not enough points near start, using first two points")
            return if (track.size >= 2) listOf(track[0], track[1]) else track
        }

        // Calculate average direction vector from nearby points
        val avgDirection = nearbyPoints.fold(Vector(0.0, 0.0)) { acc, point ->
            acc + Vector(point.longitude - startPoint.longitude,
                point.latitude - startPoint.latitude)
        } * (1.0 / nearbyPoints.size)

        // Create perpendicular vector (rotated 90 degrees)
        val perpendicular = Vector(-avgDirection.y, avgDirection.x).normalized()

        // Scale to 8-16 meters
        val lineLength = 12.0 / 111320.0  // 12 meters in degrees (adjust as needed)
        val scaledPerpendicular = perpendicular * lineLength

        // Create finish line endpoints
        return listOf(
            TrackCoordinatesData(
                id = -1,
                trackId = startPoint.trackId,
                latitude = startPoint.latitude - scaledPerpendicular.y,
                longitude = startPoint.longitude - scaledPerpendicular.x,
                altitude = startPoint.altitude,
                isStartPoint = false
            ),
            TrackCoordinatesData(
                id = -2,
                trackId = startPoint.trackId,
                latitude = startPoint.latitude + scaledPerpendicular.y,
                longitude = startPoint.longitude + scaledPerpendicular.x,
                altitude = startPoint.altitude,
                isStartPoint = false
            )
        )
    }

    private fun addLapToSession()
    {

    }

    // Creates a new lap timing session
    //Name: track name + date

    suspend fun createSession(trackId: Long, vehicleId: Long) {
        val sessionManager = SessionManager.getInstance(database)

        withContext(Dispatchers.IO) {
            val trackName =database.trackMainDao().getTrack(trackId).first().trackName
            val todayFormatted = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
            val eventType = "$trackName - $todayFormatted"

            // Check if this session already exists
            val existingSessions = database.sessionDataDao().getAllSessions().first() // assuming this returns a Flow<List<Session>>
            val duplicate = existingSessions.any {
                it.eventType == eventType && it.vehicleId == vehicleId
            }

            Log.d(TAG,"ok?")
            if (duplicate) {
                return@withContext
            }

            sessionManager.startSession(
                eventType = eventType,
                vehicleId = vehicleId,
                description = "Lap timing session"
            )
        }
    }


    // Vector math helper functions
    private data class Vector(val x: Double, val y: Double) {
        operator fun plus(v: Vector) = Vector(x + v.x, y + v.y)
        operator fun minus(v: Vector) = Vector(x - v.x, y - v.y)
        operator fun times(scalar: Double) = Vector(x * scalar, y * scalar)
        fun cross(v: Vector) = x * v.y - y * v.x
        fun length() = sqrt(x * x + y * y)
        fun normalized() = this * (1.0 / length())
    }

    private fun findIntersection(
        a1: Vector, a2: Vector,  // Path segment
        b1: Vector, b2: Vector   // Finish line segment
    ): Vector? {
        val r = a2 - a1
        val s = b2 - b1
        val rxs = r.cross(s)
        val qmp = b1 - a1
        val qpxr = qmp.cross(r)

        // If segments are parallel or collinear
        if (rxs == 0.0) return null

        val t = qmp.cross(s) / rxs
        val u = qpxr / rxs

        // If intersection is within both segments
        if (t in 0.0..1.0 && u in 0.0..1.0) {
            return a1 + r * t
        }
        return null
    }

    private fun determineCrossingDirection(
        prevPos: Vector, currPos: Vector,
        lineStart: Vector, lineEnd: Vector
    ): CrossingDirection {
        val pathVector = currPos - prevPos
        val lineVector = lineEnd - lineStart

        // Calculate cross product to determine relative direction
        val cross = pathVector.cross(lineVector)

        // If cross product is positive, path is crossing in one direction
        // If negative, it's crossing in the opposite direction
        return if (cross > 0) CrossingDirection.ENTERING else CrossingDirection.EXITING
    }


}

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

// Composable UI
@Composable
fun TimeAttackScreenView(
    database: ESPDatabase,
    trackId: Long?,
    vehicleId: Long?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: TimeAttackViewModel = viewModel(
        factory = TimeAttackViewModelFactory(context, database)
    )

    LaunchedEffect(trackId) {
        if (trackId != null && vehicleId != null) {
            vm.loadTrack(trackId)
            vm.createSession(trackId, vehicleId)
        }
    }


    val currentLap by vm.currentLapTime.collectAsState()
    val bestLap by vm.bestLap.collectAsState()
    val delta by vm.delta.collectAsState()
    val lapCount by vm.lapCount.collectAsState()
    val stintStart by vm.stintStart.collectAsState()

    val deltaColor = if (delta < 0) Color.Green else Color.Red

    val fullTrack by vm.fullTrack.collectAsState()
    val finishLine by vm.finishLine.collectAsState()
    val driver by vm.driverPosition.collectAsState()


    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> LandscapeLayout(
            currentLap = currentLap,
            delta = delta,
            deltaColor = deltaColor,
            bestLap = bestLap,
            lapCount = lapCount,
            stintStart = stintStart
        )

        else -> PortraitLayout(
            currentLap = currentLap,
            delta = delta,
            deltaColor = deltaColor,
            bestLap = bestLap,
            lapCount = lapCount,
            stintStart = stintStart,
            gpsPoints = fullTrack + finishLine,
            driver = driver ?: LatLonOffset(0.0, 0.0)
        )
    }
}

@Composable
private fun LandscapeLayout(
    currentLap: String,
    delta: Double,
    deltaColor: Color,
    bestLap: String,
    lapCount: Int,
    stintStart: Long
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LapTimeDisplay(time = currentLap, color = deltaColor, size = 68.sp)
            DeltaDisplay(delta = delta, size = 28.sp)
            ReferenceLapDisplay(bestLap = bestLap)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigDeltaDisplay(delta = delta)
            StintInfoDisplay(stintStart = stintStart, lapCount = lapCount)
        }
    }
}

@Composable
private fun PortraitLayout(
    currentLap: String,
    delta: Double,
    deltaColor: Color,
    bestLap: String,
    lapCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                LapTimeDisplay(time = currentLap, color = deltaColor, size = 56.sp)
                DeltaDisplay(delta = delta, size = 24.sp)
                ReferenceLapDisplay(bestLap = bestLap)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
               // BigDeltaDisplay(delta = delta)
                StintInfoDisplay(stintStart = stintStart, lapCount = lapCount)
            }
        }
        Box(modifier = Modifier.weight(1f))
        {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw the track, start line, and animated dot
                drawTrack(gpsPoints, 32f,driver)

            }
        }
    }
}

@Composable
private fun LapTimeDisplay(
    time: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit
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
    size: androidx.compose.ui.unit.TextUnit
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
private fun ReferenceLapDisplay(
    bestLap: String
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(text = "REFERENCE LAP", color = Color.Gray, fontSize = 18.sp)
        Text(text = bestLap, fontSize = 32.sp, color = Color.LightGray)
    }
}

@Composable
private fun StintInfoDisplay(
    stintStart: Long,
    lapCount: Int
) {
    var stintTime by rememberSaveable { mutableStateOf("00:00:00") }
    LaunchedEffect(stintStart) {
        while (true) {
            delay(100L)
            stintTime = formatDuration(SystemClock.elapsedRealtime() - stintStart)
        }
    }
    Column {
        Text(text = "Stint: $stintTime", fontSize = 20.sp, color = Color.DarkGray)
        Text(text = "Laps: $lapCount", fontSize = 20.sp, color = Color.DarkGray)
    }
}

private fun formatDuration(millis: Long): String = String.format(
    "%02d:%02d:%02d",
    millis / 3600000,
    (millis % 3600000) / 60000,
    (millis % 60000) / 1000
)
