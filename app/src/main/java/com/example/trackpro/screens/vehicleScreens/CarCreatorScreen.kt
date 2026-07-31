package com.example.trackpro.screens.vehicleScreens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.extrasForUI.CustomTextField
import com.example.trackpro.extrasForUI.DropdownMenuField
import com.example.trackpro.extrasForUI.TrackProTheme
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

            // ── Top bar ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.accentAmber)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "● VEHICLE SETUP",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            }

            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgCard, RoundedCornerShape(12.dp))
                        .border(1.dp, TrackProTheme.colors.sectorLine, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {

                        SectionTitle("Basic Info (Required)")
                        CustomTextField("Manufacturer", manufacturer, leadingIcon = Icons.Default.Business) { manufacturer = it }
                        CustomTextField("Model", model, leadingIcon = Icons.Default.DirectionsCar) { model = it }
                        CustomTextField("Year", year, leadingIcon = Icons.Default.Event) { year = it }

                        SectionTitle("Performance")
                        CustomTextField("Horsepower", horsepower, true, Icons.Default.FlashOn) { horsepower = it }
                        CustomTextField("Torque (Nm)", torque, true, Icons.Default.Settings) { torque = it }
                        CustomTextField("Weight (kg)", weight, true, Icons.Default.FitnessCenter) { weight = it }
                        CustomTextField("Top Speed (${UnitFormatter.speedUnitLabel(useMetric)})", topSpeed, true, Icons.Default.Speed) { topSpeed = it }
                        CustomTextField(
                            if (useMetric) "0-100 KM/H (s)" else "0-60 MPH (s)",
                            acceleration, true, Icons.Default.Timer
                        ) { acceleration = it }
                        CustomTextField("Fuel Capacity (L)", fuelCapacity, true, Icons.Default.LocalGasStation) { fuelCapacity = it }

                        SectionTitle("Configuration")
                        DropdownMenuField("Engine Type", jsonOptions.engineTypes, selectedEngineType) { selectedEngineType = it }
                        DropdownMenuField("Drivetrain", jsonOptions.drivetrains, selectedDrivetrain) { selectedDrivetrain = it }
                        DropdownMenuField("Fuel Type", jsonOptions.fuelTypes, selectedFuelType) { selectedFuelType = it }
                        DropdownMenuField("Tire Type", jsonOptions.tireTypes, selectedTireType) { selectedTireType = it }
                        DropdownMenuField("Transmission", jsonOptions.transmissions, selectedTransmission) { selectedTransmission = it }
                        DropdownMenuField("Suspension", jsonOptions.suspensionTypes, selectedSuspensionType) { selectedSuspensionType = it }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (manufacturer.isBlank() || model.isBlank() || year.isBlank()) {
                                    Toast.makeText(context, "Fill in required fields.", Toast.LENGTH_SHORT).show()
                                    return@Button
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TrackProTheme.colors.deltaGood),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "SAVE VEHICLE",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        color = TrackProTheme.colors.textMuted,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
