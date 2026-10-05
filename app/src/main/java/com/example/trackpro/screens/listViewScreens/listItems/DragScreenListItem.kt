package com.example.trackpro.screens.listViewScreens.listItems

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Spacer
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.components.paddockCard
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trackpro.managerClasses.calculationClasses.DragSpeedScale
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
import com.example.trackpro.dataClasses.RawGPSData
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.dataClasses.convertToLatLonOffsetList
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.TrackProApp
import com.example.trackpro.managerClasses.utilities.SpeedColorUtils
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.managerClasses.utilities.WeatherService
import com.example.trackpro.screens.telemetricScreens.DragMetricCard
import com.example.trackpro.screens.telemetricScreens.DragMetricDisplay
import com.example.trackpro.components.Haptic
import com.example.trackpro.theme.markingDim
import com.example.trackpro.components.pressable
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
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
import java.util.Locale


// Haversine distance between two GPS points (meters)
private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * atan2(sqrt(a), sqrt(1 - a))
}

// Centered moving average over GPS speed samples, tolerant of null readings. Smooths out
// per-sample GPS jitter for display/differentiation without touching the raw stored data
// (0-60/quarter-mile threshold detection still uses the unsmoothed values for accuracy).
private fun smoothSpeeds(data: List<RawGPSData>, windowSize: Int = 5): List<Float?> {
    val half = windowSize / 2
    return data.indices.map { i ->
        val lo = (i - half).coerceAtLeast(0)
        val hi = (i + half).coerceAtMost(data.lastIndex)
        val window = (lo..hi).mapNotNull { data[it].speed }
        if (window.isEmpty()) null else window.average().toFloat()
    }
}


/** Whether a run's GPS trace has been read yet, and whether there was one to read. */
private enum class TraceState { Loading, Loaded, Empty }

