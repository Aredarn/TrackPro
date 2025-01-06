package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ManagerClasses.ESP32Manager

class ESPConnectionTest : ComponentActivity()
{
    private lateinit var espManager: ESP32Manager // ESP32 Manager instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
fun ESPConnectionTestScreen(
    espManager: ESP32Manager
)
{

    espManager.connect()




    Column {
        Text(
            text = "Test page for your ESP32",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))



    }
}

@Preview(showBackground = true)
@Composable
fun ESPConnectionTestPreview()
{
    val mockESPManager = ESP32Manager(
        ip = "mock", // Mock IP
        port = 0,    // Mock Port
        onDataReceived = {} // No-op for preview
    )
    ESPConnectionTestScreen(mockESPManager)

}