package com.example.trackpro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Button
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


class TimeAttackScreen
{


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
fun TimeAttackScreenView() {
    val track = remember { mutableStateOf("Select Track") }
    val car = remember { mutableStateOf("Select Car") }
    val currentLapTime = remember { mutableStateOf("00:00.000") }
    val delta = remember { mutableStateOf(0.0) } // Positive = slower, Negative = faster
    val bestLap = remember { mutableStateOf("00:00.000") }
    val lastLap = remember { mutableStateOf("00:00.000") }

    val backgroundColor = if (delta.value < 0) Color.Green else Color.Red

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { /* Track selection logic */ }) {
                    Text(text = track.value)
                }
                Button(onClick = { /* Car selection logic */ }) {
                    Text(text = car.value)
                }
            }

            Text(
                text = "Best Lap: ${bestLap.value} | Last Lap: ${lastLap.value} | Δ ${delta.value}s",
                color = Color.White,
                fontSize = 14.sp
            )

            Box(
                modifier = Modifier
                    .background(backgroundColor, shape = RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = currentLapTime.value,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}


@Preview(showBackground = true,
    device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape")
@Composable
fun TimeAttackScreenPreview() {
    TimeAttackScreenView()
}

