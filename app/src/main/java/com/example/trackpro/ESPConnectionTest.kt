package com.example.trackpro

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sin

class ESPConnectionTest : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ESPConnectionTestScreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
@Composable
fun ESPConnectionTestScreen() {
    val isConnected = remember { mutableStateOf(false) }
    val gpsData = remember { mutableStateOf<RawGPSData?>(null) }
    val rawJson = remember { mutableStateOf("") }

    val context = LocalContext.current  // Get the Context in Compose
    val (ip, port) = remember { JsonReader.loadConfig(context) } // Load once & remember it

    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }

    // Channel to handle incoming GPS data efficiently
    val gpsChannel = remember { Channel<RawGPSData>(capacity = Channel.CONFLATED) }

    // Launch effect for connection setup
    LaunchedEffect(Unit) {
        espTcpClient = ESPTcpClient(
            serverAddress = ip,
            port = port,
            onMessageReceived = { data ->
                gpsChannel.trySend(data) // Send to channel instead of direct UI update
            },
            onConnectionStatusChanged = { connected ->
                isConnected.value = connected
            }
        )
        espTcpClient?.connect()
    }

    // Process incoming GPS data without UI lag
    LaunchedEffect(Unit) {
        for (data in gpsChannel) {
            gpsData.value = data
            rawJson.value = data.toString()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                espTcpClient?.disconnect()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Connection Status (ESP WIFI): ${if (isConnected.value) "Connected" else "Disconnected"}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected.value) Color.Green else Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))


        // Parsed GPS Data Display
        gpsData.value?.let { } ?: Text("Waiting for GPS data...")

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0, 142, 215, 255))
                .padding(8.dp)
        )
        {
            Box(
                modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF82F3CA), // Soft pastel green
                                Color(0xFF8FD5FA)  // Soft pastel blue
                            ),
                            start = Offset(0f, 0f), // Top-left
                            end = Offset(1000f, 1000f) // Bottom-right (spread wider for smoothness)
                        )
                    )
                .padding(16.dp))
            {
                Column {

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Last GPS data",
                                style = TextStyle(
                                    fontWeight = FontWeight.W700,
                                    fontSize = 18.sp
                                )

                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "Latitude: ${gpsData.value?.latitude ?: "0.000000"}",
                                style = TextStyle()
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Longitude: ${gpsData.value?.longitude?:"0.000000"}",
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "Altitude: ${gpsData.value?.altitude ?: "0.000000"}",
                                style = TextStyle()
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Satellites: ${gpsData.value?.satellites?:"0"}",
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "Speed: ${gpsData.value?.speed ?: "0.00"} km/h",
                                style = TextStyle()
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Timestamp: ${gpsData.value?.timestamp?:"12:00.000"}",
                            )
                        }
                    }
                }
            }
        }


        Spacer(modifier = Modifier.height(16.dp))


        // Raw JSON Display
        Text("Raw JSON Data:", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = rawJson.value.ifEmpty { "Waiting for data..." },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .background(Color.LightGray.copy(alpha = 0.2f))
                .padding(8.dp)
        )



        // SpeedometerView(gpsData.value?.speed ?: 0f)
    }
}

@Composable
fun GpsDataDisplay(data: RawGPSData) {
    Column {
        Text("Parsed GPS Data:", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Latitude: ${data.latitude}")
        Text("Longitude: ${data.longitude}")
        Text("Altitude: ${data.altitude ?: "N/A"}")
        Text("Speed: ${data.speed ?: "N/A"}")
        Text("Satellites: ${data.satellites ?: "N/A"}")
        Text("Timestamp: ${data.timestamp}")
    }
}

@Composable
fun SpeedometerView(speed: Float) {
    Canvas(modifier = Modifier.size(200.dp)) {
        drawArc(
            color = Color.Green,
            startAngle = -90f,
            sweepAngle = (speed / 100f) * 180f, // Adjust scale based on expected max speed
            useCenter = false,
            style = Stroke(8.dp.toPx())
        )
    }
}



// BMW wannabe gauge
@Composable
fun Speedometer(speed: Float) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Canvas(modifier = Modifier.size(300.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 1.2f  // Moves the center down to remove the lower part
        val radius = size.minDimension / 2.2f

        // Draw half-circle background
        drawArc(
            color = Color.Black,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true, // Creates a half-circle shape
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2, radius * 2)
        )

        // Text Paint for numbers
        val textPaint = androidx.compose.ui.graphics.Paint().asFrameworkPaint().apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER // Fix for Unresolved reference: Align
        }

        // Draw speed markings (every 20 km/h)
        for (i in 0..13) { // 0 - 260 km/h (every 20 km/h)
            val angle = Math.toRadians((180 + (i * 180f / 13)).toDouble()) // 0-260 mapped to 180°
            val tickStart = radius * 0.75f
            val tickEnd = radius * 0.9f

            val startX = centerX + cos(angle).toFloat() * tickStart
            val startY = centerY + sin(angle).toFloat() * tickStart
            val endX = centerX + cos(angle).toFloat() * tickEnd
            val endY = centerY + sin(angle).toFloat() * tickEnd

            drawLine(
                color = Color.White,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 4f
            )

            // Draw speed labels (20, 40, ..., 260)
            val text = (i * 20).toString()
            val textX = centerX + cos(angle).toFloat() * (tickStart - 20)
            val textY = centerY + sin(angle).toFloat() * (tickStart - 20) + 15 // Center text properly
            drawContext.canvas.nativeCanvas.drawText(text, textX, textY, textPaint)
        }

        // Draw needle
        val needleAngle = Math.toRadians((180 + (animatedSpeed / 260f) * 180f).toDouble())
        val needleLength = radius * 0.65f
        val needleX = centerX + cos(needleAngle).toFloat() * needleLength
        val needleY = centerY + sin(needleAngle).toFloat() * needleLength

        drawLine(
            color = Color.Red,
            start = Offset(centerX, centerY),
            end = Offset(needleX, needleY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ESPConnectionTestPreview() {
    ESPConnectionTestScreen()
}
