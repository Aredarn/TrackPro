package com.example.trackpro.screens

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
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
import com.example.trackpro.online.MessageText
import com.example.trackpro.components.paddockCard
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
import java.util.Locale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight

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
    val noneSelected = stringResource(R.string.rig_none_selected)
    val pairedDeviceLabel = remember(selectedBtDeviceMac, noneSelected) {
        app.bluetoothClassicClient.getBondedDevices()
            .find { it.address == selectedBtDeviceMac }
            ?.let { it.name ?: it.address }
            ?: noneSelected
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
                    GpsProviderType.WIFI -> stringResource(R.string.rig_mode_wifi)
                    GpsProviderType.BLUETOOTH -> stringResource(R.string.rig_mode_bt)
                    GpsProviderType.PHONE_GPS -> stringResource(R.string.rig_mode_phone)
                },
                // The bar's dot is the first thing read on this screen, and when the rig
                // is down it is reporting a fault - so it takes danger, not the dimmest
                // token in the palette.
                accent = if (isConnected) TrackProTheme.colors.accent
                else TrackProTheme.colors.danger,
                onBack = onBack,
                trailing = {
                    Text(
                        text = stringResource(R.string.rig_change),
                        style = TrackProType.label,
                        color = TrackProTheme.colors.accent,
                        // Bare text was the smallest tap target on the screen; give it
                        // real padding and a press response.
                        modifier = Modifier
                            .pressable(onClick = onNavigateToSettings, scale = 0.94f)
                            // Giving it "real padding" last time still only reached 22dp.
                            // The bar is 48dp tall, so the target fills it without moving
                            // anything else in the row.
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = Spacing.sm)
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
                        .padding(horizontal = Spacing.gutter)
                        .paddockCard()
                        .padding(Spacing.lg)
                ) {
                    Readout(
                        // 2 km/h of hysteresis: enough that a stationary module reading
                        // 0.4, 0.9, 0.3 holds Steady instead of twitching.
                        trend = rememberTrend(
                            UnitFormatter.convertSpeed(speed, useMetric).toFloat(),
                            threshold = 2f
                        ),
                        value = UnitFormatter.formatSpeed(speed, useMetric),
                        caption = stringResource(R.string.rig_ground_speed, UnitFormatter.speedUnitLabel(useMetric)),
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

                Spacer(Modifier.height(Spacing.md))

                // -- Link state ----------------------------
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).paddockCard()) {
                    Instrument(
                        label = stringResource(R.string.rig_source),
                        value = when (gpsSource) {
                            GpsProviderType.WIFI -> "ESP32 Wi-Fi"
                            GpsProviderType.BLUETOOTH -> "ESP32 BT"
                            GpsProviderType.PHONE_GPS -> stringResource(R.string.rig_phone_gps)
                        },
                        valueSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Bezel(vertical = true, modifier = Modifier.height(58.dp))
                    Instrument(
                        label = stringResource(R.string.rig_link),
                        value = if (isConnected) stringResource(R.string.hud_live) else stringResource(R.string.rig_offline),
                        valueColor = if (isConnected) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.danger,
                        valueSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Bezel(vertical = true, modifier = Modifier.height(58.dp))
                    Instrument(
                        label = stringResource(R.string.rig_fix),
                        value = if (fix) stringResource(R.string.rig_locked) else stringResource(R.string.rig_searching),
                        valueColor = if (fix) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.deltaBad,
                        valueSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(Spacing.md))

                // -- Diagnosis -----------------------------
                // PRODUCT.md principle 2: a stranger has to succeed alone. This screen used
                // to say OFFLINE / SEARCHING and stop there, which tells someone that they
                // have failed and nothing about why or what to do next.
                if (!isConnected || !fix) {
                    val (cause, recovery) = rigDiagnosis(gpsSource, isConnected, fix, lastFailure, ip, port)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.gutter)
                            .paddockCard()
                            .padding(Spacing.lg)
                    ) {
                        Text(
                            text = cause,
                            style = TrackProType.titleMedium,
                            color = if (!isConnected) TrackProTheme.colors.danger
                            else TrackProTheme.colors.deltaBad
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = recovery,
                            style = TrackProType.body,
                            color = TrackProTheme.colors.marking
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashAction(
                                label = stringResource(R.string.rig_retry_link),
                                onClick = { app.gpsManager.retryActiveProvider() },
                                compact = true,
                                primary = true,
                                modifier = Modifier.weight(1f)
                            )
                            DashAction(
                                label = stringResource(R.string.rig_change_source),
                                onClick = onNavigateToSettings,
                                compact = true,
                                accent = TrackProTheme.colors.markingDim,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (lastFailure != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.rig_reported, MessageText.localize(lastFailure) ?: ""),
                                style = TrackProType.label,
                                color = TrackProTheme.colors.markingDim
                            )
                        }
                    }
                }

                DashGroup(stringResource(R.string.rig_data_stream)) {
                    when (gpsSource) {
                        GpsProviderType.WIFI -> TelemetryRow(
                            if (useTestServer) stringResource(R.string.rig_remote_ip_test) else stringResource(R.string.rig_remote_ip),
                            "$ip:$port",
                            if (useTestServer) TrackProTheme.colors.accent else TrackProTheme.colors.marking,
                            TrackProTheme.colors.markingDim
                        )
                        GpsProviderType.BLUETOOTH -> TelemetryRow(
                            stringResource(R.string.rig_paired_device), pairedDeviceLabel,
                            TrackProTheme.colors.marking, TrackProTheme.colors.markingDim
                        )
                        GpsProviderType.PHONE_GPS -> {}
                    }
                    TelemetryRow(stringResource(R.string.rig_latitude), gpsData?.latitude?.let { String.format(Locale.US, "%.6f\u00b0", it) } ?: "\u2014", TrackProTheme.colors.marking, TrackProTheme.colors.markingDim)
                    TelemetryRow(stringResource(R.string.rig_longitude), gpsData?.longitude?.let { String.format(Locale.US, "%.6f\u00b0", it) } ?: "\u2014", TrackProTheme.colors.marking, TrackProTheme.colors.markingDim)
                    TelemetryRow(stringResource(R.string.rig_altitude), gpsData?.altitude?.let { String.format(Locale.US, "%.1f m", it) } ?: "\u2014", TrackProTheme.colors.marking, TrackProTheme.colors.markingDim)
                    TelemetryRow(
                        stringResource(R.string.rig_refresh),
                        when {
                            gpsSource == GpsProviderType.PHONE_GPS -> "1-5 Hz"
                            confirmedRateHz != null -> "$confirmedRateHz Hz"
                            else -> stringResource(R.string.rig_rate_pending, selectedRateHz)
                        },
                        TrackProTheme.colors.marking,
                        TrackProTheme.colors.markingDim
                    )
                }

                DashGroup(stringResource(R.string.rig_raw_packet)) {
                    Text(
                        text = gpsData?.toString() ?: stringResource(R.string.rig_awaiting),
                        color = if (gpsData != null) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.markingDim,
                        style = TrackProType.label.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        lineHeight = 16.sp
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
        Text(label, style = TrackProType.body, color = textMuted)
        Text(
            value,
            style = TrackProType.titleMedium,
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
@Composable
private fun rigDiagnosis(
    source: GpsProviderType,
    isConnected: Boolean,
    fix: Boolean,
    lastFailure: String?,
    ip: String,
    port: Int
): Pair<String, String> {
    if (isConnected && !fix) {
        return stringResource(R.string.rig_no_fix) to stringResource(R.string.rig_no_fix_hint)
    }
    return when (source) {
        GpsProviderType.WIFI -> {
            val refused = lastFailure?.contains("ECONNREFUSED", true) == true ||
                lastFailure?.contains("refused", true) == true
            val timedOut = lastFailure?.contains("timeout", true) == true ||
                lastFailure?.contains("timed out", true) == true
            when {
                refused -> stringResource(R.string.rig_refused, ip, port) to stringResource(R.string.rig_refused_hint, port)
                timedOut -> stringResource(R.string.rig_no_route, ip) to stringResource(R.string.rig_no_route_hint)
                else -> stringResource(R.string.rig_not_connected, ip, port) to stringResource(R.string.rig_not_connected_hint)
            }
        }
        GpsProviderType.BLUETOOTH -> stringResource(R.string.rig_serial_closed) to stringResource(R.string.rig_serial_closed_hint)
        GpsProviderType.PHONE_GPS -> stringResource(R.string.rig_phone_silent) to stringResource(R.string.rig_phone_silent_hint)
    }
}
