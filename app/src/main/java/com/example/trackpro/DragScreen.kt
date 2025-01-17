package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.example.trackpro.ManagerClasses.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESPTcpClient
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class DragScreen : ComponentActivity() {

    private lateinit var database: ESPDatabase

    private var sessionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize your managers or other logic here
        database = (application as TrackProApp).database

        //Create session
        sessionId = startSession(database)


    }

    override fun onDestroy() {
        super.onDestroy()
    }
}




@Composable
fun DragRaceScreen(
    onBack: () -> Unit,
    database: ESPDatabase
) {
    // SESSION RELATED VAR
    var isSessionActive by remember { mutableStateOf(false) }
    var sessionID by remember { mutableStateOf(-1) }

    // GPS DATA RELATED
    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<com.example.trackpro.ManagerClasses.RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }

    // ESP TCP Client connection
    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }

    // Tracking the last timestamp to prevent redundant inserts
    var lastTimestamp: Long? by remember { mutableStateOf(null) }

    // Start/Stop session and manage GPS data reception
    LaunchedEffect(isSessionActive) {
        Log.d("launch", "Started")
        if (isSessionActive) {
            if (sessionID != -1) {
                // Initialize and connect ESP TCP Client
                espTcpClient = ESPTcpClient(
                    serverAddress = "192.168.4.1",
                    port = 4210,
                    onMessageReceived = { data ->
                        gpsData.value = data
                        rawJson.value = data.toString()
                    },
                    onConnectionStatusChanged = { connected ->
                        isConnected.value = connected
                    }
                )
                espTcpClient?.connect()

                // Insert data while the session is active
                while (isSessionActive) {
                    val derivedData = gpsData.value?.let {
                        RawGPSData(
                            sessionid = sessionID.toLong(),
                            latitude = it.latitude,
                            longitude = it.longitude,
                            altitude = it.altitude,
                            timestamp = parseTimeToMilliseconds(it.timestamp),
                            speed = it.speed,
                            fixQuality = it.satellites
                        )
                    }

                    // Insert data if it's a new timestamp or new data
                    derivedData?.let { data ->
                        if (lastTimestamp == null || data.timestamp != lastTimestamp!!) {
                            Log.d("Database", "Inserting data: $data")
                            database.rawGPSDataDao().insert(data)
                            lastTimestamp = data.timestamp


                            // Optional: Query to verify insertion (for debugging)
                            val insertedData = database.rawGPSDataDao().getGPSDataBySession(sessionID.toInt())
                            Log.d("Database", "Inserted data for session $sessionID: $insertedData")

                        } else {
                            Log.d("Database", "Skipping duplicate data for timestamp: ${data.timestamp}")
                        }
                    }


                    // Sleep for a short duration before fetching new data again
                    delay(40) // Adjust as needed for data rate
                }
            }
        } else {
            // Disconnect TCP client if session is stopped
            espTcpClient?.disconnect()
            espTcpClient = null
        }
    }

    // UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Make a drag time calculation here!",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.Right,
            verticalAlignment = Alignment.Bottom
        ) {
            Button(
                onClick = {
                    isSessionActive = !isSessionActive
                    // Trigger session state change (start/stop session)
                    if (isSessionActive) {
                        sessionID = startSession(database) // Start session when button is pressed
                    } else {
                        // Stop session and disconnect from ESP client
                        espTcpClient?.disconnect()
                    }
                }
            ) {
                Text(if (isSessionActive) "Stop Session" else "Start Session")
            }
        }
    }
}





@Preview(showBackground = true)
@Composable
fun DragScreenPreview() {

    // Create a fake or mock database instance
    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()

    DragRaceScreen(
        onBack = {},
        database = fakeDatabase
    )
}




// Starts the session for a drag run
fun startSession(database: ESPDatabase): Int
{
    val sessionManager = SessionManager.getInstance(database)
    CoroutineScope(Dispatchers.IO).launch {
        sessionManager.startSession("DragSession", "Drag data session")
    }
    return sessionManager.getCurrentSessionId()?.toInt() ?:-1
}

fun parseTimeToMilliseconds(timeString: String): Long {
    val format = SimpleDateFormat("HH:mm:ss")
    val date: Date = format.parse(timeString) ?: throw IllegalArgumentException("Invalid time format")
    return date.time  // This will give the timestamp in milliseconds
}


