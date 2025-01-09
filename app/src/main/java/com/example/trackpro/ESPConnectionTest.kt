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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ManagerClasses.ESP32Manager

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

@Composable
fun ESPConnectionTestScreen() {
    // Create an instance of ESP32Manager
    val espManager = remember {
        ESP32Manager(
            url = "ws://192.168.4.1:81", // WebSocket URL of ESP32
            onDataReceived = { data ->
                // Handle incoming data from ESP32
                println("Data received: $data")
            },
            onConnectionStatusChanged = { isConnected ->
                // Handle connection status changes
                println("Connection status: ${if (isConnected) "Connected" else "Disconnected"}")
            }
        )
    }

    // State to hold connection status and received data
    val connectionStatus = remember { mutableStateOf(false) }
    val receivedData = remember { mutableStateOf("") }

    // Establish WebSocket connection when the Composable is first displayed
    LaunchedEffect(Unit) {
        espManager.connect()
    }

    // Clean up WebSocket connection when leaving the Composable
    DisposableEffect(Unit) {
        // Update the connection status and data when received
        espManager.onConnectionStatusChanged = { isConnected ->
            connectionStatus.value = isConnected
        }
        espManager.onDataReceived = { data ->
            receivedData.value = data
        }

        // Disconnect WebSocket when leaving this Composable
        onDispose {
            espManager.disconnect()
        }
    }

    // UI Layout for the connection status and received data
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "ESP32 Connection Test",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Display connection status (Connected/Disconnected)
        Text(
            text = if (connectionStatus.value) "Connected" else "Disconnected",
            color = if (connectionStatus.value) Color.Green else Color.Red
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Display the data received from ESP32
        Text(
            text = "Data: ${receivedData.value}",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ESPConnectionTestPreview() {
    ESPConnectionTestScreen()
}
