package com.example.trackpro.screens
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.room.Room
import com.example.trackpro.TrackProApp
import com.example.trackpro.managerClasses.calculationClasses.DragTimeCalculation
import com.example.trackpro.managerClasses.calculationClasses.PostProcessing
import com.example.trackpro.dataClasses.TrackCoordinatesData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.LatLonOffset
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.DashGroup
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.RoadRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun TrackBuilderScreen(
    database: ESPDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val coroutineScope = rememberCoroutineScope()

    val gpsData by app.gpsManager.activeGpsFlow.collectAsState(initial = null)

    // State
    var isLiveRecording by remember { mutableStateOf(false) }
    var trackID by remember { mutableLongStateOf(-1) }
    var trackMode by remember { mutableStateOf("Circuit") }
    var trackName by remember { mutableStateOf("") }
    var countryName by remember { mutableStateOf("") }

    // Track Points - the full, dense path that gets rendered and saved.
    val gpsPointsList = remember { mutableStateListOf<TrackCoordinatesData>() }
    var sectorCount by remember { mutableIntStateOf(0) }

    // UI State
    var showInfoDialog by remember { mutableStateOf(false) }
    var builderType by remember { mutableIntStateOf(0) } // 0: Live GPS, 1: Manual Map

    // --- Manual-mode routing state ---
    // waypoints are the points the user actually tapped; routedLegs[i] is the road-following
    // polyline from waypoints[i] to waypoints[i+1], excluding its start point so the legs
    // concatenate without duplicating shared points. gpsPointsList is derived from these.
    val waypoints = remember { mutableStateListOf<LatLonOffset>() }
    val routedLegs = remember { mutableStateListOf<List<LatLonOffset>>() }
    // Waypoint indices marked as sector boundaries. Stored against waypoints rather than
    // against gpsPointsList because the latter is rebuilt from scratch on every new leg.
    val sectorWaypoints = remember { mutableStateListOf<Int>() }
    var isRouting by remember { mutableStateOf(false) }
    var lastLegFellBack by remember { mutableStateOf(false) }

    fun rebuildManualPath() {
        gpsPointsList.clear()
        if (waypoints.isEmpty()) return

        fun sectorIdxFor(waypointIdx: Int): Int? =
            sectorWaypoints.indexOf(waypointIdx).takeIf { it >= 0 }

        val first = waypoints[0]
        val firstSector = sectorIdxFor(0)
        gpsPointsList.add(
            TrackCoordinatesData(
                trackId = 0L,
                latitude = first.lat,
                longitude = first.lon,
                altitude = null,
                isSectorPoint = firstSector != null,
                sectorIndex = firstSector
            )
        )

        routedLegs.forEachIndexed { legIdx, leg ->
            val endWaypointIdx = legIdx + 1
            leg.forEachIndexed { i, point ->
                val sector = if (i == leg.lastIndex) sectorIdxFor(endWaypointIdx) else null
                gpsPointsList.add(
                    TrackCoordinatesData(
                        trackId = 0L,
                        latitude = point.lat,
                        longitude = point.lon,
                        altitude = null,
                        isSectorPoint = sector != null,
                        sectorIndex = sector
                    )
                )
            }
        }
    }

    fun markSector() {
        if (builderType == 1) {
            val idx = waypoints.lastIndex
            if (idx < 0 || idx in sectorWaypoints) return
            sectorWaypoints.add(idx)
            rebuildManualPath()
        } else {
            if (gpsPointsList.isEmpty()) return
            val lastIndex = gpsPointsList.size - 1
            gpsPointsList[lastIndex] =
                gpsPointsList[lastIndex].copy(isSectorPoint = true, sectorIndex = sectorCount)
            sectorCount += 1
        }
    }

    LaunchedEffect(gpsData, isLiveRecording) {
        if (isLiveRecording && builderType == 0) {
            gpsData?.let { data ->
                gpsPointsList.add(
                    TrackCoordinatesData(
                        trackId = trackID,
                        latitude = data.latitude,
                        longitude = data.longitude,
                        altitude = data.altitude
                    )
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TrackProTheme.colors.bgDeep)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(title = "Track Builder", accent = TrackProTheme.colors.accent, onBack = onBack)

            Column(modifier = Modifier.padding(Spacing.md)) {
                TrackInfoCard(trackName, countryName, trackMode) { showInfoDialog = true }
                Spacer(modifier = Modifier.height(Spacing.md))
                ModeToggle(builderType) { newMode ->
                    if (newMode != builderType) {
                        // The two modes build gpsPointsList in incompatible ways (live appends
                        // raw GPS fixes; manual derives it from waypoints + routed legs), so
                        // carrying either one's state across a switch would produce a mangled
                        // path - reset the in-progress track instead.
                        builderType = newMode
                        gpsPointsList.clear()
                        waypoints.clear()
                        routedLegs.clear()
                        sectorWaypoints.clear()
                        sectorCount = 0
                        lastLegFellBack = false
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))

                if (builderType == 0) {
                    LiveControls(
                        isRecording = isLiveRecording,
                        onToggle = {
                            if (!isLiveRecording) {
                                if (trackName.isEmpty()) {
                                    showInfoDialog = true
                                } else {
                                    coroutineScope.launch {
                                        gpsPointsList.clear() // Clear old preview
                                        waypoints.clear()
                                        routedLegs.clear()
                                        sectorWaypoints.clear()
                                        sectorCount = 0
                                        lastLegFellBack = false
                                        trackID = startTrackBuilder(database, trackName, countryName, trackMode)
                                        isLiveRecording = true
                                    }
                                }
                            } else {
                                // Stop recording immediately (synchronously, before the save
                                // coroutine even launches) so the GPS-update effect above stops
                                // appending to gpsPointsList while we're reading/persisting it
                                // below - otherwise Room can be iterating the list to build the
                                // insert at the same moment new points are still being added to
                                // it, which aborts the whole transaction and saves nothing.
                                isLiveRecording = false
                                val pointsToSave = gpsPointsList.toList()
                                coroutineScope.launch {
                                    database.trackCoordinatesDao().insertTrackPart(pointsToSave)
                                    val isLapTrack = (trackMode == "Circuit")
                                    endTrackBuilder(context, trackID, isLapTrack)
                                    onBack()
                                }
                            }
                        }
                    )
                } else {
                    ManualControls(
                        onUndo = {
                            // Undo removes the last *tapped waypoint* and the routed leg that
                            // reached it, not a single point of the dense routed polyline.
                            if (waypoints.isNotEmpty()) {
                                val removedIdx = waypoints.lastIndex
                                waypoints.removeAt(removedIdx)
                                if (routedLegs.isNotEmpty()) routedLegs.removeAt(routedLegs.lastIndex)
                                sectorWaypoints.remove(removedIdx)
                                lastLegFellBack = false
                                rebuildManualPath()
                            }
                        },
                        onSave = {
                            coroutineScope.launch {
                                val id = startTrackBuilder(database, trackName, countryName, trackMode)
                                database.trackCoordinatesDao().insertTrackPart(gpsPointsList.map { it.copy(trackId = id) })
                                val isLapTrack = (trackMode == "Circuit")
                                endTrackBuilder(context, id,isLapTrack)
                                onBack()
                            }
                        },
                        canSave = gpsPointsList.size > 1 && trackName.isNotEmpty() && !isRouting
                    )

                    if (isRouting || lastLegFellBack) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        RoutingStatusRow(isRouting = isRouting)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                MarkSectorButton(
                    count = if (builderType == 1) sectorWaypoints.size else sectorCount,
                    enabled = gpsPointsList.isNotEmpty() && !isRouting &&
                            (builderType == 1 || isLiveRecording),
                    onClick = { markSector() }
                )
            }

            // Map/Preview Area
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.md)
                .background(TrackProTheme.colors.bgCard, TrackProShapes.card)
                .border(1.dp, TrackProTheme.colors.sectorLine, TrackProShapes.card)
            ) {

                    MapLibreBuilderView(
                        trackMode,
                        points = gpsPointsList,
                        waypoints = waypoints,
                        onMapTap = { latLng ->
                            // Only the manual builder places points by tapping; in live mode
                            // the map is a read-only preview of the recorded trace.
                            // Ignoring taps while a leg is in flight keeps routedLegs ordered:
                            // two overlapping requests could otherwise resolve out of order and
                            // append the later leg first, scrambling the path.
                            if (builderType == 1 && !isRouting) {
                                val tapped = LatLonOffset(lat = latLng.latitude, lon = latLng.longitude)
                                val previous = waypoints.lastOrNull()
                                waypoints.add(tapped)
                                if (previous == null) {
                                    rebuildManualPath()
                                } else {
                                    isRouting = true
                                    coroutineScope.launch {
                                        val result = RoadRouter.routeLeg(previous, tapped)
                                        routedLegs.add(result.points)
                                        lastLegFellBack = !result.isRouted
                                        rebuildManualPath()
                                        isRouting = false
                                    }
                                }
                            }
                        }
                    )

            }
        }
    }

    if (showInfoDialog) {
        TrackInfoAlert(
            onDismiss = { showInfoDialog = false },
            onConfirm = { name, country, mode ->
                trackName = name
                countryName = country
                trackMode = mode
                showInfoDialog = false
            }
        )
    }
}

