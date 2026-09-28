package com.example.trackpro.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.PhotoFrame
import com.example.trackpro.components.Readout
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.initialsOf
import com.example.trackpro.components.isScrolledUnderChrome
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
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.online.describe
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.OffsetDateTime
import java.util.Date

/**
 * The Profile tab: a driver's career sheet, then their account, then the app.
 *
 * The sheet is built from this phone's own records, so it is complete with no account and
 * no network. Signing in lays the account on top — a name and face, and the leaderboard
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
            DriverPlacard(
                name = snapshot?.profile?.displayName ?: signedIn?.displayName,
                country = snapshot?.profile?.country,
                bio = snapshot?.profile?.bio,
                memberSince = snapshot?.profile?.memberSince,
                avatar = online.photos.file(snapshot?.avatarFile),
                signedIn = signedIn != null,
                onEdit = { navController.navigate("profile/edit") },
            )
            Bezel()

            if (signedIn == null) {
                Box(Modifier.padding(12.dp)) {
                    DashAction(
                        label = "Sign in",
                        detail = "Back up your garage · see your leaderboard places",
                        onClick = { navController.navigate("account") },
                        labelSize = 28.sp
                    )
                }
                Bezel()
            } else if (problem != null) {
                Notice(problem!!)
                Bezel()
            }

            CareerReadouts(career = career, server = snapshot?.stats?.takeIf { career.isEmpty })

            MainCarPlacard(
                vehicle = vehicles.firstOrNull { it.vehicleId == career.mainVehicleId } ?: vehicles.singleOrNull(),
                photo = { online.photos.file(it) },
                onOpen = { navController.navigate("vehicle/$it") },
                onAdd = { navController.navigate("createvehicle") },
            )

            PersonalBests(
                career = career,
                serverBests = snapshot?.stats?.personalBests.orEmpty(),
                remoteIds = remoteIds,
                signedIn = signedIn != null,
                vehicleName = { id -> vehicles.firstOrNull { it.vehicleId == id }?.let { "${it.manufacturer} ${it.model}" } },
                onOpenTrack = { navController.navigate("track/$it") },
            )

            if (signedIn != null) {
                AccountGroup(
                    onEdit = { navController.navigate("profile/edit") },
                    onSignedOut = { scope.launch { online.profile.clear() } },
                )
            }

            SectionLabel("App", modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 8.dp))
            Bezel()
            LinkRow("Settings", "Units · theme · GPS source · server") { navController.navigate("settings") }
            Bezel()
            LinkRow("Rig connection", "ESP32 link and diagnostics") { navController.navigate("esptest") }
            Bezel()
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Driver placard ────────────────────────────────────────────

@Composable
private fun DriverPlacard(
    name: String?,
    country: String?,
    bio: String?,
    memberSince: String?,
    avatar: java.io.File?,
    signedIn: Boolean,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.field)
            .then(if (signedIn) Modifier.pressableRow(onClick = onEdit) else Modifier)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhotoFrame(
            file = avatar,
            contentDescription = if (avatar != null) "Profile photo" else null,
            initials = initialsOf(name ?: "Driver"),
            modifier = Modifier.size(84.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (name ?: "Driver").uppercase(),
                style = TrackProType.titleLarge.atSize(22.sp),
                color = TrackProTheme.colors.marking,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val since = memberSince?.let { runCatching { OffsetDateTime.parse(it).year }.getOrNull() }
            Text(
                text = when {
                    !signedIn -> "On this phone"
                    else -> listOfNotNull(country, since?.let { "Since $it" }).joinToString("  ·  ").ifEmpty { "TrackBoard driver" }
                }.uppercase(),
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim
            )
            if (!bio.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = bio,
                    style = TrackProType.body,
                    color = TrackProTheme.colors.markingDim,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (signedIn) {
            Spacer(Modifier.width(8.dp))
            Text("EDIT", style = TrackProType.label, color = TrackProTheme.colors.accent)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text.uppercase(),
        style = TrackProType.label,
        color = TrackProTheme.colors.deltaBad,
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.field)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    )
}

// ── Career ────────────────────────────────────────────────────

@Composable
private fun CareerReadouts(career: LocalCareer, server: com.example.trackpro.online.ProfileStats?) {
    // A fresh phone signed into an existing account has no local history yet; until its
    // sessions are recorded here, the account's own totals are the honest numbers to show.
    val laps = server?.lapCount ?: career.lapCount
    val sessions = server?.sessionCount ?: career.sessionCount
    val tracks = server?.trackCount ?: career.trackCount
    val km = server?.distanceKm ?: career.distanceKm
    val first = career.firstSessionAt
    val dash = "—"

    SectionLabel("Career", modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 8.dp))
    Bezel()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.field)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Readout(
            value = if (sessions == 0) dash else "$laps",
            caption = if (first != null) "Laps timed since ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(first))}"
            else "Laps timed",
            valueColor = if (sessions == 0) TrackProTheme.colors.markingDim else TrackProTheme.colors.marking,
            valueSize = 52.sp
        )
    }
    Bezel()
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicRowHeight)) {
        Instrument("Sessions", if (sessions == 0) dash else "$sessions", Modifier.weight(1f).fillMaxHeight())
        Bezel(vertical = true, modifier = Modifier.height(IntrinsicRowHeight))
        Instrument("Tracks", if (tracks == 0) dash else "$tracks", Modifier.weight(1f).fillMaxHeight())
        Bezel(vertical = true, modifier = Modifier.height(IntrinsicRowHeight))
        Instrument("Km lapped", km?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: dash, Modifier.weight(1f).fillMaxHeight())
    }
    Bezel()
}

private val IntrinsicRowHeight = 58.dp

// ── Main car ──────────────────────────────────────────────────

@Composable
private fun MainCarPlacard(
    vehicle: VehicleInformationData?,
    photo: (String?) -> java.io.File?,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    SectionLabel("Main car", modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 8.dp))
    Bezel()
    if (vehicle == null) {
        Box(Modifier.background(TrackProTheme.colors.panel).padding(12.dp)) {
            DashAction(label = "Add your car", detail = "Sessions are timed against it", onClick = onAdd, compact = true)
        }
        Bezel()
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(onClick = { onOpen(vehicle.vehicleId) })
            .background(TrackProTheme.colors.field)
    ) {
        val file = photo(vehicle.photoFile)
        if (file != null) {
            PhotoFrame(
                file = file,
                contentDescription = "${vehicle.manufacturer} ${vehicle.model}",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${vehicle.manufacturer} ${vehicle.model}".uppercase(),
                    style = TrackProType.titleLarge.atSize(20.sp),
                    color = TrackProTheme.colors.marking,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = listOfNotNull(
                        vehicle.year.takeIf { it > 0 }?.toString(),
                        vehicle.horsepower.takeIf { it > 0 }?.let { "$it HP" },
                        vehicle.drivetrain.takeIf { it.isNotBlank() }?.uppercase(),
                        vehicle.weight.takeIf { it > 0 }?.let { "${it.toInt()} KG" },
                    ).joinToString("  ·  "),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
            }
            Text(if (file == null) "ADD PHOTO ›" else "›", style = TrackProType.label, color = TrackProTheme.colors.accent)
        }
    }
    Bezel()
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
                    detail = listOfNotNull(
                        "${best.lapCount} lap${if (best.lapCount == 1) "" else "s"}",
                        vehicleName(best.vehicleId),
                    ).joinToString("  ·  "),
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
                    ).joinToString("  ·  "),
                    bestMs = best.bestLapMs.toLong(),
                    rank = best.rank,
                    fieldSize = best.fieldSize,
                    localTrackId = null,
                )
            )
        }
    }.sortedWith(compareBy<BestRow>({ it.rank == null }, { it.rank }, { it.trackName.lowercase() }))

    SectionLabel("Personal bests", modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 8.dp))
    Bezel()
    if (rows.isEmpty()) {
        Text(
            text = "NO TIMED LAPS YET · YOUR BEST ON EACH TRACK LANDS HERE",
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim,
            modifier = Modifier.fillMaxWidth().background(TrackProTheme.colors.field).padding(14.dp)
        )
        Bezel()
        return
    }

    // Column heads, placard style, aligned with the values below them.
    Row(
        modifier = Modifier.fillMaxWidth().background(TrackProTheme.colors.panel).padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text("TRACK", style = TrackProType.label, color = TrackProTheme.colors.markingDim, modifier = Modifier.weight(1f))
        Text("BEST", style = TrackProType.label, color = TrackProTheme.colors.markingDim, modifier = Modifier.width(96.dp))
        Text("PLACE", style = TrackProType.label, color = TrackProTheme.colors.markingDim, modifier = Modifier.width(64.dp))
    }
    Bezel()
    rows.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(row.localTrackId?.let { id -> Modifier.pressableRow(onClick = { onOpenTrack(id) }) } ?: Modifier)
                .background(TrackProTheme.colors.field)
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.trackName.uppercase(),
                    style = TrackProType.titleMedium,
                    color = TrackProTheme.colors.marking,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    row.detail.uppercase(),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                row.bestMs.toLapTimeString(),
                style = TrackProType.statValue.atSize(18.sp),
                // P1 is the fastest anyone has gone there: the one place purple is earned here.
                color = if (row.rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.marking,
                modifier = Modifier.width(96.dp)
            )
            Row(Modifier.width(64.dp), verticalAlignment = Alignment.Bottom) {
                if (row.rank != null) {
                    Text(
                        "P${row.rank}",
                        style = TrackProType.statValue.atSize(18.sp),
                        color = if (row.rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.marking
                    )
                    row.fieldSize?.let {
                        Text("/$it", style = TrackProType.label, color = TrackProTheme.colors.markingDim, modifier = Modifier.padding(bottom = 3.dp))
                    }
                } else {
                    Text(
                        "—",
                        style = TrackProType.statValue.atSize(18.sp),
                        color = TrackProTheme.colors.markingDim
                    )
                }
            }
        }
        Bezel()
    }
    if (!signedIn) {
        Text(
            "PLACES APPEAR WHEN YOU SIGN IN AND SHARE YOUR LAPS",
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
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
                "Export saved."
            } catch (e: ApiException) {
                e.message
            } catch (e: NetworkException) {
                e.message
            }
        }
    }

    if (confirmDelete) {
        DeleteAccountDialog(
            onDismiss = { confirmDelete = false },
            onDeleted = { confirmDelete = false },
        )
    }

    SectionLabel("Account", modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 8.dp))
    Bezel()
    LinkRow("Edit profile", "Name · photo · country · bio", onClick = onEdit)
    Bezel()
    ActionRow(
        label = "Leaderboards",
        value = if (sharing) "Sharing your laps" else "Not sharing your laps",
        action = if (sharing) "On" else "Off",
        selected = sharing,
    ) {
        online.settings.setSharingEnabled(!sharing)
        SyncScheduler.syncSoon(context)
    }
    Text(
        // Says exactly what leaves the phone, so the choice is an informed one.
        "Shared: your name, lap and sector times, session date, car make, model and year, and which " +
            "GPS timed it. Only sessions on premade or published tracks. Stopping takes them back down.",
        style = TrackProType.body.atSize(12.sp),
        color = TrackProTheme.colors.markingDim,
        modifier = Modifier.fillMaxWidth().background(TrackProTheme.colors.field).padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
    )
    Bezel()
    ActionRow(
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
    Bezel()
    LinkRow("Export my data", "Everything the server holds, as one JSON file") {
        exporter.launch("trackboard-export.json")
    }
    Bezel()
    LinkRow("Sign out", "Your records stay on this phone") {
        scope.launch {
            SyncScheduler.cancelAll(context)
            online.auth.signOut()
            onSignedOut()
        }
    }
    Bezel()
    LinkRow("Delete account", "Removes it from the server for good", danger = true) { confirmDelete = true }
    Bezel()
    message?.let {
        Text(it.uppercase(), style = TrackProType.label, color = TrackProTheme.colors.markingDim, modifier = Modifier.padding(14.dp))
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Your profile, backed-up cars, photos and every lap you shared are removed from the " +
                        "server, and you leave every leaderboard. The cars, tracks and sessions on this " +
                        "phone are not touched."
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type DELETE to confirm", color = TrackProTheme.colors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TrackProTheme.colors.textPrimary,
                        unfocusedTextColor = TrackProTheme.colors.textPrimary,
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

@Composable
internal fun LinkRow(label: String, detail: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(onClick = onClick)
            .background(TrackProTheme.colors.field)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label.uppercase(),
                style = TrackProType.titleMedium,
                color = if (danger) TrackProTheme.colors.danger else TrackProTheme.colors.marking
            )
            Spacer(Modifier.height(2.dp))
            Text(detail, style = TrackProType.body.atSize(12.sp), color = TrackProTheme.colors.markingDim)
        }
        Text("›", style = TrackProType.titleLarge, color = if (danger) TrackProTheme.colors.danger else TrackProTheme.colors.markingDim)
    }
}

@Composable
private fun ActionRow(
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
            .background(TrackProTheme.colors.field)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
            Spacer(Modifier.height(2.dp))
            Text(value, style = TrackProType.body.atSize(12.sp), color = valueColor ?: TrackProTheme.colors.markingDim)
        }
        Spacer(Modifier.width(10.dp))
        ToggleChip(text = action, selected = selected, onClick = onAction)
    }
}
