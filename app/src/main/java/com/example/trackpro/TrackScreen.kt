package com.example.trackpro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

data class LatLonOffset(val lat: Double, val lon: Double)
@Composable
fun TrackScreen() {
    // Suzuka Circuit GPS points
    val gpsPoints = listOf(
        LatLonOffset(52.06038, -1.02483), // Start/Finish line
        LatLonOffset(52.06106, -1.02571), // Turn 1 (Copse)
        LatLonOffset(52.06174, -1.02658), // Turn 2 (Maggotts)
        LatLonOffset(52.06242, -1.02745), // Turn 3 (Becketts)
        LatLonOffset(52.06298, -1.02832), // Turn 4 (Chapel)
        LatLonOffset(52.06344, -1.02919), // Turn 5 (The Loop)
        LatLonOffset(52.06390, -1.03006), // Turn 6 (Abbey)
        LatLonOffset(52.06436, -1.03093), // Turn 7 (Farm Curve)
        LatLonOffset(52.06482, -1.03180), // Turn 8 (Village)
        LatLonOffset(52.06528, -1.03267), // Turn 9 (Aintree)
        LatLonOffset(52.06574, -1.03354), // Turn 10 (Luffield)
        LatLonOffset(52.06620, -1.03441), // Turn 11 (Maggots)
        LatLonOffset(52.06666, -1.03528), // Turn 12 (Becketts)
        LatLonOffset(52.06712, -1.03615), // Turn 13 (Chapel)
        LatLonOffset(52.06758, -1.03702), // Turn 14 (The Loop)
        LatLonOffset(52.06804, -1.03789), // Turn 15 (Abbey)
        LatLonOffset(52.06850, -1.03876), // Turn 16 (Farm Curve)
        LatLonOffset(52.06896, -1.03963), // Turn 17 (Village)
        LatLonOffset(52.06942, -1.04050), // Turn 18 (Aintree)
        LatLonOffset(52.06988, -1.04137), // Turn 19 (Luffield)
        LatLonOffset(52.07034, -1.04224), // Turn 20 (Stowe)
        LatLonOffset(52.07080, -1.04311), // Turn 21 (Vale)
        LatLonOffset(52.07126, -1.04398), // Turn 22 (Club)
        LatLonOffset(52.07172, -1.04485), // Turn 23 (Abbey)
        LatLonOffset(52.07218, -1.04572), // Turn 24 (Woodcote)
        LatLonOffset(52.07264, -1.04659), // Turn 25 (Copse)
        LatLonOffset(52.06038, -1.02483)  // Back to Start/Finish
    )

    // Interpolate to generate higher resolution points
    val highResGpsPoints = interpolatePoints(gpsPoints, 50) // 50 steps between each point
    TrackView(gpsPoints = highResGpsPoints)
}

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
    // State for the offset of the track
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize() // Fill the entire screen
            .padding(16.dp), // Padding around the canvas
        contentAlignment = Alignment.Center // Center the canvas within the Box
    ) {
        // Canvas with a border
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
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Detect drag gestures and update the offsets
                        detectDragGestures { _, dragAmount ->
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
            ) {
                // Call drawTrack from the Canvas's DrawScope context, passing the current offsets
                drawTrack(gpsPoints, offsetX, offsetY)
            }
        }
    }
}

fun DrawScope.drawTrack(gpsPoints: List<LatLonOffset>, offsetX: Float, offsetY: Float) {
    if (gpsPoints.isNotEmpty()) {
        // Calculate bounds
        val minLat = gpsPoints.minOf { it.lat }
        val maxLat = gpsPoints.maxOf { it.lat }
        val minLon = gpsPoints.minOf { it.lon }
        val maxLon = gpsPoints.maxOf { it.lon }

        // Calculate aspect ratio
        val trackWidth = maxLon - minLon
        val trackHeight = maxLat - minLat
        val trackAspectRatio = trackWidth / trackHeight

        // Determine scaling
        val screenWidth = size.width
        val screenHeight = size.height
        val scaleFactor = if (trackAspectRatio > 1) {
            screenWidth / trackWidth
        } else {
            screenHeight / trackHeight
        }

        // Function to convert lat/lon to screen coordinates
        fun latLonToScreen(lat: Double, lon: Double): Offset {
            val x = (lon - minLon) * scaleFactor + offsetX
            val y = (maxLat - lat) * scaleFactor + offsetY
            return Offset(x.toFloat(), y.toFloat())
        }

        // Create a path for the track
        val path = Path().apply {
            val firstPoint = latLonToScreen(gpsPoints[0].lat, gpsPoints[0].lon)
            moveTo(firstPoint.x, firstPoint.y)

            gpsPoints.forEach {
                val screenPoint = latLonToScreen(it.lat, it.lon)
                lineTo(screenPoint.x, screenPoint.y)
            }
        }

        // Draw the track path
        drawPath(
            path = path,
            color = Color.Red,
            style = Stroke(width = 5.dp.toPx()) // Track line stroke
        )
    }
}

