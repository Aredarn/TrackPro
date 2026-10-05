package com.example.trackpro.screens.drive

import com.example.trackpro.components.pluralResource
import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.IconCircle
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.PhotoFrame
import com.example.trackpro.components.paddockCard
import com.example.trackpro.components.pressable
import com.example.trackpro.dao.VehicleUsage
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.timed
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.online.AccountState
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.accentGlow
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/** What the last session did, resolved off the main thread. */
private data class LastSession(
    val trackName: String?,
    val bestLapMs: Long?,
    val laps: Int,
    /** Against your previous session on the same track; negative is faster. */
    val deltaToPreviousMs: Long?,
)

/**
 * The Drive tab: the app's home.
 *
 * Read parked, before a session: who you are, whether the rig is talking, which car you are
 * in, and the two ways to start. The last session sits below as the thing you are trying to
 * beat. Track is the one orange element - it is what most visits are for.
 */
@Composable
fun DriveScreen(
    onStartTrack: () -> Unit,
    onStartDrag: () -> Unit,
    onOpenRig: () -> Unit,
    onOpenHistory: (section: String) -> Unit,
    onOpenCar: (Long) -> Unit,
    onAddCar: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp

    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)
    val vehicles by app.database.vehicleInformationDAO().getAllVehicles().collectAsState(initial = emptyList())
    val usage by remember { app.database.vehicleInformationDAO().observeUsage() }.collectAsState(initial = emptyList())
    val sessions by app.database.sessionDataDao().getAllSessions().collectAsState(initial = emptyList())
    val account by app.online.auth.state.collectAsState()

    var last by remember { mutableStateOf<LastSession?>(null) }
    LaunchedEffect(sessions) {
        val counted = sessions.filter { !it.voided }
        val recent = counted.maxByOrNull { it.startTime } ?: run { last = null; return@LaunchedEffect }
        last = withContext(Dispatchers.IO) {
            val laps = app.database.lapTimeDataDAO().getLapsForSession(recent.id).timed()
            val best = laps.minOfOrNull { it.millis }
            val previous = counted
                .filter { it.trackId != null && it.trackId == recent.trackId && it.startTime < recent.startTime }
                .maxByOrNull { it.startTime }
            val previousBest = previous?.let { p ->
                app.database.lapTimeDataDAO().getLapsForSession(p.id).timed().minOfOrNull { it.millis }
            }
            LastSession(
                trackName = recent.trackId?.takeIf { it != -1L }?.let { id ->
                    runCatching { app.database.trackMainDao().getTrack(id).first().trackName }.getOrNull()
                },
                bestLapMs = best,
                laps = laps.size,
                deltaToPreviousMs = if (best != null && previousBest != null) best - previousBest else null,
            )
        }
    }

    // The car you drive most, not whichever row happens to be first in the table.
    val mainId = usage.maxWithOrNull(compareBy<VehicleUsage>({ it.sessions }, { it.lastUsed ?: 0L }))?.vehicleId
    val vehicle = vehicles.firstOrNull { it.vehicleId == mainId } ?: vehicles.firstOrNull()
    val name = (account as? AccountState.SignedIn)?.displayName ?: stringResource(R.string.drive_driver)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter)
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Greeting(name = name, isConnected = isConnected, onOpenRig = onOpenRig)

        Spacer(Modifier.height(Spacing.xl))
        CarCard(vehicle = vehicle, photo = { app.online.photos.file(it) }, onOpenCar = onOpenCar, onAddCar = onAddCar)

        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ModeTile(
                title = stringResource(R.string.mode_track),
                detail = stringResource(R.string.drive_track_detail),
                icon = Icons.Default.Flag,
                primary = true,
                onClick = onStartTrack,
                modifier = Modifier.weight(1f)
            )
            ModeTile(
                title = stringResource(R.string.mode_drag),
                detail = stringResource(R.string.drive_drag_detail),
                icon = Icons.Default.Bolt,
                primary = false,
                onClick = onStartDrag,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        Text(stringResource(R.string.drive_last_session), style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
        Spacer(Modifier.height(Spacing.sm))
        LastSessionCard(last = last, onOpen = { onOpenHistory("track") })

        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            CountCard(
                label = stringResource(R.string.drive_track_sessions),
                value = sessions.count { it.trackId != null && it.trackId != -1L && !it.voided },
                icon = Icons.Default.Timer,
                onClick = { onOpenHistory("track") },
                modifier = Modifier.weight(1f)
            )
            CountCard(
                label = stringResource(R.string.drive_drag_runs),
                value = sessions.count { (it.trackId == null || it.trackId == -1L) && !it.voided },
                icon = Icons.Default.Bolt,
                onClick = { onOpenHistory("drag") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun Greeting(name: String, isConnected: Boolean, onOpenRig: () -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.drive_good_morning)
        in 12..17 -> stringResource(R.string.drive_good_afternoon)
        else -> stringResource(R.string.drive_good_evening)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = TrackProType.body, color = TrackProTheme.colors.markingDim)
            Text(
                name,
                style = TrackProType.titleLarge,
                color = TrackProTheme.colors.marking,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        RigPill(isConnected = isConnected, onClick = onOpenRig)
    }
}

/**
 * The rig's state as a status pill. No signal is red, never dimmer than healthy: a driver
 * about to start a session needs to see it.
 */
@Composable
private fun RigPill(isConnected: Boolean, onClick: () -> Unit) {
    val tone = if (isConnected) TrackProTheme.colors.deltaGood else TrackProTheme.colors.danger
    Row(
        modifier = Modifier
            .pressable(onClick = onClick, scale = 0.95f)
            .heightIn(min = 40.dp)
            .clip(TrackProShapes.pill)
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            if (isConnected) stringResource(R.string.drive_rig_linked) else stringResource(R.string.drive_no_signal),
            style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold),
            color = tone
        )
    }
}

