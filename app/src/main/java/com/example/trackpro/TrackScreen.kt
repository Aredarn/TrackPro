package com.example.trackpro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class LatLonOffset(val lat: Double, val lon: Double)


@Composable
fun TrackScreen() {
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

    // Interpolate to generate higher resolution points
    val highResGpsPoints = interpolatePoints(gpsPoints, 50) // 50 steps between each point
    TrackView(gpsPoints = highResGpsPoints)
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
    var animationProgress by remember { mutableFloatStateOf(0f) }

    // Animate the progress in a loop
    LaunchedEffect(Unit) {
        while (true) {
            animationProgress += 0.001f // Adjust speed here
            if (animationProgress > 1f) animationProgress = 0f
            delay(16) // ~60 FPS
        }
    }

    val margin = 16f // Margin for the track within the box in pixels

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
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw the track, start line, and animated dot
                drawTrack(gpsPoints, margin, animationProgress)
            }
        }
    }
}


fun DrawScope.drawTrack(
    gpsPoints: List<LatLonOffset>,
    margin: Float,
    animationProgress: Float
) {
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

        // Available space after margin
        val screenWidth = size.width - 2 * margin
        val screenHeight = size.height - 2 * margin

        // Calculate scale factor and offsets for centering
        val scaleFactor = if (trackAspectRatio > 1) {
            screenWidth / trackWidth
        } else {
            screenHeight / trackHeight
        }

        // Calculate centering offsets
        val horizontalOffset = (screenWidth - (trackWidth * scaleFactor)) / 2
        val verticalOffset = (screenHeight - (trackHeight * scaleFactor)) / 2

        // Function to convert lat/lon to CENTERED screen coordinates
        fun latLonToScreen(lat: Double, lon: Double): Offset {
            val x = (lon - minLon) * scaleFactor + margin + horizontalOffset
            val y = (maxLat - lat) * scaleFactor + margin + verticalOffset
            return Offset(x.toFloat(), y.toFloat())
        }

        // Create path for track (same as before)
        val path = Path().apply {
            val firstPoint = latLonToScreen(gpsPoints[0].lat, gpsPoints[0].lon)
            moveTo(firstPoint.x, firstPoint.y)  // Correct: uses x/y separately

            gpsPoints.forEach { point ->
                val screenPoint = latLonToScreen(point.lat, point.lon)
                lineTo(screenPoint.x, screenPoint.y)  // Pass x and y separately
            }

            close()  // Connect back to start if needed
        }

        // Draw track
        drawPath(path, Color.Red, style = Stroke(width = 5f))

        val startPoint = latLonToScreen(gpsPoints[0].lat, gpsPoints[0].lon)
        drawLine(
            color = Color.Green,
            start = Offset(startPoint.x - 20f, startPoint.y),  // Explicit X/Y
            end = Offset(startPoint.x + 20f, startPoint.y),    // Explicit X/Y
            strokeWidth = 4f
        )

        val totalPoints = gpsPoints.size
        val currentIndex = (animationProgress * totalPoints).toInt() % totalPoints
        val currentPos = latLonToScreen(
            gpsPoints[currentIndex].lat,
            gpsPoints[currentIndex].lon
        )
        drawCircle(Color.Blue, 10f, currentPos)
    }
}



fun CreateTrack()
{


}


