package com.example.trackpro.screens.vehicleScreens

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import com.example.trackpro.components.specLabelMap
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.extrasForUI.AppDropdownField
import com.example.trackpro.extrasForUI.CustomTextField
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppCard
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.theme.Spacing
import com.example.trackpro.managerClasses.JsonReader.loadJsonOptions
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import kotlinx.coroutines.launch

@Composable
fun CarCreationScreen(
    database: ESPDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
    val jsonOptions = remember { loadJsonOptions(context) }
    val coroutineScope = rememberCoroutineScope()

    var manufacturer by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var horsepower by remember { mutableStateOf("") }
    var torque by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var topSpeed by remember { mutableStateOf("") }
    var acceleration by remember { mutableStateOf("") }
    var fuelCapacity by remember { mutableStateOf("") }

    var selectedEngineType by remember { mutableStateOf(jsonOptions.engineTypes.firstOrNull() ?: "") }
    var selectedDrivetrain by remember { mutableStateOf(jsonOptions.drivetrains.firstOrNull() ?: "") }
    var selectedFuelType by remember { mutableStateOf(jsonOptions.fuelTypes.firstOrNull() ?: "") }
    var selectedTireType by remember { mutableStateOf(jsonOptions.tireTypes.firstOrNull() ?: "") }
    var selectedTransmission by remember { mutableStateOf(jsonOptions.transmissions.firstOrNull() ?: "") }
    var selectedSuspensionType by remember { mutableStateOf(jsonOptions.suspensionTypes.firstOrNull() ?: "") }

    val scrollState = rememberScrollState()

    val scrolled by scrollState.isScrolledUnderChrome()

    ScreenScaffold(
        title = stringResource(R.string.garage_add_car),
        onBack = onBack,
        accent = TrackProTheme.colors.accent,
        contentScrolled = scrolled
    ) { contentPadding ->
    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
            .fillMaxWidth()
            .padding(
                top = contentPadding.calculateTopPadding() + Spacing.md,
                start = Spacing.md,
                end = Spacing.md,
                bottom = Spacing.md
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            AppCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {

                SectionLabel(stringResource(R.string.creator_basics), modifier = Modifier.padding(vertical = Spacing.sm))
                CustomTextField(stringResource(R.string.creator_manufacturer), manufacturer, leadingIcon = Icons.Default.Business) { manufacturer = it }
                CustomTextField(stringResource(R.string.creator_model), model, leadingIcon = Icons.Default.DirectionsCar) { model = it }
                CustomTextField(stringResource(R.string.creator_year), year, leadingIcon = Icons.Default.Event) { year = it }

                SectionLabel(stringResource(R.string.car_performance), modifier = Modifier.padding(vertical = Spacing.sm))
                CustomTextField(stringResource(R.string.creator_horsepower), horsepower, true, Icons.Default.FlashOn) { horsepower = it }
                CustomTextField(stringResource(R.string.creator_torque), torque, true, Icons.Default.Settings) { torque = it }
                CustomTextField(stringResource(R.string.creator_weight), weight, true, Icons.Default.FitnessCenter) { weight = it }
                CustomTextField(stringResource(R.string.creator_top_speed, UnitFormatter.speedUnitLabel(useMetric)), topSpeed, true, Icons.Default.Speed) { topSpeed = it }
                CustomTextField(
                    if (useMetric) "0-100 km/h (s)" else "0-60 mph (s)",
                    acceleration, true, Icons.Default.Timer
                ) { acceleration = it }
                CustomTextField(stringResource(R.string.creator_fuel_capacity), fuelCapacity, true, Icons.Default.LocalGasStation) { fuelCapacity = it }

                SectionLabel(stringResource(R.string.creator_configuration), modifier = Modifier.padding(vertical = Spacing.sm))
                val spec = specLabelMap()
                val shown: (String) -> String = { spec[it] ?: it }
                AppDropdownField(stringResource(R.string.creator_engine_type), jsonOptions.engineTypes, shown(selectedEngineType), shown, { selectedEngineType = it })
                AppDropdownField(stringResource(R.string.car_drivetrain), jsonOptions.drivetrains, shown(selectedDrivetrain), shown, { selectedDrivetrain = it })
                AppDropdownField(stringResource(R.string.creator_fuel_type), jsonOptions.fuelTypes, shown(selectedFuelType), shown, { selectedFuelType = it })
                AppDropdownField(stringResource(R.string.car_tyres), jsonOptions.tireTypes, shown(selectedTireType), shown, { selectedTireType = it })
                AppDropdownField(stringResource(R.string.car_transmission), jsonOptions.transmissions, shown(selectedTransmission), shown, { selectedTransmission = it })
                AppDropdownField(stringResource(R.string.car_suspension), jsonOptions.suspensionTypes, shown(selectedSuspensionType), shown, { selectedSuspensionType = it })

                Spacer(modifier = Modifier.height(Spacing.md))

                PrimaryButton(
                    text = stringResource(R.string.creator_save),
                    onClick = {
                        if (manufacturer.isBlank() || model.isBlank() || year.isBlank()) {
                            Toast.makeText(context, context.getString(R.string.creator_missing), Toast.LENGTH_SHORT).show()
                            return@PrimaryButton
                        }

                        val vehicle = VehicleInformationData(
                            manufacturer = manufacturer,
                            model = model,
                            year = year.toIntOrNull() ?: 0,
                            engineType = selectedEngineType,
                            horsepower = horsepower.toIntOrNull() ?: 0,
                            torque = torque.toIntOrNull(),
                            weight = weight.toDoubleOrNull() ?: 0.0,
                            // Stored canonically in km/h regardless of the unit the
                            // user entered it in, matching every other speed value.
                            topSpeed = topSpeed.toDoubleOrNull()?.let { UnitFormatter.convertSpeedToKmh(it, useMetric) },
                            acceleration = acceleration.toDoubleOrNull(),
                            drivetrain = selectedDrivetrain,
                            fuelType = selectedFuelType,
                            tireType = selectedTireType,
                            fuelCapacity = fuelCapacity.toDoubleOrNull(),
                            transmission = selectedTransmission,
                            suspensionType = selectedSuspensionType
                        )

                        coroutineScope.launch {
                            database.vehicleInformationDAO().insertVehicle(vehicle)
                        }

                        Toast.makeText(context, context.getString(R.string.creator_saved), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
