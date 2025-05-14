package com.example.trackpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.trackpro.ExtrasForUI.DropdownMenuFieldMulti
import com.example.trackpro.ExtrasForUI.TrackDropdownMenu
import com.example.trackpro.ViewModels.TrackViewModel
import com.example.trackpro.ViewModels.TrackViewModelFactory
import com.example.trackpro.ViewModels.VehicleViewModel
import com.example.trackpro.ViewModels.VehicleViewModelFactory

class TrackVehicleSelector : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
        }
    }
}

@Composable
fun TrackVehicleSelectorScreenWrapper(navController: NavController) {
    val context = LocalContext.current
    val trackViewModel: TrackViewModel = viewModel(factory = TrackViewModelFactory(context))

    val database = ESPDatabase.getInstance(context) // still needed for VehicleViewModel

    TrackVehicleSelectorScreen(
        database = database,
        trackViewModel = trackViewModel,
        navController = navController
    )
}



@Composable
fun TrackVehicleSelectorScreen(
    database: ESPDatabase,
    trackViewModel: TrackViewModel,
    navController:NavController
) {
    val vehicleViewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val selectedVehicle by rememberSaveable { mutableStateOf("") }
    var selectedTrackName by remember { mutableStateOf("") }
    var selectedVehicleId by rememberSaveable { mutableLongStateOf(-1L) }
    var selectedTrackId by rememberSaveable { mutableLongStateOf(-1L) }

    val tracks by trackViewModel.tracks.collectAsState()

    LaunchedEffect(Unit) {
        vehicleViewModel.fetchVehicles()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth()
        )
        {
            TrackDropdownMenu(
                label = "Select track",
                tracks = tracks,
                selectedTrackName = selectedTrackName,
                onTrackSelected = {
                    selectedTrackName = tracks.firstOrNull { track -> track.trackId == it }?.trackName ?: ""
                    selectedTrackId = tracks.firstOrNull {track -> track.trackId == it}?.trackId?:-1
                }
            )
        }

        Row(

        )
        {
            if (vehicles.isNotEmpty()) {
                DropdownMenuFieldMulti(
                    "Select car",
                    vehicles,
                    selectedVehicle
                ) { selectedVehicleId = it }
            } else {
                androidx.compose.material3.Text(text = "No vehicles available") // Show a message if no vehicles are found
            }
        }

        if (selectedVehicleId != -1L && selectedTrackId != -1L) {
            Button(onClick = {
                navController.navigate("timeattack/$selectedVehicleId/$selectedTrackId")
            }) {
                Text("Start Time Attack")
            }
        }


    }

}