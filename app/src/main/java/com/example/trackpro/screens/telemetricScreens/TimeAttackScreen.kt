package com.example.trackpro.screens.telemetricScreens

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.timeAttackManagers.SectorSplit
import com.example.trackpro.managerClasses.timeAttackManagers.TimingMode
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.rememberHaptics
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.Readout
import com.example.trackpro.components.SegmentBar
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellDivider
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.viewModels.TimeAttackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TimeAttackScreenView(
    trackId: Long? = null,
    vehicleId: Long? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val vm: TimeAttackViewModel = viewModel(
        factory = TimeAttackViewModelFactory(context)
    )

    // ── Collect all state ──────────────────────────────────
    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)

    val currentTime  by vm.currentTime.collectAsState()
    val bestTime     by vm.bestTime.collectAsState()
    val lastTime     by vm.lastTime.collectAsState()
    val delta        by vm.delta.collectAsState()
    val liveDelta    by vm.liveDelta.collectAsState()
    val eventCount   by vm.eventCount.collectAsState()
    val stintStart   by vm.stintStart.collectAsState()
    val fullTrack    by vm.fullTrack.collectAsState()
    val finishLine   by vm.finishLine.collectAsState()
    val startLine    by vm.startLine.collectAsState()
    val driver       by vm.driverPosition.collectAsState()
    val timingMode   by vm.timingMode.collectAsState()
    val lapSplits    by vm.currentLapSplits.collectAsState()

    // Prefer the continuously-updating delta (tracked by distance into the lap); fall back
    // to the static per-lap delta before a best-lap reference exists (e.g. lap 1).
    val effectiveDelta = liveDelta ?: delta
    val isLiveDelta = liveDelta != null

    val linesToShow by remember(timingMode, startLine, finishLine) {
        derivedStateOf {
            if (timingMode is TimingMode.Sprint) startLine + finishLine else finishLine
        }
    }

    LaunchedEffect(finishLine.size) {
        Log.d("TimeAttackScreen", "Finish line size: ${finishLine.size}")
        //Finish line coords:
        Log.d("TimeAttackScreen", "Finish line coords: $finishLine")
    }

    // ── Init track + session FIRST ─────────────────────────
    LaunchedEffect(trackId) {
        if (trackId == null || vehicleId == null) {
            return@LaunchedEffect
        }

        try {
            val track = withContext(Dispatchers.IO) {
                app.database.trackMainDao().getTrack(trackId).firstOrNull()
            }

            val mode = when (track?.type?.lowercase()) {
                "sprint" -> TimingMode.Sprint
                else     -> TimingMode.Circuit
            }

            vm.loadTrack(trackId, mode)
            // Both calls are no-ops if this screen has merely been rebuilt (rotation,
            // returning from background) rather than genuinely started.
            vm.ensureSession(trackId, vehicleId)
        } catch (e: Exception) {
            Log.e("TimeAttackScreen", "Initialization error: ${e.message}", e)
        }
    }

    // The single most useful haptic in the app: a lap closing is confirmed by feel, so
    // the driver doesn't have to look away from the track to know it registered.
    // Keyed on the counter itself, so it fires exactly once per lap.
    val haptics = rememberHaptics()
    LaunchedEffect(eventCount) {
        if (eventCount > 0) haptics.perform(Haptic.Confirm)
    }

    val gpsPoints = fullTrack //+ linesToShow
    val driverPos = driver ?: LatLonOffset(0.0, 0.0)

    // With the map off this becomes a pure driver HUD. Saved rather than remembered so
    // it survives rotation - someone who turned the map off does not want it back every
    // time the phone shifts orientation in a windscreen mount.
    var mapVisible by rememberSaveable { mutableStateOf(true) }

    when (LocalConfiguration.current.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> TimeAttackLandscapeLayout(
            timingMode  = timingMode,
            currentTime = currentTime,
            bestTime    = bestTime,
            lastTime    = lastTime,
            delta       = effectiveDelta,
            isLiveDelta = isLiveDelta,
            eventCount  = eventCount,
            stintStart  = stintStart,
            gpsPoints   = gpsPoints,
            driver      = driverPos,
            isConnected = isConnected,
            linesToShow = linesToShow,
            lapSplits   = lapSplits,
            mapVisible  = mapVisible,
            onToggleMap = { mapVisible = it },
            onBack      = onBack
        )
        else -> TimeAttackPortraitLayout(
            timingMode  = timingMode,
            currentTime = currentTime,
            bestTime    = bestTime,
            lastTime    = lastTime,
            delta       = effectiveDelta,
            isLiveDelta = isLiveDelta,
            eventCount  = eventCount,
            stintStart  = stintStart,
            gpsPoints   = gpsPoints,
            driver      = driverPos,
            isConnected = isConnected,
            linesToShow = linesToShow,
            lapSplits   = lapSplits,
            mapVisible  = mapVisible,
            onToggleMap = { mapVisible = it },
            onBack      = onBack
        )
    }

}
// ── Portrait ───────────────────────────────────────────────

