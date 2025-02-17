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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData  // Make sure to use the correct package
import com.yourpackage.ui.components.SevenSegmentView
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
        super.onDestroy();
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

    // Establish connection when the composable is entered
    LaunchedEffect(Unit) {

        espTcpClient = ESPTcpClient(
            serverAddress = ip,
            port = port,
            onMessageReceived = { data ->
                //println("Received data: $data")  // Log raw JSON
                gpsData.value = data // Directly assign the RawGPSData object
                rawJson.value = data.toString()  // Store raw JSON for display
            },
            onConnectionStatusChanged = { connected ->
                isConnected.value = connected
                //println("Connection status: ${if (connected) "Connected" else "Disconnected"}")
            }
        )
        espTcpClient?.connect()  // Connect to the server

    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                espTcpClient?.disconnect()
                //Log.d("TCP", "Disconnected from server")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }



    // UI Layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Connection Status: ${if (isConnected.value) "Connected" else "Disconnected"}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isConnected.value) Color.Green else Color.Red
        )

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

        Spacer(modifier = Modifier.height(16.dp))

        // Parsed GPS Data Display
        gpsData.value?.let { data ->
            Text("Parsed GPS Data:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Latitude: ${data.latitude}")
            Text("Longitude: ${data.longitude}")
            Text("Altitude: ${data.altitude ?: "N/A"}")
            Text("Speed: ${data.speed ?: "N/A"}")
            Text("Satellites: ${data.satellites ?: "N/A"}")
            Text("Timestamp: ${data.timestamp}")
        } ?: run {
            // Display a loading message while waiting for GPS data
            Text("Waiting for GPS data...")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val currentSpeed = gpsData.value?.speed ?: 0f
            Speedometer(currentSpeed)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(50.dp)
            ) {
                SevenSegmentView(
                    number = gpsData.value?.speed?.toInt() ?: 0,
                    digitsNumber = 3,
                    segmentsSpace = 1.dp,
                    segmentWidth = 8.dp,
                    digitsSpace = 16.dp,
                    activeColor = androidx.compose.ui.graphics.Color.Gray,
                    modifier = Modifier.height(100.dp)
                )
            }
        }
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
