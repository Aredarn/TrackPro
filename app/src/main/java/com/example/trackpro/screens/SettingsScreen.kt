package com.example.trackpro.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.theme.TrackProColors

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useExternal by app.useExternalGps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        // --- Header ---
        HeaderSection(onBack = onBack, title = "SETTINGS")

        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Section: Hardware & GPS ---
            SettingsSectionHeader(title = "HARDWARE & SENSORS")

            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GPS SOURCE",
                            color = TrackProColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (useExternal) "External ESP32 Module" else "Internal Phone GPS",
                            color = if (useExternal) TrackProColors.AccentRed else TrackProColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    // The Toggle Button
                    Button(
                        onClick = { app.useExternalGps.value = !useExternal },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (useExternal) TrackProColors.AccentRed else Color(0xFF1E2530)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (useExternal) "USE PHONE" else "USE ESP32",
                            color = if (useExternal) Color.Black else TrackProColors.TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // --- Section: Measurement ---
            SettingsSectionHeader(title = "UNITS")
            SettingsCard {
                //SettingsToggleRow(label = "Use Metric Units (km/h)", isActive = true) {}
            }

            // --- Section: System ---
            SettingsSectionHeader(title = "APPLICATION")
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingsInfoRow(label = "App Version", value = "1.0.4-PRO")
                    SettingsInfoRow(label = "Database Status", value = "Connected")
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = TrackProColors.TextMuted,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProColors.BgCard, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E2530), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TrackProColors.TextMuted, fontSize = 14.sp)
        Text(value, color = TrackProColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeaderSection(onBack: () -> Unit, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("← BACK", color = TrackProColors.AccentRed, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(title, color = TrackProColors.TextPrimary, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
    }
}