@Composable
private fun CarCard(
    vehicle: VehicleInformationData?,
    photo: (String?) -> java.io.File?,
    onOpenCar: (Long) -> Unit,
    onAddCar: () -> Unit,
) {
    PaddockCard(
        onClick = { if (vehicle != null) onOpenCar(vehicle.vehicleId) else onAddCar() },
        padding = Spacing.md
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vehicle != null) {
                PhotoFrame(
                    file = photo(vehicle.photoFile),
                    contentDescription = "${vehicle.manufacturer} ${vehicle.model}",
                    modifier = Modifier.size(width = 72.dp, height = 56.dp)
                )
            } else {
                IconCircle(Icons.Default.Add, size = 56.dp)
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = vehicle?.let { "${it.manufacturer} ${it.model}" } ?: stringResource(R.string.drive_add_car),
                    style = TrackProType.titleMedium,
                    color = TrackProTheme.colors.marking,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = vehicle?.let { v ->
                        listOfNotNull(
                            v.horsepower.takeIf { it > 0 }?.let { "$it hp" },
                            v.drivetrain.takeIf { it.isNotBlank() },
                            v.weight.takeIf { it > 0 }?.let { "${it.toInt()} kg" },
                        ).joinToString(" · ")
                    } ?: stringResource(R.string.drive_add_car_hint),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    maxLines = 1
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TrackProTheme.colors.markingDim
            )
        }
    }
}

/**
 * A way to start. The primary tile is filled orange with a soft glow of its own colour; the
 * secondary is a card with the accent only in its icon.
 */
@Composable
private fun ModeTile(
    title: String,
    detail: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glow = TrackProTheme.colors.accentGlow
    val ink = if (primary) TrackProTheme.colors.onAccent else TrackProTheme.colors.marking
    Column(
        modifier = modifier
            .height(148.dp)
            .pressable(onClick = onClick, scale = 0.96f)
            .then(
                if (primary) Modifier
                    .shadow(20.dp, TrackProShapes.card, clip = false, ambientColor = glow, spotColor = glow)
                    .clip(TrackProShapes.card)
                    .background(TrackProTheme.colors.accent)
                else Modifier.paddockCard()
            )
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        IconCircle(icon = icon, tint = if (primary) TrackProTheme.colors.onAccent else TrackProTheme.colors.accent, size = 44.dp)
        Column {
            Text(title, style = TrackProType.titleLarge, color = ink)
            Text(
                detail,
                style = TrackProType.label,
                color = if (primary) ink.copy(alpha = 0.72f) else TrackProTheme.colors.markingDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LastSessionCard(last: LastSession?, onOpen: () -> Unit) {
    PaddockCard(onClick = onOpen) {
        if (last?.bestLapMs == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Default.EmojiEvents, size = 48.dp)
                Spacer(Modifier.width(Spacing.lg))
                Column {
                    Text(stringResource(R.string.drive_no_laps), style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
                    Text(
                        stringResource(R.string.drive_no_laps_hint),
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim
                    )
                }
            }
        } else {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.drive_best_lap_at, last.trackName ?: stringResource(R.string.drive_last_session_fallback)),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(last.bestLapMs.toLapTimeString(), style = TrackProType.displayNumeric, color = TrackProTheme.colors.marking)
            }
            Text(
                pluralResource(R.plurals.count_laps, last.laps),
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim,
                modifier = Modifier
                    .clip(TrackProShapes.pill)
                    .background(TrackProTheme.colors.bgElevated)
                    .padding(horizontal = Spacing.md, vertical = 6.dp)
            )
        }
        last.deltaToPreviousMs?.let { delta ->
            Spacer(Modifier.height(Spacing.sm))
            val better = delta < 0
            val tone: Color = if (better) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad
            Text(
                text = stringResource(R.string.drive_vs_previous, String.format(java.util.Locale.US, "%+.2f", delta / 1000.0)),
                style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold),
                color = tone
            )
        }
        }
    }
}

@Composable
private fun CountCard(label: String, value: Int, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PaddockCard(onClick = onClick, modifier = modifier, padding = Spacing.lg) {
        IconCircle(icon = icon, size = 36.dp)
        Spacer(Modifier.height(Spacing.md))
        Text("$value", style = TrackProType.titleLarge, color = TrackProTheme.colors.marking)
        Text(label, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
    }
}