@Composable
fun GraphScreen(onBack: () -> Unit, sessionId: Long) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
    val database = remember { ESPDatabase.getInstance(context) }
    var coordinates by remember { mutableStateOf(emptyList<RawGPSData>()) }
    var mapGpsData by remember { mutableStateOf(emptyList<RawGPSData>()) }
    var session by remember { mutableStateOf<SessionData?>(null) }
    val dragTimeClass = remember { DragTimeCalculation(sessionId, database) }
    var totalDist by remember { mutableDoubleStateOf(-1.0) }
    var showMap by remember { mutableStateOf(false) }
    
    // X = cumulative meters or seconds, Y = speed (km/h).
    // Snapshot-state lists, not plain MutableLists: the chart's AndroidView `update`
    // block reads these, and only a state read re-runs it. With plain lists the chart
    // depended on some *other* state changing after the points landed, which is a race.
    val dataPointsMeters = remember { mutableStateListOf<Entry>() }
    val dataPointsSeconds = remember { mutableStateListOf<Entry>() }

    // Distinguishes "still loading" from "this run has no GPS trace" - the two used to
    // render identically, as dashes and a chart that said "Calculating" forever.
    var traceState by remember { mutableStateOf(TraceState.Loading) }
    var xAxisInMeters by remember { mutableStateOf(true) }
    
    // Fixed: Removed 'get()' as local variables don't support custom getters
    val dataPoints = if (xAxisInMeters) dataPointsMeters else dataPointsSeconds
    
    // Rebuilt when the unit setting changes: unlike a live session, this screen derives
    // every split from the stored trace, so it can honour the setting as it is now rather
    // than as it was during the recording.
    val calculator = remember(useMetric) {
        DragTimeCalculation(sessionId, database, DragSpeedScale.of(useMetric))
    }
    // Seeded from the calculator rather than DragMetrics(), so the split tiles carry their
    // labels before the trace has loaded.
    var metrics by remember(useMetric) { mutableStateOf(calculator.getCurrentMetrics()) }

    var maxSpeed by remember { mutableDoubleStateOf(-1.0) }
    var avgSpeed by remember { mutableDoubleStateOf(-1.0) }
    var maxAcceleration by remember { mutableDoubleStateOf(-1.0) }

    // Elevation stats: net gain, total climb, total descent
    var elevationNet by remember { mutableDoubleStateOf(0.0) }
    var elevationGain by remember { mutableDoubleStateOf(0.0) }
    var elevationLoss by remember { mutableDoubleStateOf(0.0) }

    // Loaded separately from the GPS trace below, which bails early when a session has no
    // recorded points - the captured conditions are still worth showing in that case.
    LaunchedEffect(sessionId) {
        withContext(Dispatchers.IO) {
            val loaded = database.sessionDataDao().getSessionById(sessionId)
            withContext(Dispatchers.Main) { session = loaded }
        }
    }

    LaunchedEffect(sessionId, useMetric) {
        withContext(Dispatchers.IO) {
            val data = database.rawGPSDataDao().getGPSDataBySession(sessionId)
            if (data.isEmpty()) {
                withContext(Dispatchers.Main) { traceState = TraceState.Empty }
                return@withContext
            }

            // Raw per-sample GPS speed is noisy enough that both the chart and the
            // differentiated MAX ACCEL stat look jagged even for a genuinely smooth run.
            // Smooth it with a small centered moving average before using it for either.
            val smoothedSpeeds = smoothSpeeds(data)

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
                smoothedSpeeds[i]?.let {
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
                val dSpeed = (smoothedSpeeds[i] ?: continue) - (smoothedSpeeds[i - 1] ?: continue)
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

            val smoothedMapData = data.mapIndexed { i, d -> d.copy(speed = smoothedSpeeds[i]) }

            withContext(Dispatchers.Main) {
                traceState = TraceState.Loaded
                coordinates = data
                mapGpsData = smoothedMapData
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
        AppTopBar(title = stringResource(R.string.drag_run_title), accent = TrackProTheme.colors.accent, onBack = onBack)

        // ── Compact stats panel ───────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter)
                .paddockCard()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (coordinates.isNotEmpty()) {
                    val totalTime = coordinates.last().timestamp - coordinates.first().timestamp
                    StatCell(label = stringResource(R.string.drag_duration), value = formatTime(totalTime), size = StatCellSize.Regular)
                }
                StatCell(
                    label = stringResource(R.string.drag_distance),
                    value = if (totalDist <= 0) "—" else UnitFormatter.formatDistance(totalDist, useMetric),
                    size = StatCellSize.Regular,
                    horizontalAlignment = Alignment.End
                )
            }

            // Conditions captured when this run was recorded. Air temp in particular moves
            // both grip and power enough to explain a chunk of any run-to-run difference.
            session?.weatherTempC?.let { tempC ->
                val current = session
                Divider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCell(
                        label = stringResource(R.string.drag_air_temp),
                        value = UnitFormatter.formatTemperature(tempC, useMetric),
                        size = StatCellSize.Small
                    )
                    StatCell(
                        label = stringResource(R.string.session_weather),
                        value = stringResource(WeatherService.describeCodeRes(current?.weatherCode)),
                        size = StatCellSize.Small
                    )
                    StatCell(
                        label = stringResource(R.string.session_surface),
                        value = if (WeatherService.isWet(
                                current?.weatherPrecipitationMm,
                                current?.weatherCode
                            )
                        ) stringResource(R.string.session_wet) else stringResource(R.string.session_dry),
                        size = StatCellSize.Small
                    )
                }
            }

            Divider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                DragMetricCard(DragMetricDisplay(stringResource(R.string.car_top_speed),  if (maxSpeed > 0) UnitFormatter.formatSpeed(maxSpeed, useMetric) else "—", UnitFormatter.speedUnitLabel(useMetric), maxSpeed > 0), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay(stringResource(R.string.lap_avg_speed),  if (avgSpeed > 0) UnitFormatter.formatSpeed(avgSpeed, useMetric) else "—", UnitFormatter.speedUnitLabel(useMetric), avgSpeed > 0), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay(stringResource(R.string.drag_max_accel),  if (maxAcceleration > 0) String.format(Locale.US, "%.1f", UnitFormatter.convertSpeed(maxAcceleration, useMetric)) else "—", "${UnitFormatter.speedUnitLabel(useMetric)}/s", maxAcceleration > 0), modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                val hasElevation = elevationGain != 0.0 || elevationLoss != 0.0
                val netLabel  = if (elevationNet >= 0) String.format(Locale.US, "+%.0f m", elevationNet)
                                else String.format(Locale.US, "%.0f m", elevationNet)
                val gainLabel = "+%.0f m".format(elevationGain)
                val lossLabel = String.format(Locale.US, "%.0f m", elevationLoss)
                DragMetricCard(DragMetricDisplay(stringResource(R.string.drag_net_elev), if (hasElevation) netLabel  else "—", "", hasElevation), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay(stringResource(R.string.drag_climb),   if (hasElevation) gainLabel else "—", "", hasElevation), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay(stringResource(R.string.drag_descent), if (hasElevation) lossLabel else "—", "", hasElevation), modifier = Modifier.weight(1f))
            }

            // Labels come from the splits themselves, so a tile can never name a milestone
            // the timer was not measuring. Three standing splits fill the first row; the
            // longest rolling split shares the second with the two mile figures.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                metrics.standing.take(3).forEach { split ->
                    DragMetricCard(
                        DragMetricDisplay(split.label, formatMetric(split.seconds), "s", split.seconds != null),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                metrics.rolling.lastOrNull()?.let { split ->
                    DragMetricCard(
                        DragMetricDisplay(split.label, formatMetric(split.seconds), "s", split.seconds != null),
                        modifier = Modifier.weight(1f)
                    )
                }
                DragMetricCard(DragMetricDisplay(stringResource(R.string.drag_quarter_mile),   formatMetric(metrics.quarterMileTime),            "s",   metrics.quarterMileTime != null), modifier = Modifier.weight(1f))
                DragMetricCard(DragMetricDisplay(stringResource(R.string.drag_trap_speed), metrics.quarterMileSpeed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "\u2014", UnitFormatter.speedUnitLabel(useMetric), metrics.quarterMileSpeed != null), modifier = Modifier.weight(1f))
            }
        }

        // ── Chart card ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.md, bottom = Spacing.gutter)
                .paddockCard()
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showMap) stringResource(R.string.drag_gps_trace) else stringResource(R.string.drag_speed),
                style = TrackProType.titleMedium,
                color = TrackProTheme.colors.textPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!showMap) {
                    listOf(true to "m", false to "s").forEach { (isMeters, label) ->
                        TraceToggle(label = label, active = xAxisInMeters == isMeters) { xAxisInMeters = isMeters }
                    }
                    Spacer(Modifier.width(Spacing.xs))
                }
                listOf(false to stringResource(R.string.drag_chart), true to stringResource(R.string.drag_map)).forEach { (isMap, label) ->
                    TraceToggle(label = label, active = showMap == isMap) { showMap = isMap }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
        ) {
            if (showMap) {
                if (mapGpsData.isNotEmpty()) {
                    DragSessionMapView(gpsData = mapGpsData, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.drag_no_gps), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                    }
                }
            } else if (traceState != TraceState.Loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (traceState == TraceState.Loading) stringResource(R.string.drag_loading_trace)
                        else stringResource(R.string.drag_no_trace),
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim
                    )
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
                            color = android.graphics.Color.parseColor(DataVizColors.chartLine)
                            setDrawFilled(true)
                            fillColor = android.graphics.Color.parseColor(DataVizColors.chartLine)
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
                        .padding(Spacing.sm)
                )
            }
        }
        }
    }
}

