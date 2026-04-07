package com.example.trackpro.Screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.trackpro.ManagerClasses.ESPDatabase
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

    val database = ESPDatabase.Companion.getInstance(context) // still needed for VehicleViewModel

    TrackVehicleSelectorScreen(
        database = database,
        trackViewModel = trackViewModel,
        navController = navController
    )
}


// ── Design tokens (Consistent with your TrackBuilder) ──────────────────────────
private val BgDeep      = Color(0xFF080A0F)
private val BgCard      = Color(0xFF0E1117)
private val AccentRed   = Color(0xFFE8001C)
private val TextPrimary = Color(0xFFF0F2F5)
private val TextMuted   = Color(0xFF6B7280)

@Composable
fun TrackVehicleSelectorScreen(
    database: ESPDatabase,
    trackViewModel: TrackViewModel,
    navController: NavController
) {
    val vehicleViewModel: VehicleViewModel = viewModel(factory = VehicleViewModelFactory(database))
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val tracks by trackViewModel.tracks.collectAsState()

    // State
    var selectedTrackName by rememberSaveable { mutableStateOf("") }
    var selectedVehicleName by rememberSaveable { mutableStateOf("") }
    var selectedVehicleId by rememberSaveable { mutableLongStateOf(-1L) }
    var selectedTrackId by rememberSaveable { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        vehicleViewModel.fetchVehicles()
    }

    Box(modifier = Modifier.fillMaxSize().background(BgDeep)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Header ───────────────────────────────────────
            Text(
                "SESSION SETUP",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 32.dp, top = 16.dp)
            )

            // ── Track Selection Card ─────────────────────────
            SelectionCard(
                label = "CIRCUIT",
                title = if (selectedTrackName.isEmpty()) "Select Track" else selectedTrackName,
                isSet = selectedTrackId != -1L
            ) {
                TrackDropdownMenu(
                    label = "Choose Location",
                    tracks = tracks,
                    selectedTrackName = selectedTrackName,
                    onTrackSelected = { id ->
                        val track = tracks.find { it.trackId == id }
                        selectedTrackName = track?.trackName ?: ""
                        selectedTrackId = id
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Vehicle Selection Card ───────────────────────
            SelectionCard(
                label = "VEHICLE",
                title = if (selectedVehicleName.isEmpty()) "Select Vehicle" else selectedVehicleName,
                isSet = selectedVehicleId != -1L
            ) {
                if (vehicles.isNotEmpty()) {
                    DropdownMenuFieldMulti(
                        "Choose Machine",
                        vehicles,
                        selectedVehicleName
                    ) { id ->
                        selectedVehicleId = id
                        selectedVehicleName = vehicles.find { it.vehicleId == id }?.manufacturerAndModel ?: "" // Adjust 'name' to your vehicle field
                    }
                } else {
                    Text("No vehicles found in garage", color = AccentRed, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Start Action ─────────────────────────────────
            val canStart = selectedVehicleId != -1L && selectedTrackId != -1L

            Button(
                onClick = { navController.navigate("timeattack/$selectedVehicleId/$selectedTrackId") },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (canStart) AccentRed else Color(0xFF2A1014),
                    contentColor = if (canStart) Color.White else TextMuted
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "START TIME ATTACK",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
fun SelectionCard(
    label: String,
    title: String,
    isSet: Boolean,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, if (isSet) AccentRed.copy(alpha = 0.5f) else Color(0xFF1E2530), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(label, color = if (isSet) AccentRed else TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