@Composable
fun MapLibreBuilderView(
    trackMode: String,
    points: List<TrackCoordinatesData>,
    waypoints: List<LatLonOffset> = emptyList(),
    onMapTap: (LatLng) -> Unit
) {
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val sectorMarkCount = points.count { it.isSectorPoint }

    LaunchedEffect(points.size, sectorMarkCount, waypoints.size) {
        mapLibreMap?.let { map ->
            map.clear()

            // 1. Add the Path Line
            if (points.size >= 2) {
                val latLngs = points.map { LatLng(it.latitude, it.longitude) }
                map.addPolyline(
                    PolylineOptions() // Corrected reference
                        .addAll(latLngs)
                        .color(DataVizColors.trackLine.toColorInt())
                        .width(3f)
                )
            }

            // 2. Add Markers
            // Inside MapLibreBuilderView, when adding markers:
            if (points.isNotEmpty()) {
                // Start Marker is always there
                map.addMarker(MarkerOptions()
                    .position(LatLng(points.first().latitude, points.first().longitude))
                    .title("START")
                )

                if (points.size > 1) {
                    val lastPoint = points.last()
                    map.addMarker(MarkerOptions()
                        .position(LatLng(lastPoint.latitude, lastPoint.longitude))
                        // Change title based on intent
                        .title(if (trackMode == "Circuit") "LAP COMPLETE" else "FINISH LINE")
                    )
                }

                // Sector markers
                points.filter { it.isSectorPoint }
                    .sortedBy { it.sectorIndex ?: Int.MAX_VALUE }
                    .forEach { sectorPoint ->
                        map.addMarker(MarkerOptions()
                            .position(LatLng(sectorPoint.latitude, sectorPoint.longitude))
                            .title("S${(sectorPoint.sectorIndex ?: 0) + 1}")
                        )
                    }

                // Tapped waypoints (manual mode). The drawn line follows the road network
                // between these, so showing them separately makes it clear which points the
                // user actually placed versus what the router filled in.
                waypoints.forEachIndexed { index, wp ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(wp.lat, wp.lon))
                            .title("WAYPOINT ${index + 1}")
                    )
                }

                // Follow the most recent point - without this the camera stays wherever
                // it started (a default world view), so a live recording never visibly
                // moves even though points are being collected correctly.
                val last = points.last()
                map.easeCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(last.latitude, last.longitude), 17.0),
                    500
                )
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            org.maplibre.android.MapLibre.getInstance(ctx)
            MapView(ctx).apply {
                getMapAsync { map ->
                    mapLibreMap = map
                    map.setStyle("https://tiles.openfreemap.org/styles/dark")

                    map.addOnMapClickListener { latLng ->
                        onMapTap(latLng)
                        true
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TrackInfoCard(name: String, country: String, mode: String, onClick: () -> Unit) {
    DashGroup("Track configuration") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (name.isEmpty()) "Unnamed track" else name,
                    style = TrackProType.titleLarge.atSize(18.sp),
                    color = TrackProTheme.colors.marking
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(country, mode.uppercase()).filter { it.isNotBlank() }.joinToString("  \u00b7  "),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
            }
            Spacer(Modifier.width(12.dp))
            DashAction(
                label = "Edit",
                onClick = onClick,
                compact = true,
                modifier = Modifier.width(96.dp)
            )
        }
    }
}


