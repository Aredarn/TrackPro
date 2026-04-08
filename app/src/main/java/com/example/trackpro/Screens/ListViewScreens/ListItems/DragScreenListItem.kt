package com.example.trackpro.Screens.ListViewScreens.ListItems

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESPDatabase
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.convertToLatLonOffsetList
import com.example.trackpro.ui.theme.TrackProTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.TimeUnit


@Composable
fun GraphScreen(onBack: () -> Unit, sessionId: Long) {
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    var coordinates by remember { mutableStateOf(emptyList<RawGPSData>()) }
    var coordinatesSimplified by remember { mutableStateOf(emptyList<LatLonOffset>()) }
    var dragTime by remember { mutableDoubleStateOf(-1.0) }
    val dragTimeClass = remember { DragTimeCalculation(sessionId, database) }
    var totalDist by remember { mutableDoubleStateOf(-1.0) }
    var quarterMileTime by remember { mutableDoubleStateOf(-1.0) }
    val dataPoints = remember { mutableListOf<Entry>() }

    // Design tokens
    val BgDeep     = Color(0xFF080A0F)
    val BgCard     = Color(0xFF0E1117)
    val BgElevated = Color(0xFF151922)
    val AccentRed  = Color(0xFFE8001C)
    val TextPrimary= Color(0xFFF0F2F5)
    val TextMuted  = Color(0xFF6B7280)
    val DeltaGood  = Color(0xFF00E676)
    val SectorLine = Color(0xFF1E2530)

    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            val data = database.rawGPSDataDao().getGPSDataBySession(sessionId)
            data.forEachIndexed { index, d ->
                d.speed?.let { dataPoints.add(Entry(index.toFloat(), it)) }
            }
            val simplifiedData = convertToLatLonOffsetList(data)
            val dragTimeValue   = dragTimeClass.timeFromZeroToHundred()
            val totalDistValue  = dragTimeClass.totalDistance(simplifiedData)
            val quarterMile     = dragTimeClass.quarterMile()
            withContext(Dispatchers.Main) {
                coordinates          = data
                coordinatesSimplified = simplifiedData
                dragTime             = dragTimeValue
                totalDist            = totalDistValue
                quarterMileTime      = quarterMile
            }
        }
    }

    val pagerState = rememberPagerState { 2 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentRed)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "● SESSION OVERVIEW",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            }

            // ── Stats row ─────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (coordinates.isNotEmpty()) {
                    val totalTime = coordinates.last().timestamp - coordinates.first().timestamp
                    StatRow(
                        label = "DURATION",
                        value = formatTime(totalTime),
                        valueColor = TextPrimary,
                        textMuted = TextMuted
                    )
                }
                StatRow(
                    label = "DISTANCE",
                    value = if (totalDist > 0) String.format("%.3f km", totalDist) else "—",
                    valueColor = TextPrimary,
                    textMuted = TextMuted
                )
                Divider(color = SectorLine, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatColumn(
                        label = "0–100 km/h",
                        value = if (dragTime > 0) String.format("%.3fs", dragTime) else "—",
                        valueColor = if (dragTime > 0) DeltaGood else TextMuted,
                        textMuted = TextMuted
                    )
                    StatColumn(
                        label = "¼ MILE",
                        value = if (quarterMileTime > 0) String.format("%.3fs", quarterMileTime) else "—",
                        valueColor = if (quarterMileTime > 0) DeltaGood else TextMuted,
                        textMuted = TextMuted
                    )
                    StatColumn(
                        label = "DATA PTS",
                        value = "${dataPoints.size}",
                        valueColor = TextPrimary,
                        textMuted = TextMuted
                    )
                }
            }

            Divider(color = SectorLine, thickness = 1.dp)

            // ── Page indicator ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgElevated)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("MAP", "SPEED TRACE").forEachIndexed { idx, label ->
                    val active = pagerState.currentPage == idx
                    Box(
                        modifier = Modifier
                            .background(
                                if (active) AccentRed else SectorLine,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.Black else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Divider(color = SectorLine, thickness = 1.dp)

            // ── Pager ─────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> SessionMapPage(
                        coordinatesSimplified = coordinatesSimplified,
                        modifier = Modifier.fillMaxSize()
                    )
                    1 -> SessionChartPage(
                        dataPoints = dataPoints,
                        bgCard = BgCard,
                        accentRed = AccentRed,
                        textMuted = TextMuted,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

// ── Map page ───────────────────────────────────────────────

@Composable
private fun SessionMapPage(
    coordinatesSimplified: List<LatLonOffset>,
    modifier: Modifier = Modifier
) {
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    val trackDrawn = remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mapViewRef = mv
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        styleLoaded = true

                        // Pre-create source + layer
                        val src = GeoJsonSource(
                            "session-track-src",
                            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[]},"properties":{}}"""
                        )
                        style.addSource(src)
                        style.addLayer(
                            LineLayer("session-track-layer", "session-track-src").apply {
                                setProperties(
                                    PropertyFactory.lineColor("#E8001C"),
                                    PropertyFactory.lineWidth(3f),
                                    PropertyFactory.lineCap(
                                        Property.LINE_CAP_ROUND
                                    ),
                                    PropertyFactory.lineJoin(
                                        Property.LINE_JOIN_ROUND
                                    )
                                )
                            }
                        )

                        // Draw immediately if data already loaded
                        if (coordinatesSimplified.isNotEmpty() && !trackDrawn.value) {
                            drawSessionTrack(map, style, coordinatesSimplified)
                            trackDrawn.value = true
                        }
                    }
                }
            }
        },
        update = { mv ->
            if (coordinatesSimplified.isNotEmpty() && !trackDrawn.value) {
                mv.getMapAsync { map ->
                    val style = map.style ?: return@getMapAsync
                    drawSessionTrack(map, style, coordinatesSimplified)
                    trackDrawn.value = true
                }
            }
        },
        modifier = modifier
    )

    val lifecycleOwner = LocalLifecycleOwner.current
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

private fun drawSessionTrack(
    map: MapLibreMap,
    style: Style,
    points: List<LatLonOffset>
) {
    val coords = points.joinToString(",") { "[${it.lon},${it.lat}]" }
    val geojson = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}"""
    (style.getSource("session-track-src") as? GeoJsonSource)
        ?.setGeoJson(geojson)

    val bounds = LatLngBounds.Builder()
        .includes(points.map { LatLng(it.lat, it.lon) })
        .build()
    map.easeCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, 64), 800
    )
}

