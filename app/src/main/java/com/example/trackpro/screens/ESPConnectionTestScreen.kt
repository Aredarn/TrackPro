package com.example.trackpro.screens

import android.annotation.SuppressLint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.trackpro.TrackProApp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellDivider
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.models.GpsProviderType
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@Composable
fun ESPConnectionTestScreen(onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp

    // 1. Unified State Collection
    val isConnected     by app.gpsManager.connectionStatus.collectAsState(initial = false)
    val gpsData         by app.gpsManager.activeGpsFlow.collectAsState(initial = null)
    val gpsSource       by app.gpsSource.collectAsState()
    val selectedRateHz  by app.selectedRateHz.collectAsState()
    val confirmedRateHz by app.gpsManager.confirmedRateHz.collectAsState(initial = null)
    val selectedBtDeviceMac by app.selectedBtDeviceMac.collectAsState()
    val useTestServer   by app.useTestServer.collectAsState()
    val testServerAddress by app.testServerAddress.collectAsState()
    val useMetric       by app.useMetricUnits.collectAsState()

    // 2. Configuration for display
    val config = remember { JsonReader.loadConfig(context) }
    val ip = if (useTestServer && testServerAddress.isNotBlank()) testServerAddress else config.first
    val port = config.second
    val pairedDeviceLabel = remember(selectedBtDeviceMac) {
        app.bluetoothClassicClient.getBondedDevices()
            .find { it.address == selectedBtDeviceMac }
            ?.let { it.name ?: it.address }
            ?: "None selected"
    }

    // 3. Derived UI values
    val speed = gpsData?.speed ?: 0f
    val fix = (gpsData?.fixQuality ?: 0) > 0


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            AppTopBar(
                title = when (gpsSource) {
                    GpsProviderType.WIFI -> "ESP32 (WiFi) Mode"
                    GpsProviderType.BLUETOOTH -> "ESP32 (Bluetooth) Mode"
                    GpsProviderType.PHONE_GPS -> "Phone GPS Mode"
                },
                accent = if (isConnected) TrackProTheme.colors.accentBlue else TrackProTheme.colors.accentCyan,
                trailing = {
                    Text(
                        text = "Change",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.accentCyan,
                        modifier = Modifier.clickable { onNavigateToSettings() }
                    )
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f) // Takes remaining space
                    .verticalScroll(rememberScrollState())
            ) {
                // ── Speedometer ───────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgCard)
                        .padding(top = 24.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StyledSpeedometer(
                            speed = UnitFormatter.convertSpeed(speed, useMetric),
                            textPrimary = TrackProTheme.colors.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = UnitFormatter.speedUnitLabel(useMetric).lowercase(),
                            style = TrackProType.label,
                            color = TrackProTheme.colors.textMuted
                        )
                    }
                }

                HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

                // ── Signal quality row ────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgElevated)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell(
                        label = "Source",
                        value = when (gpsSource) {
                            GpsProviderType.WIFI -> "ESP32 (WiFi)"
                            GpsProviderType.BLUETOOTH -> "ESP32 (BT)"
                            GpsProviderType.PHONE_GPS -> "Internal"
                        },
                        size = StatCellSize.Small,
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    StatCellDivider()
                    StatCell(
                        label = "Status",
                        value = if (isConnected) "Live" else "Offline",
                        valueColor = if (isConnected) TrackProTheme.colors.accentBlue else TrackProTheme.colors.accentCyan,
                        size = StatCellSize.Small,
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                    StatCellDivider()
                    StatCell(
                        label = "Fix",
                        value = if (fix) "OK" else "Wait",
                        valueColor = if (fix) TrackProTheme.colors.accentBlue else TrackProTheme.colors.accentAmber,
                        size = StatCellSize.Small,
                        horizontalAlignment = Alignment.CenterHorizontally
                    )
                }

                HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

                // ── Telemetry List ────────────────────────
                SectionLabel("Data Stream", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgCard)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    when (gpsSource) {
                        GpsProviderType.WIFI -> TelemetryRow(
                            if (useTestServer) "Remote IP (Test)" else "Remote IP",
                            "$ip:$port",
                            if (useTestServer) TrackProTheme.colors.accentAmber else TrackProTheme.colors.textPrimary,
                            TrackProTheme.colors.textMuted
                        )
                        GpsProviderType.BLUETOOTH -> TelemetryRow("Paired Device", pairedDeviceLabel, TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted)
                        GpsProviderType.PHONE_GPS -> {}
                    }
                    TelemetryRow("Latitude", gpsData?.latitude?.let { String.format("%.6f°", it) } ?: "—", TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted)
                    TelemetryRow("Longitude", gpsData?.longitude?.let { String.format("%.6f°", it) } ?: "—", TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted)
                    TelemetryRow("Altitude", gpsData?.altitude?.let { String.format("%.1f m", it) } ?: "—", TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted)

                    TelemetryRow(
                        "Refresh",
                        when {
                            gpsSource == GpsProviderType.PHONE_GPS -> "1-5 Hz"
                            confirmedRateHz != null -> "$confirmedRateHz Hz"
                            else -> "$selectedRateHz Hz (pending)"
                        },
                        if (gpsSource != GpsProviderType.PHONE_GPS) TrackProTheme.colors.accentBlue else TrackProTheme.colors.accentAmber,
                        TrackProTheme.colors.textMuted
                    )
                }

                HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)

                // ── Raw Packet / Debug ────────────────────
                SectionLabel("Raw Packet", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgCard)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    Text(
                        text = gpsData?.toString() ?: "Awaiting data stream...",
                        color = if (gpsData != null) TrackProTheme.colors.accentBlue else TrackProTheme.colors.textMuted,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
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
                color = Color(DataVizColors.gaugeTrack.toColorInt()),
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
                    speedFraction < 0.5f -> Color(DataVizColors.gaugeLow.toColorInt())
                    speedFraction < 0.8f -> Color(DataVizColors.gaugeMid.toColorInt())
                    else                 -> Color(DataVizColors.gaugeHigh.toColorInt())
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
                color = DataVizColors.gaugeTick.toColorInt()
                strokeWidth = 2f
                isAntiAlias = true
            }
            val labelPaint = android.graphics.Paint().apply {
                color = DataVizColors.gaugeTick.toColorInt()
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
                color = Color(DataVizColors.gaugeHigh.toColorInt()).copy(alpha = 0.2f),
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
                color = Color(DataVizColors.gaugeHigh.toColorInt()),
                start = Offset(cx, cy),
                end = Offset(
                    (cx + cos(needleAngle) * needleLength).toFloat(),
                    (cy + sin(needleAngle) * needleLength).toFloat()
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center hub
            drawCircle(color = Color(DataVizColors.darkOutline.toColorInt()), radius = 10.dp.toPx(), center = Offset(cx, cy))
            drawCircle(
                color = Color(DataVizColors.gaugeHigh.toColorInt()),
                radius = 6.dp.toPx(),
                center = Offset(cx, cy)
            )
        }

        // Digital speed readout in center
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))
            Text(
                text = "${animatedSpeed.toInt()}",
                style = TrackProType.displayNumeric,
                color = textPrimary
            )
        }
    }
}

// ── Sub-components ─────────────────────────────────────────

@Composable
private fun TelemetryRow(label: String, value: String, textPrimary: Color, textMuted: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = TrackProType.label, color = textMuted)
        Text(
            value,
            style = TrackProType.body.copy(fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = textPrimary
        )
    }
}

