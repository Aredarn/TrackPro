package com.example.trackpro
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.room.Room
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.CalculationClasses.PostProcessing
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.DataClasses.TrackMainData
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.toDataClass
import com.example.trackpro.ui.theme.TrackProTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException


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
    var isSessionActive by remember { mutableStateOf(false) }
    var trackID by remember { mutableLongStateOf(-1) }
    val isConnected = remember { mutableStateOf(false) }

    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val gpsPointsList = remember { mutableStateListOf<TrackCoordinatesData>() }

    val rawJson = remember { mutableStateOf("") }
    val context = LocalContext.current
    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    val (ip, port) = remember { JsonReader.loadConfig(context) }
    var lastTimestamp: Long? by remember { mutableStateOf(null) }

    val dataBuffer = remember { mutableListOf<TrackCoordinatesData>() }

    val coroutineScope = rememberCoroutineScope()

    var insertJob: Job? = null
    var i = 0f

    var trackname by remember { mutableStateOf("") }
    var countryname by remember { mutableStateOf("") }

    var showDialog by remember { mutableStateOf(false) }
    var showStartBuilderButton by remember { mutableStateOf(false) }


    fun startBatchInsert() {
        insertJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(800)
                if (dataBuffer.isNotEmpty()) {
                    try {
                        //Adds the track coordinates to the database
                        database.trackCoordinatesDao().insertTrackPart(dataBuffer.toList())

                        //add the points to the gpsPoints which will be rendered on screen
                        gpsPointsList.addAll(dataBuffer.toList())

                        dataBuffer.clear()
                    } catch (e: Exception) {
                        Log.e("Database", "Insert failed ${e.message}")
                    }
                }
            }
        }
    }

    fun stopBatchInsert() {
        insertJob?.cancel()
        dataBuffer.clear()
        insertJob = null
    }




    //Tester function. works with static data from Pannonia ring
    /*
     // Index to keep track of which point to add
    var currentIndex by remember { mutableIntStateOf(0) }
    suspend fun startAddingGpsPoints() {
        // Loop to add points at intervals
        while (currentIndex < gpsPoints.size-1) {
            // Add the next point to the list
            //gpsPointsList.add(gpsPoints[currentIndex])
            // Increment the index to add the next point
            currentIndex++

            // Wait for 0.1 second before adding the next point
            delay(100)
        }
    }*/

    LaunchedEffect(Unit) {

        //startAddingGpsPoints()

        espTcpClient = ESPTcpClient(
            serverAddress = ip,
            port = port,
            onMessageReceived = { data ->
                gpsData.value = data.toDataClass()
                rawJson.value = data.toString()

                if (isSessionActive && isConnected.value && trackID > 0) {
                    val derivedData = gpsData.value?.let {
                        TrackCoordinatesData(
                            trackId = trackID,
                            latitude = it.latitude,
                            longitude = it.longitude,
                            altitude = it.altitude
                        )
                    }
                    val timestamp = gpsData.value?.timestamp
                    derivedData?.let { gpsdata ->

                        if (lastTimestamp == null || timestamp != lastTimestamp) {
                            dataBuffer.add(gpsdata)
                            i += 1
                            lastTimestamp = timestamp
                        }
                    }
                }
            },
            onConnectionStatusChanged = { connected -> isConnected.value = connected }
        )
        espTcpClient?.connect()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Track creator ", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Start driving from the finish line, make a full lap and save your track to use later for lap timing")
        Spacer(modifier = Modifier.height(16.dp))

        val buttonShape = RoundedCornerShape(12.dp)
        val buttonColors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2196F3), // Soft blue, or replace with Color.Transparent for outline style
            contentColor = Color.White
        )

        if (showStartBuilderButton) {
            Button(
                onClick = {
                    isSessionActive = !isSessionActive
                    if (isSessionActive) {
                        coroutineScope.launch(Dispatchers.IO) {
                            trackID = startTrackBuilder(database, trackname, countryname)
                            Log.d("TrckID:", "" + trackID)
                            withContext(Dispatchers.Main) {
                                startBatchInsert()
                            }
                        }
                    } else {
                        coroutineScope.launch(Dispatchers.IO) {
                            stopBatchInsert()
                            endTrackBuilder(context, trackID)
                            //postProc.processTrackPoints(trackId = trackID)
                            trackID = -1
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = buttonShape,
                colors = buttonColors,
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    if (isSessionActive) "Stop Track Builder" else "Start Track Builder",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = buttonShape,
            colors = buttonColors,
            elevation = ButtonDefaults.buttonElevation(0.dp)
        ) {
            Text(
                "Enter Track Info",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }


        // The track drawer BOX
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center // Center the canvas within the Box
        ) {
            // Canvas with a border
            Box(
                modifier = Modifier
                    .border(
                        width = 2.dp,
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp) // Optional rounded corners
                    )
                    .aspectRatio(1f) // Make the canvas square
                    .background(Color.LightGray) // Optional background for the canvas
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw the track, start line, and animated dot
                    drawTrack(gpsPointsList, 50f,1f )//,animationProgress)
                }
            }
        }
    }

    //
    TrackInfoAlert(
        showDialog = showDialog,
        onDismiss = { showDialog = false },
        onConfirm = { name, country ->
            trackname = name
            countryname = country
            showStartBuilderButton = true
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            try {
                coroutineScope.launch(Dispatchers.IO) {
                    espTcpClient?.disconnect()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun TrackInfoAlert(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var trackName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Enter Track Details") },
            text = {
                Column {
                    TextField(
                        value = trackName,
                        onValueChange = { trackName = it },
                        label = { Text("Track Name") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(trackName, country)
                    onDismiss()
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}


suspend fun startTrackBuilder(database: ESPDatabase,trackName: String,countryname: String):Long
{
    val track = TrackMainData(trackName = trackName, country = countryname)
    val id = database.trackMainDao().insertTrackMainDataDAO(track)
    return id
}

suspend fun endTrackBuilder(context: Context, trackId: Long) {
    val database = ESPDatabase.getInstance(context)
    val postProcess = PostProcessing(database)

    // Explicitly wait and ensure the processed track data is retrieved
    val track: List<TrackCoordinatesData> = postProcess.processTrackPoints(trackId)
    Log.d("TRACK:", "Received ${track.size} points: $track")

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