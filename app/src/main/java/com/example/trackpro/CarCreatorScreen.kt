package com.example.trackpro

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.ExtrasForUI.CustomTextField
import com.example.trackpro.ExtrasForUI.DropdownMenuField
import com.example.trackpro.ManagerClasses.JsonReader.loadJsonOptions
import kotlinx.coroutines.launch


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

    val showSection = remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🏎️ Vehicle Setup",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AnimatedVisibility(visible = showSection.value) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(10.dp),
                    modifier = Modifier.fillMaxWidth()
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
                        CustomTextField("Top Speed (km/h)", topSpeed, true, Icons.Default.Speed) { topSpeed = it }
                        CustomTextField("0-100 km/h (s)", acceleration, true, Icons.Default.Timer) { acceleration = it }
                        CustomTextField("Fuel Capacity (L)", fuelCapacity, true, Icons.Default.LocalGasStation) { fuelCapacity = it }

                        SectionTitle("Configuration")
                        DropdownMenuField("Engine Type", jsonOptions.engineTypes, selectedEngineType, Color.White) { selectedEngineType = it }
                        DropdownMenuField("Drivetrain", jsonOptions.drivetrains, selectedDrivetrain, Color.White) { selectedDrivetrain = it }
                        DropdownMenuField("Fuel Type", jsonOptions.fuelTypes, selectedFuelType, Color.White) { selectedFuelType = it }
                        DropdownMenuField("Tire Type", jsonOptions.tireTypes, selectedTireType, Color.White) { selectedTireType = it }
                        DropdownMenuField("Transmission", jsonOptions.transmissions, selectedTransmission, Color.White) { selectedTransmission = it }
                        DropdownMenuField("Suspension", jsonOptions.suspensionTypes, selectedSuspensionType, Color.White) { selectedSuspensionType = it }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (manufacturer.isBlank() || model.isBlank() || year.isBlank()) {
                                    Toast.makeText(context, "⚠️ Fill in required fields.", Toast.LENGTH_SHORT).show()
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
                                    topSpeed = topSpeed.toDoubleOrNull(),
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

                                Toast.makeText(context, "🚀 Vehicle saved successfully!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Save Vehicle", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
        text = title,
        color = Color(0xFFB0BEC5),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
