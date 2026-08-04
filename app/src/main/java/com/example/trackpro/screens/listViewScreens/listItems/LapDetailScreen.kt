package com.example.trackpro.screens.listViewScreens.lapDetail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.dataClasses.SectorTimeData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.TrackProApp
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.SpeedColorUtils
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.managerClasses.utilities.toLapTimeMillis
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import android.graphics.Color as AndroidColor

// ── Heatmap mode ───────────────────────────────────────────

enum class HeatmapMode(val label: String, val icon: String) {
    SPEED("SPEED",    "⚡"),
    UNIFORM("LINE",   "—")
}

// ── Activity ───────────────────────────────────────────────

class LapDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionId = intent.getLongExtra("sessionId", -1L)
        val lapId     = intent.getLongExtra("lapId", -1L)

        setContent {
            TrackProTheme {
                LapDetailScreen(
                    navController = rememberNavController(),
                    database      = Room.inMemoryDatabaseBuilder(
                        LocalContext.current, ESPDatabase::class.java
                    ).build(),
                    sessionId = sessionId,
                    primaryLapId = lapId
                )
            }
        }
    }
}

// ── Main Screen ────────────────────────────────────────────

@Composable
fun LapDetailScreen(
    navController: NavController,
    database: ESPDatabase,
    sessionId: Long,
    primaryLapId: Long
) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()

    // ── State ──────────────────────────────────────────────
    var allSessionLaps  by remember { mutableStateOf<List<LapTimeData>>(emptyList()) }
    var primaryLap      by remember { mutableStateOf<LapTimeData?>(null) }
    var primaryGps      by remember { mutableStateOf<List<LapInfoData>>(emptyList()) }
    var primarySectors  by remember { mutableStateOf<List<SectorTimeData>>(emptyList()) }
    var compareLap      by remember { mutableStateOf<LapTimeData?>(null) }
    var compareGps      by remember { mutableStateOf<List<LapInfoData>>(emptyList()) }
    var compareSectors  by remember { mutableStateOf<List<SectorTimeData>>(emptyList()) }
    var isLoading       by remember { mutableStateOf(true) }
    var heatmapMode     by remember { mutableStateOf(HeatmapMode.SPEED) }
    var showLapPicker   by remember { mutableStateOf(false) }
    var showStatsPanel  by remember { mutableStateOf(false) }

    // ── Load ───────────────────────────────────────────────
    LaunchedEffect(primaryLapId) {
        withContext(Dispatchers.IO) {
            allSessionLaps = database.lapTimeDataDAO().getLapsForSession(sessionId)
            primaryLap     = allSessionLaps.find { it.id == primaryLapId }
            primaryGps     = primaryLap?.let {
                database.lapInfoDataDAO().getLapData(it.id)
            } ?: emptyList()
            primarySectors = primaryLap?.let {
                database.sectorTimeDataDAO().getSectorTimesForLap(it.id)
            } ?: emptyList()
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    // ── Load compare lap gps when compareLap changes ───────
    LaunchedEffect(compareLap) {
        compareLap?.let { cl ->
            withContext(Dispatchers.IO) {
                compareGps = database.lapInfoDataDAO().getLapData(cl.id)
                compareSectors = database.sectorTimeDataDAO().getSectorTimesForLap(cl.id)
            }
        } ?: run { compareGps = emptyList(); compareSectors = emptyList() }
    }

    // ── Derived stats ──────────────────────────────────────
    val primaryTopSpeed = primaryGps.mapNotNull { it.spd }.maxOrNull() ?: 0f
    val compareTopSpeed = compareGps.mapNotNull { it.spd }.maxOrNull() ?: 0f
    val primaryMs       = primaryLap?.laptime?.toLapTimeMillis() ?: 0L
    val compareMs       = compareLap?.laptime?.toLapTimeMillis() ?: 0L
    val deltaMs         = compareMs - primaryMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        if (isLoading) {
            LoadingView()
        } else if (primaryLap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lap not found", style = TrackProType.label, color = TrackProTheme.colors.textFaint)
            }
        } else {
            val lap = primaryLap!!

            // ── Full-screen Map ────────────────────────────
            LapHeatmapMapView(
                primaryGps  = primaryGps,
                compareGps  = compareGps,
                heatmapMode = heatmapMode,
                modifier    = Modifier.fillMaxSize()
            )

            // ── Top HUD ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                AppTopBar(
                    title = "Lap ${lap.lapnumber} · ${lap.laptime}",
                    accent = TrackProTheme.colors.accent,
                    onBack = { navController.popBackStack() },
                    trailing = {
                        Text(
                            "${UnitFormatter.formatSpeed(primaryTopSpeed, useMetric)} ${UnitFormatter.speedUnitLabel(useMetric)}",
                            style = TrackProType.label,
                            color = TrackProTheme.colors.textMuted
                        )
                    }
                )

                // Heatmap mode switcher
                Row(
                    modifier = Modifier
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .background(
                            TrackProTheme.colors.bgCard.copy(alpha = 0.92f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HeatmapMode.values().forEach { mode ->
                        val selected = mode == heatmapMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) TrackProTheme.colors.accent
                                    else Color.Transparent
                                )
                                .clickable { heatmapMode = mode }
                                .padding(horizontal = Spacing.md, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                mode.label,
                                style = TrackProType.label,
                                color = if (selected) TrackProTheme.colors.onAccent else TrackProTheme.colors.textMuted
                            )
                        }
                    }
                }
            }

            // ── Compare lap indicator (if active) ─────────
            compareLap?.let { cl ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp)
                        .background(
                            TrackProTheme.colors.bgCard.copy(alpha = 0.92f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, COMPARE_COLOR.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(COMPARE_COLOR, CircleShape)
                        )
                        Column {
                            Text("Compare · Lap ${cl.lapnumber}", style = TrackProType.label.copy(fontSize = 8.sp), color = COMPARE_COLOR)
                            Text(cl.laptime, style = TrackProType.titleMedium.copy(fontSize = 13.sp), color = TrackProTheme.colors.textPrimary)
                            if (compareMs > 0) {
                                val sign = if (deltaMs > 0) "+" else ""
                                Text(
                                    text = "${sign}${deltaMs.toLapTimeString()}",
                                    style = TrackProType.body.copy(fontSize = 10.sp),
                                    color = if (deltaMs < 0) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove compare",
                            tint = TrackProTheme.colors.textMuted,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    compareLap = null
                                    compareGps = emptyList()
                                }
                        )
                    }
                }
            }

            // ── Bottom action bar ──────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                // Stats panel (expandable)
                AnimatedVisibility(
                    visible = showStatsPanel,
                    enter = slideInVertically { it } + fadeIn(),
                    exit  = slideOutVertically { it } + fadeOut()
                ) {
                    StatsPanel(
                        primaryLap      = lap,
                        primaryGps      = primaryGps,
                        primarySectors  = primarySectors,
                        compareLap      = compareLap,
                        compareGps      = compareGps,
                        compareSectors  = compareSectors,
                        primaryTopSpeed = primaryTopSpeed,
                        compareTopSpeed = compareTopSpeed,
                        deltaMs         = deltaMs,
                        useMetric       = useMetric,
                        onDismiss       = { showStatsPanel = false }
                    )
                }

                // Action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgCard.copy(alpha = 0.95f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // Stats toggle
                    ActionButton(
                        label = if (showStatsPanel) "Hide Stats" else "Stats",
                        color = TrackProTheme.colors.accent,
                        modifier = Modifier.weight(1f)
                    ) { showStatsPanel = !showStatsPanel }

                    // Compare toggle
                    ActionButton(
                        label    = if (compareLap != null) "Comparing" else "Compare",
                        color    = if (compareLap != null) COMPARE_COLOR else TrackProTheme.colors.accent,
                        modifier = Modifier.weight(1f)
                    ) { showLapPicker = true }
                }
            }

            // ── Lap picker bottom sheet ────────────────────
            AnimatedVisibility(
                visible = showLapPicker,
                enter   = slideInVertically { it } + fadeIn(),
                exit    = slideOutVertically { it } + fadeOut()
            ) {
                LapPickerSheet(
                    laps         = allSessionLaps.filter { it.id != primaryLapId },
                    selectedLap  = compareLap,
                    primaryLapMs = primaryMs,
                    onSelect     = { selected ->
                        compareLap    = if (compareLap?.id == selected.id) null else selected
                        showLapPicker = false
                    },
                    onDismiss    = { showLapPicker = false }
                )
            }
        }
    }
}

