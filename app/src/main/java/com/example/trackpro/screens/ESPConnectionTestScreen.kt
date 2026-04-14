package com.example.trackpro.screens

import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.trackpro.TrackProApp
import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.theme.TrackProColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ESPConnectionTestScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp

    // 1. Unified State Collection
    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)
    val gpsData     by app.gpsManager.activeGpsFlow.collectAsState(initial = null)
    val useExternal by app.useExternalGps.collectAsState()

    // 2. Configuration for display
    val config = remember { JsonReader.loadConfig(context) }
    val ip = config.first
    val port = config.second

    // 3. Derived UI values
    val speed = gpsData?.speed ?: 0f
    val fix = (gpsData?.fixQuality ?: 0) > 0


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar (Reacts to Source) ────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isConnected) TrackProColors.AccentGreen else TrackProColors.AccentRed)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (useExternal) "● ESP32 MODE" else "● PHONE GPS MODE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

                    // Simple Toggle Switch in the Top Bar
                    androidx.compose.material3.Button(
                        onClick = { app.useExternalGps.value = !useExternal },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color.Black.copy(alpha = 0.2f)
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("SWITCH", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f) // Takes remaining space
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Speedometer ───────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.BgCard)
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StyledSpeedometer(
                            speed = speed,
                            textPrimary = TrackProColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "km/h",
                            color = TrackProColors.TextMuted,
                            fontSize = 12.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

                // ── Signal quality row ────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.BgElevated)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SignalCell(
                        label = "SOURCE",
                        value = if (useExternal) "ESP32" else "INTERNAL",
                        valueColor = TrackProColors.TextPrimary,
                        textMuted = TrackProColors.TextMuted
                    )
                    VerticalDividerLine(TrackProColors.SectorLine)
                    SignalCell(
                        label = "STATUS",
                        value = if (isConnected) "LIVE" else "OFFLINE",
                        valueColor = if (isConnected) TrackProColors.AccentGreen else TrackProColors.AccentRed,
                        textMuted = TrackProColors.TextMuted
                    )
                    VerticalDividerLine(TrackProColors.SectorLine)
                    SignalCell(
                        label = "FIX",
                        value = if (fix) "OK" else "WAIT",
                        valueColor = if (fix) TrackProColors.AccentGreen else TrackProColors.AccentAmber,
                        textMuted = TrackProColors.TextMuted
                    )
                }

                HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

                // ── Telemetry List ────────────────────────
                SectionHeader("DATA STREAM", TrackProColors.TextMuted, TrackProColors.SectorLine)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.BgCard)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (useExternal) {
                        TelemetryRow("REMOTE IP", "$ip:$port", TrackProColors.TextPrimary, TrackProColors.TextMuted)
                    }
                    TelemetryRow("LATITUDE", gpsData?.latitude?.let { String.format("%.6f°", it) } ?: "—", TrackProColors.TextPrimary, TrackProColors.TextMuted)
                    TelemetryRow("LONGITUDE", gpsData?.longitude?.let { String.format("%.6f°", it) } ?: "—", TrackProColors.TextPrimary, TrackProColors.TextMuted)
                    TelemetryRow("ALTITUDE", gpsData?.altitude?.let { String.format("%.1f m", it) } ?: "—", TrackProColors.TextPrimary, TrackProColors.TextMuted)

                    TelemetryRow(
                        "REFRESH",
                        if (useExternal) "20-25 Hz" else "1-5 Hz",
                        if (useExternal) TrackProColors.AccentGreen else TrackProColors.AccentAmber,
                        TrackProColors.TextMuted
                    )
                }

                HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)

                // ── Raw Packet / Debug ────────────────────
                SectionHeader("RAW PACKET", TrackProColors.TextMuted, TrackProColors.SectorLine)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.BgCard)
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = gpsData?.toString() ?: "Awaiting data stream...",
                        color = if (gpsData != null) TrackProColors.AccentGreen else TrackProColors.TextMuted,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
// ── Styled speedometer ─────────────────────────────────────

@Composable
fun StyledSpeedometer(
    speed: Float,
    textPrimary: Color
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
                color = "#6B7280".toColorInt()
                strokeWidth = 2f
                isAntiAlias = true
            }
            val labelPaint = android.graphics.Paint().apply {
                color = "#6B7280".toColorInt()
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
    HorizontalDivider(color = sectorLine, thickness = 1.dp)
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

