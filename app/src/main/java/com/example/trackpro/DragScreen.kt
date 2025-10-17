package com.example.trackpro

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ExtrasForUI.DropdownMenuFieldMulti
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.SessionManager
import com.example.trackpro.ManagerClasses.toDataClass
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.yourpackage.ui.components.SevenSegmentView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import com.example.trackpro.ViewModels.VehicleViewModel
import com.example.trackpro.ViewModels.VehicleViewModelFactory


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

    var isSessionActive by rememberSaveable { mutableStateOf(false) }
    var sessionID by rememberSaveable { mutableLongStateOf(-1) }

    val isConnected = rememberSaveable { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = rememberSaveable { mutableStateOf("") }

    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    var lastTimestamp: Long? by rememberSaveable { mutableStateOf(null) }
    var isReady: Boolean by rememberSaveable { mutableStateOf(false) }
    var dragTime: Double? by rememberSaveable { mutableStateOf(null) }
    var quarterMileTime: Double? by rememberSaveable { mutableStateOf(null) }

    val dataPoints = remember { mutableStateListOf<Entry>() }
    val context = LocalContext.current  // Get the Context in Compose
    val (ip, port) = rememberSaveable { JsonReader.loadConfig(context) } // Load once & remember it
    val coroutineScope = rememberCoroutineScope()
    // Buffer list for batch inserts
    val dataBuffer = mutableListOf<RawGPSData>()

    // Coroutine job to handle periodic inserts
    var insertJob: Job? by remember { mutableStateOf(null) }

    var i = 0f

    // Get the ViewModel using viewModel()
    val viewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))
    // Observe the state for vehicles and loadingState
    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    val loadingState by viewModel.loadingState.collectAsState()

    val selectedVehicle by rememberSaveable { mutableStateOf("") }
    var selectedVehicleId by rememberSaveable { mutableIntStateOf(-1) }


    fun startBatchInsert() {
        insertJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)

                // Initialize safely outside the synchronized block
                val dataToInsert: List<RawGPSData> = synchronized(dataBuffer) {
                    if (dataBuffer.isNotEmpty()) {
                        dataBuffer.toList().also { dataBuffer.clear() } // Copy & clear safely
                    } else {
                        emptyList() // Return an empty list if no data is available
                    }
                }

                if (dataToInsert.isNotEmpty()) {
                    try {
                        Log.d(
                            "BatchInsert",
                            "Inserting ${dataToInsert.size} data points at ${System.currentTimeMillis()}"
                        )
                        database.rawGPSDataDao().insertAll(dataToInsert) // Safe call

                        val list = dataPoints.takeLast(5)
                        isReady = list.all { it.y <= 2 }

                    } catch (e: Exception) {
                        Log.e("Database", "Batch insert failed: ${e.message}")
                    }
                }

                // Keep only the latest 1000 points (UI-related)
                withContext(Dispatchers.Main) {
                    synchronized(dataPoints) {
                        while (dataPoints.size > 1000) {
                            dataPoints.removeAt(0)
                        }
                    }
                }
            }
        }
    }

    suspend fun stopBatchInsert() {
        endSession(database)
        insertJob?.cancel()
        dataBuffer.clear()
        insertJob = null
    }

    LaunchedEffect(Unit) {
        try {

            viewModel.fetchVehicles()

            // Initialize ESPTcpClient
            startBatchInsert()

            Log.d("trackpro ip",ip)
            Log.d("trackpro", port.toString() + "")
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
                                sessionid = sessionID,
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
                                dataBuffer.add(data)
                                lastTimestamp = data.timestamp

                                coroutineScope.launch(Dispatchers.Main) {
                                    data.speed?.let {
                                        dataPoints.add(Entry(i, it))
                                        i += 1
                                    }
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

    DisposableEffect(Unit) {
        onDispose {
            try {
                coroutineScope.launch(Dispatchers.IO) {
                    espTcpClient?.disconnect()
                    stopBatchInsert()
                    //endSessionPostProcess(sessionID, database)
                }
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
                text = "Drag Time",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isConnected.value) "Connected to ESP" else "Not Connected to ESP",
                color = if (isConnected.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))


            // Show a loading state while vehicles are being fetched
            if (loadingState) {
                Text(text = "Loading vehicles...") // Show loading message
            } else {
                if (!isSessionActive) {
                    // DropdownMenuFieldMulti will be displayed when vehicles are available
                    if (vehicles.isNotEmpty()) {
                        DropdownMenuFieldMulti(
                            "Select car",
                            vehicles,
                            selectedVehicle
                        ) { selectedVehicleId = it.toInt() }
                    } else {
                        Text(text = "No vehicles available") // Show a message if no vehicles are found
                    }
                }
            }

            if (isSessionActive) {
                Text(text = if (isReady) "Go go GO" else "Wait")
            }


            Spacer(modifier = Modifier.height(12.dp))

            //Diagram
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0, 0, 0, 255))
            )
            {
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
                        val safeList = dataPoints.toList() // create snapshot

                        val dataSet = LineDataSet(safeList, "Speed (km/h)").apply {
                            setDrawValues(false)
                            setDrawCircles(false)
                            lineWidth = 4f  // Increase line thickness
                            color = 2  // Set a more visible color
                            setDrawFilled(true)  // Fill area under the line
                            fillColor = 230 // Semi-transparent fill
                        }

                        if (chart.data == null) {
                            chart.data = LineData(dataSet)
                        } else {
                            chart.data.clearValues()
                            chart.data.addDataSet(dataSet)
                        }

                        chart.notifyDataSetChanged()
                        chart.postInvalidate()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((screenHeight / 2.1).dp)
                        .border(14.dp, Color(0, 0, 255, 0))
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            bottomStart = 20.dp,
                            bottomEnd = 20.dp
                        )
                    ) // Clip only bottom corners
                    .background(Color.White) // Box background color
                    .padding(20.dp)
            )

            {
                Column()
                {
                    /*
                    Row(modifier = Modifier.fillMaxWidth() ) {
                        Text(
                            text = "Speed : ${gpsData.value?.speed ?: -1} Km/H",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight(700))
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))*/


                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Acceleration (0-100): ${dragTime ?: -1} sec",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight(700)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Quarter mile time: ${quarterMileTime?: -1} sec",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight(700)
                            )
                        )
                    }
                }

            }


        }

        // A 7 segment display to show speed.

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                SevenSegmentView(
                    number = gpsData.value?.speed?.toInt() ?: 0,
                    digitsNumber = 3,
                    segmentsSpace = 1.dp,
                    segmentWidth = 8.dp,
                    digitsSpace = 16.dp,
                    activeColor = Color.Black,
                    modifier = Modifier.height(100.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (!isSessionActive) {
                        coroutineScope.launch {
                            Log.d("CarID", selectedVehicleId.toString())

                            if(selectedVehicleId == -1)
                            {
                                Toast.makeText(context, "⚠️ No car selected", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val id = startSession(database, selectedVehicleId.toLong())

                            if (id == -1L) {
                                Log.e("Session", "Invalid vehicle ID or failed to start session")
                                return@launch
                            }

                            sessionID = id
                            isSessionActive = true

                            Log.d("session:", "Started with Id: $sessionID")
                        }
                    } else {
                        coroutineScope.launch {
                            isSessionActive = !isSessionActive

                            if (sessionID.toInt() != -1) {
                                stopBatchInsert()
                                endSession(database)
                            }
                            Log.d("isItFalse?", isSessionActive.toString())
                            dragTime = endSessionPostProcess(sessionID, database)
                            quarterMileTime = getQuarterMileTime(sessionID,database)
                        }
                    }
                }
            ) {
                Text(if (isSessionActive) "Stop Session" else "Start Session")
            }
        }
    }

}


