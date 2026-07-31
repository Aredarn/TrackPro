package com.example.trackpro.screens.telemetricScreens

import androidx.compose.foundation.background
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
import com.example.trackpro.managerClasses.utilities.UnitFormatter
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
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppCard
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType

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
    val useMetric by app.useMetricUnits.collectAsState()

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
                    database.rawGPSDataDao().insertAll(dataBuffer)
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
            .background(TrackProTheme.colors.bgDeep)
    ) {

        // 1. TOP STATUS BAR
        AppTopBar(
            title = "Drag Mode",
            accent = TrackProTheme.colors.accentCyan,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (isSessionActive) {
                        Text(
                            elapsedTime,
                            style = TrackProType.statValue.copy(fontSize = 13.sp),
                            color = TrackProTheme.colors.accentCyan
                        )
                    }
                    Text(
                        if (isConnected) "GPS Locked" else "GPS Searching",
                        style = TrackProType.label,
                        color = if (isConnected) TrackProTheme.colors.deltaGood else TrackProTheme.colors.textFaint
                    )
                }
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            // 2. VEHICLE SELECTOR
            if (!isSessionActive) {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md)
                        .clickable { showVehicleDropdown = true }
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Vehicle", style = TrackProType.label, color = TrackProTheme.colors.textMuted)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                selectedVehicle?.let { "${it.manufacturer} ${it.model}" }
                                    ?: "Select Vehicle",
                                style = TrackProType.titleMedium,
                                color = selectedVehicle?.let { TrackProTheme.colors.textPrimary }
                                    ?: TrackProTheme.colors.textMuted.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            selectedVehicle?.let {
                                Text(
                                    "${it.horsepower}hp · ${it.drivetrain} · ${it.year}",
                                    style = TrackProType.body.copy(fontSize = 11.sp),
                                    color = TrackProTheme.colors.textMuted
                                )
                            }
                        }
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TrackProTheme.colors.textMuted
                        )
                    }

                    DropdownMenu(
                        expanded = showVehicleDropdown,
                        onDismissRequest = { showVehicleDropdown = false },
                        modifier = Modifier.background(TrackProTheme.colors.bgElevated)
                    ) {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "${vehicle.manufacturer} ${vehicle.model}",
                                            style = TrackProType.body,
                                            color = TrackProTheme.colors.textPrimary
                                        )
                                        Text(
                                            "${vehicle.horsepower}hp · ${vehicle.year}",
                                            style = TrackProType.body.copy(fontSize = 12.sp),
                                            color = TrackProTheme.colors.textMuted
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
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .background(
                            TrackProTheme.colors.bgCard.copy(alpha = 0.5f),
                            TrackProShapes.control
                        )
                        .padding(Spacing.sm)
                ) {
                    selectedVehicle?.let {
                        Text(
                            "${it.manufacturer} ${it.model} · ${it.horsepower}hp",
                            style = TrackProType.body,
                            color = TrackProTheme.colors.textPrimary
                        )
                    }
                }
            }

            // 3. CURRENT SPEED (BIG)
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                padding = Spacing.lg,
                borderColor = if (isSessionActive) TrackProTheme.colors.accentCyan.copy(alpha = 0.5f)
                    else TrackProTheme.colors.sectorLine
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Current Speed",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.textMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            gpsData?.speed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "0",
                            style = TrackProType.displayNumeric,
                            color = if (isSessionActive) TrackProTheme.colors.accentCyan
                            else TrackProTheme.colors.textPrimary
                        )
                        Text(
                            UnitFormatter.speedUnitLabel(useMetric),
                            style = TrackProType.body.copy(fontSize = 15.sp),
                            color = TrackProTheme.colors.textMuted,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    if (isSessionActive) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "MAX: ${UnitFormatter.formatSpeed(currentMetrics.maxSpeed, useMetric)} ${UnitFormatter.speedUnitLabel(useMetric)} · DIST: ${UnitFormatter.formatDistance(currentMetrics.totalDistance.toDouble(), useMetric)}",
                            style = TrackProType.body.copy(fontSize = 12.sp),
                            color = TrackProTheme.colors.textMuted
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
                    currentMetrics.quarterMileSpeed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "--",
                    UnitFormatter.speedUnitLabel(useMetric),
                    currentMetrics.quarterMileSpeed != null
                ),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SectionLabel("Performance Metrics")

                metrics.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        row.forEach { metric ->
                            DragMetricCard(metric, Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // 5. SPEED CHART (LARGE)
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = Spacing.md)
            ) {
                Column {
                    SectionLabel("Speed Profile")
                    Spacer(Modifier.height(8.dp))

                    AndroidView(
                        factory = { ctx ->
                            LineChart(ctx).apply {
                                description.isEnabled = false
                                legend.isEnabled = false
                                xAxis.position = XAxis.XAxisPosition.BOTTOM
                                xAxis.setDrawGridLines(true)
                                xAxis.gridColor = DataVizColors.chartGrid.toColorInt()
                                xAxis.textColor = DataVizColors.chartAxisText.toColorInt()
                                axisLeft.textColor = DataVizColors.chartAxisText.toColorInt()
                                axisLeft.setDrawGridLines(true)
                                axisLeft.gridColor = DataVizColors.chartGrid.toColorInt()
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
                                    color = DataVizColors.chartLine.toColorInt()
                                    lineWidth = 3f
                                    setDrawCircles(false)
                                    setDrawValues(false)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    setDrawFilled(true)
                                    fillColor = DataVizColors.chartLine.toColorInt()
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
                .background(TrackProTheme.colors.bgDeep)
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            PrimaryButton(
                text = when {
                    selectedVehicle == null -> "Select Vehicle First"
                    isSessionActive -> "Stop Session"
                    else -> "Start Drag"
                },
                onClick = {
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
                                database.rawGPSDataDao().insertAll(dataBuffer)
                                dataBuffer.clear()
                            }
                        }
                    }
                },
                enabled = selectedVehicle != null,
                accent = if (isSessionActive) TrackProTheme.colors.bgElevated else TrackProTheme.colors.accentCyan,
                contentColor = if (isSessionActive) TrackProTheme.colors.accentCyan else null,
                modifier = Modifier.weight(1f).height(56.dp)
            )
        }
    }
}

@Composable
fun DragMetricCard(
    metric: DragMetricDisplay,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        borderColor = if (metric.achieved) TrackProTheme.colors.accentCyan.copy(alpha = 0.6f) else TrackProTheme.colors.sectorLine
    ) {
        Column(
            modifier = Modifier.background(
                if (metric.achieved) TrackProTheme.colors.accentCyan.copy(alpha = 0.08f) else Color.Transparent
            )
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    metric.label.uppercase(),
                    style = TrackProType.label,
                    color = if (metric.achieved) TrackProTheme.colors.accentCyan else TrackProTheme.colors.textFaint
                )
                if (metric.achieved) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(TrackProTheme.colors.accentCyan, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    metric.value,
                    style = TrackProType.statValue.copy(fontSize = 19.sp),
                    color = if (metric.achieved) TrackProTheme.colors.textPrimary
                    else TrackProTheme.colors.textMuted.copy(alpha = 0.5f)
                )
                Text(
                    metric.unit,
                    style = TrackProType.body.copy(fontSize = 10.sp),
                    color = TrackProTheme.colors.textMuted,
                    modifier = Modifier.padding(bottom = 2.dp)
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