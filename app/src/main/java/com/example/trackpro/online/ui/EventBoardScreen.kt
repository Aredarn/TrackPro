package com.example.trackpro.online.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.DataGate
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.SectionSwitch
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.paddockCard
import com.example.trackpro.components.pressable
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.toLapDeltaString
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.models.LoadState
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.ApiEventStatus
import com.example.trackpro.online.ApiException
import com.example.trackpro.online.EventBoard
import com.example.trackpro.online.EventBoardEntry
import com.example.trackpro.online.NetworkException
import com.example.trackpro.online.SyncProblem
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One event's live board: every joined driver by best lap, overall or within a run group.
 * Refreshes every few seconds while the event is live and the screen is open; a lap the
 * driver completes on the event's track posts itself and shows here moments later.
 */
@Composable
fun EventBoardScreen(eventId: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val online = app.online
    val scope = rememberCoroutineScope()
    val myId = (online.auth.state.value as? AccountState.SignedIn)?.userId

    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var board by remember { mutableStateOf<EventBoard?>(null) }
    var group by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var localTrack by remember { mutableStateOf<Long?>(null) }
    var trackBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    // Polls while the screen is open; leaving it cancels the loop with the composition.
    LaunchedEffect(eventId, reload) {
        if (board == null) state = LoadState.Loading
        while (true) {
            try {
                val next = online.events.board(eventId)
                if (next == null) {
                    state = LoadState.Failed("This event no longer exists.")
                    return@LaunchedEffect
                }
                board = next
                state = LoadState.Ready
                if (localTrack == null) localTrack = online.events.localTrackFor(next.trackId)
            } catch (e: NetworkException) {
                // Keep the last board up through a signal drop in the paddock.
                if (board == null) state = LoadState.Failed(e.message ?: "TrackBoard could not be reached.")
            } catch (e: ApiException) {
                if (board == null) state = LoadState.Failed(e.message ?: "TrackBoard refused the request.")
            }
            delay(if (board?.status == ApiEventStatus.Live) LIVE_REFRESH_MS else IDLE_REFRESH_MS)
        }
    }

    val b = board
    val entries = b?.entries.orEmpty().filter { group == null || it.groupId == group }
    val mine = b?.entries?.firstOrNull { it.userId == myId }
    val joined = mine != null
    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()
    val groups = b?.groups.orEmpty()

    ScreenScaffold(
        title = b?.name ?: "Event",
        subtitle = b?.let { "${it.trackName} · ${window(it.startsAt, it.endsAt)}" },
        onBack = onBack,
        trailing = { b?.let { StatusPill(it.status) } },
        contentScrolled = scrolled,
        header = if (groups.isNotEmpty()) {
            {
                SectionSwitch(
                    options = listOf<Pair<String?, String>>(null to "Overall") + groups.map { it.id to it.name },
                    selected = group,
                    onSelect = { group = it }
                )
            }
        } else null
    ) { padding ->
        DataGate(
            state = state,
            items = entries,
            emptyMessage = if (group == null) "Nobody has joined yet" else "Nobody in this group yet",
            emptyHint = "Drivers join with the host's code in the Events tab. Their laps appear here as they drive.",
            loadingLabel = "Fetching the board",
            onRetry = { reload++ },
        ) { rows ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + Spacing.sm, bottom = Spacing.xxl)
            ) {
                if (b != null && joined && localTrack == null) {
                    item(key = "track") {
                        GetTrackCard(
                            trackName = b.trackName,
                            busy = trackBusy,
                            onGet = {
                                trackBusy = true
                                scope.launch {
                                    notice = try {
                                        localTrack = online.events.downloadTrack(b.trackId)
                                        "${b.trackName} is on this phone. Pick it when you start a session."
                                    } catch (e: SyncProblem) {
                                        e.message
                                    } catch (e: NetworkException) {
                                        "Getting the track needs a connection."
                                    } catch (e: ApiException) {
                                        e.message
                                    }
                                    trackBusy = false
                                }
                            }
                        )
                    }
                }

                if (mine != null && mine.rank != null) {
                    item(key = "me") { MyPlaceCard(mine, fieldSize = b?.entries?.count { it.rank != null } ?: 0, inGroup = group != null) }
                }

                notice?.let {
                    item(key = "notice") {
                        Text(it, style = TrackProType.body, color = TrackProTheme.colors.markingDim, modifier = Modifier.padding(Spacing.gutter))
                    }
                }

                item(key = "board-top") { Spacer(Modifier.height(Spacing.sm)) }
                items(rows, key = { it.userId }) { entry ->
                    BoardRow(entry, isMe = entry.userId == myId, inGroup = group != null, groupName = groups.firstOrNull { it.id == entry.groupId }?.name)
                }

                item(key = "foot") {
                    Text(
                        "Every lap a joined driver drives on this track during the event counts, private sessions included. " +
                            "Times are posted by drivers and not verified.",
                        style = TrackProType.body,
                        color = TrackProTheme.colors.markingDim,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg)
                    )
                }

                if (joined) {
                    item(key = "leave") {
                        Box(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                            if (confirmLeave) {
                                PrimaryButton(
                                    text = "Leave and remove my laps from this board",
                                    accent = TrackProTheme.colors.danger,
                                    contentColor = TrackProTheme.colors.onAccent,
                                    haptic = Haptic.Reject,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        scope.launch {
                                            try {
                                                online.events.leave(eventId)
                                                onBack()
                                            } catch (e: Exception) {
                                                notice = e.message ?: "Could not leave the event."
                                                confirmLeave = false
                                            }
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    "Leave event",
                                    style = TrackProType.titleMedium,
                                    color = TrackProTheme.colors.danger,
                                    modifier = Modifier
                                        .pressable(onClick = { confirmLeave = true }, scale = 0.96f)
                                        .heightIn(min = 48.dp)
                                        .padding(vertical = Spacing.md)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The driver's own standing, lifted out of the list: the first thing they look for. */
@Composable
private fun MyPlaceCard(me: EventBoardEntry, fieldSize: Int, inGroup: Boolean) {
    val rank = if (inGroup) me.groupRank else me.rank
    val gap = if (inGroup) me.gapToGroupLeaderMs else me.gapToLeaderMs
    Box(modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
        PaddockCard(padding = Spacing.xl) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "P${rank ?: "–"}",
                    style = TrackProType.displayNumeric.atSize(44.sp),
                    color = if (rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.marking
                )
                Spacer(Modifier.width(Spacing.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (rank == 1) "You're fastest" else "You, of $fieldSize",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim
                    )
                    Text(me.bestLapMs?.toLong()?.toLapTimeString() ?: "", style = TrackProType.statValue, color = TrackProTheme.colors.marking)
                    Text(
                        listOfNotNull(
                            gap?.takeIf { it > 0 }?.let { "${it.toLong().toLapDeltaString()} to P1" },
                            "${me.lapCount} laps",
                        ).joinToString(" · "),
                        style = TrackProType.body,
                        color = TrackProTheme.colors.markingDim
                    )
                }
                if (me.onTrack) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot()
                        Spacer(Modifier.width(6.dp))
                        Text("On track", style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold), color = TrackProTheme.colors.deltaGood)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardRow(entry: EventBoardEntry, isMe: Boolean, inGroup: Boolean, groupName: String?) {
    val rank = if (inGroup) entry.groupRank else entry.rank
    val gap = if (inGroup) entry.gapToGroupLeaderMs else entry.gapToLeaderMs
    // A new best lap lands with a brief orange wash, then settles.
    var previousBest by remember { mutableStateOf(entry.bestLapMs) }
    var fresh by remember { mutableStateOf(false) }
    LaunchedEffect(entry.bestLapMs) {
        if (previousBest != null && entry.bestLapMs != previousBest) {
            fresh = true
            delay(2_500)
            fresh = false
        }
        previousBest = entry.bestLapMs
    }
    val wash by animateColorAsState(
        when {
            fresh -> TrackProTheme.colors.accent.copy(alpha = 0.18f)
            isMe -> TrackProTheme.colors.accent.copy(alpha = 0.08f)
            else -> TrackProTheme.colors.accent.copy(alpha = 0f)
        },
        animationSpec = tween(600),
        label = "rowWash"
    )

    Column(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TrackProShapes.chip)
                .background(wash)
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                rank?.toString() ?: "–",
                style = TrackProType.titleLarge,
                color = if (rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.marking,
                modifier = Modifier.width(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isMe) "${entry.displayName} · you" else entry.displayName,
                        style = TrackProType.titleMedium,
                        color = TrackProTheme.colors.marking,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.onTrack) {
                        Spacer(Modifier.width(Spacing.sm))
                        LiveDot()
                    }
                }
                Text(
                    listOfNotNull(
                        if (!inGroup) groupName else null,
                        entry.vehicle?.let { "${it.manufacturer} ${it.model}" },
                        "${entry.lapCount} laps",
                    ).joinToString(" · "),
                    style = TrackProType.body,
                    color = TrackProTheme.colors.markingDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    entry.bestLapMs?.toLong()?.toLapTimeString() ?: "No lap",
                    style = TrackProType.titleLarge,
                    color = if (rank == 1) TrackProTheme.colors.accent else if (entry.bestLapMs == null) TrackProTheme.colors.textFaint else TrackProTheme.colors.marking
                )
                val sub = when {
                    entry.lastLapIsBest && entry.lastLapMs != null -> "Last lap PB"
                    gap != null && gap > 0 -> gap.toLong().toLapDeltaString()
                    else -> null
                }
                if (sub != null) {
                    Text(
                        sub,
                        style = TrackProType.body.atSize(12.sp),
                        color = if (entry.lastLapIsBest) TrackProTheme.colors.deltaGood else TrackProTheme.colors.markingDim
                    )
                }
            }
        }
        HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp, modifier = Modifier.padding(horizontal = Spacing.md))
    }
}

/** The event's track is not on this phone: without it, the driver's laps are timed on other gates. */
@Composable
private fun GetTrackCard(trackName: String, busy: Boolean, onGet: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .paddockCard(color = TrackProTheme.colors.deltaBad.copy(alpha = 0.10f))
                .padding(Spacing.lg)
        ) {
            Text("Get the event's track", style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "$trackName isn't on this phone yet. Download it so your laps are timed on the same gates as everyone else's.",
                style = TrackProType.body,
                color = TrackProTheme.colors.markingDim
            )
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(text = if (busy) "Downloading…" else "Get $trackName", enabled = !busy, onClick = onGet, modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val IDLE_REFRESH_MS = 60_000L
