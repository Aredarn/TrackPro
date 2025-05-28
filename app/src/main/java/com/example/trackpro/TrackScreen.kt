package com.example.trackpro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.trackpro.DataClasses.TrackCoordinatesData
import com.example.trackpro.ExtrasForUI.drawTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Composable
fun TrackScreen(onBack: () -> Unit, trackId: Long) {

    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    TrackView(database, trackId)
}

@Composable
fun TrackView(database: ESPDatabase, trackId: Long) {

    val scope = rememberCoroutineScope()

    // Use a state to hold the list of track parts, so Compose tracks changes
    val trackParts = remember { mutableStateListOf<TrackCoordinatesData>() }

    LaunchedEffect(trackId) {  // Depend on trackId so it fetches data on trackId change
        scope.launch(Dispatchers.IO) {
            // Collect data from the database
            database.trackCoordinatesDao().getCoordinatesOfTrack(trackId).collect { trackparts ->
                // Update trackParts with the new data
                trackParts.clear()
                trackParts.addAll(trackparts)
            }
        }
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
                if (trackParts.isNotEmpty()) {
                    drawTrack(trackParts, margin, 1f)
                }
            }
        }
    }
}
