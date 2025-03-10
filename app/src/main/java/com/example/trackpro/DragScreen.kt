package com.example.trackpro

import android.graphics.Color
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
import com.yourpackage.ui.components.SevenSegmentView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
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
    var dragTime: Double? by remember { mutableStateOf(null) }


    val dataPoints = remember { mutableStateListOf<Entry>() }
    val context = LocalContext.current  // Get the Context in Compose
    val (ip, port) = remember { JsonReader.loadConfig(context) } // Load once & remember it
    val coroutineScope = rememberCoroutineScope()
    // Buffer list for batch inserts
    val dataBuffer = mutableListOf<RawGPSData>()

// Coroutine job to handle periodic inserts
    var insertJob: Job? = null

    var i = 0f;


    fun startBatchInsert() {
        insertJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                if (dataBuffer.isNotEmpty()) {
                    try {
                        Log.d("BatchInsert", "Inserting ${dataBuffer.size} data points at ${System.currentTimeMillis()}")
                        database.rawGPSDataDao().insertAll(dataBuffer.toList())
                        dataBuffer.clear()
                    } catch (e: Exception) {
                        Log.e("Database", "Batch insert failed: ${e.message}")
                    }
                }
                // Keep the latest 1000 points only
                if (dataPoints.size > 1000) {
                    dataPoints.removeAt(0)
                }

            }
        }
    }

    fun stopBatchInsert() {
        insertJob?.cancel()
        dataBuffer.clear()
        insertJob = null
    }

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
            startBatchInsert()
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
                                // Add data to buffer instead of inserting directly
                                dataBuffer.add(data)

                                // Update graph points (if needed)
                                data.speed?.let {
                                    Entry(i, it.toFloat())
                                }?.let { dataPoints.add(it) }

                                i += 1
                                lastTimestamp = data.timestamp
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

    DisposableEffect(Unit) {
        onDispose {
            try {
                espTcpClient?.disconnect()
                stopBatchInsert()
                //Log.d("TCP", "Disconnected from server")
            } catch (e: IOException) {
                e.printStackTrace()
            }
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

            //Diagram
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
                    val dataSet = LineDataSet(dataPoints, "Speed (km/h)").apply {
                        setDrawValues(false)
                        setDrawCircles(false)
                        lineWidth = 4f  // Increase line thickness
                        color = Color.RED  // Set a more visible color
                        setDrawFilled(true)  // Fill area under the line
                        fillColor = Color.parseColor("#80FF0000") // Semi-transparent fill
                    }

                    if (chart.data == null) {
                        chart.data = LineData(dataSet)
                    } else {
                        chart.data.clearValues()
                        chart.data.addDataSet(dataSet)
                    }

                    chart.notifyDataSetChanged() // Notify the chart of dataset changes
                    chart.postInvalidate() // Refresh UI immediately
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height((screenHeight / 2).dp)
                    .padding(16.dp)
            )


            Spacer(modifier = Modifier.height(16.dp))


            dragTime?.let {
                Text(
                    text = "Drag Time: ${it}s",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        /*
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(50.dp)
            ) {
                SevenSegmentView(
                    number = gpsData.value?.speed?.toInt() ?: 0,
                    digitsNumber = 3,
                    segmentsSpace = 1.dp,
                    segmentWidth = 8.dp,
                    digitsSpace = 16.dp,
                    activeColor = androidx.compose.ui.graphics.Color.Black,
                    modifier = Modifier.height(100.dp)
                )
            }
        } */

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

                            if(sessionID.toInt() != -1)
                            {
                                stopBatchInsert()
                                   endSession(database)

                                    Log.d("Ended?","I guess the session ended")
                            }
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

    suspend fun endSession(database: ESPDatabase)
    {
        val sessionManager = SessionManager.getInstance(database)

        withContext(Dispatchers.IO) {
            Log.d("In end", sessionManager.getCurrentSessionId().toString())
            sessionManager.endSession()
        }
    }


    suspend fun endSessionPostProcess(sessionId: Long, database: ESPDatabase): Double {
        Log.d("trackpro", "Ending session")
        val dragTimeCalculation = DragTimeCalculation(sessionId, database)
        return dragTimeCalculation.timeFromZeroToHundred()
    }


