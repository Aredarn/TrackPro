package com.example.trackpro.screens.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.ConfirmDeleteDialog
import com.example.trackpro.components.PhotoFrame
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.pressableRow
import com.example.trackpro.components.rememberPhotoPicker
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.LocalCareer
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.online.VehicleSyncStatus
import com.example.trackpro.online.vehicleSyncStatus
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The car's photo, full bleed, and the three things you do with it. The frame is drawn even
 * with no photo, so adding one fills a space rather than pushing the sheet down.
 */
@Composable
fun CarPhotoSection(vehicle: VehicleInformationData) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun replacePhoto(newName: String?) {
        app.applicationScope.launch(Dispatchers.IO) {
            app.database.vehicleInformationDAO().setPhotoFile(vehicle.vehicleId, newName)
            app.online.photos.delete(vehicle.photoFile)
            if (app.online.auth.isSignedIn) SyncScheduler.syncSoon(context)
        }
    }

    val picker = rememberPhotoPicker { uri ->
        busy = true
        error = null
        scope.launch {
            val name = app.online.photos.import(uri, "vehicle-${vehicle.vehicleId}")
            if (name == null) error = "That file could not be read as a picture." else replacePhoto(name)
            busy = false
        }
    }

    PhotoFrame(
        file = app.online.photos.file(vehicle.photoFile),
        contentDescription = "${vehicle.manufacturer} ${vehicle.model}",
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
    )
    Row(
        modifier = Modifier.fillMaxWidth().background(TrackProTheme.colors.panel).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToggleChip(text = "Take photo", selected = false, onClick = { if (!busy) picker.fromCamera() }, modifier = Modifier.weight(1f))
        ToggleChip(text = "Choose", selected = false, onClick = { if (!busy) picker.fromGallery() }, modifier = Modifier.weight(1f))
        if (vehicle.photoFile != null) {
            ToggleChip(text = "Remove", selected = false, onClick = { if (!busy) replacePhoto(null) }, modifier = Modifier.weight(1f))
        }
    }
    error?.let {
        Text(it.uppercase(), style = TrackProType.label, color = TrackProTheme.colors.deltaBad, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
    Bezel()
}

/**
 * Whether this car is safe on the account, and the one recovery that needs the driver: a car
 * removed from the account on another phone can be put back from here.
 */
@Composable
fun CarBackupStrip(vehicle: VehicleInformationData, onSignIn: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val account by app.online.auth.state.collectAsState()
    val signedIn = account is AccountState.SignedIn
    val link by remember(vehicle.vehicleId) {
        app.database.syncDao().observeLinks(RemoteLink.KIND_VEHICLE)
    }.collectAsState(initial = emptyList())
    val own = link.firstOrNull { it.localId == vehicle.vehicleId }
    val status = vehicleSyncStatus(vehicle, own, signedIn)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.field)
            .then(if (!signedIn) Modifier.pressableRow(onClick = onSignIn) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        statusLamp(status)?.let { lamp ->
            Box(Modifier.size(6.dp).background(lamp, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                status.label.uppercase(),
                style = TrackProType.label,
                color = if (status == VehicleSyncStatus.Failed) TrackProTheme.colors.danger else TrackProTheme.colors.marking
            )
            val detail = when (status) {
                VehicleSyncStatus.OnThisPhone -> "Sign in to back it up"
                VehicleSyncStatus.Failed, VehicleSyncStatus.LocalOnly -> own?.lastError
                else -> null
            }
            detail?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = TrackProType.body.atSize(12.sp), color = TrackProTheme.colors.markingDim)
            }
        }
        when (status) {
            VehicleSyncStatus.OnThisPhone -> Text("SIGN IN ›", style = TrackProType.label, color = TrackProTheme.colors.accent)
            VehicleSyncStatus.LocalOnly, VehicleSyncStatus.Failed -> ToggleChip(
                text = if (status == VehicleSyncStatus.LocalOnly) "Back up again" else "Retry",
                selected = false,
                onClick = {
                    app.applicationScope.launch(Dispatchers.IO) {
                        // Forgetting the link makes the next sync treat it as a new car: it is
                        // uploaded under a fresh id, photo and all.
                        app.database.syncDao().deleteLink(RemoteLink.KIND_VEHICLE, vehicle.vehicleId)
                        app.database.syncDao().deleteLink(RemoteLink.KIND_VEHICLE_PHOTO, vehicle.vehicleId)
                        SyncScheduler.syncSoon(context)
                    }
                }
            )
            else -> Unit
        }
    }
    Bezel()
}

