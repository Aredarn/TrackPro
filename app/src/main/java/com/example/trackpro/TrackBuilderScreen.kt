package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    onBack:() -> Unit
)
{

    var isSessionActive by remember { mutableStateOf(false) }
    var trackID by remember { mutableLongStateOf(-1) }

    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }
    val context = LocalContext.current  // Get the Context in Compose

    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    val (ip, port) = remember { JsonReader.loadConfig(context) } // Load once & remember it
    var lastTimestamp: Long? by remember { mutableStateOf(null) }

    val dataBuffer = mutableListOf<TrackCoordinatesData>()

    val coroutineScope = rememberCoroutineScope()
    var insertJob: Job? = null
    var i = 0f


    //Starting the job which will insert the track coordinates into a temp List
    //

    fun startBatchInsert()
    {
        insertJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive)
            {
                delay(800)

                if(dataBuffer.isNotEmpty())
                {
                    try {
                        database.trackCoordinatesDao().insertTrackPart(dataBuffer.toList())
                        dataBuffer.clear()
                    }catch (e: Exception)
                    {
                        Log.e("Database", "Insert failed ${e.message}")
                    }
                }
            }
        }
    }

    fun stopBatchInsert()
    {
        insertJob?.cancel()
        dataBuffer.clear()
        insertJob = null
    }

    LaunchedEffect(Unit) {

        espTcpClient = ESPTcpClient(
            serverAddress = ip,
            port = port,
            onMessageReceived = { data ->
                // Update state with received data
                gpsData.value = data.toDataClass()
                rawJson.value = data.toString()

                // Only process and store data if session is active and connected
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
                            // Add data to buffer instead of inserting directly
                            dataBuffer.add(data)
                            i += 1
                            lastTimestamp = timestamp
                        }
                    }
                }
            },
            onConnectionStatusChanged = { connected ->
                isConnected.value = connected
            }
        )

        // Connect the client
        espTcpClient?.connect()
    }



    Row(){
        Text("Track builder screen")
    }
    Spacer(modifier = Modifier.fillMaxWidth().padding(20.dp))
    Row {

        Button(onClick = {
                isSessionActive = !isSessionActive

                if(isSessionActive)
                {
                    CoroutineScope(Dispatchers.IO).launch {
                        trackID = startTrackBuilder(database);
                        startBatchInsert()
                    }
                }
                else
                {
                    CoroutineScope(Dispatchers.IO).launch {
                        trackID = -1;
                        stopBatchInsert()
                    }
                }
            }
        )
        {
            Text(if(isSessionActive) "Stop Track Builder" else "Start Track Builder")
        }
    }
}

suspend fun startTrackBuilder(database: ESPDatabase):Long
{
    val Track = TrackMainData(trackName = "TesztTrack", totalLength = 2234.1, country = "Hun")
    val id = database.trackMainDao().insertTrackMainDataDAO(Track)

    return  id;
}
