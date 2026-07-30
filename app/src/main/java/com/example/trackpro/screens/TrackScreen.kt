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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.ESPDatabase
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

            // ── Top bar ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.accentBlue)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "● TRACK OVERVIEW",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            }

            // ── Track info card ───────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.bgCard)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = trackInfo.value.trackName,
                    color = TrackProTheme.colors.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${trackInfo.value.country} · ${trackInfo.value.type}",
                    color = TrackProTheme.colors.textMuted,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrackStatCol(
                        label = "LENGTH",
                        value = "${trackInfo.value.totalLength ?: "?"} km",
                        textPrimary = TrackProTheme.colors.textPrimary,
                        textMuted = TrackProTheme.colors.textMuted
                    )
                    TrackStatCol(
                        label = "TYPE",
                        value = trackInfo.value.type.uppercase(),
                        textPrimary = TrackProTheme.colors.textPrimary,
                        textMuted = TrackProTheme.colors.textMuted
                    )
                    TrackStatCol(
                        label = "CORNERS",
                        value = "${trackParts.size}",
                        textPrimary = TrackProTheme.colors.textPrimary,
                        textMuted = TrackProTheme.colors.textMuted
                    )
                }
            }

            HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

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
                                color = TrackProTheme.colors.accentBlue,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("LOADING TRACK DATA", color = TrackProTheme.colors.textMuted,
                                fontSize = 10.sp, letterSpacing = 2.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackStatCol(label: String, value: String, textPrimary: Color, textMuted: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = textMuted, fontSize = 9.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(value, color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun TrackStaticMapView(
    trackParts: List<TrackCoordinatesData>,
    trackType: String,
    modifier: Modifier = Modifier
) {
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    val trackDrawn = remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                mapViewRef = mv
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    map.setStyle("https://tiles.openfreemap.org/styles/dark") { style ->
                        map.uiSettings.setAllGesturesEnabled(true)
                        map.uiSettings.isCompassEnabled = false
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false

                        // A single (or empty) point can't form a line or a real bounding
                        // box - draw only the start marker and center on it directly,
                        // rather than feeding MapLibre a degenerate LineString/bounds.
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
                                        PropertyFactory.lineColor("#00C853"),
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
                                    PropertyFactory.circleColor("#E8001C"),
                                    PropertyFactory.circleRadius(8f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                    PropertyFactory.circleStrokeWidth(2f)
                                )
                            }
                        )

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
                        trackDrawn.value = true
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