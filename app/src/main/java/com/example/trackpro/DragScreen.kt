package com.example.trackpro

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ExtrasForUI.DropdownMenuFieldMulti
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.SessionManager
import com.example.trackpro.ManagerClasses.toDataClass
import com.example.trackpro.Models.VehiclePair
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

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

class VehicleViewModel(private val database: ESPDatabase) : ViewModel() {
    private val _vehicles = MutableStateFlow<List<VehiclePair>>(emptyList())
    val vehicles: StateFlow<List<VehiclePair>> = _vehicles

    private val _loadingState = MutableStateFlow(true) // Track loading state
    val loadingState: StateFlow<Boolean> = _loadingState

    fun fetchVehicles() {
        viewModelScope.launch {
            _loadingState.value = true // Set loading state to true before fetching
            Log.d("ViewModel", "Fetching vehicles...")

            try {
                val fetchedVehicles = database.vehicleInformationDAO().getPairVehicles().first() // Collect the first value
                _vehicles.value = fetchedVehicles
                Log.d("ViewModel", "Fetched vehicles: ${_vehicles.value}")
            } catch (e: Exception) {
                Log.e("Error", "Fetching vehicles failed: ${e.message}")
            } finally {
                _loadingState.value = false // Set loading state to false after fetching
                Log.d("ViewModel", "Loading state: ${_loadingState.value}")
            }
        }
    }
}

class VehicleViewModelFactory(private val database: ESPDatabase) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VehicleViewModel::class.java)) {
            // Ensure the return type is correct (casting it to T)
            @Suppress("UNCHECKED_CAST")
            return VehicleViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
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
    val gpsData = rememberSaveable { mutableStateOf<RawGPSData?>(null) }
    val rawJson = rememberSaveable { mutableStateOf("") }

    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    var lastTimestamp: Long? by rememberSaveable { mutableStateOf(null) }
    var isReady: Boolean by rememberSaveable { mutableStateOf(false) }
    var dragTime: Double? by rememberSaveable { mutableStateOf(null) }
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
                        Log.d("BatchInsert", "Inserting ${dataToInsert.size} data points at ${System.currentTimeMillis()}")
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

                                // Do this on the MAIN thread
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
                    endSessionPostProcess(sessionID,database)
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
                text = "Drag Time Calculator",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected.value) "Connected to ESP" else "Not Connected",
                color = if (isConnected.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))


            // Show a loading state while vehicles are being fetched
            if (loadingState) {
                Text(text = "Loading vehicles...") // Show loading message
            } else {
                if(!isSessionActive) {
                    // DropdownMenuFieldMulti will be displayed when vehicles are available
                    if (vehicles.isNotEmpty()) {
                        DropdownMenuFieldMulti("Select car", vehicles, selectedVehicle) { selectedVehicleId = it.toInt() }
                    } else {
                        Text(text = "No vehicles available") // Show a message if no vehicles are found
                    }
                }
            }

            if(isSessionActive)
            {
                Text( text = if(isReady) "Go go GO" else "Wait")
            }


            Spacer(modifier = Modifier.height(16.dp))

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
                        .height((screenHeight / 2).dp)
                        .border(16.dp, Color(0,0,255,0))
                )
            }


            //Overengineered bottom borders by: CHATGPT
            //Everything for design I guess...
            //Don't use AI kids.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)) // Clip only bottom corners
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


                    Row(modifier = Modifier.fillMaxWidth() ) {
                        Text(
                            text = "Acceleration (0-100): ${dragTime ?: -1} sec",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight(700))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth() ) {
                        Text(
                            text = "Quarter mile time: ${0 ?: -1} sec",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight(700))
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
                    activeColor = androidx.compose.ui.graphics.Color.Black,
                    modifier = Modifier.height(100.dp)
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
                        coroutineScope.launch {
                            Log.d("CarID", selectedVehicleId.toString())
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

                            if(sessionID.toInt() != -1)
                            {
                                stopBatchInsert()
                                   endSession(database)
                            }
                            Log.d("isItFalse?" , isSessionActive.toString())
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


    suspend fun startSession(database: ESPDatabase, selectedVehicleId: Long): Long {

        if(selectedVehicleId == null)
        {
            return -1
        }

        val sessionManager = SessionManager.getInstance(database)
        var id: Long

        // Use suspendCoroutine to suspend until the session id is retrieved.
        withContext(Dispatchers.IO) {
            sessionManager.startSession("DragSession", "Drag data session", vehicleId = selectedVehicleId )
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
        val dragTimeCalculation = DragTimeCalculation(sessionId, database)
        return dragTimeCalculation.timeFromZeroToHundred()
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


