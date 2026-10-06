package com.example.trackpro.screens.listViewScreens.listItems

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.trackpro.components.specLabel
import com.example.trackpro.theme.marking
import com.example.trackpro.components.SectionTitle
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.Bezel
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
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.theme.markingDim
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellSize
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.screens.garage.CarBackupStrip
import com.example.trackpro.screens.garage.CarBests
import com.example.trackpro.screens.garage.CarDeleteRow
import com.example.trackpro.screens.garage.CarPhotoSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
fun CarViewScreen(
    vehicleId: Long,
    onBack: () -> Unit,
    onSignIn: () -> Unit = {},
    onOpenTrack: (Long) -> Unit = {},
) {
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

    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()

    // The bar now wraps both states - previously it only existed in the loaded branch,
    // so the screen had no header (and no back affordance) while loading.
    ScreenScaffold(
        title = stringResource(R.string.car_title),
        onBack = onBack,
        accent = TrackProTheme.colors.accent,
        contentScrolled = scrolled
    ) { contentPadding ->
        if (vehicleInfo == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = TrackProTheme.colors.accent,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.car_loading), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                }
            }
        } else {
            val vehicle = vehicleInfo!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = Spacing.xl)
            ) {
                item(key = "photo") { CarPhotoSection(vehicle) }

                item(key = "hero") {
                    Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg)) {
                        Text(
                            text = "${vehicle.manufacturer} ${vehicle.model}",
                            style = TrackProType.titleLarge,
                            color = TrackProTheme.colors.marking
                        )
                        Text(
                            text = listOfNotNull(
                                vehicle.year.takeIf { it > 0 }?.toString(),
                                vehicle.engineType.takeIf { it.isNotBlank() }?.let { specLabel(it) },
                                vehicle.drivetrain.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                        Spacer(Modifier.height(Spacing.md))
                        CarBackupStrip(vehicle, onSignIn = onSignIn)
                    }
                }

                item(key = "bests") { CarBests(vehicle.vehicleId, onOpenTrack = onOpenTrack) }

                item(key = "performance") {
                    SectionTitle(stringResource(R.string.car_performance))
                    Column(Modifier.padding(horizontal = Spacing.gutter)) {
                        PaddockCard {
                            Row(Modifier.fillMaxWidth()) {
                                StatCell(label = stringResource(R.string.car_power), value = "${vehicle.horsepower}", unit = "hp", modifier = Modifier.weight(1f))
                                StatCell(label = stringResource(R.string.car_torque), value = vehicle.torque?.toString() ?: "—", unit = "Nm", modifier = Modifier.weight(1f))
                                StatCell(label = stringResource(R.string.car_weight), value = "${vehicle.weight.toInt()}", unit = "kg", modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(Spacing.lg))
                            Row(Modifier.fillMaxWidth()) {
                                StatCell(
                                    label = stringResource(R.string.car_top_speed),
                                    value = vehicle.topSpeed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "—",
                                    unit = UnitFormatter.speedUnitLabel(useMetric).lowercase(),
                                    modifier = Modifier.weight(1f)
                                )
                                StatCell(label = "0–100", value = vehicle.acceleration?.toString() ?: "—", unit = "s", modifier = Modifier.weight(1f))
                                StatCell(label = stringResource(R.string.car_drivetrain), value = vehicle.drivetrain.ifBlank { "—" }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                item(key = "specs") {
                    SectionTitle(stringResource(R.string.car_specs))
                    Column(Modifier.padding(horizontal = Spacing.gutter)) {
                        PaddockCard(padding = 0.dp) {
                            val specs = listOfNotNull(
                                stringResource(R.string.car_engine) to specLabel(vehicle.engineType),
                                stringResource(R.string.car_transmission) to specLabel(vehicle.transmission),
                                stringResource(R.string.car_fuel) to specLabel(vehicle.fuelType),
                                vehicle.fuelCapacity?.let { stringResource(R.string.car_fuel_capacity) to "$it L" },
                                vehicle.suspensionType?.let { stringResource(R.string.car_suspension) to specLabel(it) },
                                stringResource(R.string.car_tyres) to specLabel(vehicle.tireType),
                            )
                            specs.forEachIndexed { i, (label, value) ->
                                if (i > 0) Bezel(Modifier.padding(horizontal = Spacing.lg))
                                VehicleInfoRow(label, value)
                            }
                        }
                    }
                }

                item(key = "delete") { CarDeleteRow(vehicle, onDeleted = onBack) }
            }
        }
    }
}

@Composable
private fun VehicleInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TrackProType.body, color = TrackProTheme.colors.markingDim)
        Text(value.ifBlank { "—" }, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
    }
}

@Preview(
    showBackground = true,
)
@Composable
fun PreviewCarViewScreen()
{
    CarViewScreen(
        vehicleId = 1,
        onBack = {}
    )

}
