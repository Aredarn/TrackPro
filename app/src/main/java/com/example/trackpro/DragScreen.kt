package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESP32Manager
import com.example.trackpro.ManagerClasses.SessionManager
import com.example.trackpro.ESPDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DragScreen : ComponentActivity() {

    private lateinit var espManager: ESP32Manager // ESP32 Manager instance
    private lateinit var database: ESPDatabase
    private lateinit var sessionManager: SessionManager
    private var sessionId: Int = -1
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize your managers or other logic here
        database = (application as TrackProApp).database

        //Create session
        sessionId = StartSession(database);

        espManager = ESP32Manager(
            ip = "192.168.4.1", // Replace with ESP32's actual IP
            port = 8080,       // Replace with ESP32's actual port
            onDataReceived = { data ->
                println("Received data: $data") // You can handle raw data here
            }
        )
        espManager.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        espManager.disconnect() // Ensure the connection is closed when activity is destroyed
    }
}

@Composable
fun DragRaceScreen(
    espManager: ESP32Manager,
    onBack: () -> Unit,
    database: ESPDatabase
) {
    var isSessionActive by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(0.0) }
    var acceleration by remember { mutableStateOf(0.0) }
    var zeroToHundredTime by remember { mutableStateOf<Double?>(null) }
    var sessionStartTime by remember { mutableStateOf<Long?>(null) }
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val gpsData = remember { mutableStateListOf<Pair<Double, Double>>() }

    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            sessionStartTime = System.currentTimeMillis()
            gpsData.clear()
            zeroToHundredTime = null

            while (isSessionActive) {
                val newData = espManager.onDataReceived // Fetch GPS data from ESP32Manager
                //gpsData.add(newData)
                speed = calculateSpeed(gpsData) // Calculate speed from GPS data
                acceleration = calculateAcceleration(gpsData) // Calculate acceleration

                if (zeroToHundredTime == null && speed >= 100.0) {
                    zeroToHundredTime = (System.currentTimeMillis() - sessionStartTime!!) / 1000.0
                }

                delay(40) // 25 Hz = 1000ms / 25 = 40ms interval
                currentTime = System.currentTimeMillis()
            }
        }
    }

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

        Text(
            text = "Speed: ${"%.2f".format(speed)} km/h",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "Acceleration: ${"%.2f".format(acceleration)} m/s²",
            style = MaterialTheme.typography.titleMedium
        )

        zeroToHundredTime?.let {
            Text(
                text = "0-100 km/h Time: ${"%.2f".format(it)} seconds",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Session Time: ${(currentTime - (sessionStartTime ?: currentTime)) / 1000} seconds",
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Absolute.Right,
            verticalAlignment = Alignment.Bottom
        ) {
            Button(
                onClick = {
                    isSessionActive = !isSessionActive
                    startSession(database)
                }
            ) {
                Text(if (isSessionActive) "Stop Session" else "Start Session")
            }
        }
    }
}

private fun startSession(database: ESPDatabase)
{
    SessionManager.startSession(database,"drag","")
}

private fun calculateSpeed(gpsData: List<Pair<Double, Double>>): Double {
    // Implement a function to calculate speed from GPS data (in km/h)
    // This is just a placeholder
    return gpsData.size * 2.0
}

private fun calculateAcceleration(gpsData: List<Pair<Double, Double>>): Double {
    // Implement a function to calculate acceleration (in m/s^2)
    // This is just a placeholder
    return gpsData.size * 0.1
}


@Preview(showBackground = true)
@Composable
fun DragScreenPreview() {
    val mockESPManager = ESP32Manager(
        ip = "mock", // Mock IP
        port = 0,    // Mock Port
        onDataReceived = {} // No-op for preview
    )

    // Create a fake or mock database instance
    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()

    DragRaceScreen(
        espManager = mockESPManager,
        onBack = {},
        database = fakeDatabase
    )
}


// Helper functions

fun StartSession(database: ESPDatabase): Int
{
    val sessionManager = SessionManager.getInstance(database)

    CoroutineScope(Dispatchers.IO).launch {
        sessionManager.startSession("DragSession", "Drag data session")
    }

    return sessionManager.getCurrentSessionId()?.toInt() ?:-1 ;
}

fun addRawDataToSession(sessionId : Int)
{

}

fun EndSession()
{

}


