package com.example.trackpro

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ui.theme.TrackProTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESP32Manager
import com.example.trackpro.ManagerClasses.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrackProApp : Application() {
    lateinit var database: ESPDatabase private set
    override fun onCreate() {
        super.onCreate()
        database = ESPDatabase.getInstance(this)
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var espManager: ESP32Manager // ESP32 Manager instance
    private lateinit var database: ESPDatabase
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (application as TrackProApp).database
        sessionManager = SessionManager.getInstance(database)

        // Explicitly start a session here
        CoroutineScope(Dispatchers.IO).launch {
            //sessionManager.startSession(eventType = "Test Event", description = "Test description")
            Log.e("SessionManager", "Session started with ID: ${sessionManager.getCurrentSessionId()}")
        }

        // Initialize ESP32Manager with IP and port
        val espManager = ESP32Manager(
            url = "ws://192.168.4.1:81", // WebSocket URL of ESP32
            onDataReceived = { data ->
                // Handle incoming data from ESP32
                println("Data received: $data")
            },
            onConnectionStatusChanged = { isConnected ->
                // Handle connection status changes
                if (isConnected) {
                    println("Connected to ESP32")
                } else {
                    println("Disconnected from ESP32")
                }
            }
        )

        espManager.connect()

        setContent {
            TrackProTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            espManager = espManager,
                            onNavigateToGraph = { navController.navigate("graph") },
                            onNavigateToDragRace = {navController.navigate("drag")},
                            onNavigateToESPTestScreen = {navController.navigate("esptest")},
                            database = database
                        )
                    }
                    composable("graph") {
                        espManager.disconnect() // Ensure the connection is closed when activity is destroyed
                        GraphScreen(onBack = { navController.popBackStack() })
                    }
                    composable("drag")
                    {
                        espManager.disconnect();
                        DragRaceScreen(
                            espManager = espManager,
                            database = database,
                            onBack = {navController.popBackStack()}
                        )
                    }
                    composable("esptest")
                    {
                        espManager.disconnect();
                        ESPConnectionTestScreen()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        espManager.disconnect() // Ensure the connection is closed when activity is destroyed
    }
}


//

@Composable
fun MainScreen(espManager: ESP32Manager, onNavigateToGraph: () -> Unit,onNavigateToDragRace: () -> Unit,onNavigateToESPTestScreen:() -> Unit, database: ESPDatabase) {
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = SessionManager.getInstance(database)

    // Real-time state variables
    var speed by remember { mutableStateOf(0f) } // in km/h
    var acceleration by remember { mutableStateOf(0f) } // in m/s²
    var rawData by remember { mutableStateOf("No Data Yet") }
    var startTime: Long? by remember { mutableStateOf(null) }
    var isAccelerating by remember { mutableStateOf(false) }
    var accToHundred: String? by remember { mutableStateOf(null) }
    var timestamp: Long? by remember { mutableStateOf(null) }
    var accelerationStartTime: Long? by remember { mutableStateOf(null) }
    var elapsedTime: Float? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
    }

    // Composables displaying the data
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Speed: %.2f km/h".format(speed),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = "Acceleration: %.2f m/s²".format(acceleration),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Raw Data: $rawData",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateToGraph,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("View Graph")
        }
        Button(
            onClick = onNavigateToDragRace,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Drag times")
        }
        Button(
            onClick = onNavigateToESPTestScreen,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("ESP connection tester")
        }

    }
}



fun parseData(data: String): Triple<Float, Float, Long>? {
    return try {
        // Split the data by commas (expecting "speed,acceleration,timestamp")
        val parts = data.split(",")

        // Parse speed, acceleration, and timestamp from the parts
        val speed = parts[0].toFloat()
        val acceleration = parts[1].toFloat()
        val timestamp = parts[2].toLong() // Assuming timestamp is in milliseconds

        // Return a Triple with speed, acceleration, and timestamp
        Triple(speed, acceleration, timestamp)
    } catch (e: Exception) {
        e.printStackTrace()
        null // Return null in case of any errors
    }
}




@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    // Create a mock ESP32Manager
    val mockESPManager = ESP32Manager(
        url = "ws://192.168.4.1:81", // WebSocket URL of ESP32
        onDataReceived = { data ->
            // Handle incoming data from ESP32
            println("Data received: $data")
        },
        onConnectionStatusChanged = { isConnected ->
            // Handle connection status changes
            if (isConnected) {
                println("Connected to ESP32")
            } else {
                println("Disconnected from ESP32")
            }
        }
    )


    // Create a fake or mock database instance
    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()

    // Use the fake database in the preview
    TrackProTheme {
        MainScreen(
            espManager = mockESPManager,
            onNavigateToGraph = {},
            onNavigateToDragRace = {},
            onNavigateToESPTestScreen = {},
            database = fakeDatabase
        )
    }
}