// ── Heatmap Map View ───────────────────────────────────────

// These draw MapLibre GPS traces from plain Kotlin functions, which run outside any
// @Composable context and so can't read TrackProTheme.colors. Sourced from
// DataVizColors so the two series stay in step with the rest of the palette - this is
// the one sanctioned place in the app with two distinguishable hues, because telling
// two overlaid lap traces apart is a real data problem.
private val PRIMARY_COLOR  = Color(DataVizColors.seriesPrimary.toColorInt())
private val COMPARE_COLOR  = Color(DataVizColors.seriesCompare.toColorInt())

@Composable
fun LapHeatmapMapView(
    primaryGps: List<LapInfoData>,
    compareGps: List<LapInfoData>,
    heatmapMode: HeatmapMode,
    modifier: Modifier = Modifier
) {
    val mapViewRef = remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    val styleRef   = remember { mutableStateOf<Style?>(null) }

    // Redraw whenever data or mode changes
    LaunchedEffect(primaryGps, compareGps, heatmapMode) {
        val map   = mapViewRef.value ?: return@LaunchedEffect
        val style = styleRef.value   ?: return@LaunchedEffect
        drawAllLayers(style, primaryGps, compareGps, heatmapMode)
        if (primaryGps.isNotEmpty()) {
            fitCameraToGps(map, primaryGps + compareGps)
        }
    }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapViewRef.value = map
                    // Gestures ENABLED for research/zoom
                    map.uiSettings.setAllGesturesEnabled(true)
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        styleRef.value = style
                        drawAllLayers(style, primaryGps, compareGps, heatmapMode)
                        if (primaryGps.isNotEmpty()) {
                            fitCameraToGps(map, primaryGps + compareGps)
                        }
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun drawAllLayers(
    style: Style,
    primaryGps: List<LapInfoData>,
    compareGps: List<LapInfoData>,
    mode: HeatmapMode
) {
    // Remove old layers
    listOf(
        "primary-heat-layer", "compare-heat-layer",
        "primary-uniform-layer", "compare-uniform-layer",
        "start-dot-layer", "end-dot-layer"
    ).forEach { style.getLayer(it)?.let { l -> style.removeLayer(l) } }
    listOf(
        "primary-heat-src", "compare-heat-src",
        "primary-uniform-src", "compare-uniform-src",
        "start-dot-src", "end-dot-src"
    ).forEach { style.getSource(it)?.let { s -> style.removeSource(s) } }

    if (primaryGps.size < 2) return

    when (mode) {
        HeatmapMode.SPEED   -> {
            drawSpeedHeatmap(style, primaryGps, "primary", androidColorFrom(PRIMARY_COLOR))
            if (compareGps.size >= 2) {
                drawSpeedHeatmap(style, compareGps, "compare", androidColorFrom(COMPARE_COLOR))
            }
        }
        HeatmapMode.UNIFORM -> {
            drawUniformLine(style, primaryGps, "primary", androidColorFrom(PRIMARY_COLOR))
            if (compareGps.size >= 2) {
                drawUniformLine(style, compareGps, "compare", androidColorFrom(COMPARE_COLOR))
            }
        }
    }

    // Draw start/end dots
    drawEndpointDot(style, primaryGps.first(), "start", DataVizColors.startMarker)
    drawEndpointDot(style, primaryGps.last(),  "end",   DataVizColors.endMarker)
}

/** Draws a thin segmented line where each segment is colored by speed interpolation */
private fun drawSpeedHeatmap(
    style: Style,
    gps: List<LapInfoData>,
    prefix: String,
    baseColor: Int
) {
    val speeds = gps.mapNotNull { it.spd }
    val minSpd = speeds.minOrNull() ?: 0f
    val maxSpd = speeds.maxOrNull() ?: 1f

    // Build a FeatureCollection where each segment is a Feature with a speed-based color
    val features = mutableListOf<String>()
    for (i in 0 until gps.size - 1) {
        val pt0 = gps[i];  val pt1 = gps[i + 1]
        if (pt0.lat == null || pt0.lon == null ||
            pt1.lat == null || pt1.lon == null) continue

        val spd = ((pt0.spd ?: minSpd) + (pt1.spd ?: minSpd)) / 2f
        val t   = if (maxSpd > minSpd) (spd - minSpd) / (maxSpd - minSpd) else 0f
        val hex = SpeedColorUtils.speedToHex(t)

        features.add("""
            {
              "type":"Feature",
              "geometry":{"type":"LineString","coordinates":[[${pt0.lon},${pt0.lat}],[${pt1.lon},${pt1.lat}]]},
              "properties":{"color":"$hex"}
            }
        """.trimIndent())
    }

    val geojson = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    val srcId   = "$prefix-heat-src"
    val layerId = "$prefix-heat-layer"

    style.addSource(GeoJsonSource(srcId, geojson))
    style.addLayer(LineLayer(layerId, srcId).apply {
        setProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(2.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.92f)
        )
    })
}

