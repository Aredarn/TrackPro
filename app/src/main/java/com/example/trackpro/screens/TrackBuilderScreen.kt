package com.example.trackpro.screens
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.room.Room
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.CalculationClasses.PostProcessing
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.DataClasses.TrackMainData
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ManagerClasses.ESPDatabase
import com.example.trackpro.theme.TrackProColors
import com.example.trackpro.ui.theme.TrackProTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
// Use Android's native Color for the polyline
import android.graphics.Color as AndroidGraphicsColor
class TrackBuilderScreen : ComponentActivity()
{
    private lateinit var database: ESPDatabase

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        database = ESPDatabase.getInstance(applicationContext)

        setContent{
            TrackBuilderScreen(
                database, onBack = {finish()}
            )
        }

    }
}

/*
val gpsPoints = listOf(
    LatLonOffset(47.305300, 17.048138),
    LatLonOffset(47.302270, 17.049691),
    LatLonOffset(47.301004, 17.048439),
    LatLonOffset(47.300890, 17.048053),
    LatLonOffset(47.301029, 17.047764),
    LatLonOffset(47.302883, 17.046380),
    LatLonOffset(47.302997, 17.046187),
    LatLonOffset(47.303095, 17.045789),
    LatLonOffset(47.303422, 17.043212),
    LatLonOffset(47.303258, 17.042779),
    LatLonOffset(47.302997, 17.042706),
    LatLonOffset(47.300882, 17.045801),
    LatLonOffset(47.300572, 17.045994),
    LatLonOffset(47.300237, 17.045910),
    LatLonOffset(47.299959, 17.045356),
    LatLonOffset(47.299992, 17.044838),
    LatLonOffset(47.301160, 17.040972),
    LatLonOffset(47.301486, 17.040623),
    LatLonOffset(47.301846, 17.040563),
    LatLonOffset(47.302744, 17.041346),
    LatLonOffset(47.302981, 17.041382),
    LatLonOffset(47.303307, 17.041213),
    LatLonOffset(47.303724, 17.040515),
    LatLonOffset(47.303887, 17.039732),
    LatLonOffset(47.303691, 17.037420),
    LatLonOffset(47.303691, 17.037046),
    LatLonOffset(47.304508, 17.035324),
    LatLonOffset(47.304696, 17.035192),
    LatLonOffset(47.304851, 17.035276),
    LatLonOffset(47.305349, 17.036324),
    LatLonOffset(47.305373, 17.036757),
    LatLonOffset(47.305210, 17.037335),
    LatLonOffset(47.304843, 17.037733),
    LatLonOffset(47.304638, 17.038239),
    LatLonOffset(47.304565, 17.038624),
    LatLonOffset(47.304508, 17.043248),
    LatLonOffset(47.304451, 17.043513),
    LatLonOffset(47.304075, 17.044344),
    LatLonOffset(47.304026, 17.044549),
    LatLonOffset(47.304059, 17.044838),
    LatLonOffset(47.304181, 17.045151),
    LatLonOffset(47.304393, 17.045187),
    LatLonOffset(47.304557, 17.045139),
    LatLonOffset(47.305594, 17.044380),
    LatLonOffset(47.306631, 17.042670),
    LatLonOffset(47.306778, 17.042550),
    LatLonOffset(47.306958, 17.042550),
    LatLonOffset(47.307137, 17.042622),
    LatLonOffset(47.308574, 17.044501),
    LatLonOffset(47.308648, 17.044730),
    LatLonOffset(47.308721, 17.045585),
    LatLonOffset(47.308615, 17.046054),
    LatLonOffset(47.308436, 17.046392),
    LatLonOffset(47.306092, 17.047740),
    LatLonOffset(47.304973, 17.048282),
    LatLonOffset(47.305300, 17.048138)
)*/

