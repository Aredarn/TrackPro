package com.example.trackpro.screens.listViewScreens.listItems

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.trackpro.managerClasses.calculationClasses.DragMetrics
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.dataClasses.convertToLatLonOffsetList
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.TrackProApp
import com.example.trackpro.managerClasses.utilities.SpeedColorUtils
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.screens.telemetricScreens.DragMetricCard
import com.example.trackpro.screens.telemetricScreens.DragMetricDisplay
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.TimeUnit
import kotlin.math.*


// Haversine distance between two GPS points (meters)
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}


@Composable
fun GraphScreen(onBack: () -> Unit, sessionId: Long) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
    val database = remember { ESPDatabase.getInstance(context) }
    var coordinates by remember { mutableStateOf(emptyList<RawGPSData>()) }
    val dragTimeClass = remember { DragTimeCalculation(sessionId, database) }
    var totalDist by remember { mutableDoubleStateOf(-1.0) }
    var showMap by remember { mutableStateOf(false) }
    
    // X = cumulative meters or seconds, Y = speed (km/h)
    val dataPointsMeters = remember { mutableListOf<Entry>() }
    val dataPointsSeconds = remember { mutableListOf<Entry>() }
    var xAxisInMeters by remember { mutableStateOf(true) }
    
    // Fixed: Removed 'get()' as local variables don't support custom getters
    val dataPoints = if (xAxisInMeters) dataPointsMeters else dataPointsSeconds
    
    var metrics by remember { mutableStateOf(DragMetrics()) }
    val calculator = remember { DragTimeCalculation(sessionId, database) }

    var maxSpeed by remember { mutableDoubleStateOf(-1.0) }
    var avgSpeed by remember { mutableDoubleStateOf(-1.0) }
    var maxAcceleration by remember { mutableDoubleStateOf(-1.0) }

    // Elevation stats: net gain, total climb, total descent
    var elevationNet by remember { mutableDoubleStateOf(0.0) }
    var elevationGain by remember { mutableDoubleStateOf(0.0) }
    var elevationLoss by remember { mutableDoubleStateOf(0.0) }

    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            val data = database.rawGPSDataDao().getGPSDataBySession(sessionId)
            if (data.isEmpty()) return@withContext

            // Build cumulative distance array for X axis
            val cumulativeDist = DoubleArray(data.size)
            for (i in 1 until data.size) {
                val prev = data[i - 1]
                val curr = data[i]
                val segDist = haversineMeters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
                cumulativeDist[i] = cumulativeDist[i - 1] + segDist
            }

            val t0 = data.first().timestamp
            dataPointsMeters.clear()
            dataPointsSeconds.clear()
            data.forEachIndexed { i, d ->
                d.speed?.let {
                    dataPointsMeters.add(Entry(cumulativeDist[i].toFloat(), it))
                    dataPointsSeconds.add(Entry(((d.timestamp - t0) / 1000f), it))
                }
            }

            val simplifiedData = convertToLatLonOffsetList(data)
            val calculatedMetrics = calculator.calculateFullSessionMetrics(data)
            val totalDistValue = dragTimeClass.totalDistance(simplifiedData)

            val speeds = data.mapNotNull { it.speed }
            val maxSpeedValue = speeds.maxOrNull()?.toDouble() ?: -1.0
            val avgSpeedValue = if (speeds.isNotEmpty()) speeds.average() else -1.0

            var maxAccel = -1.0
            for (i in 1 until data.size) {
                val dSpeed = (data[i].speed ?: continue) - (data[i - 1].speed ?: continue)
                val dTime = (data[i].timestamp - data[i - 1].timestamp) / 1000.0
                if (dTime > 0) {
                    val accel = dSpeed / dTime
                    if (accel > maxAccel) maxAccel = accel
                }
            }

            // Elevation: sum climbs and descents separately
            var gain = 0.0
            var loss = 0.0
            for (i in 1 until data.size) {
                val alt1 = data[i - 1].altitude ?: continue
                val alt2 = data[i].altitude ?: continue
                val delta = alt2 - alt1
                if (delta > 0) gain += delta else loss += delta
            }
            val firstAlt = data.firstOrNull { it.altitude != null }?.altitude
            val lastAlt  = data.lastOrNull  { it.altitude != null }?.altitude
            val net = if (firstAlt != null && lastAlt != null) lastAlt - firstAlt else 0.0

            withContext(Dispatchers.Main) {
                coordinates = data
                metrics = calculatedMetrics
                totalDist = totalDistValue
                maxSpeed = maxSpeedValue
                avgSpeed = avgSpeedValue
                maxAcceleration = maxAccel
                elevationGain = gain
                elevationLoss = loss
                elevationNet = net
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        // ── Top bar ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.accentCyan)
                .padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            Text(
                text = "● SESSION OVERVIEW",
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            )
        }

        // ── Compact stats panel ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.bgCard)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coordinates.isNotEmpty()) {
                    val totalTime = coordinates.last().timestamp - coordinates.first().timestamp
                    CompactStat(
                        label = "DURATION",
                        value = formatTime(totalTime),
                        textMuted = TrackProTheme.colors.textMuted,
                        valueColor = TrackProTheme.colors.textPrimary
                    )
                }
                CompactStat(
                    label = "DISTANCE",
                    value = if (totalDist <= 0) "—" else UnitFormatter.formatDistance(totalDist, useMetric),
                    textMuted = TrackProTheme.colors.textMuted,
                    valueColor = TrackProTheme.colors.textPrimary
                )
            }

            Divider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DragMetricCard(DragMetricDisplay("TOP SPEED",  if (maxSpeed > 0) UnitFormatter.formatSpeed(maxSpeed, useMetric) else "—", UnitFormatter.speedUnitLabel(useMetric), maxSpeed > 0), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("AVG SPEED",  if (avgSpeed > 0) UnitFormatter.formatSpeed(avgSpeed, useMetric) else "—", UnitFormatter.speedUnitLabel(useMetric), avgSpeed > 0), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("MAX ACCEL",  if (maxAcceleration > 0) String.format("%.1f", UnitFormatter.convertSpeed(maxAcceleration, useMetric)) else "—", "${UnitFormatter.speedUnitLabel(useMetric)}/S", maxAcceleration > 0), modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val hasElevation = elevationGain != 0.0 || elevationLoss != 0.0
                val netLabel  = if (elevationNet >= 0) "+%.0f m".format(elevationNet) else "%.0f m".format(elevationNet)
                val gainLabel = "+%.0f m".format(elevationGain)
                val lossLabel = "%.0f m".format(elevationLoss)
                DragMetricCard(DragMetricDisplay("ELEV NET",  if (hasElevation) netLabel  else "—", "", hasElevation), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("ELEV ↑",    if (hasElevation) gainLabel else "—", "", hasElevation), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("ELEV ↓",    if (hasElevation) lossLabel else "—", "", hasElevation), modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DragMetricCard(DragMetricDisplay("0-60",  formatMetric(metrics.time0to60),  "SEC", metrics.time0to60 != null),  modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("0-100", formatMetric(metrics.time0to100), "SEC", metrics.time0to100 != null), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("0-160", formatMetric(metrics.time0to160), "SEC", metrics.time0to160 != null), modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DragMetricCard(DragMetricDisplay("100-200",  formatMetric(metrics.time100to200),               "SEC",   metrics.time100to200 != null),   modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("¼ MILE",   formatMetric(metrics.quarterMileTime),            "SEC",   metrics.quarterMileTime != null), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay("TRAP SPD", metrics.quarterMileSpeed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "—", UnitFormatter.speedUnitLabel(useMetric), metrics.quarterMileSpeed != null), modifier = Modifier.weight(1f))
            }
        }

        Divider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

        // ── Chart header ─────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.bgCard)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showMap) "GPS TRACE" else "SPEED TRACE",
                color = TrackProTheme.colors.textMuted,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!showMap) {
                    listOf(true to "METER", false to "SEC").forEach { (isMeters, label) ->
                        val active = xAxisInMeters == isMeters
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) TrackProTheme.colors.accentCyan else TrackProTheme.colors.sectorLine,
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable { xAxisInMeters = isMeters }
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) Color.Black else TrackProTheme.colors.textMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                listOf(false to "CHART", true to "MAP").forEach { (isMap, label) ->
                    val active = showMap == isMap
                    Box(
                        modifier = Modifier
                            .background(
                                if (active) TrackProTheme.colors.accentAmber else TrackProTheme.colors.sectorLine,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { showMap = isMap }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.Black else TrackProTheme.colors.textMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }

        Divider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TrackProTheme.colors.bgCard)
        ) {
            if (showMap) {
                if (coordinates.isNotEmpty()) {
                    DragSessionMapView(gpsData = coordinates, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "NO GPS DATA",
                            color = TrackProTheme.colors.textMuted,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp
                        )
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        LineChart(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setupChartStyle()
                        }
                    },
                    update = { chart ->
                        val convertedPoints = dataPoints.map { Entry(it.x, UnitFormatter.convertSpeed(it.y, useMetric)) }
                        val dataSet = LineDataSet(convertedPoints, "Speed").apply {
                            setDrawValues(false)
                            setDrawCircles(false)
                            lineWidth = 2f
                            color = android.graphics.Color.parseColor("#E8001C")
                            setDrawFilled(true)
                            fillColor = android.graphics.Color.parseColor("#E8001C")
                            fillAlpha = 40
                        }
                        chart.data = LineData(dataSet)
                        chart.xAxis.valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return if (xAxisInMeters) "${value.toInt()}m" else "${value.toInt()}s"
                            }
                        }
                        chart.notifyDataSetChanged()
                        chart.invalidate()
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun DragSessionMapView(
    gpsData: List<RawGPSData>,
    modifier: Modifier = Modifier
) {
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val styleRef = remember { mutableStateOf<Style?>(null) }

    LaunchedEffect(gpsData) {
        val map = mapRef.value ?: return@LaunchedEffect
        val style = styleRef.value ?: return@LaunchedEffect
        drawDragSpeedHeatmap(style, gpsData)
        if (gpsData.isNotEmpty()) fitCameraToDragGps(map, gpsData)
    }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapRef.value = map
                    map.uiSettings.setAllGesturesEnabled(true)
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        styleRef.value = style
                        drawDragSpeedHeatmap(style, gpsData)
                        if (gpsData.isNotEmpty()) fitCameraToDragGps(map, gpsData)
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun drawDragSpeedHeatmap(style: Style, gps: List<RawGPSData>) {
    listOf("drag-heat-layer", "drag-start-layer", "drag-end-layer").forEach { id ->
        style.getLayer(id)?.let { style.removeLayer(it) }
    }
    listOf("drag-heat-src", "drag-start-src", "drag-end-src").forEach { id ->
        style.getSource(id)?.let { style.removeSource(it) }
    }

    if (gps.size < 2) return

    val speeds = gps.mapNotNull { it.speed }
    val minSpd = speeds.minOrNull() ?: 0f
    val maxSpd = speeds.maxOrNull() ?: 1f

    val features = mutableListOf<String>()
    for (i in 0 until gps.size - 1) {
        val p0 = gps[i]; val p1 = gps[i + 1]
        val spd = ((p0.speed ?: minSpd) + (p1.speed ?: minSpd)) / 2f
        val t = if (maxSpd > minSpd) (spd - minSpd) / (maxSpd - minSpd) else 0f
        val hex = SpeedColorUtils.speedToHex(t)
        features.add(
            """{"type":"Feature","geometry":{"type":"LineString","coordinates":[[${p0.longitude},${p0.latitude}],[${p1.longitude},${p1.latitude}]]},"properties":{"color":"$hex"}}"""
        )
    }

    val geojson = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    style.addSource(GeoJsonSource("drag-heat-src", geojson))
    style.addLayer(LineLayer("drag-heat-layer", "drag-heat-src").apply {
        setProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    })

    val start = gps.first()
    val end = gps.last()
    style.addSource(GeoJsonSource("drag-start-src", """{"type":"Feature","geometry":{"type":"Point","coordinates":[${start.longitude},${start.latitude}]}}"""))
    style.addLayer(CircleLayer("drag-start-layer", "drag-start-src").apply {
        setProperties(
            PropertyFactory.circleColor("#00E676"),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor("#0E1117"),
            PropertyFactory.circleStrokeWidth(1.5f)
        )
    })
    style.addSource(GeoJsonSource("drag-end-src", """{"type":"Feature","geometry":{"type":"Point","coordinates":[${end.longitude},${end.latitude}]}}"""))
    style.addLayer(CircleLayer("drag-end-layer", "drag-end-src").apply {
        setProperties(
            PropertyFactory.circleColor("#FF1744"),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor("#0E1117"),
            PropertyFactory.circleStrokeWidth(1.5f)
        )
    })
}

private fun fitCameraToDragGps(map: MapLibreMap, gps: List<RawGPSData>) {
    if (gps.isEmpty()) return
    val bb = LatLngBounds.Builder()
    gps.forEach { bb.include(LatLng(it.latitude, it.longitude)) }
    map.easeCamera(CameraUpdateFactory.newLatLngBounds(bb.build(), 80), 800)
}

private fun LineChart.setupChartStyle() {
    xAxis.apply {
        position = XAxis.XAxisPosition.BOTTOM
        setDrawGridLines(false)
        textColor = android.graphics.Color.parseColor("#6B7280")
        textSize = 8f
    }
    axisLeft.apply {
        textColor = android.graphics.Color.parseColor("#6B7280")
        gridColor = android.graphics.Color.parseColor("#1E2530")
        textSize = 8f
        axisMinimum = 0f
    }
    axisRight.isEnabled = false
    description.isEnabled = false
    legend.isEnabled = false
    setNoDataText("Calculating data...")
    setNoDataTextColor(android.graphics.Color.WHITE)
    setBackgroundColor(android.graphics.Color.parseColor("#0E1117"))
}

@Composable
private fun CompactStat(label: String, value: String, textMuted: Color, valueColor: Color) {
    Column {
        Text(text = label, color = textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(text = value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    TrackProTheme {
        GraphScreen(onBack = {}, 1)
    }
}

@SuppressLint("DefaultLocale")
fun formatTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    val millis = milliseconds % 1000
    return String.format("%02d:%02d.%02d", minutes, seconds, millis / 10)
}

private fun formatMetric(value: Double?): String {
    return value?.let { String.format("%.2f", it) } ?: "—"
}