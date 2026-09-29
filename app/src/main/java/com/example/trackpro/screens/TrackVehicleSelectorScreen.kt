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
import com.example.trackpro.theme.TrackProShapes
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
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

    // Two conditions arm the session. The checklist above the start button names them, so
    // the gap is visible before you reach for the control.
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

        DashGroup("Track") {
            Text(
                text = selectedTrackName.ifEmpty { "Not selected" },
                style = TrackProType.titleLarge,
                color = if (trackSet) TrackProTheme.colors.marking
                else TrackProTheme.colors.markingDim
            )
            Spacer(Modifier.height(Spacing.sm))
            TrackDropdownMenu(
                label = "Choose track",
                tracks = tracks,
                selectedTrackName = selectedTrackName,
                onTrackSelected = { id ->
                    val track = tracks.find { it.trackId == id }
                    selectedTrackName = track?.trackName ?: ""
                    selectedTrackId = id
                }
            )
        }

        DashGroup("Car") {
            Text(
                text = selectedVehicleName.ifEmpty { "Not selected" },
                style = TrackProType.titleLarge,
                color = if (vehicleSet) TrackProTheme.colors.marking
                else TrackProTheme.colors.markingDim
            )
            Spacer(Modifier.height(Spacing.sm))
            if (vehicles.isNotEmpty()) {
                DropdownMenuFieldMulti(
                    "Choose car",
                    vehicles,
                    selectedVehicleName
                ) { id ->
                    selectedVehicleId = id
                    selectedVehicleName =
                        vehicles.find { it.vehicleId == id }?.manufacturerAndModel ?: ""
                }
            } else {
                Text(
                    "No cars in the garage yet",
                    style = TrackProType.label,
                    color = TrackProTheme.colors.deltaBad
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // -- Ready check + start ----------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.gutter)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ReadyChip("Track", trackSet)
                ReadyChip("Car", vehicleSet)
            }
            Spacer(Modifier.height(Spacing.md))
            DashAction(
                label = "Start session",
                detail = if (canStart) "Arm timing and go green"
                else if (!trackSet) "Pick a track first" else "Pick a car first",
                enabled = canStart,
                primary = true,
                icon = Icons.Filled.Flag,
                onClick = { navController.navigate("timeattack/$selectedVehicleId/$selectedTrackId") }
            )
        }
    }
}

/** One precondition for the start button: ticked when met, quiet when not. */
@Composable
private fun ReadyChip(label: String, ready: Boolean) {
    val tint = if (ready) TrackProTheme.colors.deltaGood else TrackProTheme.colors.markingDim
    Row(
        modifier = Modifier
            .clip(TrackProShapes.pill)
            .background(if (ready) tint.copy(alpha = 0.14f) else TrackProTheme.colors.bgElevated)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (ready) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            if (ready) "$label set" else "$label needed",
            style = TrackProType.label,
            color = if (ready) tint else TrackProTheme.colors.textMuted
        )
    }
}
