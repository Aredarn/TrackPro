package com.example.trackpro.Screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.ManagerClasses.ESPTcpClient
import com.example.trackpro.ManagerClasses.JsonReader
import com.example.trackpro.ManagerClasses.RawGPSData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Divider
import androidx.compose.ui.unit.sp


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
    val context = LocalContext.current
    val (ip, port) = remember { JsonReader.loadConfig(context) }
    var espTcpClient: ESPTcpClient? by remember { mutableStateOf(null) }
    val gpsDataFlow = remember { MutableSharedFlow<RawGPSData>(extraBufferCapacity = 10) }

    // Design tokens
    val BgDeep      = Color(0xFF080A0F)
    val BgCard      = Color(0xFF0E1117)
    val BgElevated  = Color(0xFF151922)
    val AccentRed   = Color(0xFFE8001C)
    val AccentGreen = Color(0xFF00C853)
    val AccentAmber = Color(0xFFFFC107)
    val TextPrimary = Color(0xFFF0F2F5)
    val TextMuted   = Color(0xFF6B7280)
    val SectorLine  = Color(0xFF1E2530)

    fun calculateDelay(espTimestamp: Long): String {
        if (espTimestamp == 0L) return "—"
        val now = System.currentTimeMillis()
        val rawDelay = now - espTimestamp
        val delay = if (kotlin.math.abs(rawDelay) > 3_600_000)
            now - (espTimestamp + 7_200_000) else rawDelay
        return if (delay < 1000) "${delay}ms" else String.format("%.3fs", delay / 1000.0)
    }

    LaunchedEffect(Unit) {
        try {
            espTcpClient = ESPTcpClient(
                serverAddress = ip,
                port = port,
                onMessageReceived = { data -> gpsDataFlow.tryEmit(data) },
                onConnectionStatusChanged = { isConnected.value = it }
            )
            espTcpClient?.connect()
        } catch (e: Exception) {
            Log.e("ESPConnection", "TCP setup failed", e)
        }
    }

    LaunchedEffect(Unit) {
        gpsDataFlow.catch { e -> Log.e("GPSFlow", "Flow error", e) }
            .collect { data -> gpsData.value = data }
    }

    DisposableEffect(Unit) {
        onDispose { try { espTcpClient?.disconnect() } catch (e: IOException) { e.printStackTrace() } }
    }

    val speed = gpsData.value?.speed ?: 0f
    val fix = (gpsData.value?.satellites ?: 0) > 0
    val delay = calculateDelay(gpsData.value?.timestamp ?: 0L)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isConnected.value) AccentGreen else AccentRed)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isConnected.value) "● ESP CONNECTED" else "● ESP DISCONNECTED",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "$ip:$port",
                        color = Color.Black.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {

                // ── Speedometer ───────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard)
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StyledSpeedometer(
                            speed = speed,
                            accentRed = AccentRed,
                            textPrimary = TextPrimary,
                            textMuted = TextMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "km/h",
                            color = TextMuted,
                            fontSize = 12.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Divider(color = SectorLine, thickness = 1.dp)

                // ── Signal quality row ────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgElevated)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SignalCell(
                        label = "GPS FIX",
                        value = if (fix) "ACQUIRED" else "SEARCHING",
                        valueColor = if (fix) AccentGreen else AccentAmber,
                        textMuted = TextMuted
                    )
                    VerticalDividerLine(SectorLine)
                    SignalCell(
                        label = "LATENCY",
                        value = delay,
                        valueColor = TextPrimary,
                        textMuted = TextMuted
                    )
                    VerticalDividerLine(SectorLine)
                    SignalCell(
                        label = "SATELLITES",
                        value = "${gpsData.value?.satellites ?: 0}",
                        valueColor = if ((gpsData.value?.satellites ?: 0) >= 4)
                            AccentGreen else AccentAmber,
                        textMuted = TextMuted
                    )
                }

                Divider(color = SectorLine, thickness = 1.dp)

                // ── GPS data grid ─────────────────────────
                SectionHeader("POSITION", TextMuted, SectorLine)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TelemetryRow(
                        label = "LATITUDE",
                        value = gpsData.value?.latitude?.let {
                            String.format("%.6f°", it)
                        } ?: "—",
                        textPrimary = TextPrimary,
                        textMuted = TextMuted
                    )
                    TelemetryRow(
                        label = "LONGITUDE",
                        value = gpsData.value?.longitude?.let {
                            String.format("%.6f°", it)
                        } ?: "—",
                        textPrimary = TextPrimary,
                        textMuted = TextMuted
                    )
                    TelemetryRow(
                        label = "ALTITUDE",
                        value = gpsData.value?.altitude?.let {
                            String.format("%.1f m", it)
                        } ?: "—",
                        textPrimary = TextPrimary,
                        textMuted = TextMuted
                    )
                    TelemetryRow(
                        label = "SPEED",
                        value = gpsData.value?.speed?.let {
                            String.format("%.2f km/h", it)
                        } ?: "—",
                        textPrimary = TextPrimary,
                        textMuted = TextMuted
                    )
                    TelemetryRow(
                        label = "FIX QUALITY",
                        value = when (gpsData.value?.satellites) {
                            0 -> "NO FIX"
                            1 -> "GPS FIX"
                            2 -> "DGPS FIX"
                            else -> "—"
                        },
                        textPrimary = if (fix) AccentGreen else AccentAmber,
                        textMuted = TextMuted
                    )
                    TelemetryRow(
                        label = "TIMESTAMP",
                        value = gpsData.value?.timestamp?.let {
                            val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(it))
                        } ?: "—",
                        textPrimary = TextPrimary,
                        textMuted = TextMuted
                    )
                }

                Divider(color = SectorLine, thickness = 1.dp)

                // ── Raw data ──────────────────────────────
                SectionHeader("RAW PACKET", TextMuted, SectorLine)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = gpsData.value?.toString() ?: "Waiting for data...",
                        color = if (gpsData.value != null) AccentGreen else TextMuted,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ── Styled speedometer ─────────────────────────────────────

