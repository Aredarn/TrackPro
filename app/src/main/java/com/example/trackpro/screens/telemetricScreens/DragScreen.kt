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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.managerClasses.calculationClasses.DragMetrics
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.core.graphics.toColorInt

data class DragMetricDisplay(
    val label: String,
    val value: String,
    val unit: String,
    val achieved: Boolean = false
)

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

    // --- SESSION STATE ---
    var isSessionActive by rememberSaveable { mutableStateOf(false) }
    var sessionID by rememberSaveable { mutableLongStateOf(-1) }
    val speedDataPoints = remember { mutableStateListOf<Entry>() }
    val dataBuffer = remember { mutableListOf<com.example.trackpro.dataClasses.RawGPSData>() }
    var chartIndex by remember { mutableFloatStateOf(0f) }

    // --- DRAG CALCULATOR ---
    val dragCalculator = remember { DragTimeCalculation(session = null, database = database) }
    var currentMetrics by remember { mutableStateOf(DragMetrics()) }

    // --- TELEMETRY STATE ---
    var sessionStartTime by remember { mutableLongStateOf(0L) }
    var elapsedTime by remember { mutableStateOf("00:00.00") }

    // --- CLEANUP ON DISPOSE ---
    DisposableEffect(Unit) {
        onDispose {
            if (isSessionActive) {
                scope.launch(Dispatchers.IO) {
                    sessionManager.endSession()
                    dataBuffer.forEach { gps ->
                        database.rawGPSDataDao().insert(gps)
                    }
                }
            }
        }
    }

    // --- GPS UPDATE & METRIC CALCULATION ---
    LaunchedEffect(gpsData) {
        val data = gpsData ?: return@LaunchedEffect

        if (isSessionActive) {
            val currentSpeed = data.speed ?: 0f
            val currentTime = System.currentTimeMillis()

            // Update elapsed time
            val elapsed = currentTime - sessionStartTime
            elapsedTime = formatElapsedTime(elapsed)

            // Process GPS through drag calculator
            currentMetrics = dragCalculator.processRealtimeGPS(data, currentTime)

            // Buffer data
            synchronized(dataBuffer) { dataBuffer.add(data.copy(sessionid = sessionID)) }
            speedDataPoints.add(Entry(chartIndex++, currentSpeed))
            if (speedDataPoints.size > 500) speedDataPoints.removeAt(0)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            // 2. VEHICLE SELECTOR
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
                // Show selected vehicle during session
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(
                            TrackProColors.BgCard.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
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

            // 3. CURRENT SPEED (BIG)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
                    .border(
                        2.dp,
                        if (isSessionActive) TrackProColors.AccentRed.copy(alpha = 0.5f)
                        else TrackProColors.SectorLine,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "CURRENT SPEED",
                        color = TrackProColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "${gpsData?.speed?.toInt() ?: 0}",
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isSessionActive) TrackProColors.AccentRed
                            else TrackProColors.TextPrimary,
                            letterSpacing = (-2).sp
                        )
                        Text(
                            "KM/H",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TrackProColors.TextMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    if (isSessionActive) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "MAX: ${currentMetrics.maxSpeed.toInt()} KM/H · DIST: ${String.format("%.1f", currentMetrics.totalDistance)}M",
                            color = TrackProColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4. DRAG METRICS GRID
            val metrics = listOf(
                DragMetricDisplay(
                    "0-60",
                    currentMetrics.time0to60?.let { formatTime(it) } ?: "--.-",
                    "SEC",
                    currentMetrics.time0to60 != null
                ),
                DragMetricDisplay(
                    "0-100",
                    currentMetrics.time0to100?.let { formatTime(it) } ?: "--.-",
                    "SEC",
                    currentMetrics.time0to100 != null
                ),
                DragMetricDisplay(
                    "0-160",
                    currentMetrics.time0to160?.let { formatTime(it) } ?: "--.-",
                    "SEC",
                    currentMetrics.time0to160 != null
                ),
                DragMetricDisplay(
                    "0-200",
                    currentMetrics.time0to200?.let { formatTime(it) } ?: "--.-",
                    "SEC",
                    currentMetrics.time0to200 != null
                ),
                DragMetricDisplay(
                    "50-150",
                    currentMetrics.time50to150?.let { formatTime(it) } ?: "--.-",
                    "SEC",
                    currentMetrics.time50to150 != null
                ),
                DragMetricDisplay(
                    "100-200",
                    currentMetrics.time100to200?.let { formatTime(it) } ?: "--.-",
                    "SEC",
                    currentMetrics.time100to200 != null
                ),
                DragMetricDisplay(
                    "1/4 MI",
                    currentMetrics.quarterMileTime?.let { formatTime(it) } ?: "--.-",
                    "SEC",
                    currentMetrics.quarterMileTime != null
                ),
                DragMetricDisplay(
                    "1/4 TRAP",
                    currentMetrics.quarterMileSpeed?.toInt()?.toString() ?: "--",
                    "KM/H",
                    currentMetrics.quarterMileSpeed != null
                ),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "PERFORMANCE METRICS",
                    color = TrackProColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                metrics.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { metric ->
                            DragMetricCard(metric, Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 5. SPEED CHART (LARGE)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp)
                    .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
                    .border(1.dp, TrackProColors.SectorLine, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        "SPEED PROFILE",
                        color = TrackProColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    AndroidView(
                        factory = { ctx ->
                            LineChart(ctx).apply {
                                description.isEnabled = false
                                legend.isEnabled = false
                                xAxis.position = XAxis.XAxisPosition.BOTTOM
                                xAxis.setDrawGridLines(true)
                                xAxis.gridColor = "#1E2530".toColorInt()
                                xAxis.textColor = "#6B7280".toColorInt()
                                axisLeft.textColor = "#F0F2F5".toColorInt()
                                axisLeft.setDrawGridLines(true)
                                axisLeft.gridColor = "#1E2530".toColorInt()
                                axisLeft.axisMinimum = 0f
                                axisRight.isEnabled = false
                                setTouchEnabled(true)
                                setPinchZoom(true)
                                setDrawBorders(false)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { chart ->
                            if (speedDataPoints.isNotEmpty()) {
                                val dataSet = LineDataSet(speedDataPoints.toList(), "Speed").apply {
                                    color = "#E8001C".toColorInt()
                                    lineWidth = 3f
                                    setDrawCircles(false)
                                    setDrawValues(false)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    setDrawFilled(true)
                                    fillColor = "#E8001C".toColorInt()
                                    fillAlpha = 40
                                }
                                chart.data = LineData(dataSet)
                                chart.notifyDataSetChanged()
                                chart.invalidate()
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 6. CONTROLS (Fixed at bottom)
        Row(
            Modifier
                .fillMaxWidth()
                .background(TrackProColors.BgDeep)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                                        trackId = null
                                    )
                                }
                                isSessionActive = true
                                sessionStartTime = System.currentTimeMillis()

                                // Reset calculator
                                dragCalculator.resetRealtimeTracking()
                                currentMetrics = DragMetrics()
                                speedDataPoints.clear()
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
fun DragMetricCard(
    metric: DragMetricDisplay,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (metric.achieved) TrackProColors.AccentRed.copy(alpha = 0.1f)
                else TrackProColors.BgCard,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.5.dp,
                if (metric.achieved) TrackProColors.AccentRed.copy(alpha = 0.6f)
                else TrackProColors.SectorLine,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    metric.label,
                    color = if (metric.achieved) TrackProColors.AccentRed
                    else TrackProColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                if (metric.achieved) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(TrackProColors.AccentRed, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    metric.value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (metric.achieved) TrackProColors.TextPrimary
                    else TrackProColors.TextMuted.copy(alpha = 0.5f),
                    letterSpacing = (-0.5).sp
                )
                Text(
                    metric.unit,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TrackProColors.TextMuted,
                    modifier = Modifier.padding(bottom = 3.dp)
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

private fun formatTime(seconds: Double): String {
    return String.format("%.2f", seconds)
}