package com.example.trackpro.screens.listViewScreens.lapDetail

import TrackProTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.trackpro.dataClasses.LapInfoData
import com.example.trackpro.dataClasses.LapTimeData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.theme.TrackProColors
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
    // ── State ──────────────────────────────────────────────
    var allSessionLaps  by remember { mutableStateOf<List<LapTimeData>>(emptyList()) }
    var primaryLap      by remember { mutableStateOf<LapTimeData?>(null) }
    var primaryGps      by remember { mutableStateOf<List<LapInfoData>>(emptyList()) }
    var compareLap      by remember { mutableStateOf<LapTimeData?>(null) }
    var compareGps      by remember { mutableStateOf<List<LapInfoData>>(emptyList()) }
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
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    // ── Load compare lap gps when compareLap changes ───────
    LaunchedEffect(compareLap) {
        compareLap?.let { cl ->
            withContext(Dispatchers.IO) {
                compareGps = database.lapInfoDataDAO().getLapData(cl.id)
            }
        } ?: run { compareGps = emptyList() }
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
            .background(TrackProColors.BgDeep)
    ) {
        if (isLoading) {
            LoadingView()
        } else if (primaryLap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("LAP NOT FOUND", color = TrackProColors.TextMuted,
                    fontSize = 12.sp, letterSpacing = 3.sp)
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
                // Status bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.AccentGreen)
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Back button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { navController.popBackStack() }
                                    .background(Color.Black.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("← BACK", color = Color.Black, fontSize = 9.sp,
                                    fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            }
                            Text(
                                "LAP ${lap.lapnumber}  ·  ${lap.laptime}",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            )
                        }
                        Text(
                            "⚡ ${String.format("%.0f", primaryTopSpeed)} km/h",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Heatmap mode switcher
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .background(
                            TrackProColors.BgCard.copy(alpha = 0.92f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HeatmapMode.values().forEach { mode ->
                        val selected = mode == heatmapMode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (selected) TrackProColors.AccentGreen
                                    else Color.Transparent
                                )
                                .clickable { heatmapMode = mode }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${mode.icon} ${mode.label}",
                                color = if (selected) Color.Black else TrackProColors.TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
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
                            TrackProColors.BgCard.copy(alpha = 0.92f),
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
                            Text("COMPARE · LAP ${cl.lapnumber}",
                                color = COMPARE_COLOR, fontSize = 8.sp,
                                fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text(cl.laptime,
                                color = TrackProColors.TextPrimary, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                            if (compareMs > 0) {
                                val sign = if (deltaMs > 0) "+" else ""
                                Text(
                                    text = "${sign}${deltaMs.toLapTimeString()}",
                                    color = if (deltaMs < 0) TrackProColors.DeltaGood else TrackProColors.DeltaBad,
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove compare",
                            tint = TrackProColors.TextMuted,
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
                        compareLap      = compareLap,
                        compareGps      = compareGps,
                        primaryTopSpeed = primaryTopSpeed,
                        compareTopSpeed = compareTopSpeed,
                        deltaMs         = deltaMs,
                        onDismiss       = { showStatsPanel = false }
                    )
                }

                // Action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.BgCard.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Stats toggle
                    ActionButton(
                        label = if (showStatsPanel) "HIDE STATS" else "STATS",
                        icon  = "📊",
                        color = TrackProColors.AccentGreen,
                        modifier = Modifier.weight(1f)
                    ) { showStatsPanel = !showStatsPanel }

                    // Compare toggle
                    ActionButton(
                        label    = if (compareLap != null) "COMPARING" else "COMPARE",
                        icon     = "⚖",
                        color    = if (compareLap != null) COMPARE_COLOR else TrackProColors.AccentAmber,
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

private val PRIMARY_COLOR  = Color(0xFF8989FF) // AccentGreen from theme
private val COMPARE_COLOR  = Color(0xFF5BB6A8) // AccentAmber from theme

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
    drawEndpointDot(style, primaryGps.first(), "start", "#00E676")
    drawEndpointDot(style, primaryGps.last(),  "end",   "#FF1744")
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
        val hex = speedToHex(t)

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
            PropertyFactory.circleStrokeColor("#0E1117"),
            PropertyFactory.circleStrokeWidth(1.5f)
        )
    })
}

/** t in 0..1 → blue (slow) → green → yellow → red (fast) */
private fun speedToHex(t: Float): String {
    val clamped = t.coerceIn(0f, 1f)
    val r: Int; val g: Int; val b: Int
    when {
        clamped < 0.25f -> {
            val s = clamped / 0.25f
            r = 0; g = (s * 180).toInt(); b = 255
        }
        clamped < 0.5f -> {
            val s = (clamped - 0.25f) / 0.25f
            r = 0; g = (180 + s * 75).toInt(); b = (255 * (1 - s)).toInt()
        }
        clamped < 0.75f -> {
            val s = (clamped - 0.5f) / 0.25f
            r = (s * 255).toInt(); g = 255; b = 0
        }
        else -> {
            val s = (clamped - 0.75f) / 0.25f
            r = 255; g = (255 * (1 - s)).toInt(); b = 0
        }
    }
    return String.format("#%02X%02X%02X", r.coerceIn(0,255), g.coerceIn(0,255), b.coerceIn(0,255))
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
    compareLap: LapTimeData?,
    compareGps: List<LapInfoData>,
    primaryTopSpeed: Float,
    compareTopSpeed: Float,
    deltaMs: Long,
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
                TrackProColors.BgCard.copy(alpha = 0.97f),
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
                .background(TrackProColors.TextMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LAP STATS", color = TrackProColors.TextMuted, fontSize = 9.sp,
                fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Icon(
                Icons.Default.Close, "dismiss",
                tint = TrackProColors.TextMuted,
                modifier = Modifier.size(16.dp).clickable { onDismiss() }
            )
        }

        HorizontalDivider(color = TrackProColors.SectorLine)

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("METRIC", color = TrackProColors.TextMuted, fontSize = 8.sp,
                fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                modifier = Modifier.weight(1.4f))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size(6.dp).background(PRIMARY_COLOR, CircleShape))
                Text("LAP ${primaryLap.lapnumber}", color = PRIMARY_COLOR, fontSize = 8.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
            if (compareLap != null) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(Modifier.size(6.dp).background(COMPARE_COLOR, CircleShape))
                    Text("LAP ${compareLap.lapnumber}", color = COMPARE_COLOR, fontSize = 8.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }

        HorizontalDivider(color = TrackProColors.SectorLine)

        val rows = buildList {
            add(Triple("LAP TIME",    primaryLap.laptime, compareLap?.laptime ?: "—"))
            add(Triple("TOP SPEED",   "${String.format("%.0f", primaryTopSpeed)} km/h",
                if (compareLap != null) "${String.format("%.0f", compareTopSpeed)} km/h" else "—"))
            add(Triple("AVG SPEED",   "${String.format("%.1f", primaryAvgSpd)} km/h",
                if (compareLap != null) "${String.format("%.1f", compareAvgSpd)} km/h" else "—"))
            add(Triple("GPS POINTS",  "${primaryGps.size}",
                if (compareLap != null) "${compareGps.size}" else "—"))
            if (compareLap != null) {
                val sign = if (deltaMs > 0) "+" else ""
                add(Triple("DELTA", "${sign}${deltaMs.toLapTimeString()}", ""))
            }
        }

        rows.forEachIndexed { i, (label, v1, v2) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (i % 2 == 0) Color.Transparent
                        else TrackProColors.BgElevated.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, color = TrackProColors.TextMuted, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    modifier = Modifier.weight(1.4f))
                Text(v1, color = if (label == "DELTA" && deltaMs < 0) TrackProColors.DeltaGood
                    else TrackProColors.TextPrimary,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                if (compareLap != null && v2.isNotEmpty()) {
                    Text(v2, color = if (label == "DELTA") TrackProColors.AccentRed
                        else TrackProColors.TextPrimary,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                }
            }
            if (i < rows.lastIndex) {
                HorizontalDivider(color = TrackProColors.SectorLine.copy(alpha = 0.5f))
            }
        }

        Spacer(Modifier.height(12.dp))
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
                TrackProColors.BgCard,
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
                .background(TrackProColors.TextMuted.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SELECT LAP TO COMPARE", color = TrackProColors.TextMuted, fontSize = 9.sp,
                fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Icon(Icons.Default.Close, "close", tint = TrackProColors.TextMuted,
                modifier = Modifier.size(16.dp).clickable { onDismiss() })
        }
        HorizontalDivider(color = TrackProColors.SectorLine)

        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)) {
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
                                isBest     -> TrackProColors.AccentGreen.copy(alpha = 0.05f)
                                else       -> Color.Transparent
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                isSelected -> COMPARE_COLOR.copy(alpha = 0.5f)
                                isBest     -> TrackProColors.AccentGreen.copy(alpha = 0.2f)
                                else       -> Color.Transparent
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(lap) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            String.format("%02d", lap.lapnumber),
                            color = if (isBest) TrackProColors.AccentGreen else TrackProColors.TextPrimary,
                            fontSize = 18.sp, fontWeight = FontWeight.Black
                        )
                        if (isBest) {
                            Box(
                                Modifier
                                    .background(TrackProColors.AccentGreen.copy(alpha = 0.15f),
                                        RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("BEST", color = TrackProColors.AccentGreen, fontSize = 7.sp,
                                    fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Delta vs primary
                        val sign = if (deltaMs > 0) "+" else ""
                        Text(
                            "${sign}${deltaMs.toLapTimeString()}",
                            color = if (deltaMs < 0) TrackProColors.DeltaGood else TrackProColors.DeltaBad,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                        Text(lap.laptime, color = TrackProColors.TextPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        if (isSelected) {
                            Box(Modifier.size(8.dp).background(COMPARE_COLOR, CircleShape))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Loading ────────────────────────────────────────────────

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = TrackProColors.AccentGreen,
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(12.dp))
            Text("LOADING LAP", color = TrackProColors.TextMuted,
                fontSize = 10.sp, letterSpacing = 3.sp)
        }
    }
}

// ── Action button ──────────────────────────────────────────

@Composable
private fun ActionButton(
    label: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(icon, fontSize = 14.sp)
            Text(label, color = color, fontSize = 10.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        }
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

fun String.toLapTimeMillis(): Long {
    val parts = this.split(":", ".", limit = 3)
    val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val millis  = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return minutes * 60_000 + seconds * 1_000 + millis
}

fun Long.toLapTimeString(): String {
    val abs     = if (this < 0) -this else this
    val minutes = abs / 60_000
    val seconds = (abs % 60_000) / 1_000
    val millis  = abs % 1_000
    return String.format("%02d:%02d.%03d", minutes, seconds, millis)
}

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