@Composable
fun StyledSpeedometer(
    speed: Float,
    accentRed: Color,
    textPrimary: Color,
    textMuted: Color
) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "speed"
    )

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension / 2f - 16.dp.toPx()
            val startAngle = 135f
            val sweepTotal = 270f

            // Background arc track
            drawArc(
                color = Color(0xFF1E2530),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Speed fill arc
            val speedFraction = (animatedSpeed / 260f).coerceIn(0f, 1f)
            if (speedFraction > 0f) {
                // Color shifts from green → amber → red as speed increases
                val arcColor = when {
                    speedFraction < 0.5f -> Color(0xFF00C853)
                    speedFraction < 0.8f -> Color(0xFFFFC107)
                    else                 -> Color(0xFFE8001C)
                }
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * speedFraction,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Tick marks every 20 km/h
            val tickPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#6B7280")
                strokeWidth = 2f
                isAntiAlias = true
            }
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#6B7280")
                textSize = 22f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            for (i in 0..13) {
                val fraction = i / 13f
                val angle = Math.toRadians((startAngle + sweepTotal * fraction).toDouble())
                val outerR = radius - 18.dp.toPx()
                val innerR = radius - 28.dp.toPx()
                val labelR = radius - 44.dp.toPx()

                drawContext.canvas.nativeCanvas.drawLine(
                    (cx + cos(angle) * innerR).toFloat(),
                    (cy + sin(angle) * innerR).toFloat(),
                    (cx + cos(angle) * outerR).toFloat(),
                    (cy + sin(angle) * outerR).toFloat(),
                    tickPaint
                )

                if (i % 2 == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${i * 20}",
                        (cx + cos(angle) * labelR).toFloat(),
                        (cy + sin(angle) * labelR).toFloat() + 8f,
                        labelPaint
                    )
                }
            }

            // Needle
            val needleFraction = (animatedSpeed / 260f).coerceIn(0f, 1f)
            val needleAngle = Math.toRadians((startAngle + sweepTotal * needleFraction).toDouble())
            val needleLength = radius - 32.dp.toPx()

            // Needle glow (wider, semi-transparent)
            drawLine(
                color = Color(0xFFE8001C).copy(alpha = 0.2f),
                start = Offset(cx, cy),
                end = Offset(
                    (cx + cos(needleAngle) * needleLength).toFloat(),
                    (cy + sin(needleAngle) * needleLength).toFloat()
                ),
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round
            )
            // Needle sharp
            drawLine(
                color = Color(0xFFE8001C),
                start = Offset(cx, cy),
                end = Offset(
                    (cx + cos(needleAngle) * needleLength).toFloat(),
                    (cy + sin(needleAngle) * needleLength).toFloat()
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center hub
            drawCircle(color = Color(0xFF0E1117), radius = 10.dp.toPx(), center = Offset(cx, cy))
            drawCircle(
                color = Color(0xFFE8001C),
                radius = 6.dp.toPx(),
                center = Offset(cx, cy)
            )
        }

        // Digital speed readout in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))
            Text(
                text = "${animatedSpeed.toInt()}",
                color = textPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
        }
    }
}

// ── Sub-components ─────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, textMuted: Color, sectorLine: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0C11))
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        Text(title, color = textMuted, fontSize = 9.sp,
            fontWeight = FontWeight.Black, letterSpacing = 3.sp)
    }
    Divider(color = sectorLine, thickness = 1.dp)
}

@Composable
private fun TelemetryRow(label: String, value: String, textPrimary: Color, textMuted: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = textMuted, fontSize = 10.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(value, color = textPrimary, fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

@Composable
private fun SignalCell(label: String, value: String, valueColor: Color, textMuted: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = textMuted, fontSize = 9.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VerticalDividerLine(color: Color) {
    Box(modifier = Modifier
        .width(1.dp)
        .height(36.dp)
        .background(color))
}

@Preview(showBackground = true)
@Composable
fun ESPConnectionTestPreview() {
    ESPConnectionTestScreen()
}
