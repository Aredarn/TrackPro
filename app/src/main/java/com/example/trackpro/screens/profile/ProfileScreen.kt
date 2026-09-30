package com.example.trackpro.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.IconCircle
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.PhotoFrame
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.SectionTitle
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.initialsOf
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.pressable
import com.example.trackpro.components.pressableRow
import com.example.trackpro.components.rememberHaptics
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.ApiException
import com.example.trackpro.online.LocalCareer
import com.example.trackpro.online.NetworkException
import com.example.trackpro.online.PersonalBest
import com.example.trackpro.online.ProfileStats
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.online.describe
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.accentSoft
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.OffsetDateTime
import java.util.Date

/**
 * The Profile tab: who you are as a driver, then your account, then the app.
 *
 * The career is built from this phone's own records, so it is complete with no account and
 * no network. Signing in lays the account on top - a name and face, and the leaderboard
 * place next to each personal best, which only the server can know.
 */
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val online = app.online
    val scope = rememberCoroutineScope()

    val accountState by online.auth.state.collectAsState()
    val signedIn = accountState as? AccountState.SignedIn
    val snapshotAll by online.profile.account.collectAsState()
    val snapshot = remember(snapshotAll, accountState) { online.profile.snapshotFor(accountState) }
    val problem by online.profile.problem.collectAsState()
    val career by remember { online.profile.career }.collectAsState(initial = LocalCareer.EMPTY)
    val vehicles by remember { app.database.vehicleInformationDAO().getAllVehicles() }.collectAsState(initial = emptyList())

    LaunchedEffect(signedIn?.userId) {
        if (signedIn != null) online.profile.refresh()
    }

    // Local track id -> server track id, so a local best can carry its leaderboard place.
    var remoteIds by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(career.bests, signedIn?.userId) {
        remoteIds = if (signedIn == null) emptyMap() else career.bests.mapNotNull { best ->
            online.remoteTrackIdFor(best.trackId)?.let { best.trackId to it }
        }.toMap()
    }

    val scroll = rememberScrollState()
    val scrolled by scroll.isScrolledUnderChrome()

    ScreenScaffold(title = "Profile", contentScrolled = scrolled) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrackProTheme.colors.panel)
                .verticalScroll(scroll)
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Spacer(Modifier.height(Spacing.xs))
                DriverCard(
                    name = snapshot?.profile?.displayName ?: signedIn?.displayName,
                    country = snapshot?.profile?.country,
                    bio = snapshot?.profile?.bio,
                    memberSince = snapshot?.profile?.memberSince,
                    avatar = online.photos.file(snapshot?.avatarFile),
                    signedIn = signedIn != null,
                    onEdit = { navController.navigate("profile/edit") },
                    onSignIn = { navController.navigate("account") },
                )
                if (signedIn != null && problem != null) Notice(problem!!)
                CareerCard(career = career, server = snapshot?.stats?.takeIf { career.isEmpty })
            }

            SectionTitle("Main car")
            MainCarCard(
                vehicle = vehicles.firstOrNull { it.vehicleId == career.mainVehicleId } ?: vehicles.singleOrNull(),
                photo = { online.photos.file(it) },
                onOpen = { navController.navigate("vehicle/$it") },
                onAdd = { navController.navigate("createvehicle") },
            )

            SectionTitle("Personal bests")
            PersonalBests(
                career = career,
                serverBests = snapshot?.stats?.personalBests.orEmpty(),
                remoteIds = remoteIds,
                signedIn = signedIn != null,
                vehicleName = { id -> vehicles.firstOrNull { it.vehicleId == id }?.let { "${it.manufacturer} ${it.model}" } },
                onOpenTrack = { navController.navigate("track/$it") },
            )

            if (signedIn != null) {
                SectionTitle("Account")
                AccountGroup(
                    onEdit = { navController.navigate("profile/edit") },
                    onSignedOut = { scope.launch { online.profile.clear() } },
                )
            }

            SectionTitle("App")
            ListCard {
                LinkRow(Icons.Default.Settings, "Settings", "Units, contrast, GPS source, server") { navController.navigate("settings") }
                RowDivider()
                LinkRow(Icons.Default.Router, "Rig connection", "ESP32 link and diagnostics") { navController.navigate("esptest") }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

// ── Driver ────────────────────────────────────────────────────

@Composable
private fun DriverCard(
    name: String?,
    country: String?,
    bio: String?,
    memberSince: String?,
    avatar: java.io.File?,
    signedIn: Boolean,
    onEdit: () -> Unit,
    onSignIn: () -> Unit,
) {
    PaddockCard(onClick = if (signedIn) onEdit else null, padding = Spacing.lg) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoFrame(
                file = avatar,
                contentDescription = if (avatar != null) "Profile photo" else null,
                initials = initialsOf(name ?: "Driver"),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name ?: "Driver",
                    style = TrackProType.titleLarge,
                    color = TrackProTheme.colors.marking,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val since = memberSince?.let { runCatching { OffsetDateTime.parse(it).year }.getOrNull() }
                Text(
                    text = when {
                        !signedIn -> "Racing on this phone"
                        else -> listOfNotNull(country, since?.let { "Driver since $it" }).joinToString(" · ").ifEmpty { "TrackBoard driver" }
                    },
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
            }
            if (signedIn) {
                IconCircle(Icons.Default.Edit, size = 36.dp)
            }
        }
        if (!bio.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.md))
            Text(text = bio, style = TrackProType.body, color = TrackProTheme.colors.markingDim, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (!signedIn) {
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Sign in to back up your garage and see where you place on every track.",
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim
            )
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = "Sign in", onClick = onSignIn, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        style = TrackProType.label,
        color = TrackProTheme.colors.marking,
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackProShapes.control)
            .background(TrackProTheme.colors.deltaBad.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    )
}