@Composable
fun TimeAttackPortraitLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    isLiveDelta: Boolean = false,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset,
    isConnected: Boolean,
    linesToShow : List<TrackCoordinatesData>,
    lapSplits: List<SectorSplit> = emptyList(),
    mapVisible: Boolean,
    onToggleMap: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val deltaColor = if (delta <= 0) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad
    val eventName  = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeLabel  = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"
    val modeColor  = TrackProTheme.colors.accent
    // Gaining pushes the bar forward, so a lower-is-better delta is negated before
    // it reaches the bar. Two seconds fills it; past that the exact figure has
    // stopped being actionable and pinning is the honest response.
    val deltaFraction = (-delta / 2.0).coerceIn(-1.0, 1.0).toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
    ) {

        AppTopBar(
            title = modeLabel,
            onBack = onBack,
            accent = modeColor,
            trailing = { HudTrailing(isConnected, mapVisible, onToggleMap) }
        )

        // -- Delta: the largest thing on the screen --------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.field)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Readout(
                value = String.format("%+.3f", delta),
                caption = if (isLiveDelta) "Delta to best - live" else "Delta to best",
                valueColor = deltaColor,
                valueSize = if (mapVisible) 60.sp else 84.sp,
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$eventCount",
                            style = TrackProType.displayNumeric.atSize(28.sp),
                            color = TrackProTheme.colors.marking
                        )
                        Text(
                            text = eventName,
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            SegmentBar(
                signedFraction = deltaFraction,
                activeColor = deltaColor,
                segments = 25,
                height = if (mapVisible) 18.dp else 26.dp
            )
        }

        Bezel()

        // -- Clocks ---------------------------------------
        Row(modifier = Modifier.fillMaxWidth()) {
            Instrument(
                label = "Current $eventName",
                value = currentTime,
                valueSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
            Bezel(vertical = true, modifier = Modifier.height(62.dp))
            Instrument(
                label = "Best",
                value = bestTime,
                valueColor = TrackProTheme.colors.accent,
                valueSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
            Bezel(vertical = true, modifier = Modifier.height(62.dp))
            Instrument(
                label = "Last",
                value = lastTime,
                valueSize = 24.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Bezel()

        if (lapSplits.isNotEmpty()) {
            SectorSplitsRow(splits = lapSplits)
            Bezel()
        }

        if (mapVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TrackProTheme.colors.panel)
            ) {
                if (gpsPoints.isNotEmpty()) {
                    MapLibreTrackView(
                        gpsPoints = gpsPoints,
                        driverPosition = driver,
                        modifier = Modifier.fillMaxSize(),
                        linesToShow
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "AWAITING GPS",
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth()) {
                StintTimerCell(stintStart = stintStart, modifier = Modifier.weight(1f))
            }
            Bezel()
        }
    }
}



// ── Landscape ──────────────────────────────────────────────

@Composable
fun TimeAttackLandscapeLayout(
    timingMode: TimingMode,
    currentTime: String,
    bestTime: String,
    lastTime: String,
    delta: Double,
    isLiveDelta: Boolean = false,
    eventCount: Int,
    stintStart: Long,
    gpsPoints: List<TrackCoordinatesData>,
    driver: LatLonOffset,
    isConnected: Boolean,
    linesToShow: List<TrackCoordinatesData>,
    lapSplits: List<SectorSplit> = emptyList(),
    mapVisible: Boolean,
    onToggleMap: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val deltaColor = if (delta <= 0) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad
    val eventName  = if (timingMode is TimingMode.Circuit) "LAP" else "RUN"
    val modeLabel  = if (timingMode is TimingMode.Circuit) "CIRCUIT" else "SPRINT"
    val modeColor  = TrackProTheme.colors.accent
    // Gaining pushes the bar forward, so a lower-is-better delta is negated before
    // it reaches the bar. Two seconds fills it; past that the exact figure has
    // stopped being actionable and pinning is the honest response.
    val deltaFraction = (-delta / 2.0).coerceIn(-1.0, 1.0).toFloat()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
    ) {
        Column(
            modifier = Modifier
                .weight(if (mapVisible) 0.46f else 1f)
                .fillMaxSize()
        ) {
            AppTopBar(
                title = modeLabel,
                onBack = onBack,
                accent = modeColor,
                trailing = { HudTrailing(isConnected, mapVisible, onToggleMap) }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.field)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Readout(
                    value = String.format("%+.3f", delta),
                    caption = if (isLiveDelta) "Delta to best - live" else "Delta to best",
                    valueColor = deltaColor,
                    valueSize = if (mapVisible) 56.sp else 76.sp,
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$eventCount",
                                style = TrackProType.displayNumeric.atSize(26.sp),
                                color = TrackProTheme.colors.marking
                            )
                            Text(
                                text = eventName,
                                style = TrackProType.label,
                                color = TrackProTheme.colors.markingDim
                            )
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                SegmentBar(
                    signedFraction = deltaFraction,
                    activeColor = deltaColor,
                    segments = 25,
                    height = if (mapVisible) 16.dp else 24.dp
                )
            }

            Bezel()

            Row(modifier = Modifier.fillMaxWidth()) {
                Instrument(
                    label = "Current $eventName",
                    value = currentTime,
                    valueSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                Bezel(vertical = true, modifier = Modifier.height(58.dp))
                Instrument(
                    label = "Best",
                    value = bestTime,
                    valueColor = TrackProTheme.colors.accent,
                    valueSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Bezel()
            Row(modifier = Modifier.fillMaxWidth()) {
                Instrument(
                    label = "Last",
                    value = lastTime,
                    valueSize = 22.sp,
                    modifier = Modifier.weight(1f)
                )
                Bezel(vertical = true, modifier = Modifier.height(58.dp))
                StintTimerCell(stintStart = stintStart, modifier = Modifier.weight(1f))
            }
            Bezel()

            if (lapSplits.isNotEmpty()) {
                SectorSplitsRow(splits = lapSplits)
            }
        }

        if (mapVisible) {
            Box(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxSize()
                    .background(TrackProTheme.colors.panel)
            ) {
                if (gpsPoints.isNotEmpty()) {
                    MapLibreTrackView(
                        gpsPoints = gpsPoints,
                        driverPosition = driver,
                        modifier = Modifier.fillMaxSize(),
                        linesToShow
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "AWAITING GPS",
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                }
            }
        }
    }
}



// ── Shared sub-components ──────────────────────────────────

/**
 * Top-bar trailing block: signal state plus the map switch.
 *
 * The map is both the most expensive thing on this screen - a MapLibre surface redrawing
 * on every GPS tick - and the thing a driver needs least mid-session. Switching it off
 * leaves only what gets read at speed.
 */
@Composable
private fun HudTrailing(
    isConnected: Boolean,
    mapVisible: Boolean,
    onToggleMap: (Boolean) -> Unit
) {
    val haptics = rememberHaptics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isConnected) "LIVE" else "NO SIGNAL",
            style = TrackProType.label,
            color = if (isConnected) TrackProTheme.colors.deltaGood else TrackProTheme.colors.textFaint
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = "MAP",
            style = TrackProType.label,
            color = if (mapVisible) TrackProTheme.colors.textPrimary else TrackProTheme.colors.textFaint
        )
        Spacer(Modifier.width(4.dp))
        Switch(
            checked = mapVisible,
            onCheckedChange = {
                // A discrete commit that changes the whole screen - earns a haptic.
                haptics.perform(Haptic.Selection)
                onToggleMap(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = TrackProTheme.colors.onAccent,
                checkedTrackColor = TrackProTheme.colors.accent,
                checkedBorderColor = TrackProTheme.colors.accent,
                uncheckedThumbColor = TrackProTheme.colors.textMuted,
                uncheckedTrackColor = TrackProTheme.colors.bgElevated,
                uncheckedBorderColor = TrackProTheme.colors.sectorLine
            )
        )
    }
}

@Composable
private fun StintTimerCell(stintStart: Long, modifier: Modifier = Modifier) {
    var stintTime by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(stintStart) {
        while (true) {
            delay(500)
            val elapsed = SystemClock.elapsedRealtime() - stintStart
            val h = elapsed / 3_600_000
            val m = (elapsed % 3_600_000) / 60_000
            val s = (elapsed % 60_000) / 1_000
            stintTime = String.format("%02d:%02d:%02d", h, m, s)
        }
    }
    Instrument(label = "Stint", value = stintTime, valueSize = 22.sp, modifier = modifier)
}

@Composable
private fun SectorSplitsRow(splits: List<SectorSplit>) {
    // The labanotation discipline the direction inherited: a sector's block is as wide as
    // the time it took, so a slow sector is visibly longer before any digit is read. The
    // numbers stay underneath for the deliberate read; the widths are the glance.
    val total = splits.sumOf { it.splitMs }.coerceAtLeast(1L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.field)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        splits.forEach { split ->
            val gained = (split.deltaMs ?: 0L) <= 0L
            val color = when {
                split.deltaMs == null -> TrackProTheme.colors.markingDim
                gained -> TrackProTheme.colors.deltaGood
                else -> TrackProTheme.colors.deltaBad
            }
            Column(
                modifier = Modifier.weight(
                    (split.splitMs.toFloat() / total.toFloat()).coerceAtLeast(0.08f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(color)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "S${split.sectorIndex + 1}",
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
                Text(
                    text = String.format("%.2f", split.splitMs / 1000.0),
                    style = TrackProType.statValue.atSize(14.sp),
                    color = color
                )
            }
        }
    }
}

// ── Reusable sub-components ────────────────────────────────
@Composable
fun MapLibreTrackView(
    gpsPoints: List<TrackCoordinatesData>,
    driverPosition: LatLonOffset,
    modifier: Modifier = Modifier,
    linesToShow: List<TrackCoordinatesData>
) {
    val driverSource = remember { mutableStateOf<GeoJsonSource?>(null) }
    val mapReady = remember { mutableStateOf<MapLibreMap?>(null) }

    // 1. Only update the Driver Source (No Camera Movement)
    LaunchedEffect(driverPosition) {
        Log.d("MapLibreTrackView", "Driver position changed: ${driverPosition.lat}, ${driverPosition.lon}")
        val src = driverSource.value ?: run {
            Log.w("MapLibreTrackView", "Driver source is null!")
            return@LaunchedEffect
        }
        if (driverPosition.lat == 0.0 && driverPosition.lon == 0.0) {
            Log.w("MapLibreTrackView", "Driver position is 0,0, skipping")
            return@LaunchedEffect
        }

        val geojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${driverPosition.lon},${driverPosition.lat}]},"properties":{}}"""
        Log.d("MapLibreTrackView", "Updating driver GeoJSON: $geojson")
        src.setGeoJson(geojson)
    }

    // 2. Separate Effect to fit the camera to the track whenever the track data changes
    LaunchedEffect(gpsPoints) {
        val map = mapReady.value ?: return@LaunchedEffect
        if (gpsPoints.isNotEmpty()) {
            fitCameraToTrack(map, gpsPoints)
        }
    }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapReady.value = map
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        // Disable gestures so the user doesn't accidentally move away from the track
                        map.uiSettings.setAllGesturesEnabled(false)

                        if (gpsPoints.isNotEmpty()) {
                            drawTrackOnStyle(style, gpsPoints,linesToShow)
                            fitCameraToTrack(map, gpsPoints) // Initial fit
                        }

                        // Driver position marker
                        val src = GeoJsonSource(
                            "driver-src",
                            """{"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]},"properties":{}}"""
                        )
                        style.addSource(src)
                        style.addLayer(
                            CircleLayer("driver-layer", "driver-src").apply {
                                setProperties(
                                    PropertyFactory.circleColor(DataVizColors.boundaryLine),
                                    PropertyFactory.circleRadius(6f),
                                    PropertyFactory.circleStrokeColor(DataVizColors.trackLine),
                                    PropertyFactory.circleStrokeWidth(2f)
                                )
                            }
                        )
                        driverSource.value = src
                    }
                }
            }
        },
        modifier = modifier
    )
}

