package com.example.trackpro

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import java.io.IOException
import kotlin.math.abs
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
    //val gpsChannel = remember { Channel<RawGPSData>(capacity = Channel.CONFLATED) }
    val gpsDataFlow = remember { MutableSharedFlow<RawGPSData>(extraBufferCapacity = 10) }
    val updateRate = remember { mutableStateOf(0) }

    fun calculateUiDelayFormatted(espTimestamp: Long): String {
        val now = System.currentTimeMillis()
        if(espTimestamp == 0L || espTimestamp == null)
        {
            return "-1";
        }
        val rawDelay = now - espTimestamp

        val delay = if (abs(rawDelay) > 3_600_000) {
            val adjustedEspTime = espTimestamp + 7_200_000 // Adjust for timezone drift
            now - adjustedEspTime
        } else {
            rawDelay
        }

        return if (delay < 1000) {
            // Less than 1 second: show in milliseconds
            "${delay} ms"
        } else {
            // 1 second or more: show in seconds with 3 decimal places
            String.format("%.3f s", delay / 1000.0)
        }
    }



    // Reusable Info Item
    @Composable
    fun InfoItem(icon: ImageVector, label: String, value: String) {
        Column(
            modifier = Modifier
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$label: $value",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )
            }
        }
    }

// 1. Connection Setup (runs once)
    LaunchedEffect(Unit) {
        try {
            Log.d("trackpro ip",ip)
            Log.d("trackpro", port.toString() + "")
            espTcpClient = ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data ->
                    // Send data to Flow (thread-safe)
                    gpsDataFlow.tryEmit(data)
                },
                onConnectionStatusChanged = { connected ->
                    isConnected.value = connected
                }
            )
            espTcpClient?.connect()
        } catch (e: Exception) {
            Log.e("ESPConnection", "TCP setup failed", e)
        }
    }

// 2. Data Processing (UI updates)
    LaunchedEffect(Unit) {
        Log.d("GPSFlow", "Starting data collection...")
        gpsDataFlow
            .onStart { Log.d("GPSFlow", "Flow active") }
            .catch { e -> Log.e("GPSFlow", "Flow error", e) }
            .collect { data ->
                Log.d("TimeDebug", """
            Raw GPS Time: ${data.timestamp}
            System Now: ${System.currentTimeMillis()}
        """.trimIndent())

                // Update UI states (automatically dispatched to Main thread)
                gpsData.value = data
                rawJson.value = data.toString()
                Log.d("GPSFlow", "UI updated with: $data")
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
        // Connection Status
        Text(
            text = "Connection Status (ESP WIFI): ${if (isConnected.value) "Connected" else "Disconnected"}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isConnected.value) Color(0xFF4CAF50) else Color(0xFFF44336)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {

            val delayText = calculateUiDelayFormatted(gpsData.value?.timestamp ?: 0L)
            //val delayColor = if (delayText.toDouble() < 500) Color(0xFF4CAF50) else Color(0xFFF44336) // Green or Red

            Text(
                text = "Delay: ${delayText}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Green
                )
            )
        }


        Spacer(modifier = Modifier.height(16.dp))

        // GPS Data Card
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Last GPS Data",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Latitude and Longitude
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoItem(
                        icon = Icons.Default.LocationOn,
                        label = "Latitude",
                        value = "${gpsData.value?.latitude ?: "0.000000"}"
                    )
                    InfoItem(
                        icon = Icons.Default.LocationOn,
                        label = "Longitude",
                        value = "${gpsData.value?.longitude ?: "0.000000"}"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Altitude and Satellites
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoItem(
                        icon = Icons.Default.Terrain,
                        label = "Altitude",
                        value = "${gpsData.value?.altitude ?: "0.000000"} m"
                    )
                    InfoItem(
                        icon = Icons.Default.Satellite,
                        label = "Satellites",
                        value = "${gpsData.value?.satellites ?: "0"}"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speed and Timestamp
                Row(modifier = Modifier.fillMaxWidth()) {
                    InfoItem(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        value = "${gpsData.value?.speed ?: "0.00"} km/h"
                    )
                    InfoItem(
                        icon = Icons.Default.AccessTimeFilled,
                        label = "Timestamp",
                        value = "${gpsData.value?.timestamp ?: "--:--:--"}"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))


        // Raw JSON Card
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Raw JSON Data:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = rawJson.value.ifEmpty { "Waiting for data..." },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
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
