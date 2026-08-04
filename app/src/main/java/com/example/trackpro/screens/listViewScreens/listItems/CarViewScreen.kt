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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun CarViewScreen(vehicleId: Long) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
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
            .background(TrackProTheme.colors.bgDeep)
    ) {
        if (vehicleInfo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = TrackProTheme.colors.accent,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Loading vehicle", style = TrackProType.label, color = TrackProTheme.colors.textFaint)
                }
            }
        } else {
            val vehicle = vehicleInfo!!
            Column(modifier = Modifier.fillMaxSize()) {

                AppTopBar(title = "Vehicle Profile", accent = TrackProTheme.colors.accent)

                LazyColumn(modifier = Modifier.fillMaxSize()) {

                    // ── Hero ──────────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TrackProTheme.colors.bgCard)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        ) {
                            Text(
                                text = "${vehicle.manufacturer} ${vehicle.model}",
                                style = TrackProType.titleLarge,
                                color = TrackProTheme.colors.textPrimary
                            )
                            Text(
                                text = "${vehicle.year}",
                                style = TrackProType.body,
                                color = TrackProTheme.colors.textMuted
                            )
                        }
                        HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                    }

                    // ── Performance stats ─────────────────
                    item {
                        SectionLabel("Performance", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TrackProTheme.colors.bgCard)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCell(label = "Power", value = "${vehicle.horsepower}", unit = "hp", size = StatCellSize.Large, horizontalAlignment = Alignment.CenterHorizontally)
                            StatCell(label = "Torque", value = vehicle.torque?.toString() ?: "—", unit = "Nm", size = StatCellSize.Large, horizontalAlignment = Alignment.CenterHorizontally)
                            StatCell(label = "Weight", value = "${vehicle.weight}", unit = "kg", size = StatCellSize.Large, horizontalAlignment = Alignment.CenterHorizontally)
                        }
                        HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                    }

                    // ── Speed stats ───────────────────────
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(TrackProTheme.colors.bgElevated)
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCell(
                                label = "Top Speed",
                                value = vehicle.topSpeed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "—",
                                unit = UnitFormatter.speedUnitLabel(useMetric).lowercase(),
                                size = StatCellSize.Large,
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                            StatCell(
                                label = "0–100",
                                value = vehicle.acceleration?.toString() ?: "—",
                                unit = "sec",
                                size = StatCellSize.Large,
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                            StatCell(
                                label = "Drivetrain",
                                value = vehicle.drivetrain,
                                size = StatCellSize.Large,
                                horizontalAlignment = Alignment.CenterHorizontally
                            )
                        }
                        HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                    }

                    // ── Mechanical details ────────────────
                    item {
                        SectionLabel("Mechanical", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                    }
                    item {
                        VehicleInfoRow("Engine Type", vehicle.engineType,
                            TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted, TrackProTheme.colors.sectorLine, TrackProTheme.colors.bgCard)
                    }
                    item {
                        VehicleInfoRow("Transmission", vehicle.transmission,
                            TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted, TrackProTheme.colors.sectorLine, TrackProTheme.colors.bgCard)
                    }
                    item {
                        VehicleInfoRow("Fuel Type", vehicle.fuelType,
                            TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted, TrackProTheme.colors.sectorLine, TrackProTheme.colors.bgCard)
                    }
                    vehicle.fuelCapacity?.let {
                        item {
                            VehicleInfoRow("Fuel Capacity", "$it L",
                                TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted, TrackProTheme.colors.sectorLine, TrackProTheme.colors.bgCard)
                        }
                    }
                    vehicle.suspensionType?.let {
                        item {
                            VehicleInfoRow("Suspension", it,
                                TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted, TrackProTheme.colors.sectorLine, TrackProTheme.colors.bgCard)
                        }
                    }

                    // ── Tyres ─────────────────────────────
                    item {
                        HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
                        SectionLabel("Tyres & Setup", modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
                    }
                    item {
                        VehicleInfoRow("Tyre Type", vehicle.tireType,
                            TrackProTheme.colors.textPrimary, TrackProTheme.colors.textMuted, TrackProTheme.colors.sectorLine, TrackProTheme.colors.bgCard)
                    }

                    item { Spacer(Modifier.height(Spacing.xl)) }
                }
            }
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
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label.uppercase(), style = TrackProType.label, color = textMuted)
            Text(value, style = TrackProType.body, color = textPrimary)
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
