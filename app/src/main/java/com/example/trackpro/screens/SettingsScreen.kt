package com.example.trackpro.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.AppCard
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useExternal by app.useExternalGps.collectAsState()
    val useDarkTheme by app.useDarkTheme.collectAsState()
    val useMetric by app.useMetricUnits.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        AppTopBar(title = "Settings", accent = TrackProTheme.colors.textMuted, onBack = onBack)

        Column(
            modifier = Modifier
                .padding(Spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // --- Section: Hardware & GPS ---
            SectionLabel("Hardware & Sensors")
            AppCard {
                SettingsToggleRow(
                    label = "GPS Source",
                    valueText = if (useExternal) "External ESP32 Module" else "Internal Phone GPS",
                    valueColor = if (useExternal) TrackProTheme.colors.accentCyan else TrackProTheme.colors.textMuted,
                    buttonText = if (useExternal) "Use Phone" else "Use ESP32",
                    isActive = useExternal,
                    onClick = { app.useExternalGps.value = !useExternal }
                )
            }

            // --- Section: Appearance ---
            SectionLabel("Appearance")
            AppCard {
                SettingsToggleRow(
                    label = "Theme",
                    valueText = if (useDarkTheme) "Dark" else "Light",
                    valueColor = TrackProTheme.colors.accentCyan,
                    buttonText = if (useDarkTheme) "Use Light" else "Use Dark",
                    isActive = true,
                    onClick = { app.setDarkTheme(!useDarkTheme) }
                )
            }

            // --- Section: Units ---
            SectionLabel("Units")
            AppCard {
                SettingsToggleRow(
                    label = "Speed & Distance",
                    valueText = if (useMetric) "Metric (km/h, km)" else "Imperial (mph, mi)",
                    valueColor = TrackProTheme.colors.accentCyan,
                    buttonText = if (useMetric) "Use mph" else "Use km/h",
                    isActive = true,
                    onClick = { app.setMetricUnits(!useMetric) }
                )
            }

            // --- Section: System ---
            SectionLabel("Application")
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SettingsInfoRow(label = "App Version", value = "1.0.4-PRO")
                    SettingsInfoRow(label = "Database Status", value = "Connected")
                    SettingsInfoRow(label = "Map & Track Data", value = "© OpenStreetMap contributors")
                }
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

/**
 * A labeled setting with a value line on the left and a single toggle action on the
 * right. The three toggles on this screen (GPS source, theme, units) all used to
 * hand-roll this same Row+Button block.
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    valueText: String,
    valueColor: Color,
    buttonText: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label.uppercase(), style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(valueText, style = TrackProType.body, color = valueColor)
        }
        ToggleChip(
            text = buttonText,
            selected = isActive,
            onClick = onClick,
            accent = TrackProTheme.colors.accentCyan
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TrackProType.body, color = TrackProTheme.colors.textMuted)
        Text(value, style = TrackProType.body, color = TrackProTheme.colors.textPrimary)
    }
}