@Composable
private fun MarkSectorButton(count: Int, enabled: Boolean, onClick: () -> Unit) {
    DashAction(
        label = "Mark sector ${count + 1}",
        onClick = onClick,
        enabled = enabled,
        compact = true,
        haptic = Haptic.Confirm
    )
}


/**
 * Surfaces routing state in the manual builder: either a leg is being fetched, or the last
 * one couldn't be routed and fell back to a straight line - which the user needs to know,
 * since an unrouted leg silently looks the same as the old straight-line behaviour.
 */
@Composable
private fun RoutingStatusRow(isRouting: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (isRouting) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = TrackProTheme.colors.accent
            )
            Text(
                text = "Snapping to roads...",
                color = TrackProTheme.colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        } else {
            Text(
                text = "! Last leg couldn't be routed - straight line used",
                color = TrackProTheme.colors.deltaBad,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun ModeToggle(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        val modes = listOf("Live GPS", "Manual Map")
        modes.forEachIndexed { index, label ->
            ToggleChip(
                text = label,
                selected = selected == index,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

suspend fun startTrackBuilder(database: ESPDatabase, trackName: String, countryname: String, trackType: String):Long
{
    val track = TrackMainData(trackName = trackName, country = countryname, type = trackType)
    val id = database.trackMainDao().insertTrackMainDataDAO(track)
    return id
}

suspend fun endTrackBuilder(context: Context, trackId: Long, isLapTrack: Boolean) {
    Log.d("endTrackBuilder", "Inside")
    val database = ESPDatabase.getInstance(context)
    val postProcess = PostProcessing(database)

    // The dedup/lap-detection/distance loops below are CPU-bound and can take a
    // noticeable amount of time for a longer recording (thousands of points) - run them
    // off the caller's dispatcher so they don't block the UI thread.
    withContext(Dispatchers.Default) {
        // Explicitly wait and ensure the processed track data is retrieved
        val track: List<TrackCoordinatesData> = postProcess.processTrackPoints(trackId, isLapTrack)

        if (track.isEmpty()) {
            Log.w("endTrackBuilder", "No track points found for trackId=$trackId! Aborting.")
            return@withContext
        }

        // Map to lat/lon offsets (synchronously after suspend)
        val latlon: List<LatLonOffset> = track.map { point ->
            LatLonOffset(lat = point.latitude, lon = point.longitude)
        }
        Log.d("latlon:", latlon.toString())

        // Explicitly calculate total distance (blocking inside suspend)
        val helper = DragTimeCalculation(database = database)
        val totalLengthMeters = helper.totalDistance(latlon)
        // TrackMainData.totalLength is stored in kilometers - matches the bundled seed tracks
        // (see res/raw/tracks.json), which are authored in km.
        val totalLengthKm = totalLengthMeters / 1000.0
        Log.d("Total length (km):", totalLengthKm.toString())

        // Perform database update on IO dispatcher (ensure proper thread)
        withContext(Dispatchers.IO) {
            val affectedRows = database.trackMainDao().updateTotalLength(trackId, totalLengthKm)
            Log.d("DB Update", "Updated totalLength on trackId=$trackId, affected rows: $affectedRows")
        }
    }
}


@Composable
private fun LiveControls(isRecording: Boolean, onToggle: () -> Unit) {
    // Recording is the one state on this screen that must be unmistakable while you are
    // walking a circuit, so it takes the danger accent rather than a label change alone.
    DashAction(
        label = if (isRecording) "Stop recording" else "Start GPS recording",
        detail = if (isRecording) "Recording \u00b7 walk the circuit" else null,
        onClick = onToggle,
        compact = true,
        haptic = Haptic.Confirm,
        accent = if (isRecording) TrackProTheme.colors.danger else TrackProTheme.colors.accent
    )
}


@Composable
private fun ManualControls(onUndo: () -> Unit, onSave: () -> Unit, canSave: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        DashAction(
            label = "Undo last",
            onClick = onUndo,
            compact = true,
            accent = TrackProTheme.colors.markingDim,
            modifier = Modifier.weight(1f)
        )
        DashAction(
            label = "Save track",
            onClick = onSave,
            enabled = canSave,
            compact = true,
            haptic = Haptic.Confirm,
            modifier = Modifier.weight(1f)
        )
    }
}


@Composable
fun TrackInfoAlert(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("Circuit") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrackProTheme.colors.bgCard,
        title = { Text("Track Details", color = TrackProTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Track Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Timing Mode", color = TrackProTheme.colors.textMuted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == "Circuit", onClick = { mode = "Circuit" })
                    Text("Circuit", color = TrackProTheme.colors.textPrimary)
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = mode == "Sprint", onClick = { mode = "Sprint" })
                    Text("Sprint", color = TrackProTheme.colors.textPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, country, mode) }) {
                Text("DONE", color = TrackProTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TrackProTheme.colors.textMuted)
            }
        }
    )
}


@Preview(showBackground = true)
@Composable
fun TrackBuilderScreenPreview() {

    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()


    TrackProTheme {
        TrackBuilderScreen(
            database = fakeDatabase,
            onBack = {}
        )
    }
}