package com.example.trackpro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.trackpro.DataClasses.RawGPSData
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.toDataClass


@Composable
fun TrackScreen(onBack: () -> Unit, trackId: Long) {
    // Pannonia ring
    val gpsPoints = listOf(
        LatLonOffset(47.305300, 17.048138),
        LatLonOffset(47.302270, 17.049691),
        LatLonOffset(47.301004, 17.048439),
        LatLonOffset(47.300890, 17.048053),
        LatLonOffset(47.301029, 17.047764),
        LatLonOffset(47.302883, 17.046380),
        LatLonOffset(47.302997, 17.046187),
        LatLonOffset(47.303095, 17.045789),
        LatLonOffset(47.303422, 17.043212),
        LatLonOffset(47.303258, 17.042779),
        LatLonOffset(47.302997, 17.042706),
        LatLonOffset(47.300882, 17.045801),
        LatLonOffset(47.300572, 17.045994),
        LatLonOffset(47.300237, 17.045910),
        LatLonOffset(47.299959, 17.045356),
        LatLonOffset(47.299992, 17.044838),
        LatLonOffset(47.301160, 17.040972),
        LatLonOffset(47.301486, 17.040623),
        LatLonOffset(47.301846, 17.040563),
        LatLonOffset(47.302744, 17.041346),
        LatLonOffset(47.302981, 17.041382),
        LatLonOffset(47.303307, 17.041213),
        LatLonOffset(47.303724, 17.040515),
        LatLonOffset(47.303887, 17.039732),
        LatLonOffset(47.303691, 17.037420),
        LatLonOffset(47.303691, 17.037046),
        LatLonOffset(47.304508, 17.035324),
        LatLonOffset(47.304696, 17.035192),
        LatLonOffset(47.304851, 17.035276),
        LatLonOffset(47.305349, 17.036324),
        LatLonOffset(47.305373, 17.036757),
        LatLonOffset(47.305210, 17.037335),
        LatLonOffset(47.304843, 17.037733),
        LatLonOffset(47.304638, 17.038239),
        LatLonOffset(47.304565, 17.038624),
        LatLonOffset(47.304508, 17.043248),
        LatLonOffset(47.304451, 17.043513),
        LatLonOffset(47.304075, 17.044344),
        LatLonOffset(47.304026, 17.044549),
        LatLonOffset(47.304059, 17.044838),
        LatLonOffset(47.304181, 17.045151),
        LatLonOffset(47.304393, 17.045187),
        LatLonOffset(47.304557, 17.045139),
        LatLonOffset(47.305594, 17.044380),
        LatLonOffset(47.306631, 17.042670),
        LatLonOffset(47.306778, 17.042550),
        LatLonOffset(47.306958, 17.042550),
        LatLonOffset(47.307137, 17.042622),
        LatLonOffset(47.308574, 17.044501),
        LatLonOffset(47.308648, 17.044730),
        LatLonOffset(47.308721, 17.045585),
        LatLonOffset(47.308615, 17.046054),
        LatLonOffset(47.308436, 17.046392),
        LatLonOffset(47.306092, 17.047740),
        LatLonOffset(47.304973, 17.048282)
    )


    var showDialog by remember { mutableStateOf(false) }


    // Interpolate to generate higher resolution points
    val highResGpsPoints = interpolatePoints(gpsPoints, 50) // 50 steps between each point
    TrackView(gpsPoints = highResGpsPoints)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { showDialog = true }) {
            Text("Open Dialog")
        }

        if (showDialog) {
            TrackCreationDialog(
                onDismiss = { showDialog = false },
                onConfirm = { trackName, lastName ->
                    println("First Name: $trackName, Last Name: $lastName")
                    showDialog = false
                }
            )
        }
    }

}

//************************************//
fun interpolatePoints(points: List<LatLonOffset>, steps: Int): List<LatLonOffset> {
    val interpolatedPoints = mutableListOf<LatLonOffset>()
    for (i in 0 until points.size - 1) {
        val start = points[i]
        val end = points[i + 1]
        for (j in 0..steps) {
            val lat = start.lat + (end.lat - start.lat) * (j / steps.toDouble())
            val lon = start.lon + (end.lon - start.lon) * (j / steps.toDouble())
            interpolatedPoints.add(LatLonOffset(lat, lon))
        }
    }
    return interpolatedPoints
}

@Composable
fun TrackView(gpsPoints: List<LatLonOffset>) {
    // State for the animation progress
    // var animationProgress by remember { mutableFloatStateOf(0f) }
    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val isConnected = remember { mutableStateOf(false) }

    val context = LocalContext.current  // Get the Context in Compose
    val (ip, port) = remember { JsonReader.loadConfig(context) } // Load once & remember it

    LaunchedEffect(Unit) {
        //Just for testing purposes.
        //Moves a "Car dot" around the track
        /*while (true) {
            animationProgress += 0.001f // Adjust speed here
            if (animationProgress > 1f) animationProgress = 0f
            delay(16) // ~60 FPS
        }*/

        espTcpClient = ESPTcpClient(
            serverAddress = ip,
            port = port,
            onMessageReceived = { data ->
                gpsData.value = data.toDataClass()

            },
            onConnectionStatusChanged = { connected ->
                isConnected.value = connected
            }
        )


    }

    val margin = 16f // Margin for the track within the box in pixels

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center // Center the canvas within the Box
    ) {
        // Canvas with a border
        Box(
            modifier = Modifier
                .border(
                    width = 2.dp,
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp)
                )
                .aspectRatio(1f) // Make the canvas square
                .background(Color.LightGray)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw the track, start line, and animated dot
                val driverPos: LatLonOffset? = gpsData.value?.let { LatLonOffset(it.latitude,
                    gpsData.value!!.longitude) }

                if (driverPos != null) {
                    drawTrack(gpsPoints, margin,driverPos )
                }
                else
                {
                    drawTrack(gpsPoints,margin,1f)
                }
            }
        }
    }
}


@Composable
fun TrackCreationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var trackName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = "Enter Details") },
        text = {
            Column {
                TextField(
                    value = trackName,
                    onValueChange = { trackName = it },
                    label = { Text("Name of the track") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Total length (if known)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(trackName, lastName) }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}




fun CreateTrack()
{


}


