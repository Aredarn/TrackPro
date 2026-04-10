package com.example.trackpro.screens

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.trackpro.calculationClasses.DragTimeCalculation
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.extrasForUI.DropdownMenuFieldMulti
import com.example.trackpro.extrasForUI.LatLonOffset
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.ESPTcpClient
import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.managerClasses.SessionManager
import com.example.trackpro.managerClasses.toDataClass
import com.example.trackpro.viewModels.VehicleViewModel
import com.example.trackpro.viewModels.VehicleViewModelFactory
import com.example.trackpro.theme.TrackProColors
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt
import com.example.trackpro.TrackProApp


class DragScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DragRaceScreen(
                database = (applicationContext as TrackProApp).database,
            )
        }
    }
}

@Composable
fun DragRaceScreen(
    database: ESPDatabase,
) {
    var isSessionActive by rememberSaveable { mutableStateOf(false) }
    var sessionID by rememberSaveable { mutableLongStateOf(-1) }
    val isConnected = rememberSaveable { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    var lastTimestamp: Long? by rememberSaveable { mutableStateOf(null) }
    var isReady by rememberSaveable { mutableStateOf(false) }
    var dragTime: Double? by rememberSaveable { mutableStateOf(null) }
    var quarterMileTime: Double? by rememberSaveable { mutableStateOf(null) }
    val dataPoints = remember { mutableStateListOf<Entry>() }
    val context = LocalContext.current
    val (ip, port) = rememberSaveable { JsonReader.loadConfig(context) }
    val coroutineScope = rememberCoroutineScope()
    val dataBuffer = remember { mutableListOf<RawGPSData>() }
    var insertJob: Job? by remember { mutableStateOf(null) }
    var i = 0f
    val viewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))
    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    val loadingState by viewModel.loadingState.collectAsState()
    val selectedVehicle by rememberSaveable { mutableStateOf("") }
    var selectedVehicleId by rememberSaveable { mutableIntStateOf(-1) }

    // Track path for map
    val trackPath = remember { mutableStateListOf<LatLonOffset>() }
    var driverPosition by remember { mutableStateOf(LatLonOffset(0.0, 0.0)) }

    // Design tokens (same as TimeAttack)

    fun startBatchInsert() {
        insertJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                val dataToInsert: List<RawGPSData> = synchronized(dataBuffer) {
                    if (dataBuffer.isNotEmpty()) dataBuffer.toList().also { dataBuffer.clear() }
                    else emptyList()
                }
                if (dataToInsert.isNotEmpty()) {
                    try {
                        database.rawGPSDataDao().insertAll(dataToInsert)
                        val list = dataPoints.takeLast(5)
                        isReady = list.all { it.y <= 2 }
                    } catch (e: Exception) {
                        Log.e("Database", "Batch insert failed: ${e.message}")
                    }
                }
                withContext(Dispatchers.Main) {
                    synchronized(dataPoints) {
                        while (dataPoints.size > 1000) dataPoints.removeAt(0)
                    }
                }
            }
        }
    }

    suspend fun stopBatchInsert() {
        endSession(database)
        insertJob?.cancel()
        dataBuffer.clear()
    }

    LaunchedEffect(Unit) {
        try {
            viewModel.fetchVehicles()
            startBatchInsert()
            espTcpClient = ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data ->
                    val parsed = data.toDataClass()
                    gpsData.value = parsed

                    // Always update map position
                    parsed.let { gps ->
                        driverPosition = LatLonOffset(gps.latitude, gps.longitude)
                        if (isSessionActive) {
                            trackPath.add(LatLonOffset(gps.latitude, gps.longitude))
                        }
                    }

                    if (isSessionActive && isConnected.value) {
                        val derivedData = gpsData.value?.let {
                            RawGPSData(
                                sessionid = sessionID,
                                latitude = it.latitude,
                                longitude = it.longitude,
                                altitude = it.altitude,
                                timestamp = it.timestamp,
                                speed = it.speed,
                                fixQuality = it.fixQuality
                            )
                        }
                        derivedData?.let { d ->
                            if (lastTimestamp == null || d.timestamp != lastTimestamp) {
                                dataBuffer.add(d)
                                coroutineScope.launch(Dispatchers.Main) {
                                    d.speed?.let { dataPoints.add(Entry(i++, it)) }
                                }
                            }
                        }
                    }
                },
                onConnectionStatusChanged = { isConnected.value = it }
            )
            espTcpClient?.connect()
        } catch (e: Exception) {
            Log.e("LaunchedEffect", "Error: ${e.message}")
        } finally {
            espTcpClient?.disconnect()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch(Dispatchers.IO) {
                espTcpClient?.disconnect()
                stopBatchInsert()
            }
        }
    }

    // ── UI ────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top mode bar ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.AccentRed)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "● DRAG MODE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = if (isConnected.value) "LIVE" else "NO SIGNAL",
                        color = if (isConnected.value) Color.Black else Color(0xFF330000),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                }
            }

            // ── Speed + readiness ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.BgCard)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Column {
                    Text(
                        text = "SPEED",
                        color = TrackProColors.TextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${gpsData.value?.speed?.toInt() ?: 0}",
                            color = if (isSessionActive) TrackProColors.AccentRed else TrackProColors.TextPrimary,
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-2).sp,
                            lineHeight = 80.sp
                        )
                        Text(
                            text = " km/h",
                            color = TrackProColors.TextMuted,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Ready / Go indicator
                    if (isSessionActive) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isReady) TrackProColors.DeltaGood.copy(alpha = 0.15f)
                                    else TrackProColors.AccentAmber.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isReady) "⬛ READY — LAUNCH!" else "◌ WAITING FOR STANDSTILL",
                                color = if (isReady) TrackProColors.DeltaGood else TrackProColors.AccentAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // GPS fix indicator top-right
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "GPS",
                        color = TrackProColors.TextMuted,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if ((gpsData.value?.fixQuality ?: 0) > 0) "✓ FIX" else "✗ NO FIX",
                        color = if ((gpsData.value?.fixQuality ?: 0) > 0) TrackProColors.DeltaGood else TrackProColors.AccentRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ALT ${gpsData.value?.altitude?.toInt() ?: 0}m",
                        color = TrackProColors.TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

            // ── Results row ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.BgElevated)
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ResultCell(
                    label = "0–100 km/h",
                    value = dragTime?.let { String.format("%.3fs", it) } ?: "—",
                    valueColor = if (dragTime != null) TrackProColors.DeltaGood else TrackProColors.TextMuted
                )
                Box(modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(TrackProColors.SectorLine))
                ResultCell(
                    label = "¼ MILE",
                    value = quarterMileTime?.let { String.format("%.3fs", it) } ?: "—",
                    valueColor = if (quarterMileTime != null) TrackProColors.DeltaGood else TrackProColors.TextMuted
                )
                Box(modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(TrackProColors.SectorLine))
                ResultCell(
                    label = "DATA PTS",
                    value = "${dataPoints.size}",
                    valueColor = TrackProColors.TextPrimary
                )
            }

            HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

            // ── Speed chart ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(TrackProColors.BgCard)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "SPEED TRACE",
                    color = TrackProColors.TextMuted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 6.dp)
                )
                AndroidView(
                    factory = { ctx ->
                        LineChart(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            xAxis.position = XAxis.XAxisPosition.BOTTOM
                            xAxis.setDrawGridLines(false)
                            xAxis.textColor = "#6B7280".toColorInt()
                            axisLeft.textColor = "#6B7280".toColorInt()
                            axisRight.isEnabled = false
                            description.isEnabled = false
                            legend.isEnabled = false
                            setBackgroundColor("#0E1117".toColorInt())
                            setGridBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { chart ->
                        val safeList = dataPoints.toList()
                        val dataSet = LineDataSet(safeList, "Speed").apply {
                            setDrawValues(false)
                            setDrawCircles(false)
                            lineWidth = 2.5f
                            color = "#E8001C".toColorInt()
                            setDrawFilled(true)
                            fillColor = "#E8001C".toColorInt()
                            fillAlpha = 30
                        }
                        if (chart.data == null) chart.data = LineData(dataSet)
                        else {
                            chart.data.clearValues()
                            chart.data.addDataSet(dataSet)
                        }
                        chart.notifyDataSetChanged()
                        chart.postInvalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

            // ── Map ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TrackProColors.BgCard)
            ) {
                DragMapView(
                    trackPath = trackPath.toList(),
                    driverPosition = driverPosition,
                    modifier = Modifier.fillMaxSize()
                )
                if (trackPath.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "MAP — START SESSION TO RECORD PATH",
                            color = TrackProColors.TextMuted,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

            // ── Bottom controls ───────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.BgElevated)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Vehicle selector
                    if (!isSessionActive) {
                        Box(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            if (loadingState) {
                                Text("Loading vehicles...", color = TrackProColors.TextMuted, fontSize = 12.sp)
                            } else if (vehicles.isNotEmpty()) {
                                DropdownMenuFieldMulti(
                                    "Select car",
                                    vehicles,
                                    selectedVehicle
                                ) { selectedVehicleId = it.toInt() }
                            } else {
                                Text("No vehicles", color = TrackProColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "● SESSION ACTIVE",
                            color = TrackProColors.AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Start/Stop button
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSessionActive) Color(0xFF1A0005) else TrackProColors.AccentRed,
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (isSessionActive) TrackProColors.AccentRed else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    if (!isSessionActive) {
                                        if (selectedVehicleId == -1) {
                                            Toast.makeText(context, "⚠️ No car selected", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        val id = startSession(database, selectedVehicleId.toLong())
                                        if (id == -1L) return@launch
                                        sessionID = id
                                        trackPath.clear()
                                        isSessionActive = true
                                    } else {
                                        isSessionActive = false
                                        if (sessionID != -1L) {
                                            stopBatchInsert()
                                            endSession(database)
                                        }
                                        dragTime = endSessionPostProcess(sessionID, database)
                                        quarterMileTime = getQuarterMileTime(sessionID, database)
                                    }
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (isSessionActive) "■ STOP" else "▶ START",
                            color = if (isSessionActive) TrackProColors.AccentRed else Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Result cell ────────────────────────────────────────────

@Composable
private fun ResultCell(label: String, value: String, valueColor: Color) {
    val textMuted = Color(0xFF6B7280)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = textMuted, fontSize = 9.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = valueColor, fontSize = 22.sp,
            fontWeight = FontWeight.Black)
    }
}

// ── Drag map (records path, follows car) ───────────────────

@Composable
fun DragMapView(
    trackPath: List<LatLonOffset>,
    driverPosition: LatLonOffset,
    modifier: Modifier = Modifier
) {
    var mapViewRef by remember { mutableStateOf<org.maplibre.android.maps.MapView?>(null) }
    val driverSource = remember { mutableStateOf<org.maplibre.android.style.sources.GeoJsonSource?>(null) }
    val pathSource = remember { mutableStateOf<org.maplibre.android.style.sources.GeoJsonSource?>(null) }
    val mapReady = remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }

    // Update driver dot
    LaunchedEffect(driverPosition) {
        val src = driverSource.value ?: return@LaunchedEffect
        if (driverPosition.lat == 0.0 && driverPosition.lon == 0.0) return@LaunchedEffect
        val geojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${driverPosition.lon},${driverPosition.lat}]},"properties":{}}"""
        src.setGeoJson(geojson)
        mapReady.value?.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.newLatLng(
                org.maplibre.android.geometry.LatLng(driverPosition.lat, driverPosition.lon)
            ), 200
        )
    }

    // Update path line as car moves
    LaunchedEffect(trackPath.size) {
        val src = pathSource.value ?: return@LaunchedEffect
        if (trackPath.size < 2) return@LaunchedEffect
        val coords = trackPath.joinToString(",") { "[${it.lon},${it.lat}]" }
        val geojson = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}"""
        src.setGeoJson(geojson)
    }

    AndroidView(
        factory = { ctx ->
            org.maplibre.android.MapLibre.getInstance(ctx)
            org.maplibre.android.maps.MapView(ctx).also { mv ->
                mapViewRef = mv
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapReady.value = map
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false

                        // Path source + layer
                        val pSrc = org.maplibre.android.style.sources.GeoJsonSource(
                            "drag-path-src",
                            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[]},"properties":{}}"""
                        )
                        style.addSource(pSrc)
                        style.addLayer(
                            org.maplibre.android.style.layers.LineLayer("drag-path-layer", "drag-path-src").apply {
                                setProperties(
                                    org.maplibre.android.style.layers.PropertyFactory.lineColor("#E8001C"),
                                    org.maplibre.android.style.layers.PropertyFactory.lineWidth(3f),
                                    org.maplibre.android.style.layers.PropertyFactory.lineCap(
                                        org.maplibre.android.style.layers.Property.LINE_CAP_ROUND
                                    )
                                )
                            }
                        )
                        pathSource.value = pSrc

                        // Driver source + layer
                        val dSrc = org.maplibre.android.style.sources.GeoJsonSource(
                            "drag-driver-src",
                            """{"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]},"properties":{}}"""
                        )
                        style.addSource(dSrc)
                        style.addLayer(
                            org.maplibre.android.style.layers.CircleLayer("drag-driver-layer", "drag-driver-src").apply {
                                setProperties(
                                    org.maplibre.android.style.layers.PropertyFactory.circleColor("#FFFFFF"),
                                    org.maplibre.android.style.layers.PropertyFactory.circleRadius(8f),
                                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor("#E8001C"),
                                    org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth(3f)
                                )
                            }
                        )
                        driverSource.value = dSrc
                    }
                }
            }
        },
        update = {},
        modifier = modifier
    )

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapViewRef?.onStart()
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_STOP -> mapViewRef?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}


suspend fun startSession(database: ESPDatabase, selectedVehicleId: Long): Long {

    val sessionManager = SessionManager.getInstance(database)
    var id: Long

    // Use suspendCoroutine to suspend until the session id is retrieved.
    withContext(Dispatchers.IO) {
        sessionManager.startSession(
            eventType = "Drag data session",
            vehicleId = selectedVehicleId
        )
        Log.d("In start", sessionManager.getCurrentSessionId().toString())
        id = (sessionManager.getCurrentSessionId() ?: -1)
    }

    return id
}

suspend fun endSession(database: ESPDatabase) {
    val sessionManager = SessionManager.getInstance(database)

    withContext(Dispatchers.IO) {
        Log.d("In end", sessionManager.getCurrentSessionId().toString())
        sessionManager.endSession()
    }
}

suspend fun endSessionPostProcess(sessionId: Long, database: ESPDatabase): Double {
    val dragTimeCalculation = DragTimeCalculation(sessionId, database)
    return dragTimeCalculation.timeFromZeroToHundred()
}

suspend fun getQuarterMileTime(sessionId: Long,database: ESPDatabase) : Double
{
    val quarterTime = DragTimeCalculation(sessionId,database)
    return quarterTime.quarterMile()

}

@Preview(
    showBackground = true,
    //device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
)
@Composable
fun DragScreenPreview() {
    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()

    DragRaceScreen(
        database = fakeDatabase
    )
}


