package com.example.trackpro

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.DataClasses.VehicleInformationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CarScreen : ComponentActivity()
{

}


@Composable
fun CarViewScreen(onBack: () -> Unit, vehicleId: Long)
{
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var vehicleInfo by remember { mutableStateOf<VehicleInformationData?>(null) }

    LaunchedEffect(vehicleId) {
        scope.launch(Dispatchers.IO)
        {
            database.vehicleInformationDAO().getVehicle(vehicleId).collect { vehicle ->
                vehicleInfo = vehicle
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F7F7) // very light gray background
    ) {
        Column {
            TopAppBar(
                backgroundColor = Color.White,
                elevation = 4.dp,
                title = {
                    Text(
                        text = "Vehicle Info",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            vehicleInfo?.let { vehicle ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    item { InfoRow("Manufacturer", vehicle.manufacturer) }
                    item { InfoRow("Model", vehicle.model) }
                    item { InfoRow("Year", vehicle.year.toString()) }
                    item { InfoRow("Engine", vehicle.engineType) }
                    item { InfoRow("Horsepower", "${vehicle.horsepower} HP") }
                    vehicle.torque?.let { item { InfoRow("Torque", "$it Nm") } }
                    item { InfoRow("Weight", "${vehicle.weight} kg") }
                    vehicle.topSpeed?.let { item { InfoRow("Top Speed", "$it km/h") } }
                    vehicle.acceleration?.let { item { InfoRow("0-100 km/h", "$it sec") } }
                    item { InfoRow("Drivetrain", vehicle.drivetrain) }
                    item { InfoRow("Fuel Type", vehicle.fuelType) }
                    item { InfoRow("Tires", vehicle.tireType) }
                    vehicle.fuelCapacity?.let { item { InfoRow("Fuel Capacity", "$it L") } }
                    item { InfoRow("Transmission", vehicle.transmission) }
                    vehicle.suspensionType?.let { item { InfoRow("Suspension", it) } }
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                color = Color.Gray,
                fontSize = 14.sp
            )
            Text(
                text = value,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
                fontSize = 14.sp
            )
        }
        Divider(modifier = Modifier.padding(top = 8.dp), color = Color.LightGray, thickness = 0.5.dp)
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
