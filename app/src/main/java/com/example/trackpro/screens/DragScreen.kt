package com.example.trackpro.screens

import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.extrasForUI.DropdownMenuFieldMulti
import com.example.trackpro.extrasForUI.LatLonOffset
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.theme.TrackProColors
import com.example.trackpro.viewModels.VehicleViewModel
import com.example.trackpro.viewModels.VehicleViewModelFactory
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun DragRaceScreen(database: ESPDatabase, sessionManager: SessionManager) {

    val app = LocalContext.current.applicationContext as TrackProApp
    val tcpClient = app.espTcpClient

    // --- STATE ---
    var isSessionActive by rememberSaveable { mutableStateOf(false) }
    var sessionID by rememberSaveable { mutableLongStateOf(-1) }

    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)
    val gpsData by app.gpsManager.activeGpsFlow.collectAsState(initial = null)


    var lastTimestamp by remember { mutableStateOf<Long?>(null) }
    var isReady by remember { mutableStateOf(false) }

    val dataPoints = remember { mutableStateListOf<Entry>() }
    val dataBuffer = remember { mutableListOf<RawGPSData>() }
    val trackPath = remember { mutableStateListOf<LatLonOffset>() }

    var driverPosition by remember { mutableStateOf(LatLonOffset(0.0, 0.0)) }
    var chartIndex by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- VEHICLES ---
    val viewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))
    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    var selectedVehicleId by rememberSaveable { mutableIntStateOf(-1) }
    val selectedVehicle = vehicles.find { it.vehicleId.toInt() == selectedVehicleId }

    // --- GPS PROCESSING ---
    LaunchedEffect(gpsData) {
        val data = gpsData ?: return@LaunchedEffect

        driverPosition = LatLonOffset(data.latitude, data.longitude)

        if (isSessionActive) {
            if (lastTimestamp != data.timestamp) {

                trackPath.add(LatLonOffset(data.latitude, data.longitude))

                synchronized(dataBuffer) {
                    dataBuffer.add(data.copy(sessionid = sessionID))
                }

                val speed = data.speed ?: return@LaunchedEffect
                dataPoints.add(Entry(chartIndex++, speed))

                if (dataPoints.size > 800) dataPoints.removeAt(0)

                isReady = speed <= 1.5f
            }
        } else {
            isReady = (data.speed ?: 0f) <= 1.5f
        }
    }

    // --- BATCH INSERT ---
    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            tcpClient.connect()
            while (isActive) {
                delay(2000)

                val toInsert = synchronized(dataBuffer) {
                    val copy = dataBuffer.toList()
                    dataBuffer.clear()
                    copy
                }

                if (toInsert.isNotEmpty()) {
                    try {
                        database.rawGPSDataDao().insertAll(toInsert)
                    } catch (e: Exception) {
                        Log.e("DragScreen", "DB error: ${e.message}")
                    }
                }
            }
        }
    }

    // --- UI ---
    Box(
        Modifier.fillMaxSize().background(TrackProColors.BgDeep)
    ) {
        Column {

            // TOP BAR
            Box(
                Modifier.fillMaxWidth().background(TrackProColors.AccentRed).padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("● DRAG MODE", color = Color.Black)
                    Text(
                        if (isConnected) "LIVE" else "NO SIGNAL",
                        color = Color.Black
                    )
                }
            }

            // SPEED
            Box(Modifier.fillMaxWidth().padding(20.dp)) {
                Column {
                    Text("SPEED", color = TrackProColors.TextMuted)

                    Text(
                        "${gpsData?.speed?.toInt() ?: 0}",
                        fontSize = 72.sp,
                        color = if (isSessionActive) TrackProColors.AccentRed else TrackProColors.TextPrimary
                    )

                    if (isSessionActive) {
                        Text(
                            if (isReady) "READY" else "WAIT",
                            color = if (isReady) TrackProColors.DeltaGood else TrackProColors.AccentAmber
                        )
                    }
                }
            }

            HorizontalDivider()

            // CHART
            Box(Modifier.height(160.dp)) {
                AndroidView(factory = { ctx ->
                    LineChart(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        axisRight.isEnabled = false
                        description.isEnabled = false
                    }
                }, update = { chart ->
                    val dataSet = LineDataSet(dataPoints.toList(), "").apply {
                        setDrawCircles(false)
                        setDrawValues(false)
                    }
                    chart.data = LineData(dataSet)
                    chart.invalidate()
                })
            }


            HorizontalDivider()

            // CONTROLS
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                if (!isSessionActive) {
                    DropdownMenuFieldMulti(
                        label = "Select car",
                        options = vehicles,
                        selectedOption = selectedVehicle?.manufacturerAndModel ?: "",
                        onOptionSelected = { selectedVehicleId = it.toInt() }
                    )
                } else {
                    Text("SESSION ACTIVE", color = TrackProColors.AccentRed)
                }

                Box(
                    Modifier
                        .background(TrackProColors.AccentRed, RoundedCornerShape(6.dp))
                        .clickable {
                            scope.launch {
                                if (!isSessionActive) {
                                    if (selectedVehicleId == -1) {
                                        Toast.makeText(context, "Select car", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    sessionID = sessionManager.startSession(eventType = "" , vehicleId = selectedVehicleId.toLong())
                                    trackPath.clear()
                                    isSessionActive = true
                                } else {
                                    isSessionActive = false
                                    sessionManager.endSession()
                                }
                            }
                        }
                        .padding(12.dp)
                ) {
                    Text(if (isSessionActive) "STOP" else "START")
                }
            }
        }
    }


}
