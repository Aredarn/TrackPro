package com.example.trackpro.screens.vehicleScreens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.theme.Spacing
import com.example.trackpro.managerClasses.JsonReader.loadJsonOptions
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import kotlinx.coroutines.launch

@Composable
fun CarCreationScreen(
    database: ESPDatabase
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            AppTopBar(title = "Vehicle Setup", accent = TrackProTheme.colors.accentAmber)

            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth()
                    .padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {

                    SectionLabel("Basic Info (Required)", modifier = Modifier.padding(vertical = Spacing.sm))
                    CustomTextField("Manufacturer", manufacturer, leadingIcon = Icons.Default.Business, accent = TrackProTheme.colors.accentAmber) { manufacturer = it }
                    CustomTextField("Model", model, leadingIcon = Icons.Default.DirectionsCar, accent = TrackProTheme.colors.accentAmber) { model = it }
                    CustomTextField("Year", year, leadingIcon = Icons.Default.Event, accent = TrackProTheme.colors.accentAmber) { year = it }

                    SectionLabel("Performance", modifier = Modifier.padding(vertical = Spacing.sm))
                    CustomTextField("Horsepower", horsepower, true, Icons.Default.FlashOn, accent = TrackProTheme.colors.accentAmber) { horsepower = it }
                    CustomTextField("Torque (Nm)", torque, true, Icons.Default.Settings, accent = TrackProTheme.colors.accentAmber) { torque = it }
                    CustomTextField("Weight (kg)", weight, true, Icons.Default.FitnessCenter, accent = TrackProTheme.colors.accentAmber) { weight = it }
                    CustomTextField("Top Speed (${UnitFormatter.speedUnitLabel(useMetric)})", topSpeed, true, Icons.Default.Speed, accent = TrackProTheme.colors.accentAmber) { topSpeed = it }
                    CustomTextField(
                        if (useMetric) "0-100 KM/H (s)" else "0-60 MPH (s)",
                        acceleration, true, Icons.Default.Timer, accent = TrackProTheme.colors.accentAmber
                    ) { acceleration = it }
                    CustomTextField("Fuel Capacity (L)", fuelCapacity, true, Icons.Default.LocalGasStation, accent = TrackProTheme.colors.accentAmber) { fuelCapacity = it }

                    SectionLabel("Configuration", modifier = Modifier.padding(vertical = Spacing.sm))
                    AppDropdownField("Engine Type", jsonOptions.engineTypes, selectedEngineType, { it }, { selectedEngineType = it }, accent = TrackProTheme.colors.accentAmber)
                    AppDropdownField("Drivetrain", jsonOptions.drivetrains, selectedDrivetrain, { it }, { selectedDrivetrain = it }, accent = TrackProTheme.colors.accentAmber)
                    AppDropdownField("Fuel Type", jsonOptions.fuelTypes, selectedFuelType, { it }, { selectedFuelType = it }, accent = TrackProTheme.colors.accentAmber)
                    AppDropdownField("Tire Type", jsonOptions.tireTypes, selectedTireType, { it }, { selectedTireType = it }, accent = TrackProTheme.colors.accentAmber)
                    AppDropdownField("Transmission", jsonOptions.transmissions, selectedTransmission, { it }, { selectedTransmission = it }, accent = TrackProTheme.colors.accentAmber)
                    AppDropdownField("Suspension", jsonOptions.suspensionTypes, selectedSuspensionType, { it }, { selectedSuspensionType = it }, accent = TrackProTheme.colors.accentAmber)

                    Spacer(modifier = Modifier.height(Spacing.md))

                    PrimaryButton(
                        text = "Save Vehicle",
                        onClick = {
                            if (manufacturer.isBlank() || model.isBlank() || year.isBlank()) {
                                Toast.makeText(context, "Fill in required fields.", Toast.LENGTH_SHORT).show()
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

                            Toast.makeText(context, "Vehicle saved successfully.", Toast.LENGTH_SHORT).show()
                        },
                        accent = TrackProTheme.colors.deltaGood,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