// ── Career ────────────────────────────────────────────────────

@Composable
private fun CareerCard(career: LocalCareer, server: ProfileStats?) {
    // A fresh phone signed into an existing account has no local history yet; until its
    // sessions are recorded here, the account's own totals are the honest numbers to show.
    val laps = server?.lapCount ?: career.lapCount
    val sessions = server?.sessionCount ?: career.sessionCount
    val tracks = server?.trackCount ?: career.trackCount
    val km = server?.distanceKm ?: career.distanceKm
    val first = career.firstSessionAt
    val dash = "—"

    PaddockCard {
        Text(
            if (first != null) "Laps timed since ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(first))}" else "Laps timed",
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim
        )
        Text(
            if (sessions == 0) dash else "$laps",
            style = TrackProType.displayNumeric,
            color = if (sessions == 0) TrackProTheme.colors.markingDim else TrackProTheme.colors.marking
        )
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TrackProShapes.control)
                .background(TrackProTheme.colors.bgElevated)
        ) {
            Instrument("Sessions", if (sessions == 0) dash else "$sessions", Modifier.weight(1f), valueSize = TrackProType.titleMedium.fontSize)
            Instrument("Tracks", if (tracks == 0) dash else "$tracks", Modifier.weight(1f), valueSize = TrackProType.titleMedium.fontSize)
            Instrument(
                "Km lapped",
                km?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: dash,
                Modifier.weight(1f),
                valueSize = TrackProType.titleMedium.fontSize
            )
        }
    }
}

// ── Main car ──────────────────────────────────────────────────

@Composable
private fun MainCarCard(
    vehicle: VehicleInformationData?,
    photo: (String?) -> java.io.File?,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        if (vehicle == null) {
            PaddockCard(onClick = onAdd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Default.EmojiEvents, size = 44.dp)
                    Spacer(Modifier.width(Spacing.lg))
                    Column(Modifier.weight(1f)) {
                        Text("Add your car", style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
                        Text("Every lap is filed against the car that set it", style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                    }
                }
            }
        } else {
        PaddockCard(onClick = { onOpen(vehicle.vehicleId) }, padding = Spacing.md) {
            PhotoFrame(
                file = photo(vehicle.photoFile),
                contentDescription = "${vehicle.manufacturer} ${vehicle.model}",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${vehicle.manufacturer} ${vehicle.model}",
                        style = TrackProType.titleMedium,
                        color = TrackProTheme.colors.marking,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            vehicle.year.takeIf { it > 0 }?.toString(),
                            vehicle.horsepower.takeIf { it > 0 }?.let { "$it hp" },
                            vehicle.drivetrain.takeIf { it.isNotBlank() },
                            vehicle.weight.takeIf { it > 0 }?.let { "${it.toInt()} kg" },
                        ).joinToString(" · "),
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TrackProTheme.colors.markingDim)
            }
        }
        }
    }
}

// ── Personal bests ────────────────────────────────────────────

private data class BestRow(
    val trackName: String,
    val detail: String,
    val bestMs: Long,
    val rank: Int?,
    val fieldSize: Int?,
    val localTrackId: Long?,
)