/** Draws a plain thin uniform-color line */
private fun drawUniformLine(
    style: Style,
    gps: List<LapInfoData>,
    prefix: String,
    color: Int
) {
    val coords = gps
        .filter { it.lat != null && it.lon != null }
        .joinToString(",") { "[${it.lon},${it.lat}]" }

    if (coords.isEmpty()) return

    val geojson = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coords]},"properties":{}}"""
    val srcId   = "$prefix-uniform-src"
    val layerId = "$prefix-uniform-layer"
    val hex     = String.format("#%06X", 0xFFFFFF and color)

    style.addSource(GeoJsonSource(srcId, geojson))
    style.addLayer(LineLayer(layerId, srcId).apply {
        setProperties(
            PropertyFactory.lineColor(hex),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.9f)
        )
    })
}

private fun drawEndpointDot(style: Style, pt: LapInfoData, id: String, color: String) {
    if (pt.lat == null || pt.lon == null) return
    val geojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pt.lon},${pt.lat}]}}"""
    val srcId   = "$id-dot-src";  val layerId = "$id-dot-layer"
    style.getSource(srcId)?.let { style.removeSource(it) }
    style.getLayer(layerId)?.let { style.removeLayer(it) }
    style.addSource(GeoJsonSource(srcId, geojson))
    style.addLayer(CircleLayer(layerId, srcId).apply {
        setProperties(
            PropertyFactory.circleColor(color),
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleStrokeColor(DataVizColors.darkOutline),
            PropertyFactory.circleStrokeWidth(1.5f)
        )
    })
}

