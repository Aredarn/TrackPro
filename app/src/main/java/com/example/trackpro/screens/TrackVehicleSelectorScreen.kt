package com.example.trackpro.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.trackpro.TrackProApp
import com.example.trackpro.extrasForUI.DropdownMenuFieldMulti
import com.example.trackpro.extrasForUI.TrackDropdownMenu
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppCard
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.TrackViewModelFactory
import com.example.trackpro.viewModels.VehicleViewModel
import com.example.trackpro.viewModels.VehicleViewModelFactory


@Composable
fun TrackVehicleSelectorScreen(
    trackViewModel: TrackViewModel,
    vehicleViewModel: VehicleViewModel,
    navController: NavController
) {
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

    Box(modifier = Modifier.fillMaxSize().background(TrackProTheme.colors.bgDeep)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "Session Setup",
                accent = TrackProTheme.colors.accent,
                onBack = { navController.popBackStack() }
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Track Selection Card ─────────────────────────
                SelectionCard(
                    label = "Circuit",
                    title = selectedTrackName.ifEmpty { "Select Track" },
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

                Spacer(modifier = Modifier.height(Spacing.md))

                // ── Vehicle Selection Card ───────────────────────
                SelectionCard(
                    label = "Vehicle",
                    title = selectedVehicleName.ifEmpty { "Select Vehicle" },
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
                        Text("No vehicles found in garage", style = TrackProType.body.copy(fontSize = 12.sp), color = TrackProTheme.colors.accent)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // ── Start Action ─────────────────────────────────
                val canStart = selectedVehicleId != -1L && selectedTrackId != -1L

                PrimaryButton(
                    text = "Start Time Attack",
                    onClick = { navController.navigate("timeattack/$selectedVehicleId/$selectedTrackId") },
                    enabled = canStart,
                    accent = TrackProTheme.colors.accent,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
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
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isSet) TrackProTheme.colors.accent.copy(alpha = 0.5f) else TrackProTheme.colors.sectorLine
    ) {
        Text(
            label.uppercase(),
            style = TrackProType.label,
            color = if (isSet) TrackProTheme.colors.accent else TrackProTheme.colors.textMuted
        )
        Text(title, style = TrackProType.titleMedium, color = TrackProTheme.colors.textPrimary, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(modifier = Modifier.height(Spacing.sm))
        content()
    }
}
