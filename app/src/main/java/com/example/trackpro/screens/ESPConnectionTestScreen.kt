package com.example.trackpro.screens

import android.annotation.SuppressLint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
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
import com.example.trackpro.components.pressable
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.DashGroup
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.Readout
import com.example.trackpro.components.SegmentBar
import com.example.trackpro.components.rememberTrend
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Motion
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.models.GpsProviderType
import com.example.trackpro.theme.segmentOff
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@Composable
fun ESPConnectionTestScreen(
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp

    // 1. Unified State Collection
    val isConnected     by app.gpsManager.connectionStatus.collectAsState(initial = false)
    // Why the link is down, straight from the provider that observed it. Null while
    // connected, and null for phone GPS, which is not a stream link.
    val lastFailure     by app.gpsManager.lastFailure.collectAsState(initial = null)
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
    // "valid" is the GPS module's own fix status (gps.location.isValid() in the
    // firmware) - fixQuality is actually a satellite *count*, not a fix indicator,
    // and could read 0 (or just not correlate) even with a real fix locked in.
    val fix = gpsData?.valid == true


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
                accent = if (isConnected) TrackProTheme.colors.accent else TrackProTheme.colors.textFaint,
                onBack = onBack,
                trailing = {
                    Text(
                        text = "Change",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.accent,
                        // Bare text was the smallest tap target on the screen; give it
                        // real padding and a press response.
                        modifier = Modifier
                            .pressable(onClick = onNavigateToSettings, scale = 0.94f)
                            .padding(horizontal = Spacing.sm, vertical = 4.dp)
                    )
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f) // Takes remaining space
                    .verticalScroll(rememberScrollState())
            ) {
                // -- Speed ---------------------------------
                // A numeral rather than a dial. A drawn gauge here would be a picture of
                // an instrument; the panel's own language is a readout plus a bar, and
                // the bar carries the range the numeral cannot.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.field)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Readout(
                        // 2 km/h of hysteresis: enough that a stationary module reading
                        // 0.4, 0.9, 0.3 holds Steady instead of twitching.
                        trend = rememberTrend(
                            UnitFormatter.convertSpeed(speed, useMetric).toFloat(),
                            threshold = 2f
                        ),
                        value = UnitFormatter.formatSpeed(speed, useMetric),
                        caption = "Ground speed \u00b7 ${UnitFormatter.speedUnitLabel(useMetric)}",
                        valueColor = if (isConnected) TrackProTheme.colors.marking
                        else TrackProTheme.colors.markingDim,
                        valueSize = 64.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    SegmentBar(
                        signedFraction = (UnitFormatter.convertSpeed(speed, useMetric) / 200.0)
                            .coerceIn(0.0, 1.0).toFloat(),
                        activeColor = if (isConnected) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.segmentOff,
                        bidirectional = false,
                        segments = 20,
                        height = 14.dp
                    )
                }

                Bezel()

                // -- Link state ----------------------------
                Row(modifier = Modifier.fillMaxWidth()) {
                    Instrument(
                        label = "Source",
                        value = when (gpsSource) {
                            GpsProviderType.WIFI -> "ESP32 WIFI"
                            GpsProviderType.BLUETOOTH -> "ESP32 BT"
                            GpsProviderType.PHONE_GPS -> "INTERNAL"
                        },
                        valueSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Bezel(vertical = true, modifier = Modifier.height(58.dp))
                    Instrument(
                        label = "Link",
                        value = if (isConnected) "LIVE" else "OFFLINE",
                        valueColor = if (isConnected) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.deltaBad,
                        valueSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Bezel(vertical = true, modifier = Modifier.height(58.dp))
                    Instrument(
                        label = "Fix",
                        value = if (fix) "LOCKED" else "SEARCHING",
                        valueColor = if (fix) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.deltaBad,
                        valueSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Bezel()

                // -- Diagnosis -----------------------------
                // PRODUCT.md principle 2: a stranger has to succeed alone. This screen used
                // to say OFFLINE / SEARCHING and stop there, which tells someone that they
                // have failed and nothing about why or what to do next.
                if (!isConnected || !fix) {
                    val (cause, recovery) = rigDiagnosis(gpsSource, isConnected, fix, lastFailure, ip, port)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProTheme.colors.field)
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = cause.uppercase(),
                            style = TrackProType.label,
                            color = if (!isConnected) TrackProTheme.colors.deltaBad
                            else TrackProTheme.colors.markingDim
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = recovery,
                            style = TrackProType.body.atSize(13.sp),
                            color = TrackProTheme.colors.marking
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashAction(
                                label = "Retry link",
                                onClick = { app.gpsManager.retryActiveProvider() },
                                compact = true,
                                modifier = Modifier.weight(1f)
                            )
                            DashAction(
                                label = "Change source",
                                onClick = onNavigateToSettings,
                                compact = true,
                                accent = TrackProTheme.colors.markingDim,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (lastFailure != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Reported: $lastFailure",
                                style = TrackProType.label,
                                color = TrackProTheme.colors.markingDim
                            )
                        }
                    }
                    Bezel()
                }

                DashGroup("Data stream") {
                    when (gpsSource) {
                        GpsProviderType.WIFI -> TelemetryRow(
                            if (useTestServer) "Remote IP (test)" else "Remote IP",
                            "$ip:$port",
                            if (useTestServer) TrackProTheme.colors.accent else TrackProTheme.colors.marking,
                            TrackProTheme.colors.markingDim
                        )
                        GpsProviderType.BLUETOOTH -> TelemetryRow(
                            "Paired device", pairedDeviceLabel,
                            TrackProTheme.colors.marking, TrackProTheme.colors.markingDim
                        )
                        GpsProviderType.PHONE_GPS -> {}
                    }
                    TelemetryRow("Latitude", gpsData?.latitude?.let { String.format("%.6f\u00b0", it) } ?: "\u2014", TrackProTheme.colors.marking, TrackProTheme.colors.markingDim)
                    TelemetryRow("Longitude", gpsData?.longitude?.let { String.format("%.6f\u00b0", it) } ?: "\u2014", TrackProTheme.colors.marking, TrackProTheme.colors.markingDim)
                    TelemetryRow("Altitude", gpsData?.altitude?.let { String.format("%.1f m", it) } ?: "\u2014", TrackProTheme.colors.marking, TrackProTheme.colors.markingDim)
                    TelemetryRow(
                        "Refresh",
                        when {
                            gpsSource == GpsProviderType.PHONE_GPS -> "1-5 Hz"
                            confirmedRateHz != null -> "$confirmedRateHz Hz"
                            else -> "$selectedRateHz Hz (pending)"
                        },
                        TrackProTheme.colors.marking,
                        TrackProTheme.colors.markingDim
                    )
                }

                DashGroup("Raw packet") {
                    Text(
                        text = gpsData?.toString() ?: "Awaiting data stream\u2026",
                        color = if (gpsData != null) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.markingDim,
                        style = TrackProType.body.atSize(10.sp),
                        lineHeight = 15.sp
                    )
                }

                Spacer(Modifier.height(Spacing.xl))
            }
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
            style = TrackProType.body.atSize(13.sp).copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            color = textPrimary
        )
    }
}

