package com.example.trackpro

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.trackpro.CalculationClasses.rotateTrackPoints
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.ExtrasForUI.DropdownMenuFieldMulti
import com.example.trackpro.ManagerClasses.*
import com.example.trackpro.ViewModels.VehicleViewModel
import com.example.trackpro.ViewModels.VehicleViewModelFactory
import kotlinx.coroutines.flow.MutableSharedFlow


class TimeAttackScreen {


}


//MANDATORY: Landscape mode!!!

@Composable
fun TimeAttackScreenView(
    database: ESPDatabase,
    onBack: () -> Unit,
) {

    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    val context = LocalContext.current  // Get the Context in Compose
    val (ip, port) = rememberSaveable { JsonReader.loadConfig(context) } // Load once & remember it
    val isConnected = rememberSaveable { mutableStateOf(false) }

    val gpsDataFlow = remember { MutableSharedFlow<RawGPSData>(extraBufferCapacity = 10) }

    //TRACK
    val track :List<TrackCoordinatesData> = emptyList()
    val trackId = 1
    //val trackCoordinates = database.trackCoordinatesDao().getCoordinatesOfTrack(trackId)

    //LAP DATA
    val currentLapTime = remember { mutableStateOf("00:00.00''") }
    val delta = remember { mutableDoubleStateOf(0.0) } // Positive = slower, Negative = faster
    val bestLap = remember { mutableStateOf("00'00.00''") }
    val lastLap = remember { mutableStateOf("00'00,00''") }
    val lastCrossTime = remember { mutableLongStateOf(0L) }

    val deltaColor by remember {
        derivedStateOf {
            if (delta.doubleValue < 0) Color.Green else Color.Red
        }
    }

    val viewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))

    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    var selectedVehicle by rememberSaveable { mutableStateOf("") }
    var selectedVehicleId by rememberSaveable { mutableIntStateOf(-1) }
    val lapStartTime = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    val finishLine by remember(track) { mutableStateOf(finishLine(track)) }

    // var finishLine = finishLine(track)

    //User selects vehicle and starts the session
    //Check if the user has passed the finish line
    // Add laps, check if best lap, update delta
    //very CPU
    LaunchedEffect(Unit) {

        viewModel.fetchVehicles()

        try {
            espTcpClient = ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data ->
                    gpsDataFlow.tryEmit(data)

                    //5 second debounce
                    if (SystemClock.elapsedRealtime() - lastCrossTime.longValue > 5000) {
                        if (crossedFinishLine(data, finishLine)) {
                            lastCrossTime.longValue = SystemClock.elapsedRealtime()
                            val finishedTime = SystemClock.elapsedRealtime() - lapStartTime.longValue
                            val lastTimeStr = currentLapTime.value
                            lastLap.value = lastTimeStr

                            val finishedSeconds = finishedTime / 1000.0

                            if (bestLap.value == "00:00.00" || finishedSeconds < parseLapTimeToSeconds(
                                    bestLap.value
                                )
                            ) {
                                bestLap.value = lastTimeStr
                            }

                            val bestSeconds = parseLapTimeToSeconds(bestLap.value)
                            delta.doubleValue = finishedSeconds - bestSeconds

                            lapStartTime.longValue = SystemClock.elapsedRealtime()
                        }
                    }
                    val elapsedMillis = SystemClock.elapsedRealtime() - lapStartTime.longValue
                    val minutes = (elapsedMillis / 60000).toInt()
                    val seconds = ((elapsedMillis % 60000) / 1000).toInt()
                    val centis = ((elapsedMillis % 1000) / 10).toInt()
                    currentLapTime.value = String.format("%02d:%02d.%02d", minutes, seconds, centis)
                },
                onConnectionStatusChanged = { connected ->
                    isConnected.value = connected
                }
            )
            espTcpClient?.connect()
        } catch (e: Exception) {
            Log.e("ESPConnection", "TCP setup failed", e)
        }
    }


    DisposableEffect(Unit) {
        onDispose {
            espTcpClient?.disconnect()
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Top Box - Track selection and current lap time
        Box(
            modifier = Modifier
                .weight(1f) // Half of the screen
                .fillMaxWidth(),
            //.background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                //verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(25, 35, 255)),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { /* Track selection logic */ }) {
                        Text(text = if (track.isEmpty()) "No Track Loaded" else "Track #$trackId")
                    }

                    if (vehicles.isNotEmpty()) {
                        DropdownMenuFieldMulti(
                            label = "Select car",
                            options = vehicles,
                            selectedOption = selectedVehicle
                        ) { selectedId ->
                            selectedVehicleId = selectedId.toInt()
                            selectedVehicle = vehicles.find { it.vehicleId == selectedId }?.manufacturerAndModel ?: ""
                        }

                    } else {
                        Text(text = "No vehicles available")
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    //verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // CURRENT LAP TIME
                    Row(
                        modifier = Modifier
                            .padding(top = 50.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = currentLapTime.value,
                            color = deltaColor,
                            fontSize = 68.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    // + -
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = delta.doubleValue.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    //BASIC TEXT
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = "REF LAP:",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.W500,
                            color = Color.Gray
                        )
                    }

                    // BEST LAP
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = bestLap.value,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.W500,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Bottom Box - Lap info
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Δ ${delta.doubleValue}s",
                        fontSize = 68.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Green,
                        fontWeight = FontWeight.W700
                    )

                    Spacer(modifier = Modifier.height(16.dp))


                }
            }
        }
    }
}

//
fun finishLine(track: List<TrackCoordinatesData>) : List<TrackCoordinatesData>
{
    val finishPoint : TrackCoordinatesData? = track.find { it.isStartPoint == true }
    var finishLine:List<TrackCoordinatesData> = emptyList()

    if(finishPoint == null)
    {
        return finishLine
    }

    val coordinates : List<TrackCoordinatesData> = track.filter { finishPoint.id - 10 <= it.id && it.id <= finishPoint.id + 10 }

    finishLine = rotateTrackPoints(coordinates, finishPoint)

    return finishLine
}

fun crossedFinishLine(data: RawGPSData, finishLine: List<TrackCoordinatesData>): Boolean {
    if (finishLine.size < 2) return false

    val (lat1, lon1) = finishLine[0].let { it.latitude to it.longitude }
    val (lat2, lon2) = finishLine[1].let { it.latitude to it.longitude }

    // Simple bounding box check
    val minLat = minOf(lat1, lat2)
    val maxLat = maxOf(lat1, lat2)
    val minLon = minOf(lon1, lon2)
    val maxLon = maxOf(lon1, lon2)

    return data.latitude in minLat..maxLat && data.longitude in minLon..maxLon
}

fun parseLapTimeToSeconds(lapTime: String): Double {
    val regex = Regex("""(\d{2}):(\d{2})\.(\d{2})""")
    val match = regex.find(lapTime) ?: return 0.0
    val (min, sec, ms) = match.destructured
    return min.toInt() * 60 + sec.toInt() + ms.toInt() / 100.0
}



@Preview(
    showBackground = true,
    //device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape")
)
@Composable
fun TimeAttackScreenPreview() {

    val fakeDatabase = Room.inMemoryDatabaseBuilder(
        LocalContext.current,
        ESPDatabase::class.java
    ).build()

    TimeAttackScreenView(
        database = fakeDatabase,
        onBack = {}
    )
}

