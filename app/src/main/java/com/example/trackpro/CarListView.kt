package com.example.trackpro

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.TabRowDefaults.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.ManagerClasses.SessionManager
import com.example.trackpro.ViewModels.VehicleFULLViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarListView : ComponentActivity()
{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent{

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarListScreen(navController: NavController, viewModel: VehicleFULLViewModel)
{
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }

    val vehicles by viewModel.vehicles.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold (
        topBar = { TopAppBar(title = { Text("Your saved vehicles") }) }
    )
    { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues))
        {
            if(vehicles.isEmpty())
            {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ){
                    Text("No vehicles available", fontSize = 22.sp, color = Color.Gray)
                }
            }
            else
            {
                LazyColumn(modifier = Modifier.padding(16.dp))
                {
                    items(vehicles) { vehicle ->
                        TrackCard(
                            vehicle = vehicle,
                            navController = navController,
                            onDelete = { vehicleToDelete ->
                                scope.launch(Dispatchers.IO)
                                {
                                    DeleteVehicle(context,database, vehicleToDelete.vehicleId)
                                }
                                Toast.makeText(context, "🚀 Vehicle deleted successfully!", Toast.LENGTH_SHORT).show()

                            }
                        )

                    }
                }

            }
        }
    }
}
@Composable
fun TrackCard(
    vehicle: VehicleInformationData,
    navController: NavController,
    onDelete: (VehicleInformationData) -> Unit  // Add this callback to handle deletion
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable {
                navController.navigate("vehicle/${vehicle.vehicleId}")
            },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(0.dp),  // No shadow
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Header: Model and Manufacturer
                Text(
                    text = "${vehicle.model} ${vehicle.manufacturer}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 28.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Year: ${vehicle.year}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${vehicle.horsepower} Hp",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Horsepower",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${vehicle.torque} Nm",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Torque",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Delete Button (red X) at the top-right corner
            IconButton(
                onClick = { onDelete(vehicle) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Vehicle",
                    tint = Color.Red
                )
            }
        }
    }
}


suspend fun DeleteVehicle(context: Context, database: ESPDatabase, vehicleId: Long)
{
    database.vehicleInformationDAO().deleteVehicle(vehicleId)

}


