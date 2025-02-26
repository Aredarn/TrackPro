package com.example.trackpro

import android.os.Bundle
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Database
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.toDataClass
import com.github.mikephil.charting.data.Entry


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
    var sessionID by remember { mutableLongStateOf(-1) }

    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }
    val context = LocalContext.current  // Get the Context in Compose

    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    val (ip, port) = remember { JsonReader.loadConfig(context) } // Load once & remember it
    var lastTimestamp: Long? by remember { mutableStateOf(null) }

    val dataBuffer = mutableListOf<RawGPSData>()
    var i = 0f;

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
                        RawGPSData(
                            sessionid = sessionID.toLong(),
                            latitude = it.latitude,
                            longitude = it.longitude,
                            altitude = it.altitude,
                            timestamp = it.timestamp,
                            speed = it.speed,
                            fixQuality = it.fixQuality
                        )
                    }

                    derivedData?.let { data ->
                        if (lastTimestamp == null || data.timestamp != lastTimestamp) {
                            // Add data to buffer instead of inserting directly
                            dataBuffer.add(data)

                            i += 1
                            lastTimestamp = data.timestamp
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
            }
        )
        {
            Text(if(isSessionActive) "Stop Track Builder" else "Start Track Builder")
        }
    }
}




suspend fun startTrackBuilder(database: ESPDatabase):Long
{
    database.trackMainDao().insertTrackMainDataDAO()



}