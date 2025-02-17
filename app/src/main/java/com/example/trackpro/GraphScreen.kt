package com.example.trackpro
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.ExtrasForUI.LatLonOffset
import com.example.trackpro.ExtrasForUI.convertToLatLonOffsetList
import com.example.trackpro.ExtrasForUI.drawTrack
import com.example.trackpro.ui.theme.TrackProTheme

@Composable
fun GraphScreen(onBack: () -> Unit, sessionId: Long) {


    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    var coordinates by remember { mutableStateOf<List<SmoothedGPSData>>(emptyList()) }
    var coordinatesSimplified by remember { mutableStateOf<List<LatLonOffset>>(emptyList()) }
    val margin = 16f // Margin for the track within the box in pixels

    val session = database.sessionDataDao().getSessionById(sessionId)

    LaunchedEffect(sessionId) {
        coordinates = database.smoothedDataDao().getSmoothedGPSDataBySession(sessionId)
        coordinatesSimplified = convertToLatLonOffsetList(coordinates)
    }

    Row {
        Box(
            modifier = Modifier
                .border(
                    width = 2.dp,
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp) // Optional rounded corners
                )
                .aspectRatio(1f) // Make the canvas square
                .background(Color.LightGray) // Optional background for the canvas
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw the track, start line, and animated dot
                drawTrack(coordinatesSimplified, margin,1f )//,animationProgress)
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