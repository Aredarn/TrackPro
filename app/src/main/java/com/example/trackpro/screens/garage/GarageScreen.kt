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
            contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = 24.dp)
        ) {
            item {
                Box(modifier = Modifier.padding(12.dp)) {
                    DashAction(
                        label = "Add car",
                        detail = "Every session is timed against one",
                        onClick = { navController.navigate("createvehicle") },
                        compact = true
                    )
                }
                BackupStrip(
                    signedIn = signedIn,
                    statuses = statuses.values,
                    onSignIn = { navController.navigate("account") }
                )
                Bezel()
            }

            if (ordered.isEmpty()) {
                item {
                    DataGate(
                        state = loadState,
                        items = ordered,
                        emptyMessage = "No cars yet",
                        emptyHint = "Add the car you drive and every lap is filed against it",
                        loadingLabel = "Reading garage",
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    ) { }
                }
            }

            items(ordered, key = { it.vehicleId }) { vehicle ->
                CarRow(
                    vehicle = vehicle,
                    photo = app.online.photos.file(vehicle.photoFile),
                    sessions = usageById[vehicle.vehicleId]?.sessions ?: 0,
                    isMain = vehicle.vehicleId == mainId,
                    status = statuses.getValue(vehicle.vehicleId),
                    onOpen = { navController.navigate("vehicle/${vehicle.vehicleId}") },
                    onLongPress = { pendingDelete = vehicle }
                )
                Bezel()
            }
        }
    }
}

/**
 * One line that says whether the garage is safe. Signed out it is an invitation, and the
 * whole strip is the way to act on it.
 */
@Composable
private fun BackupStrip(
    signedIn: Boolean,
    statuses: Collection<VehicleSyncStatus>,
    onSignIn: () -> Unit,
) {
    val backedUp = statuses.count { it == VehicleSyncStatus.Synced }
    val failed = statuses.count { it == VehicleSyncStatus.Failed }
    val (lamp, text) = when {
        !signedIn -> null to "Not backed up · sign in to keep your garage"
        failed > 0 -> TrackProTheme.colors.danger to "$failed car${if (failed == 1) "" else "s"} could not be backed up"
        backedUp == statuses.size -> TrackProTheme.colors.deltaGood to "Garage backed up to your account"
        else -> TrackProTheme.colors.deltaBad to "$backedUp of ${statuses.size} backed up · the rest go on the next sync"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.field)
            .then(if (!signedIn) Modifier.pressableRow(onClick = onSignIn) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (lamp != null) {
            Box(Modifier.size(6.dp).background(lamp, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text.uppercase(),
            style = TrackProType.label,
            color = if (failed > 0 && signedIn) TrackProTheme.colors.danger else TrackProTheme.colors.markingDim,
            modifier = Modifier.weight(1f)
        )
        if (!signedIn) {
            Text("SIGN IN ›", style = TrackProType.label, color = TrackProTheme.colors.accent)
        }
    }
}

@Composable
private fun CarRow(
    vehicle: VehicleInformationData,
    photo: java.io.File?,
    sessions: Int,
    isMain: Boolean,
    status: VehicleSyncStatus,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onOpen, onLongClick = onLongPress, scale = 0.99f)
            .background(TrackProTheme.colors.field)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhotoFrame(
            file = photo,
            contentDescription = "${vehicle.manufacturer} ${vehicle.model}",
            modifier = Modifier.size(width = 112.dp, height = 84.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${vehicle.manufacturer} ${vehicle.model}".uppercase(),
                    style = TrackProType.titleLarge.atSize(17.sp),
                    color = TrackProTheme.colors.marking,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isMain) {
                    Spacer(Modifier.width(8.dp))
                    Text("MAIN", style = TrackProType.label, color = TrackProTheme.colors.accent)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    vehicle.year.takeIf { it > 0 }?.toString(),
                    vehicle.horsepower.takeIf { it > 0 }?.let { "$it HP" },
                    vehicle.drivetrain.takeIf { it.isNotBlank() }?.uppercase(),
                ).joinToString("  ·  "),
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim,
                maxLines = 1
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                statusLamp(status)?.let { lamp ->
                    Box(Modifier.size(6.dp).background(lamp, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = "${status.label} · $sessions session${if (sessions == 1) "" else "s"}".uppercase(),
                    style = TrackProType.label,
                    // A failed backup is a fault; it is never drawn dimmer than a healthy one.
                    color = if (status == VehicleSyncStatus.Failed) TrackProTheme.colors.danger
                    else TrackProTheme.colors.markingDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
