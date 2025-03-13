package com.example.trackpro

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.ExtrasForUI.CustomTextField
import com.example.trackpro.ExtrasForUI.DropdownMenuField
import com.example.trackpro.ManagerClasses.JsonReader.loadJsonOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CarCreatorScreen : ComponentActivity()
{
    private lateinit var database: ESPDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = ESPDatabase.getInstance(applicationContext)

        setContent {
            CarCreationScreen(
             database = database,
             onBack = { finish()}
            )
        }

    }
}


@Composable
fun CarCreationScreen(
    database: ESPDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
            .background(Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF3C3C3C)))) // Dark Racing Theme
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Vehicle Survey",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = Color.White),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Manual Inputs
                    CustomTextField("Manufacturer", manufacturer) { manufacturer = it }
                    CustomTextField("Model", model) { model = it }
                    CustomTextField("Year", year, true) { year = it }
                    CustomTextField("Horsepower", horsepower, true) { horsepower = it }
                    CustomTextField("Torque (Nm)", torque, true) { torque = it }
                    CustomTextField("Weight (kg)", weight, true) { weight = it }

                    // Dropdowns
                    DropdownMenuField("Engine Type", jsonOptions.engineTypes, selectedEngineType) { selectedEngineType = it }
                    DropdownMenuField("Drivetrain", jsonOptions.drivetrains, selectedDrivetrain) { selectedDrivetrain = it }
                    DropdownMenuField("Fuel Type", jsonOptions.fuelTypes, selectedFuelType) { selectedFuelType = it }
                    DropdownMenuField("Tire Type", jsonOptions.tireTypes, selectedTireType) { selectedTireType = it }
                    DropdownMenuField("Transmission", jsonOptions.transmissions, selectedTransmission) { selectedTransmission = it }
                    DropdownMenuField("Suspension Type", jsonOptions.suspensionTypes, selectedSuspensionType) { selectedSuspensionType = it }

                    Spacer(modifier = Modifier.height(12.dp))

                    ElevatedButton(
                        onClick = {
                            val vehicle = VehicleInformationData(
                                manufacturer = manufacturer,
                                model = model,
                                year = year.toIntOrNull() ?: 0,
                                engineType = selectedEngineType,
                                horsepower = horsepower.toIntOrNull() ?: 0,
                                torque = torque.toIntOrNull(),
                                weight = weight.toDoubleOrNull() ?: 0.0,
                                topSpeed = topSpeed.toDoubleOrNull(),
                                acceleration = acceleration.toDoubleOrNull(),
                                drivetrain = selectedDrivetrain,
                                fuelType = selectedFuelType,
                                tireType = selectedTireType,
                                fuelCapacity = fuelCapacity.toDoubleOrNull(),
                                transmission = selectedTransmission,
                                suspensionType = selectedSuspensionType
                            )
                            Log.d("VehicleSurvey", vehicle.toString())


                            coroutineScope.launch {
                                database.vehicleInformationDAO().insertVehicle(vehicle)
                            }                            },
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF007BFF), contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}