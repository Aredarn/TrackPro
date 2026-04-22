package com.example.trackpro.screens.telemetricScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.theme.TrackProColors
import com.example.trackpro.viewModels.VehicleFULLViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.core.graphics.toColorInt

@Composable
fun DragRaceScreen(
    database: ESPDatabase,
    sessionManager: SessionManager,
    vehicleViewModel: VehicleFULLViewModel
) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val scope = rememberCoroutineScope()

    // --- GPS & CONNECTION STATE ---
    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)
    val gpsData by app.gpsManager.activeGpsFlow.collectAsState(initial = null)

    // --- VEHICLE SELECTION ---
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    var selectedVehicle by remember { mutableStateOf<VehicleInformationData?>(null) }
    var showVehicleDropdown by remember { mutableStateOf(false) }

    // --- MAP STATE ---
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    // --- SESSION STATE ---
    var isSessionActive by rememberSaveable { mutableStateOf(false) }
    var sessionID by rememberSaveable { mutableLongStateOf(-1) }
    val speedDataPoints = remember { mutableStateListOf<Entry>() }
    val dataBuffer = remember { mutableListOf<RawGPSData>() }
    var chartIndex by remember { mutableFloatStateOf(0f) }

    // --- TELEMETRY STATE ---
    var maxSpeed by remember { mutableFloatStateOf(0f) }
    var currentAltitude by remember { mutableFloatStateOf(0f) }
    var sessionStartTime by remember { mutableLongStateOf(0L) }
    var elapsedTime by remember { mutableStateOf("00:00.00") }

    // --- AUTO-FOCUS & MARKER UPDATES ---
    LaunchedEffect(gpsData) {
        val data = gpsData ?: return@LaunchedEffect
        val position = LatLng(data.latitude, data.longitude)

        mapLibreMap?.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(position, 16.0)
        )

        // Handle Session Logic
        if (isSessionActive) {
            val currentSpeed = data.speed ?: 0f

            // Update telemetry
            if (currentSpeed > maxSpeed) maxSpeed = currentSpeed
            currentAltitude = (data.altitude?.toFloat() ?: 0f)

            // Update elapsed time
            val elapsed = System.currentTimeMillis() - sessionStartTime
            elapsedTime = formatElapsedTime(elapsed)

            synchronized(dataBuffer) { dataBuffer.add(data.copy(sessionid = sessionID)) }
            speedDataPoints.add(Entry(chartIndex++, currentSpeed))
            if (speedDataPoints.size > 200) speedDataPoints.removeAt(0)
        }
    }

    Column(Modifier.fillMaxSize().background(TrackProColors.BgDeep)) {

        // 1. TOP STATUS BAR
        Box(
            Modifier
                .fillMaxWidth()
                .background(TrackProColors.AccentRed)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceBetween,
                Alignment.CenterVertically
            ) {
                Text(
                    "● DRAG MODE",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSessionActive) {
                        Text(
                            "⏱ $elapsedTime",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        if (isConnected) "GPS LOCKED" else "GPS SEARCHING",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. VEHICLE SELECTOR (Only show when not in session)
        if (!isSessionActive) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
                    .border(1.dp, TrackProColors.SectorLine, RoundedCornerShape(12.dp))
                    .clickable { showVehicleDropdown = true }
                    .padding(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "VEHICLE",
                            color = TrackProColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            selectedVehicle?.let { "${it.manufacturer} ${it.model}" }
                                ?: "Select Vehicle",
                            color = selectedVehicle?.let { TrackProColors.TextPrimary }
                                ?: TrackProColors.TextMuted.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        selectedVehicle?.let {
                            Text(
                                "${it.horsepower}hp · ${it.drivetrain} · ${it.year}",
                                color = TrackProColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TrackProColors.TextMuted
                    )
                }

                DropdownMenu(
                    expanded = showVehicleDropdown,
                    onDismissRequest = { showVehicleDropdown = false },
                    modifier = Modifier.background(TrackProColors.BgElevated)
                ) {
                    vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        "${vehicle.manufacturer} ${vehicle.model}",
                                        color = TrackProColors.TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${vehicle.horsepower}hp · ${vehicle.year}",
                                        color = TrackProColors.TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            },
                            onClick = {
                                selectedVehicle = vehicle
                                showVehicleDropdown = false
                            }
                        )
                    }
                }
            }
        } else {
            // Show selected vehicle info during session
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(TrackProColors.BgCard.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                selectedVehicle?.let {
                    Text(
                        "${it.manufacturer} ${it.model} · ${it.horsepower}hp",
                        color = TrackProColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. TELEMETRY GRID (Enhanced)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Current Speed
            TelemetryCard(
                label = "SPEED",
                value = "${gpsData?.speed?.toInt() ?: 0}",
                unit = "KM/H",
                modifier = Modifier.weight(1f),
                highlight = isSessionActive
            )

            // Max Speed
            TelemetryCard(
                label = "MAX",
                value = "${maxSpeed.toInt()}",
                unit = "KM/H",
                modifier = Modifier.weight(1f)
            )

            // Altitude
            TelemetryCard(
                label = "ALT",
                value = "${currentAltitude.toInt()}",
                unit = "M",
                modifier = Modifier.weight(1f)
            )
        }

        // 4. THE MAP
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
                .border(1.dp, TrackProColors.SectorLine, RoundedCornerShape(12.dp))
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

            // Car marker
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .background(TrackProColors.AccentRed, CircleShape)
                    .border(3.dp, Color.White, CircleShape)
            )
        }

        // 5. SPEED CHART
        Box(
            Modifier
                .height(120.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
                .border(1.dp, TrackProColors.SectorLine, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    LineChart(ctx).apply {
                        description.isEnabled = false
                        legend.isEnabled = false
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.setDrawGridLines(false)
                        xAxis.textColor = "#6B7280".toColorInt()
                        axisLeft.textColor = "#F0F2F5".toColorInt()
                        axisLeft.setDrawGridLines(true)
                        axisLeft.gridColor = "#1E2530".toColorInt()
                        axisRight.isEnabled = false
                        setTouchEnabled(false)
                        setDrawBorders(false)
                    }
                },
                update = { chart ->
                    if (speedDataPoints.isNotEmpty()) {
                        val dataSet = LineDataSet(speedDataPoints.toList(), "Speed").apply {
                            color = "#E8001C".toColorInt()
                            lineWidth = 2.5f
                            setDrawCircles(false)
                            setDrawValues(false)
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                            setDrawFilled(true)
                            fillColor = "#E8001C".toColorInt()
                            fillAlpha = 30
                        }
                        chart.data = LineData(dataSet)
                        chart.invalidate()
                    }
                }
            )
        }

        // 6. CONTROLS
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start/Stop Button
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(
                        when {
                            isSessionActive -> TrackProColors.BgElevated
                            selectedVehicle == null -> TrackProColors.BgElevated.copy(alpha = 0.3f)
                            else -> TrackProColors.AccentRed
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        2.dp,
                        when {
                            isSessionActive -> TrackProColors.AccentRed
                            selectedVehicle == null -> TrackProColors.SectorLine
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(enabled = selectedVehicle != null) {
                        scope.launch {
                            if (!isSessionActive && selectedVehicle != null) {
                                // Start session
                                val eventType =
                                    "Drag - ${
                                        LocalDateTime
                                            .now()
                                            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
                                    }"
                                sessionID = withContext(Dispatchers.IO) {
                                    sessionManager.startSession(
                                        eventType = eventType,
                                        vehicleId = selectedVehicle!!.vehicleId,
                                        trackId = null // Drag sessions have no track
                                    )
                                }
                                isSessionActive = true
                                sessionStartTime = System.currentTimeMillis()
                                speedDataPoints.clear()
                                maxSpeed = 0f
                                chartIndex = 0f
                            } else if (isSessionActive) {
                                // End session
                                isSessionActive = false
                                withContext(Dispatchers.IO) {
                                    sessionManager.endSession()

                                    // Save buffered GPS data
                                    dataBuffer.forEach { gps ->
                                        database.rawGPSDataDao().insert(gps)
                                    }
                                    dataBuffer.clear()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        selectedVehicle == null -> "SELECT VEHICLE FIRST"
                        isSessionActive -> "STOP SESSION"
                        else -> "START DRAG"
                    },
                    color = when {
                        isSessionActive -> TrackProColors.AccentRed
                        selectedVehicle == null -> TrackProColors.TextMuted
                        else -> Color.Black
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun TelemetryCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Box(
        modifier = modifier
            .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (highlight) TrackProColors.AccentRed.copy(alpha = 0.5f) else TrackProColors.SectorLine,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Text(
                label,
                color = TrackProColors.TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = if (highlight) TrackProColors.AccentRed else TrackProColors.TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    unit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TrackProColors.TextMuted,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

private fun formatElapsedTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 60000) % 60
    val centiseconds = (millis % 1000) / 10
    return String.format("%02d:%02d.%02d", minutes, seconds, centiseconds)
}