/** This car's best lap on each track it has been driven on, from the phone's own records. */
@Composable
fun CarBests(vehicleId: Long, onOpenTrack: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val career by remember(vehicleId) {
        val dao = app.database.careerDao()
        combine(dao.observeSessions(), dao.observeLaps(), dao.observeTracks(), dao.observeVehicles()) { s, l, t, v ->
            withContext(Dispatchers.Default) { LocalCareer.compute(s.filter { it.vehicleId == vehicleId }, l, t, v) }
        }
    }.collectAsState(initial = LocalCareer.EMPTY)

    SectionLabel("Bests in this car", modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp))
    Bezel()
    if (career.bests.isEmpty()) {
        Text(
            if (career.sessionCount == 0) "NOT DRIVEN ON A TRACK YET" else "NO TIMED LAPS YET",
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim,
            modifier = Modifier.fillMaxWidth().background(TrackProTheme.colors.field).padding(14.dp)
        )
        Bezel()
        return
    }
    career.bests.forEach { best ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressableRow(onClick = { onOpenTrack(best.trackId) })
                .background(TrackProTheme.colors.field)
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(best.trackName.uppercase(), style = TrackProType.titleMedium, color = TrackProTheme.colors.marking, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${best.lapCount} LAP${if (best.lapCount == 1) "" else "S"}", style = TrackProType.label, color = TrackProTheme.colors.markingDim)
            }
            Text(best.bestLapMs.toLapTimeString(), style = TrackProType.statValue.atSize(18.sp), color = TrackProTheme.colors.marking)
        }
        Bezel()
    }
}

/** The car's delete, kept at the foot of its own sheet with the cost stated. */
@Composable
fun CarDeleteRow(vehicle: VehicleInformationData, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    var confirm by remember { mutableStateOf(false) }
    val usage by remember { app.database.vehicleInformationDAO().observeUsage() }.collectAsState(initial = emptyList())
    val sessions = usage.firstOrNull { it.vehicleId == vehicle.vehicleId }?.sessions ?: 0

    if (confirm) {
        ConfirmDeleteDialog(
            title = "Delete ${vehicle.manufacturer} ${vehicle.model}?",
            message = (if (sessions > 0) "Its $sessions recorded session${if (sessions == 1) "" else "s"} and their laps are deleted with it. " else "") +
                (if (app.online.auth.isSignedIn) "It is also removed from your account. " else "") +
                "This cannot be undone.",
            onConfirm = {
                confirm = false
                app.applicationScope.launch(Dispatchers.IO) {
                    app.database.vehicleInformationDAO().deleteVehicle(vehicle.vehicleId)
                    app.online.photos.delete(vehicle.photoFile)
                    if (app.online.auth.isSignedIn) SyncScheduler.syncSoon(context)
                }
                onDeleted()
            },
            onDismiss = { confirm = false }
        )
    }

    Spacer(Modifier.height(18.dp))
    Bezel()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(onClick = { confirm = true })
            .background(TrackProTheme.colors.field)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("DELETE CAR", style = TrackProType.titleMedium, color = TrackProTheme.colors.danger, modifier = Modifier.weight(1f))
        Text("›", style = TrackProType.titleLarge, color = TrackProTheme.colors.danger)
    }
    Bezel()
}
