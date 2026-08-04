package com.example.trackpro.screens.listViewScreens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.StatCell
import com.example.trackpro.components.StatCellDivider
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.viewModels.VehicleFULLViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun CarListScreen(navController: NavController, viewModel: VehicleFULLViewModel) {
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    val vehicles by viewModel.vehicles.collectAsState()
    val scope = rememberCoroutineScope()


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            AppTopBar(
                title = "My Vehicles",
                accent = TrackProTheme.colors.accent,
                trailing = {
                    Text(
                        text = "${vehicles.size} cars",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.textMuted
                    )
                }
            )

            if (vehicles.isEmpty()) {
                EmptyState(
                    message = "No vehicles yet",
                    hint = "Add a vehicle from the main screen"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(vehicles) { vehicle ->
                        VehicleCard(
                            vehicle = vehicle,
                            navController = navController,
                            bgCard = TrackProTheme.colors.bgCard,
                            bgElevated = TrackProTheme.colors.bgElevated,
                            accent = TrackProTheme.colors.accent,
                            dangerColor = TrackProTheme.colors.danger,
                            textPrimary = TrackProTheme.colors.textPrimary,
                            textMuted = TrackProTheme.colors.textMuted,
                            sectorLine = TrackProTheme.colors.sectorLine,
                            onDelete = { vehicleToDelete ->
                                scope.launch(Dispatchers.IO) {
                                    database.vehicleInformationDAO()
                                        .deleteVehicle(vehicleToDelete.vehicleId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleCard(
    vehicle: VehicleInformationData,
    navController: NavController,
    bgCard: Color,
    bgElevated: Color,
    accent: Color,
    dangerColor: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color,
    onDelete: (VehicleInformationData) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = TrackProTheme.colors.bgCard,
            titleContentColor = TrackProTheme.colors.textPrimary,
            textContentColor = TrackProTheme.colors.textMuted,
            confirmButton = {
                TextButton(onClick = {
                    onDelete(vehicle)
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = dangerColor, style = TrackProType.titleMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = textMuted)
                }
            },
            title = { Text("Delete Vehicle?") },
            text = { Text("${vehicle.manufacturer} ${vehicle.model} will be permanently removed.") }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgCard, TrackProShapes.card)
            .border(1.dp, sectorLine, TrackProShapes.card)
            .clickable { navController.navigate("vehicle/${vehicle.vehicleId}") }
    ) {
        Column {

            // ── Header ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        TrackProTheme.colors.bgElevated,
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(bgCard, TrackProShapes.badge)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = vehicle.fuelType.uppercase(),
                                style = TrackProType.label.copy(fontSize = 9.sp),
                                color = textMuted
                            )
                        }
                        Text(
                            text = vehicle.drivetrain.uppercase(),
                            style = TrackProType.label.copy(fontSize = 9.sp),
                            color = textMuted
                        )
                    }

                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(dangerColor.copy(alpha = 0.1f), TrackProShapes.badge)
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = dangerColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // ── Vehicle name ────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${vehicle.manufacturer} ${vehicle.model}",
                    style = TrackProType.titleMedium,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${vehicle.year} · ${vehicle.engineType}",
                    style = TrackProType.body.copy(fontSize = 12.sp),
                    color = textMuted
                )
            }

            HorizontalDivider(color = sectorLine, thickness = 1.dp)

            // ── Stats row ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        bgElevated,
                        RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCell(label = "Power", value = "${vehicle.horsepower}", unit = "hp", horizontalAlignment = Alignment.CenterHorizontally)
                StatCellDivider()
                StatCell(label = "Torque", value = vehicle.torque?.toString() ?: "—", unit = "Nm", horizontalAlignment = Alignment.CenterHorizontally)
                StatCellDivider()
                StatCell(label = "Weight", value = "${vehicle.weight}", unit = "kg", horizontalAlignment = Alignment.CenterHorizontally)
                Text(
                    text = "View",
                    style = TrackProType.label,
                    color = accent
                )
            }
        }
    }
}

suspend fun DeleteVehicle(context: Context, database: ESPDatabase, vehicleId: Long)
{
    database.vehicleInformationDAO().deleteVehicle(vehicleId)
}