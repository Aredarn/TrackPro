package com.example.trackpro.screens.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.trackpro.theme.accentSoft
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.Spacing
import com.example.trackpro.components.PaddockCard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.ConfirmDeleteDialog
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.DataGate
import com.example.trackpro.components.PhotoFrame
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.SectionSwitch
import com.example.trackpro.components.countLabel
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.pressable
import com.example.trackpro.components.pressableRow
import com.example.trackpro.dao.VehicleUsage
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.online.VehicleSyncStatus
import com.example.trackpro.online.vehicleSyncStatus
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.VehicleFULLViewModel
import com.example.trackpro.screens.listViewScreens.TrackListScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class GarageSection { Cars, Tracks }

/**
 * The Garage tab: the cars you drive and the tracks you drive them on. Both are things you
 * own and maintain between track days, which is why they share a tab and the history of what
 * you did with them does not.
 */
@Composable
fun GarageScreen(
    navController: NavController,
    vehicleViewModel: VehicleFULLViewModel,
    trackViewModel: TrackViewModel,
    initial: GarageSection = GarageSection.Cars,
) {
    var section by rememberSaveable { mutableStateOf(initial) }
    val header: @Composable () -> Unit = {
        SectionSwitch(
            options = listOf(GarageSection.Cars to "Cars", GarageSection.Tracks to "Tracks"),
            selected = section,
            onSelect = { section = it }
        )
    }
    when (section) {
        GarageSection.Cars -> GarageCars(navController, vehicleViewModel, header)
        GarageSection.Tracks -> TrackListScreen(
            navController = navController,
            viewModel = trackViewModel,
            onBack = null,
            header = header,
            onBuildTrack = { navController.navigate("trackbuilder") },
            title = "Garage",
        )
    }
}

@Composable
private fun GarageCars(
    navController: NavController,
    viewModel: VehicleFULLViewModel,
    header: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val vehicles by viewModel.vehicles.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val usage by remember { app.database.vehicleInformationDAO().observeUsage() }.collectAsState(initial = emptyList())
    val links by remember { app.database.syncDao().observeLinks(RemoteLink.KIND_VEHICLE) }.collectAsState(initial = emptyList())
    val account by app.online.auth.state.collectAsState()
    val signedIn = account is AccountState.SignedIn

    val usageById = remember(usage) { usage.associateBy { it.vehicleId } }
    val linkById = remember(links) { links.associateBy { it.localId } }
    // The main car is the one you drive most; ties go to the one driven last.
    val mainId = remember(usage) {
        usage.maxWithOrNull(compareBy<VehicleUsage>({ it.sessions }, { it.lastUsed ?: 0L }))?.vehicleId
    }
    val ordered = remember(vehicles, usageById) {
        vehicles.sortedWith(
            compareByDescending<VehicleInformationData> { usageById[it.vehicleId]?.lastUsed ?: 0L }
                .thenBy { "${it.manufacturer} ${it.model}".lowercase() }
        )
    }
    val statuses = remember(ordered, linkById, signedIn) {
        ordered.associate { it.vehicleId to vehicleSyncStatus(it, linkById[it.vehicleId], signedIn) }
    }

    var pendingDelete by remember { mutableStateOf<VehicleInformationData?>(null) }
    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()

    pendingDelete?.let { vehicle ->
        val sessions = usageById[vehicle.vehicleId]?.sessions ?: 0
        ConfirmDeleteDialog(
            title = "Delete ${vehicle.manufacturer} ${vehicle.model}?",
            message = buildString {
                // Deleting a car cascades to its sessions in the database. Say so, with the count.
                append(
                    if (sessions > 0) "Its $sessions recorded session${if (sessions == 1) "" else "s"} and their laps are deleted with it. "
                    else "It has no recorded sessions. "
                )
                if (signedIn) append("It is also removed from your account.")
                append(" This cannot be undone.")
            },
            onConfirm = {
                pendingDelete = null
                app.applicationScope.launch(Dispatchers.IO) {
                    app.database.vehicleInformationDAO().deleteVehicle(vehicle.vehicleId)
                    app.online.photos.delete(vehicle.photoFile)
                    if (signedIn) SyncScheduler.syncSoon(context)
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }

    ScreenScaffold(
        title = "Garage",
        header = header,
        trailing = {
            Text(countLabel(vehicles.size, "car"), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
        },
        contentScrolled = scrolled
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(TrackProTheme.colors.panel),
            state = listState,
            contentPadding = PaddingValues(
                start = Spacing.gutter,
                end = Spacing.gutter,
                top = contentPadding.calculateTopPadding() + Spacing.xs,
                bottom = Spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                BackupStrip(
                    signedIn = signedIn,
                    statuses = statuses.values,
                    onSignIn = { navController.navigate("account") }
                )
            }

            if (ordered.isEmpty()) {
                item {
                    DataGate(
                        state = loadState,
                        items = ordered,
                        emptyMessage = "Add your first car",
                        emptyHint = "Every lap is filed against the car that set it, so this is where it starts.",
                        loadingLabel = "Reading garage",
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    ) { }
                }
            }

            items(ordered, key = { it.vehicleId }) { vehicle ->
                CarCard(
                    vehicle = vehicle,
                    photo = app.online.photos.file(vehicle.photoFile),
                    sessions = usageById[vehicle.vehicleId]?.sessions ?: 0,
                    isMain = vehicle.vehicleId == mainId,
                    status = statuses.getValue(vehicle.vehicleId),
                    onOpen = { navController.navigate("vehicle/${vehicle.vehicleId}") },
                    onLongPress = { pendingDelete = vehicle }
                )
            }

            item {
                DashAction(
                    label = "Add car",
                    onClick = { navController.navigate("createvehicle") },
                    compact = true,
                    icon = Icons.Default.Add
                )
            }
        }
    }
}

/**
 * Whether the garage is safe, as one small pill. Signed out it is an invitation, and the
 * whole pill is the way to act on it.
 */
@Composable
private fun BackupStrip(
    signedIn: Boolean,
    statuses: Collection<VehicleSyncStatus>,
    onSignIn: () -> Unit,
) {
    val backedUp = statuses.count { it == VehicleSyncStatus.Synced }
    val failed = statuses.count { it == VehicleSyncStatus.Failed }
    val (tone, icon, text) = when {
        !signedIn -> Triple(TrackProTheme.colors.accent, Icons.Default.CloudOff, "Not backed up. Sign in to keep your garage safe.")
        failed > 0 -> Triple(TrackProTheme.colors.danger, Icons.Default.ErrorOutline, "$failed car${if (failed == 1) "" else "s"} could not be backed up")
        backedUp == statuses.size -> Triple(TrackProTheme.colors.deltaGood, Icons.Default.CloudDone, "Garage backed up to your account")
        else -> Triple(TrackProTheme.colors.deltaBad, Icons.Default.CloudSync, "$backedUp of ${statuses.size} backed up, the rest go on the next sync")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!signedIn) Modifier.pressable(onClick = onSignIn, scale = 0.98f) else Modifier)
            .clip(TrackProShapes.control)
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(text = text, style = TrackProType.label, color = TrackProTheme.colors.marking, modifier = Modifier.weight(1f))
        if (!signedIn) {
            Spacer(Modifier.width(Spacing.sm))
            Text("Sign in", style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold), color = TrackProTheme.colors.accent)
        }
    }
}

