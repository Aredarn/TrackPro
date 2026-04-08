package com.example.trackpro.screens.listViewScreens.listItems

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.theme.TrackProColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun CarViewScreen(vehicleId: Long) {
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    var vehicleInfo by remember { mutableStateOf<VehicleInformationData?>(null) }


    LaunchedEffect(vehicleId) {
        withContext(Dispatchers.IO) {
            database.vehicleInformationDAO().getVehicle(vehicleId).collect { vehicle ->
                vehicleInfo = vehicle
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        if (vehicleInfo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = TrackProColors.AccentAmber,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("LOADING VEHICLE", color = TrackProColors.TextMuted,
                        fontSize = 10.sp, letterSpacing = 3.sp)
                }
            }
        } else {
            val vehicle = vehicleInfo!!
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Top bar ───────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProColors.AccentAmber)
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "● VEHICLE PROFILE",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    // ── Hero ──────────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TrackProColors.BgCard)
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            Text(
                                text = "${vehicle.manufacturer} ${vehicle.model}",
                                color = TrackProColors.TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "${vehicle.year}",
                                color = TrackProColors.TextMuted,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)
                    }

                    // ── Performance stats ─────────────────
                    item {
                        VehicleSectionHeader("PERFORMANCE", TrackProColors.TextMuted, TrackProColors.SectorLine)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TrackProColors.BgCard)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            VehicleStatCol(
                                label = "POWER",
                                value = "${vehicle.horsepower}",
                                unit = "hp",
                                accentColor = TrackProColors.AccentAmber,
                                textMuted = TrackProColors.TextMuted
                            )
                            VehicleStatCol(
                                label = "TORQUE",
                                value = vehicle.torque?.toString() ?: "—",
                                unit = "Nm",
                                accentColor = TrackProColors.AccentAmber,
                                textMuted = TrackProColors.TextMuted
                            )
                            VehicleStatCol(
                                label = "WEIGHT",
                                value = "${vehicle.weight}",
                                unit = "kg",
                                accentColor = TrackProColors.AccentAmber,
                                textMuted = TrackProColors.TextMuted
                            )
                        }
                        HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)
                    }

                    // ── Speed stats ───────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TrackProColors.BgElevated)
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            VehicleStatCol(
                                label = "TOP SPEED",
                                value = vehicle.topSpeed?.toString() ?: "—",
                                unit = "km/h",
                                accentColor = TrackProColors.AccentAmber,
                                textMuted = TrackProColors.TextMuted
                            )
                            VehicleStatCol(
                                label = "0–100",
                                value = vehicle.acceleration?.toString() ?: "—",
                                unit = "sec",
                                accentColor = TrackProColors.AccentAmber,
                                textMuted = TrackProColors.TextMuted
                            )
                            VehicleStatCol(
                                label = "DRIVETRAIN",
                                value = vehicle.drivetrain,
                                unit = "",
                                accentColor = TrackProColors.AccentAmber,
                                textMuted = TrackProColors.TextMuted
                            )
                        }
                        HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)
                    }

                    // ── Mechanical details ────────────────
                    item {
                        VehicleSectionHeader("MECHANICAL", TrackProColors.TextMuted, TrackProColors.SectorLine)
                    }
                    item {
                        VehicleInfoRow("ENGINE TYPE", vehicle.engineType,
                            TrackProColors.TextPrimary, TrackProColors.TextMuted, TrackProColors.SectorLine, TrackProColors.BgCard)
                    }
                    item {
                        VehicleInfoRow("TRANSMISSION", vehicle.transmission,
                            TrackProColors.TextPrimary, TrackProColors.TextMuted, TrackProColors.SectorLine, TrackProColors.BgCard)
                    }
                    item {
                        VehicleInfoRow("FUEL TYPE", vehicle.fuelType,
                            TrackProColors.TextPrimary, TrackProColors.TextMuted, TrackProColors.SectorLine, TrackProColors.BgCard)
                    }
                    vehicle.fuelCapacity?.let {
                        item {
                            VehicleInfoRow("FUEL CAPACITY", "$it L",
                                TrackProColors.TextPrimary, TrackProColors.TextMuted, TrackProColors.SectorLine, TrackProColors.BgCard)
                        }
                    }
                    vehicle.suspensionType?.let {
                        item {
                            VehicleInfoRow("SUSPENSION", it,
                                TrackProColors.TextPrimary, TrackProColors.TextMuted, TrackProColors.SectorLine, TrackProColors.BgCard)
                        }
                    }

                    // ── Tyres ─────────────────────────────
                    item {
                        HorizontalDivider(color = TrackProColors.SectorLine, thickness = 1.dp)
                        VehicleSectionHeader("TYRES & SETUP", TrackProColors.TextMuted, TrackProColors.SectorLine)
                    }
                    item {
                        VehicleInfoRow("TYRE TYPE", vehicle.tireType,
                            TrackProColors.TextPrimary, TrackProColors.TextMuted, TrackProColors.SectorLine, TrackProColors.BgCard)
                    }

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun VehicleSectionHeader(title: String, textMuted: Color, sectorLine: Color) {
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
private fun VehicleStatCol(
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    textMuted: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = textMuted, fontSize = 9.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Text(value, color = accentColor, fontSize = 26.sp, fontWeight = FontWeight.Black)
        if (unit.isNotEmpty()) {
            Text(unit, color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VehicleInfoRow(
    label: String,
    value: String,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color,
    bgCard: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = textMuted, fontSize = 10.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(value, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = sectorLine, thickness = 1.dp)
    }
}
@Preview(
    showBackground = true,
)
@Composable
fun PreviewCarViewScreen()
{
    CarViewScreen(
        vehicleId = 1
    )

}
