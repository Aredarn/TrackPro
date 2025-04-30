package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.DataClasses.VehicleInformationData
import com.example.trackpro.ViewModels.VehicleFULLViewModel
import com.example.trackpro.ViewModels.VehicleViewModel

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
    val vehicles by viewModel.vehicles.collectAsState()


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
                        TrackCard(vehicle,navController)
                    }
                }

            }
        }
    }
}


@Composable
fun TrackCard(vehicle: VehicleInformationData, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable {
                navController.navigate("/${vehicle.vehicleId}")
            },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: Model and Manufacturer
            Text(
                text = "${vehicle.model} ${vehicle.manufacturer}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Year: ${vehicle.year}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(vertical = 8.dp, horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Horsepower: ${vehicle.horsepower} Hp  •  Torque: ${vehicle.torque} Nm",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}