suspend fun startSession(database: ESPDatabase, selectedVehicleId: Long): Long {

    val sessionManager = SessionManager.getInstance(database)
    var id: Long

    // Use suspendCoroutine to suspend until the session id is retrieved.
    withContext(Dispatchers.IO) {
        sessionManager.startSession(
            "DragSession",
            "Drag data session",
            vehicleId = selectedVehicleId
        )
        Log.d("In start", sessionManager.getCurrentSessionId().toString())
        id = (sessionManager.getCurrentSessionId() ?: -1).toLong()
    }

    return id
}

suspend fun endSession(database: ESPDatabase) {
    val sessionManager = SessionManager.getInstance(database)

    withContext(Dispatchers.IO) {
        Log.d("In end", sessionManager.getCurrentSessionId().toString())
        sessionManager.endSession()
    }
}

suspend fun endSessionPostProcess(sessionId: Long, database: ESPDatabase): Double {
    val dragTimeCalculation = DragTimeCalculation(sessionId, database)
    return dragTimeCalculation.timeFromZeroToHundred()
}

suspend fun getQuarterMileTime(sessionId: Long,database: ESPDatabase) : Double
{
    val quarterTime = DragTimeCalculation(sessionId,database)
    return quarterTime.quarterMile()

}

@Preview(
    showBackground = true,
    //device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
)
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


