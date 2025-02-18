package com.example.trackpro
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.CalculationClasses.DragTimeCalculation
import com.example.trackpro.CalculationClasses.PostProcessing
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.convertToLatLonOffsetList
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ui.theme.TrackProTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Time
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun GraphScreen(onBack: () -> Unit, sessionId: Long) {
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    var coordinates by remember { mutableStateOf(emptyList<SmoothedGPSData>()) }
    var coordinatesSimplified by remember { mutableStateOf(emptyList<LatLonOffset>()) }
    var dragTime by remember { mutableStateOf(-1.0) }
    val dragTimeClass = remember { DragTimeCalculation(sessionId, database) }
    val scope = rememberCoroutineScope()
    var totalDist by remember { mutableStateOf(-1.0) }
    val margin = 16f

    LaunchedEffect(sessionId) {
        scope.launch(Dispatchers.IO) {
            val data = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionId)
            val simplifiedData = convertToLatLonOffsetList(data)
            val dragTimeValue = dragTimeClass.timeFromZeroToHundred()
            val totalDistValue = dragTimeClass.totalDistance(simplifiedData)

            withContext(Dispatchers.Main) {
                coordinates = data
                coordinatesSimplified = simplifiedData
                dragTime = dragTimeValue
                totalDist = totalDistValue
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Session Overview",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)

        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Time of Creation:")
                if (coordinates.isNotEmpty()) {
                    val totalTime = coordinates.last().timestamp - coordinates.first().timestamp
                    Text("Total Time: ${formatTime(totalTime)}")
                } else {
                    Text("No data available")
                }
                Text("Total Distance: $totalDist km")
                Text("0-100 Time: ${if (dragTime > 0) "$dragTime sec" else "Calculating..."}")
                Text("1/4 Mile:")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                .background(Color.LightGray)
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTrack(coordinatesSimplified, margin, 1f)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    TrackProTheme {
        GraphScreen(onBack = {},1)
    }
}

fun formatTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    val millis = milliseconds % 1000

    return String.format("%02d:%02d.%02d", minutes, seconds, millis)
}