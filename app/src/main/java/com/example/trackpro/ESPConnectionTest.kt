package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ManagerClasses.ESP32Manager
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import androidx.compose.runtime.*
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.RawGPSData  // Make sure to use the correct package
class ESPConnectionTest : ComponentActivity() {

    private lateinit var espManager: ESP32Manager // ESP32 Manager instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ESPConnectionTestScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        espManager.disconnect() // Ensure the connection is closed when activity is destroyed
    }
}

data class RawGPSData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val timestamp: Long,
    val speed: Float?,
    val satellites: Int?
)

@Composable
fun ESPConnectionTestScreen() {
    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }

    // Use LaunchedEffect to launch a side effect when the composable is first entered
    LaunchedEffect(Unit) {
        val espTcpClient = ESPTcpClient(
            serverAddress = "192.168.4.1",  // Replace with your server's IP address
            port = 4210,  // Replace with your server's port
            onMessageReceived = { data ->
                println("Received data: $data")  // Log raw JSON
                gpsData.value = try {
                    Json.decodeFromString<RawGPSData>(data.toString())  // Parse into RawGPSData
                } catch (e: Exception) {
                    println("Error parsing data: ${e.message}")
                    null
                }
                rawJson.value = data.toString()  // Store raw JSON for display
            },
            onConnectionStatusChanged = { connected ->
                isConnected.value = connected
                println("Connection status: ${if (connected) "Connected" else "Disconnected"}")
            }
        )

        espTcpClient.connect()  // Connect to the server
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Display connection status
        Text("Connection Status: ${if (isConnected.value) "Connected" else "Disconnected"}")
        Spacer(modifier = Modifier.height(16.dp))

        // Display the raw JSON data
        Text("Raw JSON Data:")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = rawJson.value, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(16.dp))

        // Display parsed GPS data
        gpsData.value?.let { data ->
            Text("Latitude: ${data.latitude}")
            Text("Longitude: ${data.longitude}")
            Text("Speed: ${data.speed}")
            Text("Satellites: ${data.satellites}")
            Text("Timestamp: ${data.timestamp}")
        } ?: run {
            // Show a loading or placeholder text while data is being fetched
            Text("Waiting for GPS data...")
        }
    }
}



@Preview(showBackground = true)
@Composable
fun ESPConnectionTestPreview() {
    ESPConnectionTestScreen()
}