@Composable
private fun PersonalBests(
    career: LocalCareer,
    serverBests: List<PersonalBest>,
    remoteIds: Map<Long, String>,
    signedIn: Boolean,
    vehicleName: (Long?) -> String?,
    onOpenTrack: (Long) -> Unit,
) {
    val serverById = serverBests.associateBy { it.trackId }
    val rows = buildList {
        career.bests.forEach { best ->
            val server = remoteIds[best.trackId]?.let(serverById::get)
            add(
                BestRow(
                    trackName = best.trackName,
                    detail = listOfNotNull("${best.lapCount} lap${if (best.lapCount == 1) "" else "s"}", vehicleName(best.vehicleId)).joinToString(" · "),
                    bestMs = best.bestLapMs,
                    rank = server?.rank,
                    fieldSize = server?.fieldSize,
                    localTrackId = best.trackId,
                )
            )
        }
        // Bests the account holds for tracks this phone has never driven: another phone's history.
        val shown = remoteIds.values.toSet()
        serverBests.filter { it.trackId !in shown }.forEach { best ->
            add(
                BestRow(
                    trackName = best.trackName,
                    detail = listOfNotNull(
                        "${best.lapCount} lap${if (best.lapCount == 1) "" else "s"}",
                        best.vehicle?.let { "${it.manufacturer} ${it.model}" },
                        "from your account",
                    ).joinToString(" · "),
                    bestMs = best.bestLapMs.toLong(),
                    rank = best.rank,
                    fieldSize = best.fieldSize,
                    localTrackId = null,
                )
            )
        }
    }.sortedWith(compareBy<BestRow>({ it.rank == null }, { it.rank }, { it.trackName.lowercase() }))

    if (rows.isEmpty()) {
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            PaddockCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Default.EmojiEvents, size = 44.dp)
                    Spacer(Modifier.width(Spacing.lg))
                    Column {
                        Text("No personal bests yet", style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
                        Text("Your best on each track lands here after a timed lap", style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                    }
                }
            }
        }
        return
    }

    ListCard {
        rows.forEachIndexed { i, row ->
            if (i > 0) RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(row.localTrackId?.let { id -> Modifier.pressableRow(onClick = { onOpenTrack(id) }) } ?: Modifier)
                    .heightIn(min = 64.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaceBadge(row.rank)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(row.trackName, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(row.detail, row.fieldSize?.let { "of $it driver${if (it == 1) "" else "s"}" }).joinToString(" · "),
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    row.bestMs.toLapTimeString(),
                    style = TrackProType.statValue.copy(fontSize = TrackProType.titleMedium.fontSize),
                    color = if (row.rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.marking
                )
            }
        }
    }
    if (!signedIn) {
        Text(
            "Places appear when you sign in and share your laps.",
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim,
            modifier = Modifier.padding(horizontal = Spacing.gutter + Spacing.xs, vertical = Spacing.sm)
        )
    }
}

