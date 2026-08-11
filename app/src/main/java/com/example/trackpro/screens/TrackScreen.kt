package com.example.trackpro.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.pressable
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.timeAttackManagers.TrackGeometry
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource


@Composable
fun TrackScreen(trackId: Long) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val database = app.database
    TrackView(database, trackId)
}

@Composable
fun TrackView(database: ESPDatabase, trackId: Long) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val trackParts = remember { mutableStateListOf<TrackCoordinatesData>() }
    val trackInfo = remember {
        mutableStateOf(
            TrackMainData(
                trackId = 0L, trackName = "Loading...",
                totalLength = 0.0, country = "", type = "Circuit"
            )
        )
    }

    LaunchedEffect(trackId) {
        launch(Dispatchers.IO) {
            database.trackCoordinatesDao().getCoordinatesOfTrack(trackId).collect { parts ->
                trackParts.clear()
                trackParts.addAll(parts)
            }
        }
        launch(Dispatchers.IO) {
            database.trackMainDao().getTrack(trackId).collect { track ->
                trackInfo.value = track
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            AppTopBar(title = "Track Overview", accent = TrackProTheme.colors.accent)

            // ── Track info card ───────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.bgCard)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = trackInfo.value.trackName,
                    style = TrackProType.titleLarge,
                    color = TrackProTheme.colors.textPrimary
                )
                Text(
                    text = "${trackInfo.value.country} · ${trackInfo.value.type}",
                    style = TrackProType.body,
                    color = TrackProTheme.colors.textMuted
                )
                HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell(
                        label = "Length",
                        // totalLength is stored in km; formatDistance takes meters, so convert
                        // first. This also makes the unit dynamic instead of a hardcoded "km".
                        value = trackInfo.value.totalLength?.let { UnitFormatter.formatDistance(it * 1000.0, useMetric) } ?: "?",
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    StatCell(
                        label = "Type",
                        value = trackInfo.value.type.uppercase(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    StatCell(
                        label = "Corners",
                        value = "${trackParts.size}",
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                }
            }

            HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

            // ── Sectors card ────────────────────────────────────
            if (trackParts.isNotEmpty()) {
                SectorSlicerCard(
                    trackParts = trackParts,
                    onSlice = { sectorCount ->
                        val sliced = TrackGeometry.autoSliceSectors(trackParts.toList(), sectorCount)
                        coroutineScope.launch(Dispatchers.IO) {
                            database.trackCoordinatesDao().updateTrackCoordinates(sliced)
                        }
                    },
                    onClear = {
                        val cleared = TrackGeometry.autoSliceSectors(trackParts.toList(), 1)
                        coroutineScope.launch(Dispatchers.IO) {
                            database.trackCoordinatesDao().updateTrackCoordinates(cleared)
                        }
                    }
                )
                HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
            }

            // ── Map ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(TrackProTheme.colors.bgCard)
            ) {
                if (trackParts.isNotEmpty()) {
                    TrackStaticMapView(
                        trackParts = trackParts,
                        trackType = trackInfo.value.type,
                        modifier = Modifier.fillMaxSize()

                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = TrackProTheme.colors.accent,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Loading track data", style = TrackProType.label, color = TrackProTheme.colors.textFaint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lets the user auto-slice an already-saved track into N even sectors after the fact,
 * instead of only being able to mark them by hand while live-recording. Picking a count
 * replaces any previous slicing (the points are matched back to the DB by id).
 */
@Composable
private fun SectorSlicerCard(
    trackParts: List<TrackCoordinatesData>,
    onSlice: (Int) -> Unit,
    onClear: () -> Unit
) {
    val markedBoundaries = trackParts.count { it.isSectorPoint }
    val sectorCount = if (markedBoundaries > 0) markedBoundaries + 1 else 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.bgCard)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Sectors")
            Text(
                text = if (sectorCount > 0) "$sectorCount marked" else "None marked",
                style = TrackProType.body.atSize(11.sp),
                color = if (sectorCount > 0) TrackProTheme.colors.accent else TrackProTheme.colors.textMuted
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            (2..6).forEach { n ->
                ToggleChip(
                    text = "$n",
                    selected = sectorCount == n,
                    onClick = { onSlice(n) },
                    accent = TrackProTheme.colors.accent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (sectorCount > 0) {
            Text(
                text = "Clear sectors",
                style = TrackProType.label,
                color = TrackProTheme.colors.danger,
                modifier = Modifier
                    .pressable(onClick = onClear, scale = 0.94f, haptic = Haptic.Reject)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun TrackStaticMapView(
    trackParts: List<TrackCoordinatesData>,
    trackType: String,
    modifier: Modifier = Modifier
) {
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapRef by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<org.maplibre.android.maps.Style?>(null) }
    var hasFitCamera by remember { mutableStateOf(false) }

    // Redraw whenever the track's points change (e.g. after auto-slicing sectors) instead
    // of only ever drawing once on first load. trackParts is a SnapshotStateList mutated
    // in place, so its reference never changes - key on derived values instead, or this
    // would never restart.
    //
    // mapRef/styleRef are also keys, not just guards: getMapAsync/setStyle are async, and
    // trackParts is normally already fully loaded *before* the map finishes initializing
    // (this composable isn't even entered until trackParts is non-empty - see TrackView).
    // Without these as keys, the effect's first run would hit them still null, bail out,
    // and - since trackParts.size/sectorMarkCount never change again afterwards - never get
    // a second chance to run once the map actually becomes ready, leaving the track
    // permanently undrawn (default world view) despite the data being there.
    val sectorMarkCount = trackParts.count { it.isSectorPoint }
    LaunchedEffect(trackParts.size, sectorMarkCount, mapRef, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        if (trackParts.isEmpty()) return@LaunchedEffect

        listOf("track-static-layer", "start-layer", "sector-layer").forEach { id ->
            style.getLayer(id)?.let { style.removeLayer(it) }
        }
        listOf("track-static-src", "start-src", "sector-src").forEach { id ->
            style.getSource(id)?.let { style.removeSource(it) }
        }

        // A single (or empty) point can't form a line or a real bounding box - draw only
        // the start marker and center on it directly, rather than feeding MapLibre a
        // degenerate LineString/bounds.
        if (trackParts.size >= 2) {
            val coords = trackParts.joinToString(",") {
                "[${it.longitude},${it.latitude}]"
            }

            // Only close the loop if it's a Circuit
            val finalCoordinates = if (trackType == "Circuit") {
                val first = trackParts.first()
                "$coords,[${first.longitude},${first.latitude}]"
            } else {
                coords // Keep it as an open line for Sprints
            }

            val geojson = """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$finalCoordinates]},"properties":{}}"""
            style.addSource(GeoJsonSource("track-static-src", geojson))
            style.addLayer(
                LineLayer("track-static-layer", "track-static-src").apply {
                    setProperties(
                        PropertyFactory.lineColor(DataVizColors.trackLine),
                        PropertyFactory.lineWidth(3f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                    )
                }
            )
        }

        // Start/finish marker - a single point at the actual start coordinate
        val startPoint = trackParts.first()
        val startGeojson = """{"type":"Feature","geometry":{"type":"Point","coordinates":[${startPoint.longitude},${startPoint.latitude}]},"properties":{}}"""
        style.addSource(GeoJsonSource("start-src", startGeojson))
        style.addLayer(
            CircleLayer("start-layer", "start-src").apply {
                setProperties(
                    PropertyFactory.circleColor(DataVizColors.startMarker),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor(DataVizColors.boundaryLine),
                    PropertyFactory.circleStrokeWidth(2f)
                )
            }
        )

        // Sector markers, if any have been marked/auto-sliced
        val sectorPoints = trackParts.filter { it.isSectorPoint }
        if (sectorPoints.isNotEmpty()) {
            val sectorFeatures = sectorPoints.joinToString(",") {
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.longitude},${it.latitude}]},"properties":{}}"""
            }
            style.addSource(GeoJsonSource("sector-src", """{"type":"FeatureCollection","features":[$sectorFeatures]}"""))
            style.addLayer(
                CircleLayer("sector-layer", "sector-src").apply {
                    setProperties(
                        PropertyFactory.circleColor(DataVizColors.sectorMarker),
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleStrokeColor(DataVizColors.boundaryLine),
                        PropertyFactory.circleStrokeWidth(1.5f)
                    )
                }
            )
        }

        // Only fit the camera once - re-fitting on every sector change would be jarring.
        if (!hasFitCamera) {
            if (trackParts.size >= 2) {
                val bounds = LatLngBounds.Builder()
                    .includes(trackParts.map { LatLng(it.latitude, it.longitude) })
                    .build()
                map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, 64), 800)
            } else {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(startPoint.latitude, startPoint.longitude), 16.0)
                )
            }
            hasFitCamera = true
        }
    }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mapViewRef = mv
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapRef = map
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        map.uiSettings.setAllGesturesEnabled(true)
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        styleRef = style
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