/**
 * Turns what the link layer actually observed into one cause and one recovery sentence.
 *
 * Deliberately only reports states the code can genuinely distinguish: whether the socket
 * opened, whether the provider reported a failure and what it said, and whether a fix has
 * been acquired. It does not guess at causes it cannot see.
 */
private fun rigDiagnosis(
    source: GpsProviderType,
    isConnected: Boolean,
    fix: Boolean,
    lastFailure: String?,
    ip: String,
    port: Int
): Pair<String, String> {
    if (isConnected && !fix) {
        return "Linked, no satellite fix" to
            "The module is connected and talking. It has not locked onto satellites yet - " +
            "that needs an open view of the sky and can take a minute from cold."
    }
    return when (source) {
        GpsProviderType.WIFI -> {
            val refused = lastFailure?.contains("ECONNREFUSED", true) == true ||
                lastFailure?.contains("refused", true) == true
            val timedOut = lastFailure?.contains("timeout", true) == true ||
                lastFailure?.contains("timed out", true) == true
            when {
                refused -> "Nothing listening at $ip:$port" to
                    "The phone reached that address but nothing answered on port $port. The " +
                    "ESP32 is powered but its firmware may not be running - reflash from the " +
                    "TrackPro_ESP repo."
                timedOut -> "No route to $ip" to
                    "The phone could not reach the module at all. Join the ESP32's Wi-Fi " +
                    "network in Android settings - it does not route over mobile data."
                else -> "Not connected to $ip:$port" to
                    "Check the ESP32 is powered, then that this phone is joined to its Wi-Fi " +
                    "network rather than your home or phone network."
            }
        }
        GpsProviderType.BLUETOOTH -> "Serial link not open" to
            "Check the ESP32 is powered and still paired in Android Bluetooth settings. A " +
            "module that was unpaired or reset has to be paired again before it appears here."
        GpsProviderType.PHONE_GPS -> "Phone GPS not reporting" to
            "Allow location access for TrackPro and go outdoors with a clear view of the sky. " +
            "Phone GPS also samples far slower than the ESP rig, so times will be coarser."
    }
}
