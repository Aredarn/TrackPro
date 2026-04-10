package com.example.trackpro.screens

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.extrasForUI.LatLonOffset
import com.example.trackpro.managerClasses.timeAttackManagers.TimingMode
import com.example.trackpro.theme.TrackProColors
import com.example.trackpro.viewModels.TimeAttackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style


// ── TimeAttackScreen.kt ───────────────────────────────────

class TimeAttackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimeAttackScreenView()
        }
    }
}

@Composable
fun TimeAttackScreenView(
    trackId: Long? = null,
    vehicleId: Long? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val vm: TimeAttackViewModel = viewModel(
        factory = TimeAttackViewModelFactory(context)
    )

    // ── Collect all state ──────────────────────────────────
    val isConnected by app.espTcpClient.connectionStatus.collectAsState()
    val gpsData     by app.espTcpClient.gpsFlow.collectAsState()

    val currentTime  by vm.currentTime.collectAsState()
    val bestTime     by vm.bestTime.collectAsState()
    val lastTime     by vm.lastTime.collectAsState()
    val delta        by vm.delta.collectAsState()
    val eventCount   by vm.eventCount.collectAsState()
    val stintStart   by vm.stintStart.collectAsState()
    val fullTrack    by vm.fullTrack.collectAsState()
    val finishLine   by vm.finishLine.collectAsState()
    val startLine    by vm.startLine.collectAsState()
    val driver       by vm.driverPosition.collectAsState()
    val timingMode   by vm.timingMode.collectAsState()

    val linesToShow by remember(timingMode, startLine, finishLine) {
        derivedStateOf {
            if (timingMode is TimingMode.Sprint) startLine + finishLine else finishLine
        }
    }

    // ── Wire GPS from shared client into ViewModel ─────────
    LaunchedEffect(gpsData) {
        gpsData?.let { vm.handleGpsUpdate(it) }
    }

    // ── Init track + session ───────────────────────────────
    LaunchedEffect(trackId) {
        if (trackId == null || vehicleId == null) return@LaunchedEffect
        val track = withContext(Dispatchers.IO) {
            app.database.trackMainDao().getTrack(trackId).firstOrNull()
        }
        val mode = when (track?.type?.lowercase()) {
            "sprint" -> TimingMode.Sprint
            else     -> TimingMode.Circuit
        }
        vm.loadTrack(trackId, mode)
        vm.createSession(trackId, vehicleId)
    }

    val gpsPoints = fullTrack + linesToShow
    val driverPos = driver ?: LatLonOffset(0.0, 0.0)

    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> TimeAttackLandscapeLayout(
            timingMode  = timingMode,
            currentTime = currentTime,
            bestTime    = bestTime,
            lastTime    = lastTime,
            delta       = delta,
            eventCount  = eventCount,
            stintStart  = stintStart,
            gpsPoints   = gpsPoints,
            driver      = driverPos,
            isConnected = isConnected
        )
        else -> TimeAttackPortraitLayout(
            timingMode  = timingMode,
            currentTime = currentTime,
            bestTime    = bestTime,
            lastTime    = lastTime,
            delta       = delta,
            eventCount  = eventCount,
            stintStart  = stintStart,
            gpsPoints   = gpsPoints,
            driver      = driverPos,
            isConnected = isConnected
        )
    }
}

// ── Portrait ───────────────────────────────────────────────

@Composable
fun TimeAttackPortraitLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset,
    isConnected: Boolean
) {
    val deltaColor = if (delta <= 0) TrackProColors.DeltaGood else TrackProColors.DeltaBad
    val eventName  = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeColor  = if (timingMode is TimingMode.Circuit) TrackProColors.AccentRed else TrackProColors.AccentAmber
    val modeLabel  = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(modeColor)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "● $modeLabel MODE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = if (isConnected) "LIVE" else "NO SIGNAL",
                        color = if (isConnected) Color.Black else Color.Black.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
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
                    Box(
                        modifier = Modifier
                            .background(deltaColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Δ ${String.format("%+.3f", delta)}s",
                            color = deltaColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = eventName,
                        color = TrackProColors.TextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "$eventCount",
                        color = TrackProColors.TextPrimary,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

            // ── Best / Last / Stint ───────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.BgElevated)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LapTimeCell(label = "BEST", value = bestTime, valueColor = TrackProColors.DeltaGood)
                SectorDivider()
                LapTimeCell(label = "LAST", value = lastTime, valueColor = TrackProColors.TextPrimary)
                SectorDivider()
                StintTimerCell(stintStart = stintStart)
            }

            HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

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
                        Text(
                            "AWAITING GPS SIGNAL",
                            color = TrackProColors.TextMuted,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
                Text(
                    text = if (timingMode is TimingMode.Circuit) "◎" else "▶",
                    color = modeColor.copy(alpha = 0.06f),
                    fontSize = 180.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

// ── Landscape ──────────────────────────────────────────────

@Composable
fun TimeAttackLandscapeLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset,
    isConnected: Boolean
) {
    val deltaColor = if (delta <= 0) TrackProColors.DeltaGood else TrackProColors.DeltaBad
    val eventName  = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeColor  = if (timingMode is TimingMode.Circuit) TrackProColors.AccentRed else TrackProColors.AccentAmber
    val modeLabel  = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        // ── Left: telemetry panel ──────────────────────────
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxSize()
                .background(TrackProColors.BgCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(modeColor)
                    .padding(horizontal = 16.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "● $modeLabel",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        if (isConnected) "LIVE" else "NO SIGNAL",
                        color = Color.Black.copy(alpha = if (isConnected) 1f else 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CURRENT $eventName",
                    color = TrackProColors.TextMuted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    text = currentTime,
                    color = deltaColor,
                    fontSize = 48.sp,
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = TrackProColors.SectorLine)
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    LapTimeCell(label = "BEST", value = bestTime, valueColor = TrackProColors.DeltaGood)
                    LapTimeCell(label = "LAST", value = lastTime, valueColor = TrackProColors.TextPrimary)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = TrackProColors.SectorLine)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StintTimerCell(stintStart = stintStart)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            eventName,
                            color = TrackProColors.TextMuted,
                            fontSize = 9.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "$eventCount",
                            color = modeColor,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // ── Right: map ─────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(0.58f)
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
                    Text(
                        "AWAITING GPS",
                        color = TrackProColors.TextMuted,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}

// ── Shared sub-components ──────────────────────────────────

@Composable
private fun LapTimeCell(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            color = TrackProColors.TextMuted,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StintTimerCell(stintStart: Long) {
    var stintTime by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(stintStart) {
        while (true) {
            delay(500)
            val elapsed = SystemClock.elapsedRealtime() - stintStart
            val h = elapsed / 3_600_000
            val m = (elapsed % 3_600_000) / 60_000
            val s = (elapsed % 60_000) / 1_000
            stintTime = String.format("%02d:%02d:%02d", h, m, s)
        }
    }
    Column {
        Text(
            text = "STINT",
            color = TrackProColors.TextMuted,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stintTime,
            color = TrackProColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectorDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(TrackProColors.SectorLine)
    )
}
// ── Reusable sub-components ────────────────────────────────

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