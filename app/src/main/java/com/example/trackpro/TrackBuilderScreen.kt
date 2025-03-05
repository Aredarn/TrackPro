package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Database
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.DataClasses.TrackMainData
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.toDataClass
import com.github.mikephil.charting.data.Entry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch



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



@Composable
fun TrackBuilderScreen(
    database: ESPDatabase,
    onBack: () -> Unit
) {
    var isSessionActive by remember { mutableStateOf(false) }
    var trackID by remember { mutableLongStateOf(-1) }
    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }
    val context = LocalContext.current
    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    val (ip, port) = remember { JsonReader.loadConfig(context) }
    var lastTimestamp: Long? by remember { mutableStateOf(null) }
    val dataBuffer = remember { mutableListOf<TrackCoordinatesData>() }
    val coroutineScope = rememberCoroutineScope()
    var insertJob: Job? = null
    var i = 0f
    var showDialog by remember { mutableStateOf(false) }
    var trackname by remember { mutableStateOf("") }
    var countryname by remember { mutableStateOf("") }
    var lengthoftrack by remember { mutableStateOf(0.0) }

    fun startBatchInsert() {
        insertJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(800)
                if (dataBuffer.isNotEmpty()) {
                    try {
                        database.trackCoordinatesDao().insertTrackPart(dataBuffer.toList())
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

    LaunchedEffect(Unit) {
        espTcpClient = ESPTcpClient(
            serverAddress = ip,
            port = port,
            onMessageReceived = { data ->
                gpsData.value = data.toDataClass()
                rawJson.value = data.toString()

                if (isSessionActive && isConnected.value) {
                    val derivedData = gpsData.value?.let {
                        TrackCoordinatesData(
                            trackId = trackID.toInt(),
                            latitude = it.latitude,
                            longitude = it.longitude,
                            altitude = it.altitude
                        )
                    }
                    val timestamp = gpsData.value?.timestamp
                    derivedData?.let { data ->
                        if (lastTimestamp == null || timestamp != lastTimestamp) {
                            dataBuffer.add(data)
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
        Text("Track Builder Screen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            isSessionActive = !isSessionActive
            if (isSessionActive) {
                coroutineScope.launch(Dispatchers.IO) {
                    trackID = startTrackBuilder(database)
                    startBatchInsert()
                }
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    trackID = -1
                    stopBatchInsert()
                }
            }
        }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isSessionActive) "Stop Track Builder" else "Start Track Builder")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Enter Track Info")
        }
    }

    TrackInfoAlert(
        showDialog = showDialog,
        onDismiss = { showDialog = false },
        onConfirm = { name, country, length ->
            trackname = name
            countryname = country
            lengthoftrack = length
        }
    )
}

@Composable
fun TrackInfoAlert(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double) -> Unit
) {
    var trackName by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }

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
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = length,
                        onValueChange = { length = it },
                        label = { Text("Track Length (m)") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val number = length.toDoubleOrNull() ?: 0.0
                    onConfirm(trackName, country, number)
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


suspend fun startTrackBuilder(database: ESPDatabase):Long
{
    val Track = TrackMainData(trackName = "TesztTrack", totalLength = 2234.1, country = "Hun")
    val id = database.trackMainDao().insertTrackMainDataDAO(Track)
    return  id;
}