/** A leaderboard place as a round badge: orange for P1, raised for the rest, a dash for none. */
@Composable
private fun PlaceBadge(rank: Int?) {
    val p1 = rank == 1
    Text(
        text = rank?.let { "P$it" } ?: "—",
        style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold),
        color = when {
            p1 -> TrackProTheme.colors.onAccent
            rank != null -> TrackProTheme.colors.marking
            else -> TrackProTheme.colors.markingDim
        },
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (p1) TrackProTheme.colors.accent else TrackProTheme.colors.bgElevated)
            .padding(top = 11.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

// ── Account ───────────────────────────────────────────────────

@Composable
private fun AccountGroup(onEdit: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val scope = rememberCoroutineScope()
    val sharing by online.settings.sharingEnabled.collectAsState()
    val lastSync by online.settings.lastSync.collectAsState()
    val syncing by remember { SyncScheduler.isSyncing(context) }.collectAsState(initial = false)
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            message = try {
                online.profile.export(uri)
                "Export saved"
            } catch (e: ApiException) {
                e.message
            } catch (e: NetworkException) {
                e.message
            }
        }
    }

    if (confirmDelete) {
        DeleteAccountDialog(onDismiss = { confirmDelete = false }, onDeleted = { confirmDelete = false })
    }

    ListCard {
        LinkRow(Icons.Default.Edit, "Edit profile", "Name, photo, country, bio", onClick = onEdit)
        RowDivider()
        ActionRow(
            icon = Icons.Default.Leaderboard,
            label = "Share laps on leaderboards",
            value = if (sharing) "Your laps are on the leaderboards" else "Your laps stay private",
            action = if (sharing) "On" else "Off",
            selected = sharing,
        ) {
            online.settings.setSharingEnabled(!sharing)
            SyncScheduler.syncSoon(context)
        }
        Text(
            // Says exactly what leaves the phone, so the choice is an informed one.
            "Shared: your name, lap and sector times, session date, car make, model and year, and which " +
                "GPS timed it. Only sessions on premade or published tracks. Turning it off takes them back down.",
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim,
            modifier = Modifier.padding(start = 72.dp, end = Spacing.lg, bottom = Spacing.md)
        )
        RowDivider()
        ActionRow(
            icon = Icons.Default.Sync,
            label = "Sync",
            value = describe(lastSync),
            valueColor = if (lastSync?.problem != null && lastSync?.offline != true) TrackProTheme.colors.deltaBad else null,
            action = if (syncing) "Syncing…" else "Sync now",
            selected = false,
        ) {
            if (!syncing) {
                SyncScheduler.syncSoon(context)
                scope.launch { online.profile.refresh() }
            }
        }
        RowDivider()
        LinkRow(Icons.Default.Download, "Export your data", "Everything the server holds, as one JSON file") {
            exporter.launch("trackboard-export.json")
        }
        RowDivider()
        LinkRow(Icons.AutoMirrored.Filled.Logout, "Sign out", "Your records stay on this phone") {
            scope.launch {
                SyncScheduler.cancelAll(context)
                online.events.clear()
                online.auth.signOut()
                onSignedOut()
            }
        }
        RowDivider()
        LinkRow(Icons.Default.DeleteForever, "Delete account", "Removes it from the server for good", danger = true) { confirmDelete = true }
    }
    message?.let {
        Text(it, style = TrackProType.label, color = TrackProTheme.colors.markingDim, modifier = Modifier.padding(horizontal = Spacing.gutter + Spacing.xs, vertical = Spacing.sm))
    }
}

/**
 * Account deletion is irreversible, so it takes a typed word, not a second tap: a tap can be
 * a slip on a bumpy paddock, typing DELETE cannot.
 */
@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = TrackProTheme.colors.bgCard,
        titleContentColor = TrackProTheme.colors.textPrimary,
        textContentColor = TrackProTheme.colors.textMuted,
        title = { Text("Delete your TrackBoard account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Your profile, backed-up cars, photos and every lap you shared are removed from the " +
                        "server, and you leave every leaderboard. The cars, tracks and sessions on this " +
                        "phone are not touched."
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type DELETE to confirm") },
                    singleLine = true,
                    shape = TrackProShapes.control,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrackProTheme.colors.danger,
                        unfocusedBorderColor = TrackProTheme.colors.sectorLine
                    )
                )
                error?.let { Text(it, color = TrackProTheme.colors.danger) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = typed.trim() == "DELETE" && !busy,
                onClick = {
                    busy = true
                    error = null
                    haptics.perform(Haptic.Reject)
                    scope.launch {
                        try {
                            online.profile.deleteAccount()
                            online.events.clear()
                            onDeleted()
                        } catch (e: ApiException) {
                            error = e.message
                        } catch (e: NetworkException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(if (busy) "Deleting…" else "Delete account", color = TrackProTheme.colors.danger, style = TrackProType.titleMedium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = TrackProTheme.colors.textMuted) }
        }
    )
}

// ── Rows ──────────────────────────────────────────────────────

/** A card holding a list of rows, inside the page margin. */
@Composable
internal fun ListCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.gutter)) {
        PaddockCard(padding = 0.dp, content = content)
    }
}

@Composable
internal fun RowDivider() {
    Bezel(modifier = Modifier.padding(start = 72.dp))
}

@Composable
internal fun LinkRow(icon: ImageVector, label: String, detail: String, danger: Boolean = false, onClick: () -> Unit) {
    val tone = if (danger) TrackProTheme.colors.danger else TrackProTheme.colors.accent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(icon, tint = tone, size = 40.dp)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(label, style = TrackProType.titleMedium, color = if (danger) TrackProTheme.colors.danger else TrackProTheme.colors.marking)
            Text(detail, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TrackProTheme.colors.markingDim)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String,
    action: String,
    selected: Boolean,
    valueColor: Color? = null,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconCircle(icon, size = 40.dp)
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(label, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
            Text(value, style = TrackProType.label, color = valueColor ?: TrackProTheme.colors.markingDim)
        }
        Spacer(Modifier.width(Spacing.md))
        ToggleChip(text = action, selected = selected, onClick = onAction)
    }
}