// ── Chart page ─────────────────────────────────────────────

@Composable
private fun SessionChartPage(
    dataPoints: List<Entry>,
    bgCard: Color,
    accentRed: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(bgCard)) {
        Text(
            text = "SPEED TRACE",
            color = textMuted,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 12.dp)
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
                    xAxis.textColor = android.graphics.Color.parseColor("#6B7280")
                    axisLeft.textColor = android.graphics.Color.parseColor("#6B7280")
                    axisLeft.gridColor = android.graphics.Color.parseColor("#1E2530")
                    axisRight.isEnabled = false
                    description.isEnabled = false
                    legend.isEnabled = false
                    setBackgroundColor(android.graphics.Color.parseColor("#0E1117"))
                }
            },
            update = { chart ->
                val dataSet = LineDataSet(dataPoints, "Speed").apply {
                    setDrawValues(false)
                    setDrawCircles(false)
                    lineWidth = 2.5f
                    color = android.graphics.Color.parseColor("#E8001C")
                    setDrawFilled(true)
                    fillColor = android.graphics.Color.parseColor("#E8001C")
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
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp)
        )
    }
}

// ── Shared sub-components ──────────────────────────────────

@Composable
private fun StatRow(label: String, value: String, valueColor: Color, textMuted: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = textMuted, fontSize = 10.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = valueColor, fontSize = 16.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatColumn(label: String, value: String, valueColor: Color, textMuted: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = textMuted, fontSize = 9.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = valueColor, fontSize = 22.sp,
            fontWeight = FontWeight.Black)
    }
}


@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    TrackProTheme {
        GraphScreen(onBack = {},1)
    }
}

@SuppressLint("DefaultLocale")
fun formatTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    val millis = milliseconds % 1000

    return String.format("%02d:%02d.%02d", minutes, seconds, millis)
}