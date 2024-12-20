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
            sessionManager.startSession(eventType = "Test Event", description = "Test description")
            Log.e("SessionManager", "Session started with ID: ${sessionManager.getCurrentSessionId()}")
        }

        // Initialize ESP32Manager with IP and port
        espManager = ESP32Manager(
            ip = "192.168.4.1", // Replace with ESP32's actual IP
            port = 8080,       // Replace with ESP32's actual port
            onDataReceived = { data ->
                println("Received data: $data") // You can handle raw data here
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
                            database = database
                        )
                    }
                    composable("graph") {
                        espManager.disconnect() // Ensure the connection is closed when activity is destroyed
                        GraphScreen(onBack = { navController.popBackStack() })
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


@Composable
fun MainScreen(espManager: ESP32Manager, onNavigateToGraph: () -> Unit, database: ESPDatabase) {
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
        espManager.connect()
        espManager.onDataReceived = { data ->
            rawData = data // Update raw data string
            val parsed = parseData(data) // Parse the data if it's formatted as "speed,acceleration,timestamp"
            if (parsed != null) {
                speed = parsed.first
                acceleration = parsed.second
                timestamp = parsed.third

                // If the speed is greater than 0 and the timer hasn't started, start tracking the time
                if (speed > 0f && !isAccelerating) {
                    startTime = timestamp // Use Arduino's timestamp as the start time
                    accelerationStartTime = timestamp // Store the timestamp of the start of acceleration
                    isAccelerating = true
                }

                // If the speed reaches 100 km/h and the timer is running, calculate the time taken
                if (speed >= 100f && isAccelerating) {
                    // Calculate elapsed time in seconds
                    val elapsedMillis = timestamp!! - startTime!! // Time elapsed in milliseconds
                    val elapsedSeconds = elapsedMillis / 1000f // Convert to seconds
                    accToHundred = "Time: %.2f sec".format(elapsedSeconds)
                    startTime = null // Reset the timer for the next acceleration cycle
                    accelerationStartTime = null // Reset the acceleration start time
                }

                // Fetch the current session ID and insert the GPS data only if the session is active
                val currentSessionId = sessionManager.getCurrentSessionId()

                if (currentSessionId != null) {
                    // Prepare the RawGPSData object with your data (e.g., speed, timestamp, etc.)
                    val rawGPSData = RawGPSData(
                        sessionid = currentSessionId,  // Use the session ID fetched
                        latitude = 0.0,  // Replace with actual latitude
                        longitude = 0.0, // Replace with actual longitude
                        altitude = null, // Replace with actual altitude if available
                        timestamp = timestamp!!, // Make sure to handle the nullability of timestamp
                        speed = speed, // Use actual speed value
                        fixQuality = null // Replace with actual fix quality if available
                    )

                    // Insert the RawGPSData into the database
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            database.rawGPSDataDao().insert(rawGPSData)
                        } catch (e: Exception) {
                            println("Error inserting data: ${e.message}")
                        }
                    }
                } else {
                    // Log that there is no active session
                    Log.e("SessionManager", "No active session. Cannot insert data.")
                }
            } else {
                // Handle parsing errors, e.g., log an error or display a message to the user
                println("Error parsing data: $data")
            }
        }
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

        // Show the 0-100 time
        Text(
            text = "0-100: $elapsedTime",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(6.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Show the 0-100 time
        Text(
            text = "0-100: $accToHundred",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(6.dp)
        )
        Button(
            onClick = onNavigateToGraph,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("View Graph")
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
        ip = "mock", // Mock IP
        port = 0,    // Mock Port
        onDataReceived = {} // No-op for preview
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
            database = fakeDatabase
        )
    }
}
