package com.example.trackpro

import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
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
    val currentLapTime = remember { mutableStateOf("0'00.00''") }
    val delta = remember { mutableStateOf(0.0) } // Positive = slower, Negative = faster
    val bestLap = remember { mutableStateOf("00'00.00''") }
    val lastLap = remember { mutableStateOf("00'00,00''") }


    val deltaColor = if (delta.value < 0) Color.Green else Color.Red

    val viewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))

    val vehicles by viewModel.vehicles.collectAsState(initial = emptyList())
    val selectedVehicle by rememberSaveable { mutableStateOf("") }
    var selectedVehicleId by rememberSaveable { mutableIntStateOf(-1) }


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
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                //verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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


                // CURRENT LAP TIME
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = currentLapTime.value,
                        fontSize = 58.sp,
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
                        text = delta.value.toString(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                //BASIC TEXT
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                ){
                    Text(
                        text = "REF LAP:",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.W500,
                        color = Color.DarkGray
                    )
                }

                // BEST LAP
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                ){
                    Text(
                        text = bestLap.value,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.W500,
                        color = Color.DarkGray
                    )
                }
            }
        }





        // Bottom Box - Lap info
        Box(
            modifier = Modifier
                .weight(1f) // Half of the screen
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
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
                        text = "Best Lap: ${bestLap.value} | Last Lap: ${lastLap.value} | Δ ${delta.value}s",
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

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

