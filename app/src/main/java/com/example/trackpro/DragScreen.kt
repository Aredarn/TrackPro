package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.room.Room
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESP32Manager
import com.example.trackpro.ManagerClasses.SessionManager
import com.example.trackpro.ESPDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
fun DragRaceScreen(espManager: ESP32Manager, onBack: () -> Unit, database: ESPDatabase) {

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