@Composable
private fun CarCard(
    vehicle: VehicleInformationData,
    photo: java.io.File?,
    sessions: Int,
    isMain: Boolean,
    status: VehicleSyncStatus,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    PaddockCard(onClick = onOpen, onLongClick = onLongPress, padding = Spacing.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoFrame(
                file = photo,
                contentDescription = "${vehicle.manufacturer} ${vehicle.model}",
                modifier = Modifier.size(width = 104.dp, height = 80.dp)
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${vehicle.manufacturer} ${vehicle.model}",
                        style = TrackProType.titleMedium,
                        color = TrackProTheme.colors.marking,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isMain) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Main",
                            style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold),
                            color = TrackProTheme.colors.accent,
                            modifier = Modifier
                                .clip(TrackProShapes.pill)
                                .background(TrackProTheme.colors.accentSoft)
                                .padding(horizontal = Spacing.sm, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = listOfNotNull(
                        vehicle.year.takeIf { it > 0 }?.toString(),
                        vehicle.horsepower.takeIf { it > 0 }?.let { "$it hp" },
                        vehicle.drivetrain.takeIf { it.isNotBlank() }?.uppercase(),
                    ).joinToString(" · "),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    maxLines = 1
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    statusLamp(status)?.let { lamp ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(lamp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "${status.label} · $sessions session${if (sessions == 1) "" else "s"}",
                        style = TrackProType.label,
                        // A failed backup is a fault; never drawn dimmer than a healthy one.
                        color = if (status == VehicleSyncStatus.Failed) TrackProTheme.colors.danger
                        else TrackProTheme.colors.markingDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun statusLamp(status: VehicleSyncStatus): Color? = when (status) {
    VehicleSyncStatus.OnThisPhone -> null
    VehicleSyncStatus.Synced -> TrackProTheme.colors.deltaGood
    VehicleSyncStatus.Pending, VehicleSyncStatus.LocalOnly -> TrackProTheme.colors.deltaBad
    VehicleSyncStatus.Failed -> TrackProTheme.colors.danger
}
