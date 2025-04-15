package com.example.trackpro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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


class TimeAttackScreen {


}


//Select track
//Select car
//  |
//  |
//  |
// \ /
// Lap timing.
//Show: DELTA, Best lap, last lap, in SMALL
//SHOW BIG: Current lap time, red/green background based off of DELTA or

//MANDATORY: Landscape mode!!!

@Composable
fun TimeAttackScreenView(
    database: ESPDatabase,
    onBack: () -> Unit,
) {

    val track = remember { mutableStateOf("Select Track") }
    val trackId = 1
    //val trackCoordinates = database.trackCoordinatesDao().getCoordinatesOfTrack(trackId)
    val currentLapTime = remember { mutableStateOf("0'00.00''") }
    val delta = remember { mutableDoubleStateOf(0.0) } // Positive = slower, Negative = faster
    val bestLap = remember { mutableStateOf("00'00.00''") }
    val lastLap = remember { mutableStateOf("00'00,00''") }


    val deltaColor = if (delta.doubleValue < 0) Color.Green else Color.Red

    val viewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))

    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    val selectedVehicle by rememberSaveable { mutableStateOf("") }
    var selectedVehicleId by rememberSaveable { mutableIntStateOf(-1) }


    // var finishLine = finishLine(track)



    //User selects vehicle and starts the session
    //Check if the user has passed the finish line
    // Add laps, check if best lap, update delta
    //very CPU
    LaunchedEffect(Unit) {



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
                        Text(text = track.value)
                    }

                    if (vehicles.isNotEmpty()) {
                        DropdownMenuFieldMulti(
                            label = "Select car",
                            options = vehicles,
                            selectedOption = selectedVehicle
                        ) { selectedVehicleId = it.toInt() }
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