private fun fitCameraToTrack(map: MapLibreMap, points: List<TrackCoordinatesData>) {
    if (points.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    points.forEach {
        boundsBuilder.include(LatLng(it.latitude, it.longitude))
    }

    map.easeCamera(
        CameraUpdateFactory.newLatLngBounds(
            boundsBuilder.build(),
            100 // Padding in pixels from the edges of the view
        ), 1000 // Animation duration
    )
}

private fun drawTrackOnStyle(
    style: Style,
    trackPath: List<TrackCoordinatesData>,
    timingLines: List<TrackCoordinatesData>
) {
    if (trackPath.size < 2) return

    // ── 1. Determine Drawing Mode ──
    // Sprint logic usually has 4 points (2 for start, 2 for finish).
    // Circuit logic usually has 2 points (just the finish line).
    val isSprint = timingLines.size > 2

    val displayPath = if (isSprint) {
        val start = timingLines.first()
        val finish = timingLines.last()
        extractSprintSegment(trackPath, start, finish)
    } else {
        // It's a Circuit: Draw the full path from the DB
        trackPath
    }

    if (displayPath.size < 2) return

    // ── 2. Draw the Main Track Line ──
    val trackCoords = displayPath.joinToString(",") {
        "[${it.longitude},${it.latitude}]"
    }

    val trackGeoJson = """
        {
            "type":"Feature",
            "geometry":{ "type":"LineString", "coordinates":[ $trackCoords ] },
            "properties":{}
        }
    """.trimIndent()

    // Add Source (Remove old one if it exists to avoid crashes on update)
    style.getSource("track-src")?.let { style.removeSource(it) }
    style.getLayer("track-layer")?.let { style.removeLayer(it) }

    style.addSource(GeoJsonSource("track-src", trackGeoJson))
    style.addLayer(
        LineLayer("track-layer", "track-src").apply {
            setProperties(
                PropertyFactory.lineColor(DataVizColors.trackLine),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
    )

    // ── 3. Draw Track Boundaries ──
    // Use the displayPath to calculate boundaries so they match the track
    drawBoundaries(style, displayPath)

    // ── 4. Draw Start/Finish Markers ──
    drawMarkers(style, timingLines)
}

private fun drawBoundaries(style: Style, path: List<TrackCoordinatesData>) {
    val trackWidth = 4.0 // meters
    val sides = listOf("left" to -trackWidth/2, "right" to trackWidth/2)

    sides.forEach { (side, offset) ->
        val boundaryCoords = calculateParallelLine(path, offset)
        val id = "$side-boundary"

        val geojson = """
            {
                "type":"Feature",
                "geometry":{
                    "type":"LineString",
                    "coordinates": ${boundaryCoords.map { "[${it.first}, ${it.second}]" }}
                }
            }
        """.trimIndent()

        style.getSource(id)?.let { style.removeSource(it) }
        style.getLayer("$id-layer")?.let { style.removeLayer(it) }

        style.addSource(GeoJsonSource(id, geojson))
        style.addLayer(LineLayer("$id-layer", id).apply {
            setProperties(
                PropertyFactory.lineColor(DataVizColors.boundaryLine),
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.3f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f))
            )
        })
    }
}

private fun drawMarkers(style: Style, timingLines: List<TrackCoordinatesData>) {
    val markers = if (timingLines.size >= 4) {
        listOf(timingLines[0], timingLines.last()) // Sprint: Show both
    } else if (timingLines.isNotEmpty()) {
        listOf(timingLines.first()) // Circuit: Show just the finish line
    } else emptyList()

    markers.forEachIndexed { i, pt ->
        val id = "marker-$i"
        val geojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${pt.longitude},${pt.latitude}]}}"""

        style.getSource(id)?.let { style.removeSource(it) }
        style.addSource(GeoJsonSource(id, geojson))
        style.addLayer(CircleLayer("$id-layer", id).apply {
            setProperties(
                PropertyFactory.circleColor(DataVizColors.boundaryLine),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(DataVizColors.trackLine)
            )
        })
    }
}

private fun findClosestIndex(
    track: List<TrackCoordinatesData>,
    target: TrackCoordinatesData
): Int {
    return track.indices.minByOrNull { i ->
        val dLat = track[i].latitude - target.latitude
        val dLon = track[i].longitude - target.longitude
        dLat * dLat + dLon * dLon
    } ?: 0
}

private fun extractSprintSegment(
    track: List<TrackCoordinatesData>,
    start: TrackCoordinatesData,
    finish: TrackCoordinatesData
): List<TrackCoordinatesData> {

    if (track.isEmpty()) return emptyList()

    val startIndex = findClosestIndex(track, start)
    val finishIndex = findClosestIndex(track, finish)

    return if (startIndex <= finishIndex) {
        track.subList(startIndex, finishIndex + 1)
    } else {
        track.subList(finishIndex, startIndex + 1)
    }
}

// Helper function to calculate parallel lines for track boundaries
private fun calculateParallelLine(
    points: List<TrackCoordinatesData>,
    offsetMeters: Double
): List<Pair<Double, Double>> {
    if (points.size < 2) return emptyList()

    return points.mapIndexed { index, point ->
        val bearing = when {
            index == 0 -> {
                // First point: use bearing to next point
                calculateBearing(point, points[index + 1])
            }
            index == points.lastIndex -> {
                // Last point: use bearing from previous point
                calculateBearing(points[index - 1], point)
            }
            else -> {
                // Middle points: average bearing
                val bearingFrom = calculateBearing(points[index - 1], point)
                val bearingTo = calculateBearing(point, points[index + 1])
                (bearingFrom + bearingTo) / 2.0
            }
        }

        // Calculate perpendicular offset (90 degrees to the right)
        val perpBearing = bearing + 90.0
        offsetPoint(point.latitude, point.longitude, offsetMeters, perpBearing)
    }
}

private fun calculateBearing(from: TrackCoordinatesData, to: TrackCoordinatesData): Double {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)

    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val bearing = Math.toDegrees(atan2(y, x))

    return (bearing + 360) % 360
}

private fun offsetPoint(lat: Double, lon: Double, distanceMeters: Double, bearing: Double): Pair<Double, Double> {
    val earthRadius = 6371000.0 // meters
    val angularDistance = distanceMeters / earthRadius
    val bearingRad = Math.toRadians(bearing)
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)

    val newLatRad = asin(
        sin(latRad) * cos(angularDistance) +
                cos(latRad) * sin(angularDistance) * cos(bearingRad)
    )

    val newLonRad = lonRad + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(latRad),
        cos(angularDistance) - sin(latRad) * sin(newLatRad)
    )

    return Pair(Math.toDegrees(newLonRad), Math.toDegrees(newLatRad))
}

class TimeAttackViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimeAttackViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimeAttackViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}