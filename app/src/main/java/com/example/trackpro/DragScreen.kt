package com.example.trackpro

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.room.Room
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.SessionManager
import com.example.trackpro.ManagerClasses.toDataClass
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DragScreen : ComponentActivity() {

    private lateinit var database: ESPDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ESPDatabase.getInstance(applicationContext)

        setContent {
            DragRaceScreen(
                database = database,
                onBack = { finish() }
            )
        }
    }
}

@Composable
fun DragRaceScreen(
    database: ESPDatabase,
    onBack: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp


    var isSessionActive by remember { mutableStateOf(false) }
    var sessionID by remember { mutableLongStateOf(-1) }

    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }

    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    var lastTimestamp: Long? by remember { mutableStateOf(null) }
    var dragTime: Int? by remember { mutableStateOf(null) }


    val dataPoints = remember { mutableStateListOf<Entry>() }
    val context = LocalContext.current  // Get the Context in Compose
    val (ip, port) = remember { JsonReader.loadConfig(context) } // Load once & remember it

    var i = 0f;


    LaunchedEffect(Unit) {
        try {
            /*
            dataPoints.add(Entry(1f,0f))
            dataPoints.add(Entry(2f,21f))
            dataPoints.add(Entry(3f,29f))
            dataPoints.add(Entry(4f,36f))
            dataPoints.add(Entry(5f,45f))
            dataPoints.add(Entry(6f,58f))
            dataPoints.add(Entry(7f,75f))
            dataPoints.add(Entry(8f,86f))
            dataPoints.add(Entry(9f,95f))
            dataPoints.add(Entry(10f,102f))
            */
            // Initialize ESPTcpClient

            espTcpClient = ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data ->
                    // Update state with received data
                    gpsData.value = data.toDataClass()
                    rawJson.value = data.toString()

                    // Only process and store data if session is active and connected
                    if (isSessionActive && isConnected.value) {
                        val derivedData = gpsData.value?.let {
                            RawGPSData(
                                sessionid = sessionID.toLong(),
                                latitude = it.latitude,
                                longitude = it.longitude,
                                altitude = it.altitude,
                                timestamp = it.timestamp,
                                speed = it.speed,
                                fixQuality = it.fixQuality
                            )
                        }

                        derivedData?.let { data ->
                            if (lastTimestamp == null || data.timestamp != lastTimestamp) {
                                try {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            Log.d("Data:", data.toString())
                                            database.rawGPSDataDao().insert(data)

                                            data.speed?.let {
                                                Entry(i,
                                                    it.toFloat())
                                            }?.let { dataPoints.add(it) }

                                            i = i+1;

                                            lastTimestamp = data.timestamp
                                        } catch (e: Exception) {
                                            Log.e("Database", "Error inserting data: ${e.message}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("Database", "Error inserting data: ${e.message}")
                                }
                            }
                        }
                    }
                },
                onConnectionStatusChanged = { connected ->
                    isConnected.value = connected
                }
            )

            // Connect the client
            espTcpClient?.connect()

        } catch (e: Exception) {
            Log.e("LaunchedEffect", "Error: ${e.message}")
        } finally {
            espTcpClient?.disconnect()
        }
    }





    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Drag Time Calculator",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected.value) "Connected to ESP" else "Not Connected",
                color = if (isConnected.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))
            AndroidView(
                factory = { context ->
                    LineChart(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // Customize the chart
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.setDrawGridLines(false)
                        axisRight.isEnabled = false
                        description.isEnabled = false
                    }
                },
                update = { chart ->
                    // Update chart data
                    val dataSet = LineDataSet(dataPoints, "Speed (km/h)")
                    dataSet.setDrawValues(false)
                    dataSet.setDrawCircles(false)

                    chart.data = LineData(dataSet)
                    chart.invalidate() // Refresh the chart
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenHeight / 2).dp) // Half of the screen height
                    .padding(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))


            dragTime?.let {
                Text(
                    text = "Drag Time: ${it}ms",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }

            Button(
                onClick = {
                    if (!isSessionActive) {
                        CoroutineScope(Dispatchers.Main).launch {
                            isSessionActive = !isSessionActive

                            Log.d("isItTrue?", isSessionActive.toString());
                            sessionID = startSession(database)
                            Log.d("sessionid:", "Id:" + sessionID)
                        }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            isSessionActive = !isSessionActive

                            Log.d("isItFalse?" , isSessionActive.toString());
                            dragTime = endSessionPostProcess(sessionID, database)
                        }
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
    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()

    DragRaceScreen(
        database = fakeDatabase,
        onBack = {}
    )
}

    suspend fun startSession(database: ESPDatabase): Long {
        val sessionManager = SessionManager.getInstance(database)
        var id: Long = -1

        // Use suspendCoroutine to suspend until the session id is retrieved.
        withContext(Dispatchers.IO) {
            sessionManager.startSession("DragSession", "Drag data session")
            Log.d("In start", sessionManager.getCurrentSessionId().toString())
            id = (sessionManager.getCurrentSessionId() ?: -1).toLong()
        }

        return id
    }


    suspend fun endSessionPostProcess(sessionId: Long, database: ESPDatabase): Int {
        Log.d("trackpro", "Ending session")
        val dragTimeCalculation = DragTimeCalculation(sessionId, database)
        return dragTimeCalculation.timeFromZeroToHundred()
    }

fun parseTimeToMilliseconds(timeString: String): Long {
    val format = SimpleDateFormat("HH:mm:ss")
    val date: Date = format.parse(timeString) ?: throw IllegalArgumentException("Invalid time format")
    return date.time.toLong()  // This will give the timestamp in milliseconds
}


