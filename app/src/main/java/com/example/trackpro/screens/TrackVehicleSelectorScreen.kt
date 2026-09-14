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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.DashGroup
import com.example.trackpro.components.SegmentBar
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.theme.atSize
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

    // Two conditions arm the session. The strip below reads them the way a dash reads
    // any other pre-condition: how many of the required inputs are satisfied, drawn
    // rather than described, so the gap is visible before you reach for the control.
    val trackSet = selectedTrackId != -1L
    val vehicleSet = selectedVehicleId != -1L
    val readyCount = listOf(trackSet, vehicleSet).count { it }
    val canStart = trackSet && vehicleSet

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
    ) {
        AppTopBar(
            title = "Session setup",
            accent = TrackProTheme.colors.accent,
            onBack = { navController.popBackStack() }
        )

        DashGroup("Circuit") {
            Text(
                text = selectedTrackName.ifEmpty { "Not selected" },
                style = TrackProType.statValue.atSize(22.sp),
                color = if (trackSet) TrackProTheme.colors.marking
                else TrackProTheme.colors.markingDim
            )
            Spacer(Modifier.height(Spacing.sm))
            TrackDropdownMenu(
                label = "Choose location",
                tracks = tracks,
                selectedTrackName = selectedTrackName,
                onTrackSelected = { id ->
                    val track = tracks.find { it.trackId == id }
                    selectedTrackName = track?.trackName ?: ""
                    selectedTrackId = id
                }
            )
        }

        DashGroup("Vehicle") {
            Text(
                text = selectedVehicleName.ifEmpty { "Not selected" },
                style = TrackProType.statValue.atSize(22.sp),
                color = if (vehicleSet) TrackProTheme.colors.marking
                else TrackProTheme.colors.markingDim
            )
            Spacer(Modifier.height(Spacing.sm))
            if (vehicles.isNotEmpty()) {
                DropdownMenuFieldMulti(
                    "Choose machine",
                    vehicles,
                    selectedVehicleName
                ) { id ->
                    selectedVehicleId = id
                    selectedVehicleName =
                        vehicles.find { it.vehicleId == id }?.manufacturerAndModel ?: ""
                }
            } else {
                Text(
                    "No vehicles in the garage yet",
                    style = TrackProType.body.atSize(12.sp),
                    color = TrackProTheme.colors.deltaBad
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // -- Arming strip ---------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.panel)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SegmentBar(
                    signedFraction = readyCount / 2f,
                    activeColor = if (canStart) TrackProTheme.colors.deltaGood
                    else TrackProTheme.colors.deltaBad,
                    bidirectional = false,
                    segments = 2,
                    height = 10.dp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "$readyCount / 2 SET",
                    style = TrackProType.label,
                    color = if (canStart) TrackProTheme.colors.deltaGood
                    else TrackProTheme.colors.markingDim
                )
            }
            Spacer(Modifier.height(10.dp))
            DashAction(
                label = "Start session",
                detail = if (canStart) "Arm timing and go green"
                else if (!trackSet) "Select a circuit first" else "Select a vehicle first",
                enabled = canStart,
                labelSize = 28.sp,
                onClick = { navController.navigate("timeattack/$selectedVehicleId/$selectedTrackId") }
            )
        }
    }
}
