package com.example.trackpro.screens.telemetricScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.theme.TrackProColors
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun DragRaceScreen(database: ESPDatabase, sessionManager: SessionManager) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val scope = rememberCoroutineScope()

    // --- GPS & CONNECTION STATE ---
    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)
    val gpsData by app.gpsManager.activeGpsFlow.collectAsState(initial = null)

    // --- MAP STATE ---
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    // --- SESSION STATE ---
    var isSessionActive by rememberSaveable { mutableStateOf(false) }
    var sessionID by rememberSaveable { mutableLongStateOf(-1) }
    val dataPoints = remember { mutableStateListOf<Entry>() }
    val dataBuffer = remember { mutableListOf<RawGPSData>() }
    var chartIndex by remember { mutableFloatStateOf(0f) }

    // --- AUTO-FOCUS & MARKER UPDATES ---
    LaunchedEffect(gpsData) {
        val data = gpsData ?: return@LaunchedEffect
        val position = LatLng(data.latitude, data.longitude)

        mapLibreMap?.let { map ->
            // Update Camera to follow car
            map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(position, 15.0))

            // Optional: Draw a line of where you've been during the drag
            if (isSessionActive) {
                // You can add a Polyline here if needed
            }
        }

        // Handle Session Logic
        if (isSessionActive) {
            synchronized(dataBuffer) { dataBuffer.add(data.copy(sessionid = sessionID)) }
            dataPoints.add(Entry(chartIndex++, data.speed ?: 0f))
            if (dataPoints.size > 100) dataPoints.removeAt(0)
        }
    }

    Column(Modifier.fillMaxSize().background(TrackProColors.BgDeep)) {

        // 1. TOP STATUS BAR
        Box(Modifier.fillMaxWidth().background(TrackProColors.AccentRed).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("● DRAG MODE", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(if (isConnected) "GPS FIXED" else "SEARCHING...", color = Color.Black, fontSize = 12.sp)
            }
        }

        // 2. SPEED DISPLAY (Visual Hero)
        Row(Modifier.fillMaxWidth().padding(20.dp), Arrangement.SpaceBetween) {
            Column {
                Text("SPEED KM/H", color = TrackProColors.TextMuted, fontSize = 12.sp)
                Text(
                    "${gpsData?.speed?.toInt() ?: 0}",
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isSessionActive) TrackProColors.AccentRed else TrackProColors.TextPrimary
                )
            }

            // G-Force or Ready Indicator
            if (isSessionActive) {
                Text("LIVE", color = TrackProColors.AccentRed, fontWeight = FontWeight.Bold)
            }
        }

        // 3. THE MAP (This fills the middle space)
        Box(
            modifier = Modifier
                .weight(1f) // THIS PREVENTS THE BLACK SCREEN / ZERO HEIGHT
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E2530), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    org.maplibre.android.MapLibre.getInstance(ctx)
                    MapView(ctx).apply {
                        getMapAsync { map ->
                            mapLibreMap = map
                            map.setStyle("https://tiles.openfreemap.org/styles/dark")
                            map.uiSettings.isAttributionEnabled = false
                            map.uiSettings.isLogoEnabled = false
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Static "Car" Overlay in center of map
            Box(
                Modifier.align(Alignment.Center).size(12.dp)
                    .background(TrackProColors.AccentRed, CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
        }

        // 4. CHART (Small preview at bottom)
        Box(Modifier.height(100.dp).fillMaxWidth().padding(top = 8.dp)) {
            AndroidView(factory = { ctx ->
                LineChart(ctx).apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    xAxis.isEnabled = false
                    axisLeft.textColor = android.graphics.Color.WHITE
                    axisRight.isEnabled = false
                    setTouchEnabled(false)
                }
            }, update = { chart ->
                val dataSet = LineDataSet(dataPoints.toList(), "Speed").apply {
                    color = android.graphics.Color.RED
                    setDrawCircles(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                chart.data = LineData(dataSet)
                chart.invalidate()
            })
        }

        // 5. CONTROLS
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start/Stop Button
            Box(
                Modifier.weight(1f).height(50.dp)
                    .background(if (isSessionActive) Color(0xFF330000) else TrackProColors.AccentRed, RoundedCornerShape(8.dp))
                    .clickable {
                        scope.launch {
                            if (!isSessionActive) {
                                sessionID = sessionManager.startSession("", null)
                                isSessionActive = true
                                dataPoints.clear()
                            } else {
                                isSessionActive = false
                                sessionManager.endSession()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isSessionActive) "STOP SESSION" else "START DRAG",
                    color = if (isSessionActive) TrackProColors.AccentRed else Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
