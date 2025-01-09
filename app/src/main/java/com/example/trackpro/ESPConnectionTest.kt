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
import com.example.trackpro.ManagerClasses.ESPWebSocketClient

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
    val espWebSocketClient = remember {
        ESPWebSocketClient(
            url = "ws://192.168.4.1:81", // WebSocket URL of ESP32
            onMessageReceived = { data ->
                // Handle incoming data from ESP32
                println("Data received: $data")
            },
            onConnectionStatusChanged = { isConnected ->
                // Handle connection status changes
                println("Connection status: ${if (isConnected) "Connected" else "Disconnected"}")
            }
        )
    }

    val connectionStatus = remember { mutableStateOf(false) }
    val receivedData = remember { mutableStateOf("") }

    // Force the WebSocket to connect when the Composable is displayed
    LaunchedEffect(Unit) {
        espWebSocketClient.connect() // Force the WebSocket to connect
    }

    // Clean up the WebSocket connection when leaving the Composable
    DisposableEffect(Unit) {
        espWebSocketClient.onConnectionStatusChanged = { isConnected ->
            connectionStatus.value = isConnected
        }
        espWebSocketClient.onMessageReceived = { data ->
            receivedData.value = data
        }

        onDispose {
            espWebSocketClient.disconnect()
        }
    }

    // UI Layout for connection status and received data
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