/** A small pill toggle in the chart card's header. */
@Composable
private fun TraceToggle(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(TrackProShapes.pill)
            .pressable(onClick = onClick, scale = 0.96f, haptic = Haptic.Selection)
            .background(if (active) TrackProTheme.colors.accent else TrackProTheme.colors.bgElevated)
            .semantics { selected = active }
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TrackProType.label,
            color = if (active) TrackProTheme.colors.onAccent else TrackProTheme.colors.textMuted
        )
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
            PropertyFactory.circleColor(DataVizColors.startMarker),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor(DataVizColors.darkOutline),
            PropertyFactory.circleStrokeWidth(1.5f)
        )
    })
    style.addSource(GeoJsonSource("drag-end-src", """{"type":"Feature","geometry":{"type":"Point","coordinates":[${end.longitude},${end.latitude}]}}"""))
    style.addLayer(CircleLayer("drag-end-layer", "drag-end-src").apply {
        setProperties(
            PropertyFactory.circleColor(DataVizColors.endMarker),
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleStrokeColor(DataVizColors.darkOutline),
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
        textColor = android.graphics.Color.parseColor(DataVizColors.chartAxisText)
        textSize = 8f
    }
    axisLeft.apply {
        textColor = android.graphics.Color.parseColor(DataVizColors.chartAxisText)
        gridColor = android.graphics.Color.parseColor(DataVizColors.chartGrid)
        textSize = 8f
        axisMinimum = 0f
    }
    axisRight.isEnabled = false
    description.isEnabled = false
    legend.isEnabled = false
    setNoDataText(context.getString(R.string.drag_calculating))
    setNoDataTextColor(android.graphics.Color.parseColor(DataVizColors.chartAxisText))
    setBackgroundColor(android.graphics.Color.parseColor(DataVizColors.chartBackground))
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
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, millis / 10)
}

private fun formatMetric(value: Double?): String {
    return value?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
}