private fun fitCameraToGps(map: org.maplibre.android.maps.MapLibreMap, gps: List<LapInfoData>) {
    val valid = gps.filter { it.lat != null && it.lon != null }
    if (valid.isEmpty()) return
    val bb = LatLngBounds.Builder()
    valid.forEach { bb.include(LatLng(it.lat!!, it.lon!!)) }
    map.easeCamera(CameraUpdateFactory.newLatLngBounds(bb.build(), 80), 800)
}

private fun androidColorFrom(color: Color): Int =
    AndroidColor.argb(
        (color.alpha * 255).toInt(),
        (color.red   * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue  * 255).toInt()
    )

// ── Stats Panel ────────────────────────────────────────────

@Composable
private fun StatsPanel(
    primaryLap: LapTimeData,
    primaryGps: List<LapInfoData>,
    primarySectors: List<SectorTimeData>,
    compareLap: LapTimeData?,
    compareGps: List<LapInfoData>,
    compareSectors: List<SectorTimeData>,
    primaryTopSpeed: Float,
    compareTopSpeed: Float,
    deltaMs: Long,
    useMetric: Boolean,
    onDismiss: () -> Unit
) {
    val primaryAvgSpd = primaryGps.mapNotNull { it.spd }.let {
        if (it.isEmpty()) 0f else it.average().toFloat()
    }
    val compareAvgSpd = compareGps.mapNotNull { it.spd }.let {
        if (it.isEmpty()) 0f else it.average().toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                TrackProTheme.colors.bgCard.copy(alpha = 0.97f),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(bottom = 4.dp)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 6.dp)
                .width(40.dp)
                .height(3.dp)
                .background(TrackProTheme.colors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Lap Stats")
            Icon(
                Icons.Default.Close, "dismiss",
                tint = TrackProTheme.colors.textMuted,
                modifier = Modifier.size(16.dp).clickable { onDismiss() }
            )
        }

        HorizontalDivider(color = TrackProTheme.colors.sectorLine)

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Metric", style = TrackProType.label.copy(fontSize = 8.sp), color = TrackProTheme.colors.textMuted,
                modifier = Modifier.weight(1.4f))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size(6.dp).background(PRIMARY_COLOR, CircleShape))
                Text("Lap ${primaryLap.lapnumber}", style = TrackProType.label.copy(fontSize = 8.sp), color = PRIMARY_COLOR)
            }
            if (compareLap != null) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(6.dp).background(COMPARE_COLOR, CircleShape))
                    Text("Lap ${compareLap.lapnumber}", style = TrackProType.label.copy(fontSize = 8.sp), color = COMPARE_COLOR)
                }
            }
        }

        HorizontalDivider(color = TrackProTheme.colors.sectorLine)

        val speedUnit = UnitFormatter.speedUnitLabel(useMetric)
        val rows = buildList {
            add(Triple("Lap Time",    primaryLap.laptime, compareLap?.laptime ?: "—"))
            add(Triple("Top Speed",   "${UnitFormatter.formatSpeed(primaryTopSpeed, useMetric)} $speedUnit",
                if (compareLap != null) "${UnitFormatter.formatSpeed(compareTopSpeed, useMetric)} $speedUnit" else "—"))
            add(Triple("Avg Speed",   "${UnitFormatter.formatSpeedPrecise(primaryAvgSpd.toDouble(), useMetric)} $speedUnit",
                if (compareLap != null) "${UnitFormatter.formatSpeedPrecise(compareAvgSpd.toDouble(), useMetric)} $speedUnit" else "—"))
            add(Triple("GPS Points",  "${primaryGps.size}",
                if (compareLap != null) "${compareGps.size}" else "—"))
            primarySectors.sortedBy { it.sectorIndex }.forEach { sector ->
                val compareSplit = compareSectors.find { it.sectorIndex == sector.sectorIndex }
                add(Triple(
                    "Sector ${sector.sectorIndex + 1}",
                    String.format("%.2fs", sector.splitTimeMs / 1000.0),
                    if (compareLap != null) compareSplit?.let { String.format("%.2fs", it.splitTimeMs / 1000.0) } ?: "—" else "—"
                ))
            }
            if (compareLap != null) {
                val sign = if (deltaMs > 0) "+" else ""
                add(Triple("Delta", "${sign}${deltaMs.toLapTimeString()}", ""))
            }
        }

        rows.forEachIndexed { i, (label, v1, v2) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (i % 2 == 0) Color.Transparent
                        else TrackProTheme.colors.bgElevated.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label.uppercase(), style = TrackProType.label.copy(fontSize = 9.sp), color = TrackProTheme.colors.textMuted,
                    modifier = Modifier.weight(1.4f))
                Text(v1, style = TrackProType.body.copy(fontSize = 13.sp), color = if (label == "Delta" && deltaMs < 0) TrackProTheme.colors.deltaGood
                    else TrackProTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f))
                if (compareLap != null && v2.isNotEmpty()) {
                    Text(v2, style = TrackProType.body.copy(fontSize = 13.sp), color = TrackProTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f))
                }
            }
            if (i < rows.lastIndex) {
                HorizontalDivider(color = TrackProTheme.colors.sectorLine.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(Spacing.sm))
    }
}

