package com.example.trackpro.ExtrasForUI

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.trackpro.DataClasses.SmoothedGPSData
import com.example.trackpro.DataClasses.TrackCoordinatesData

data class LatLonOffset(val lat: Double, val lon: Double)


@JvmName("LatLonTrackDrawer")
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
        val trackWidth = (maxLon - minLon).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero width
        val trackHeight = (maxLat - minLat).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero height

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

            //close()  // Connect back to start if needed
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

@JvmName("LatLonTrackDrawerWithDriver")
fun DrawScope.drawTrack(
    gpsPoints: List<LatLonOffset>,
    margin: Float,
    driverPosition: LatLonOffset
) {
    if (gpsPoints.isNotEmpty()) {
        // Calculate bounds
        val minLat = gpsPoints.minOf { it.lat }
        val maxLat = gpsPoints.maxOf { it.lat }
        val minLon = gpsPoints.minOf { it.lon }
        val maxLon = gpsPoints.maxOf { it.lon }

        // Calculate aspect ratio
        val trackWidth = (maxLon - minLon).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero width
        val trackHeight = (maxLat - minLat).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero height

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

            //close()  // Connect back to start if needed
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

        val currentPos = latLonToScreen(
            driverPosition.lat,
            driverPosition.lon
        )
        drawCircle(Color.Blue, 10f, currentPos)
    }
}


@JvmName("TrackCoordinatesTrackDrawer")
fun DrawScope.drawTrack(
    gpsPoints: List<TrackCoordinatesData>,
    margin: Float,
    animationProgress: Float
) {
    if (gpsPoints.isEmpty()) return

    val minLat = gpsPoints.minOf { it.latitude }
    val maxLat = gpsPoints.maxOf { it.latitude }
    val minLon = gpsPoints.minOf { it.longitude }
    val maxLon = gpsPoints.maxOf { it.longitude }

    val trackWidth = (maxLon - minLon).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero width
    val trackHeight = (maxLat - minLat).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero height

    val trackAspectRatio = trackWidth / trackHeight

    val screenWidth = size.width - 2 * margin
    val screenHeight = size.height - 2 * margin

    val safeTrackWidth = if (trackWidth != 0.0) trackWidth else 1.0
    val safeTrackHeight = if (trackHeight != 0.0) trackHeight else 1.0

    val scaleFactor = if (trackAspectRatio > 1) {
        screenWidth / safeTrackWidth
    } else {
        screenHeight / safeTrackHeight
    }


    val horizontalOffset = (screenWidth - (trackWidth * scaleFactor)) / 2
    val verticalOffset = (screenHeight - (trackHeight * scaleFactor)) / 2

    fun latLonToScreen(lat: Double, lon: Double): Offset {
        val x = (lon - minLon) * scaleFactor + margin + horizontalOffset
        val y = (maxLat - lat) * scaleFactor + margin + verticalOffset

        return if (x.isNaN() || y.isNaN()) {
            Offset.Zero  // Default to (0,0) instead of crashing
        } else {
            Offset(x.toFloat(), y.toFloat())
        }
    }



    val path = Path().apply {
        val firstPoint = latLonToScreen(gpsPoints[0].latitude, gpsPoints[0].longitude)
        if (!firstPoint.x.isNaN() && !firstPoint.y.isNaN()) {
            moveTo(firstPoint.x, firstPoint.y)
        }

        gpsPoints.forEach { point ->
            val screenPoint = latLonToScreen(point.latitude, point.longitude)
            if (!screenPoint.x.isNaN() && !screenPoint.y.isNaN()) {
                lineTo(screenPoint.x, screenPoint.y)
            }
        }
    }

    drawPath(path, Color.Red, style = Stroke(width = 5f))

    val startPoint = latLonToScreen(gpsPoints[0].latitude, gpsPoints[0].longitude)
    drawLine(
        color = Color.Green,
        start = Offset(startPoint.x - 20f, startPoint.y),
        end = Offset(startPoint.x + 20f, startPoint.y),
        strokeWidth = 4f
    )

    val totalPoints = gpsPoints.size
    if (totalPoints > 0) {
        val currentIndex = (animationProgress * totalPoints).toInt() % totalPoints
        val currentPos = latLonToScreen(
            gpsPoints[currentIndex].latitude,
            gpsPoints[currentIndex].longitude
        )

        drawCircle(Color.Blue, 10f, currentPos)
    }
}


//This uses a driver position var to display the user/driver on the track
@JvmName("TrackCoordinatesTrackDrawerWithDriver")
fun DrawScope.drawTrack(
    gpsPoints: List<TrackCoordinatesData>,
    margin: Float,
    driverPosition: LatLonOffset
) {
    if (gpsPoints.isEmpty()) return

    val minLat = gpsPoints.minOf { it.latitude }
    val maxLat = gpsPoints.maxOf { it.latitude }
    val minLon = gpsPoints.minOf { it.longitude }
    val maxLon = gpsPoints.maxOf { it.longitude }

    val trackWidth = (maxLon - minLon).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero width
    val trackHeight = (maxLat - minLat).takeIf { it != 0.0 } ?: 0.0001 // Prevent zero height

    val trackAspectRatio = trackWidth / trackHeight

    val screenWidth = size.width - 2 * margin
    val screenHeight = size.height - 2 * margin

    val safeTrackWidth = if (trackWidth != 0.0) trackWidth else 1.0
    val safeTrackHeight = if (trackHeight != 0.0) trackHeight else 1.0

    val scaleFactor = if (trackAspectRatio > 1) {
        screenWidth / safeTrackWidth
    } else {
        screenHeight / safeTrackHeight
    }


    val horizontalOffset = (screenWidth - (trackWidth * scaleFactor)) / 2
    val verticalOffset = (screenHeight - (trackHeight * scaleFactor)) / 2

    fun latLonToScreen(lat: Double, lon: Double): Offset {
        val x = (lon - minLon) * scaleFactor + margin + horizontalOffset
        val y = (maxLat - lat) * scaleFactor + margin + verticalOffset

        return if (x.isNaN() || y.isNaN()) {
            Offset.Zero  // Default to (0,0) instead of crashing
        } else {
            Offset(x.toFloat(), y.toFloat())
        }
    }



    val path = Path().apply {
        val firstPoint = latLonToScreen(gpsPoints[0].latitude, gpsPoints[0].longitude)
        if (!firstPoint.x.isNaN() && !firstPoint.y.isNaN()) {
            moveTo(firstPoint.x, firstPoint.y)
        }

        gpsPoints.forEach { point ->
            val screenPoint = latLonToScreen(point.latitude, point.longitude)
            if (!screenPoint.x.isNaN() && !screenPoint.y.isNaN()) {
                lineTo(screenPoint.x, screenPoint.y)
            }
        }
    }

    drawPath(path, Color.Red, style = Stroke(width = 5f))

    val startPoint = latLonToScreen(gpsPoints[0].latitude, gpsPoints[0].longitude)
    drawLine(
        color = Color.Green,
        start = Offset(startPoint.x - 20f, startPoint.y),
        end = Offset(startPoint.x + 20f, startPoint.y),
        strokeWidth = 4f
    )

    val totalPoints = gpsPoints.size
    if (totalPoints > 0) {
        val currentPos = latLonToScreen(
            driverPosition.lat,
            driverPosition.lon
        )

        drawCircle(Color.Blue, 10f, currentPos)
    }
}




fun convertToLatLonOffsetList(data: List<SmoothedGPSData>): List<LatLonOffset> {
    return data.map { LatLonOffset(lat = it.latitude, lon = it.longitude) }
}