@Composable
fun TrackBuilderScreen(
    database: ESPDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State
    var isLiveRecording by remember { mutableStateOf(false) }
    var trackID by remember { mutableLongStateOf(-1) }
    var trackMode by remember { mutableStateOf("Circuit") }
    var trackName by remember { mutableStateOf("") }
    var countryName by remember { mutableStateOf("") }

    // Track Points
    val gpsPointsList = remember { mutableStateListOf<TrackCoordinatesData>() }

    // UI State
    var showInfoDialog by remember { mutableStateOf(false) }
    var builderType by remember { mutableStateOf(0) } // 0: Live GPS, 1: Manual Map

    Box(modifier = Modifier.fillMaxSize().background(TrackProColors.BgDeep)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ───────────────────────────────────────
            HeaderSection(onBack)

            // ── Control Panel ────────────────────────────────
            Column(modifier = Modifier.padding(16.dp)) {

                // Track Info Card
                TrackInfoCard(trackName, countryName, trackMode) { showInfoDialog = true }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Selector (Live vs Manual)
                ModeToggle(builderType) { builderType = it }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                if (builderType == 0) {
                    LiveControls(
                        isRecording = isLiveRecording,
                        onToggle = {
                            if (!isLiveRecording) {
                                if (trackName.isEmpty()) { showInfoDialog = true }
                                else {
                                    coroutineScope.launch {
                                        trackID = startTrackBuilder(database, trackName, countryName, trackMode)
                                        isLiveRecording = true
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    endTrackBuilder(context, trackID)
                                    isLiveRecording = false
                                    onBack()
                                }
                            }
                        }
                    )
                } else {
                    ManualControls(
                        onUndo = { if (gpsPointsList.isNotEmpty()) gpsPointsList.removeAt(gpsPointsList.size - 1) },
                        onSave = {
                            coroutineScope.launch {
                                val id = startTrackBuilder(database, trackName, countryName, trackMode)
                                database.trackCoordinatesDao().insertTrackPart(gpsPointsList.map { it.copy(trackId = id) })
                                endTrackBuilder(context, id)
                                onBack()
                            }
                        },
                        canSave = gpsPointsList.size > 1 && trackName.isNotEmpty()
                    )
                }
            }

            // ── Map/Preview Area ─────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)
                .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF1E2530), RoundedCornerShape(12.dp))
            ) {
                if (builderType == 1) {
                    // MapLibre for Manual Selection
                    MapLibreBuilderView(
                        points = gpsPointsList,
                        onMapTap = { latLng ->
                            gpsPointsList.add(
                                TrackCoordinatesData(
                                    trackId = 0, // Temp ID
                                    latitude = latLng.latitude,
                                    longitude = latLng.longitude,
                                    altitude = 0.0
                                )
                            )
                        }
                    )
                } else {
                    // Existing Canvas Preview for Live GPS
                    Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        drawTrack(gpsPointsList, 50f, 2f)
                    }
                }
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
    points: List<TrackCoordinatesData>,
    onMapTap: (LatLng) -> Unit
) {
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }

    LaunchedEffect(points.size) {
        mapLibreMap?.let { map ->
            map.clear()

            // 1. Add the Path Line
            if (points.size >= 2) {
                val latLngs = points.map { LatLng(it.latitude, it.longitude) }
                map.addPolyline(
                    PolylineOptions() // Corrected reference
                        .addAll(latLngs)
                        .color(AndroidGraphicsColor.parseColor("#E8001C"))
                        .width(3f)
                )
            }

            // 2. Add Markers
            if (points.isNotEmpty()) {
                // Start Marker
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(points.first().latitude, points.first().longitude))
                        .title("START")
                )

                // Current Last Point
                if (points.size > 1) {
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(points.last().latitude, points.last().longitude))
                            .title("END")
                    )
                }
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
                        true;
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TrackInfoCard(name: String, country: String, mode: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProColors.BgCard, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E2530), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column {
            Text("TRACK CONFIGURATION", color = TrackProColors.TextMuted, fontSize = 10.sp, letterSpacing = 2.sp)
            Text(if (name.isEmpty()) "Unnamed Track" else "$name ($country)", color = TrackProColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("MODE: ${mode.uppercase()}", color = TrackProColors.AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Button(
            onClick = onClick,
            modifier = Modifier.align(Alignment.CenterEnd),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2530))
        ) {
            Text("EDIT", color = TrackProColors.TextPrimary)
        }
    }
}

