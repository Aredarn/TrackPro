// TimeAttackScreen.kt
package com.example.trackpro

import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.trackpro.CalculationClasses.rotateTrackPoints
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.pow

// Data and enums
private enum class CrossingDirection { ENTERING, EXITING }
private data class CrossingResult(val isValid: Boolean, val direction: CrossingDirection)
private data class Point(val x: Double, val y: Double)

// ViewModel
class TimeAttackViewModel(
    private val database: ESPDatabase,
    context: Context
) : ViewModel() {
    // Load config without destructuring
    private val config = JsonReader.loadConfig(context)
    private val ip = config.first
    private val port = config.second

    // Finish line state
    private val _finishLine = MutableStateFlow<List<TrackCoordinatesData>>(emptyList())
    val finishLine: StateFlow<List<TrackCoordinatesData>> = _finishLine.asStateFlow()

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
                _finishLine.value = calculateFinishLine(coords)
                Log.d("Track:", "Loaded track with ${coords.size} points, finish line: ${_finishLine.value}")

            }
        }
    }

    init {
        startTcpClient()
    }

    private fun startTcpClient() = viewModelScope.launch {
        runCatching {
            ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data -> handleGpsUpdate(data) },
                onConnectionStatusChanged = { connected -> if (!connected) resetTiming() }
            ).connect()
        }

    }

    private fun handleGpsUpdate(current: RawGPSData) {
        val now = SystemClock.elapsedRealtime()
        Log.d("TAG", "Received GPS: lat=${current.latitude}, lon=${current.longitude}, time=${current.timestamp}")
        previousGPSData?.let { prev ->
            checkFinishLineCrossing(prev, current)?.let { crossing ->
                Log.d(TAG, "Crossing detected: valid=${crossing.isValid}, dir=${crossing.direction}")
                if (crossing.isValid && now - lastCrossTime > 5000) {
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
        _stintStart.value = lapStartTime
        _lapCount.value = 0
    }

    private fun checkFinishLineCrossing(prev: RawGPSData, curr: RawGPSData): CrossingResult? {
        val line = _finishLine.value
        if (line.size < 2) return null

        val midLat = (line[0].latitude + line[1].latitude) / 2
        val midLon = (line[0].longitude + line[1].longitude) / 2

        val distance = haversine(curr.latitude, curr.longitude, midLat, midLon)

        Log.d(TAG, "Checking proximity to finish line midpoint: $distance m")

        return if (distance < 5.0) {  // 5 meters radius for simplicity
            CrossingResult(true, CrossingDirection.ENTERING)
        } else {
            null
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).pow(2.0) + Math.cos(Math.toRadians(lat1)) *
                Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).pow(2.0)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }



    private fun lineSegmentsIntersect(a1: Point, a2: Point, b1: Point, b2: Point): Boolean {
        fun ccw(p1: Point, p2: Point, p3: Point) =
            (p2.x - p1.x) * (p3.y - p1.y) - (p2.y - p1.y) * (p3.x - p1.x)
        val c1 = ccw(a1, a2, b1)
        val c2 = ccw(a1, a2, b2)
        val c3 = ccw(b1, b2, a1)
        val c4 = ccw(b1, b2, a2)
        return (c1 * c2 < 0) && (c3 * c4 < 0)
    }

    private fun determineCrossingDirection(a1: Point, a2: Point, b1: Point, b2: Point): CrossingDirection {
        val trackVec = Point(b2.x - b1.x, b2.y - b1.y)
        val moveVec = Point(a2.x - a1.x, a2.y - a1.y)
        return if ((trackVec.x * moveVec.y - trackVec.y * moveVec.x) > 0)
            CrossingDirection.ENTERING else CrossingDirection.EXITING
    }

    private fun formatLapTime(millis: Long) = String.format(
        "%02d:%02d.%02d",
        (millis / 60000),
        ((millis % 60000) / 1000),
        ((millis % 1000) / 10)
    )

    private fun calculateFinishLine(track: List<TrackCoordinatesData>): List<TrackCoordinatesData> {
        val start = track.find { it.isStartPoint == true }
        val surrounding = if (start != null) {
            track.filter { it.id in (start.id - 10)..(start.id + 10) }
        } else {
            Log.w(TAG, "No start point flagged; falling back to first two track points.")
            if (track.size >= 2) listOf(track[0], track[1]) else track
        }
        val finish = rotateTrackPoints(surrounding, start ?: surrounding.first())
        Log.d(TAG, "Finish line calculated with ${finish.size} points: $finish")
        return finish
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
        trackId?.let { vm.loadTrack(it) }
    }



    val currentLap by vm.currentLapTime.collectAsState()
    val bestLap by vm.bestLap.collectAsState()
    val delta by vm.delta.collectAsState()
    val lapCount by vm.lapCount.collectAsState()
    val stintStart by vm.stintStart.collectAsState()

    val deltaColor = if (delta < 0) Color.Green else Color.Red

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
            stintStart = stintStart
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
            modifier = Modifier.weight(1f).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LapTimeDisplay(time = currentLap, color = deltaColor, size = 68.sp)
            DeltaDisplay(delta = delta, size = 28.sp)
            ReferenceLapDisplay(bestLap = bestLap)
        }
        Column(
            modifier = Modifier.weight(1f).padding(16.dp),
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
    stintStart: Long
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
                BigDeltaDisplay(delta = delta)
                StintInfoDisplay(stintStart = stintStart, lapCount = lapCount)
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