// ── Lap Picker Sheet ───────────────────────────────────────

@Composable
private fun LapPickerSheet(
    laps: List<LapTimeData>,
    selectedLap: LapTimeData?,
    primaryLapMs: Long,
    onSelect: (LapTimeData) -> Unit,
    onDismiss: () -> Unit
) {
    val bestMs = laps.minByOrNull { it.laptime.toLapTimeMillis() }?.laptime?.toLapTimeMillis() ?: 0L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                TrackProTheme.colors.bgCard,
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .heightIn(max = 360.dp)
    ) {
        // Handle + header
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp, bottom = 8.dp)
                .width(40.dp)
                .height(3.dp)
                .background(TrackProTheme.colors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Select Lap to Compare")
            Icon(Icons.Default.Close, "close", tint = TrackProTheme.colors.textMuted,
                modifier = Modifier.size(16.dp).clickable { onDismiss() })
        }
        HorizontalDivider(color = TrackProTheme.colors.sectorLine)

        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp, horizontal = Spacing.sm)) {
            items(laps) { lap ->
                val lapMs    = lap.laptime.toLapTimeMillis()
                val deltaMs  = lapMs - primaryLapMs
                val isSelected = lap.id == selectedLap?.id
                val isBest   = lapMs == bestMs

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isSelected -> COMPARE_COLOR.copy(alpha = 0.15f)
                                isBest     -> TrackProTheme.colors.accent.copy(alpha = 0.05f)
                                else       -> Color.Transparent
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                isSelected -> COMPARE_COLOR.copy(alpha = 0.5f)
                                isBest     -> TrackProTheme.colors.accent.copy(alpha = 0.2f)
                                else       -> Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(lap) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            String.format("%02d", lap.lapnumber),
                            style = TrackProType.statValue.copy(fontSize = 16.sp),
                            color = if (isBest) TrackProTheme.colors.accent else TrackProTheme.colors.textPrimary
                        )
                        if (isBest) {
                            Box(
                                Modifier
                                    .background(TrackProTheme.colors.accent.copy(alpha = 0.15f), TrackProShapes.badge)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("Best", style = TrackProType.label.copy(fontSize = 7.sp), color = TrackProTheme.colors.accent)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Delta vs primary
                        val sign = if (deltaMs > 0) "+" else ""
                        Text(
                            "${sign}${deltaMs.toLapTimeString()}",
                            style = TrackProType.body.copy(fontSize = 11.sp),
                            color = if (deltaMs < 0) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad
                        )
                        Text(lap.laptime, style = TrackProType.titleMedium.copy(fontSize = 15.sp), color = TrackProTheme.colors.textPrimary)
                        if (isSelected) {
                            Box(Modifier.size(8.dp).background(COMPARE_COLOR, CircleShape))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
    }
}

// ── Loading ────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = TrackProTheme.colors.accent,
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(12.dp))
            Text("Loading lap", style = TrackProType.label, color = TrackProTheme.colors.textFaint)
        }
    }
}

// ── Action button ──────────────────────────────────────────

@Composable
private fun ActionButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TrackProType.titleMedium.copy(fontSize = 12.sp), color = color)
    }
}

// ── Navigation helper ──────────────────────────────────────
// Add to your NavGraph like:
//   composable("lap_detail/{sessionId}/{lapId}") { backStackEntry ->
//       LapDetailScreen(
//           navController = navController,
//           database      = database,
//           sessionId     = backStackEntry.arguments?.getString("sessionId")?.toLong() ?: -1,
//           primaryLapId  = backStackEntry.arguments?.getString("lapId")?.toLong() ?: -1
//       )
//   }
//
// Navigate from TimeAttackListItem lap row click:
//   navController.navigate("lap_detail/$sessionId/${lap.id}")

// ── Helpers ────────────────────────────────────────────────



// ── Preview ────────────────────────────────────────────────

@Preview
@Composable
fun LapDetailScreenPreview() {
    val fakeDb = Room.inMemoryDatabaseBuilder(
        LocalContext.current, ESPDatabase::class.java
    ).build()
    LapDetailScreen(
        navController = rememberNavController(),
        database      = fakeDb,
        sessionId     = 1L,
        primaryLapId  = 1L
    )
}
