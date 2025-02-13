package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData  // Make sure to use the correct package
import java.io.IOException

class ESPConnectionTest : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ESPConnectionTestScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy();
    }
}

@Composable
fun ESPConnectionTestScreen() {
    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }

    val context = LocalContext.current  // Get the Context in Compose
    val (ip, port) = remember { JsonReader.loadConfig(context) } // Load once & remember it


    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }

    // Establish connection when the composable is entered
    LaunchedEffect(Unit) {

        espTcpClient = ESPTcpClient(
            serverAddress = ip,
            port = port,
            onMessageReceived = { data ->
                //println("Received data: $data")  // Log raw JSON
                gpsData.value = data // Directly assign the RawGPSData object
                rawJson.value = data.toString()  // Store raw JSON for display
            },
            onConnectionStatusChanged = { connected ->
                isConnected.value = connected
                //println("Connection status: ${if (connected) "Connected" else "Disconnected"}")
            }
        )
        espTcpClient?.connect()  // Connect to the server

    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                espTcpClient?.disconnect()
                //Log.d("TCP", "Disconnected from server")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Connection Status: ${if (isConnected.value) "Connected" else "Disconnected"}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected.value) Color.Green else Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Raw JSON Display
        Text("Raw JSON Data:", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = rawJson.value.ifEmpty { "Waiting for data..." },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .background(Color.LightGray.copy(alpha = 0.2f))
                .padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Parsed GPS Data Display
        gpsData.value?.let { data ->
            Text("Parsed GPS Data:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Latitude: ${data.latitude}")
            Text("Longitude: ${data.longitude}")
            Text("Altitude: ${data.altitude ?: "N/A"}")
            Text("Speed: ${data.speed ?: "N/A"}")
            Text("Satellites: ${data.satellites ?: "N/A"}")
            Text("Timestamp: ${data.timestamp}")
        } ?: run {
            // Display a loading message while waiting for GPS data
            Text("Waiting for GPS data...")
        }

        Spacer(modifier = Modifier.height(16.dp))

    }
}




@Preview(showBackground = true)
@Composable
fun ESPConnectionTestPreview() {
    ESPConnectionTestScreen()
}