@Composable
private fun ModeToggle(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(TrackProColors.BgCard, RoundedCornerShape(8.dp)).padding(4.dp)) {
        val modes = listOf("LIVE GPS", "MANUAL MAP")
        modes.forEachIndexed { index, label ->
            Box(
                modifier = Modifier.weight(1f).height(40.dp)
                    .background(if (selected == index) TrackProColors.AccentRed else Color.Transparent, RoundedCornerShape(6.dp))
                    .padding(4.dp).clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected == index) Color.Black else TrackProColors.TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

suspend fun startTrackBuilder(database: ESPDatabase, trackName: String, countryname: String, trackType: String):Long
{
    val track = TrackMainData(trackName = trackName, country = countryname, type = trackType)
    val id = database.trackMainDao().insertTrackMainDataDAO(track)
    return id
}

suspend fun endTrackBuilder(context: Context, trackId: Long) {
    Log.d("endTrackBuilder", "Inside")
    val database = ESPDatabase.Companion.getInstance(context)
    val postProcess = PostProcessing(database)

    // Explicitly wait and ensure the processed track data is retrieved
    val track: List<TrackCoordinatesData> = postProcess.processTrackPoints(trackId)

    if (track.isEmpty()) {
        Log.w("endTrackBuilder", "No track points found for trackId=$trackId! Aborting.")
        return
    }

    // Map to lat/lon offsets (synchronously after suspend)
    val latlon: List<LatLonOffset> = track.map { point ->
        LatLonOffset(lat = point.latitude, lon = point.longitude)
    }
    Log.d("latlon:", latlon.toString())

    // Explicitly calculate total distance (blocking inside suspend)
    val helper = DragTimeCalculation(database = database)
    val totalLength = helper.totalDistance(latlon)
    Log.d("Total length (meters):", totalLength.toString())

    // Perform database update on IO dispatcher (ensure proper thread)
    withContext(Dispatchers.IO) {
        val affectedRows = database.trackMainDao().updateTotalLength(trackId, totalLength)
        Log.d("DB Update", "Updated totalLength on trackId=$trackId, affected rows: $affectedRows")
    }
}


@Composable
private fun HeaderSection(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("← BACK", color = TrackProColors.AccentRed, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("TRACK BUILDER", color = TrackProColors.TextPrimary, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}

@Composable
private fun LiveControls(isRecording: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) Color(0xFF330000) else Color(0xFF1E2530)
        ),
        //border = border(1.dp, if (isRecording) AccentRed else Color.Transparent, RoundedCornerShape(8.dp))
    ) {
        val label = if (isRecording) "STOP RECORDING" else "START GPS RECORDING"
        val icon = if (isRecording) "■" else "●"
        Text("$icon $label", color = if (isRecording) TrackProColors.AccentRed else TrackProColors.TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ManualControls(onUndo: () -> Unit, onSave: () -> Unit, canSave: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onUndo,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2530)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("UNDO LAST", color = TrackProColors.TextPrimary)
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f).height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TrackProColors.AccentRed,
                disabledContainerColor = Color(0xFF2A1014)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("SAVE TRACK", color = if (canSave) Color.Black else TrackProColors.TextMuted, fontWeight = FontWeight.Bold)
        }
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
        containerColor = TrackProColors.BgCard,
        title = { Text("Track Details", color = TrackProColors.TextPrimary) },
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

                Text("Timing Mode", color = TrackProColors.TextMuted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == "Circuit", onClick = { mode = "Circuit" })
                    Text("Circuit", color = TrackProColors.TextPrimary)
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = mode == "Sprint", onClick = { mode = "Sprint" })
                    Text("Sprint", color = TrackProColors.TextPrimary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, country, mode) }) {
                Text("DONE", color = TrackProColors.AccentRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TrackProColors.TextMuted)
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