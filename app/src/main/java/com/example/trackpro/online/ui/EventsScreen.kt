package com.example.trackpro.online.ui

import com.example.trackpro.online.MessageText
import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.DashTabBarHeight
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.IconCircle
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.SectionTitle
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.toLapDeltaString
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.ApiEventStatus
import com.example.trackpro.online.ApiException
import com.example.trackpro.online.EventBoardEntry
import com.example.trackpro.online.EventDetail
import com.example.trackpro.online.EventSummary
import com.example.trackpro.online.NetworkException
import com.example.trackpro.online.SyncProblem
import com.example.trackpro.online.parseInstant
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The Events tab: track days on TrackBoard. Join with the host's code, see the one that is
 * live right now with your place in it, and every event you have joined or host.
 *
 * Hosting happens on the website; the phone is for the people driving.
 */
@Composable
fun EventsScreen(onOpenEvent: (String) -> Unit, onSignIn: () -> Unit) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val online = app.online
    val account by online.auth.state.collectAsState()

    if (account !is AccountState.SignedIn) {
        ScreenScaffold(title = stringResource(R.string.tab_events)) { padding ->
            EmptyState(
                message = stringResource(R.string.events_signed_out),
                hint = stringResource(R.string.events_signed_out_hint),
                icon = Icons.AutoMirrored.Outlined.Login,
                actionLabel = stringResource(R.string.common_sign_in),
                onAction = onSignIn,
                modifier = Modifier.padding(top = padding.calculateTopPadding())
            )
        }
        return
    }

    var events by remember { mutableStateOf<List<EventSummary>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loadError = null
        try {
            events = online.events.refresh()
        } catch (e: NetworkException) {
            loadError = app.getString(R.string.events_offline)
        } catch (e: ApiException) {
            // A server from before events existed answers the listing with 404.
            loadError = if (e.status == 404) app.getString(R.string.events_server_none) else MessageText.localize(e.message)
        }
    }

    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()
    val joined by online.events.joined.collectAsState()
    // Offline, fall back to what the phone remembers, so a paddock with no signal still works.
    val shown = events ?: joined.map { it.asSummary() }
    val live = shown.firstOrNull { it.isJoined && it.status == ApiEventStatus.Live }

    ScreenScaffold(title = stringResource(R.string.tab_events), contentScrolled = scrolled) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + Spacing.sm, bottom = DashTabBarHeight + Spacing.xxl)
        ) {
            if (live != null) {
                item(key = "live") { LiveNowCard(live, onOpen = { onOpenEvent(live.id) }) }
            }

            item(key = "join") {
                SectionTitle(stringResource(R.string.events_join_title))
                JoinCard(onJoined = { id ->
                    reload++
                    onOpenEvent(id)
                })
            }

            loadError?.let { message ->
                item(key = "error") {
                    Text(
                        message,
                        style = TrackProType.body,
                        color = TrackProTheme.colors.deltaBad,
                        modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
                    )
                }
            }

            item(key = "mine-title") { SectionTitle(stringResource(R.string.events_yours), action = stringResource(R.string.rig_refresh), onAction = { reload++ }) }

            if (shown.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (events == null && loadError == null) stringResource(R.string.events_fetching)
                        else stringResource(R.string.events_none),
                        style = TrackProType.body,
                        color = TrackProTheme.colors.markingDim,
                        modifier = Modifier.padding(horizontal = Spacing.gutter)
                    )
                }
            }

            items(shown, key = { it.id }) { event ->
                EventCard(event, onClick = { onOpenEvent(event.id) })
            }

            item(key = "host-note") {
                Text(
                    stringResource(R.string.events_host_note),
                    style = TrackProType.body,
                    color = TrackProTheme.colors.markingDim,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xl)
                )
            }
        }
    }
}

/**
 * The peak of the tab: the event running right now, with where the driver stands in it.
 * Refreshed while on screen, so it moves as laps come in.
 */
@Composable
private fun LiveNowCard(event: EventSummary, onOpen: () -> Unit) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val online = app.online
    val myId = (online.auth.state.value as? AccountState.SignedIn)?.userId
    var me by remember { mutableStateOf<EventBoardEntry?>(null) }
    var fieldSize by remember { mutableIntStateOf(0) }
    var hasTrack by remember { mutableStateOf(true) }

    LaunchedEffect(event.id) {
        hasTrack = online.events.localTrackFor(event.trackId) != null
        while (true) {
            runCatching { online.events.board(event.id) }.getOrNull()?.let { board ->
                me = board.entries.firstOrNull { it.userId == myId }
                fieldSize = board.entries.count { it.rank != null }
            }
            kotlinx.coroutines.delay(LIVE_REFRESH_MS)
        }
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)) {
        PaddockCard(onClick = onOpen, padding = Spacing.xl) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.events_live_now), style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold), color = TrackProTheme.colors.deltaGood)
                Spacer(Modifier.weight(1f))
                Text(timeLeft(event.endsAt), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
            }
            Spacer(Modifier.height(Spacing.md))
            Text(event.name, style = TrackProType.titleLarge, color = TrackProTheme.colors.marking, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(event.trackName, style = TrackProType.body, color = TrackProTheme.colors.markingDim)
            Spacer(Modifier.height(Spacing.lg))

            val mine = me
            if (mine?.rank != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    // The value leads: the place is what the driver came to see.
                    Text(
                        "P${mine.rank}",
                        style = TrackProType.displayNumeric.atSize(48.sp),
                        color = if (mine.rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.marking
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                        Text(stringResource(R.string.events_of_field, fieldSize), style = TrackProType.body, color = TrackProTheme.colors.markingDim)
                        Text(
                            buildString {
                                append(mine.bestLapMs?.toLong()?.toLapTimeString() ?: "")
                                mine.gapToLeaderMs?.takeIf { it > 0 }?.let { append("  ·  ").append(it.toLong().toLapDeltaString()) }
                            },
                            style = TrackProType.titleMedium,
                            color = TrackProTheme.colors.marking
                        )
                    }
                }
            } else {
                Text(
                    if (hasTrack) stringResource(R.string.events_no_lap_yet, event.trackName)
                    else stringResource(R.string.events_get_track_first, event.trackName),
                    style = TrackProType.body,
                    color = TrackProTheme.colors.markingDim
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(text = stringResource(R.string.events_open_board), onClick = onOpen, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Code in, event out: the code is looked up first, so the driver sees what they are joining
 * and picks a run group before committing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JoinCard(onJoined: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val online = app.online
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<EventDetail?>(null) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun lookUp() {
        val clean = code.filter(Char::isLetterOrDigit)
        if (clean.length < 4) {
            error = app.getString(R.string.events_enter_code)
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                found = online.events.lookUp(clean)
                groupId = found?.event?.myGroupId
            } catch (e: ApiException) {
                error = if (e.status == 404) app.getString(R.string.events_no_such_code) else MessageText.localize(e.message)
            } catch (e: NetworkException) {
                error = app.getString(R.string.events_join_needs_connection, MessageText.localize(e.message) ?: "")
            } finally {
                busy = false
            }
        }
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
        PaddockCard(padding = Spacing.lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.uppercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.take(9)
                        found = null
                    },
                    label = { Text(stringResource(R.string.events_join_code), color = TrackProTheme.colors.textMuted) },
                    placeholder = { Text("K7Q 2M9", color = TrackProTheme.colors.textFaint) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { lookUp() }),
                    textStyle = TrackProType.titleLarge,
                    modifier = Modifier.weight(1f),
                    colors = fieldColors()
                )
                Spacer(Modifier.width(Spacing.md))
                ToggleChip(text = if (busy && found == null) "…" else stringResource(R.string.events_find), selected = false, onClick = { if (!busy) lookUp() })
            }

            error?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = TrackProType.body, color = TrackProTheme.colors.deltaBad)
            }

            found?.let { detail ->
                val ev = detail.event
                Spacer(Modifier.height(Spacing.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(icon = Icons.Default.Flag)
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ev.name, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
                        Text(
                            stringResource(R.string.events_found_line, ev.trackName, window(ev.startsAt, ev.endsAt), ev.hostDisplayName),
                            style = TrackProType.body,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                }

                if (detail.groups.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.lg))
                    Text(stringResource(R.string.events_run_group), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                    Spacer(Modifier.height(Spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        detail.groups.forEach { g ->
                            ToggleChip(text = g.name, selected = groupId == g.id, onClick = { groupId = if (groupId == g.id) null else g.id })
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                Text(
                    stringResource(R.string.events_join_shares, ev.trackName),
                    style = TrackProType.body,
                    color = TrackProTheme.colors.markingDim
                )
                Spacer(Modifier.height(Spacing.lg))
                PrimaryButton(
                    text = when {
                        busy -> stringResource(R.string.events_joining)
                        ev.isJoined -> stringResource(R.string.events_update_group)
                        else -> stringResource(R.string.events_join)
                    },
                    enabled = !busy && ev.status != ApiEventStatus.Finished,
                    haptic = Haptic.Confirm,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                online.events.join(code.filter(Char::isLetterOrDigit), groupId)
                                // The event's track: fetched now, while there is signal, so the
                                // session at the circuit is timed on the host's gates.
                                runCatching { online.events.downloadTrack(ev.trackId) }
                                code = ""
                                found = null
                                onJoined(ev.id)
                            } catch (e: ApiException) {
                                error = if (e.status == 409) app.getString(R.string.events_finished_short) else MessageText.localize(e.message)
                            } catch (e: NetworkException) {
                                error = app.getString(R.string.events_join_needs_connection, MessageText.localize(e.message) ?: "")
                            } catch (e: SyncProblem) {
                                error = MessageText.localize(e.message)
                            } finally {
                                busy = false
                            }
                        }
                    }
                )
                if (ev.status == ApiEventStatus.Finished) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(stringResource(R.string.events_finished_open), style = TrackProType.body, color = TrackProTheme.colors.markingDim)
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: EventSummary, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.xs)) {
        PaddockCard(onClick = onClick, padding = Spacing.lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(
                    icon = Icons.Default.Flag,
                    tint = if (event.status == ApiEventStatus.Live) TrackProTheme.colors.accent else TrackProTheme.colors.accentMuted
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.name, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${event.trackName} · ${window(event.startsAt, event.endsAt)}",
                        style = TrackProType.body,
                        color = TrackProTheme.colors.markingDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(event.status)
                    if (event.isHost) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(stringResource(R.string.events_host), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusPill(status: ApiEventStatus) {
    val (label, tone) = when (status) {
        ApiEventStatus.Live -> stringResource(R.string.hud_live) to TrackProTheme.colors.deltaGood
        ApiEventStatus.Upcoming -> stringResource(R.string.events_upcoming) to TrackProTheme.colors.accent
        ApiEventStatus.Finished -> stringResource(R.string.events_finished) to TrackProTheme.colors.textFaint
    }
    Row(
        modifier = Modifier
            .clip(TrackProShapes.pill)
            .background(tone.copy(alpha = 0.14f))
            .heightIn(min = 28.dp)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == ApiEventStatus.Live) {
            LiveDot(tone)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold), color = tone)
    }
}

@Composable
internal fun LiveDot(tone: Color = TrackProTheme.colors.deltaGood) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
}

/** How the phone shows a remembered event while offline: enough for the list and the live card. */
private fun com.example.trackpro.online.JoinedEvent.asSummary(): EventSummary {
    val now = System.currentTimeMillis()
    val status = when {
        now < startsAtMs -> ApiEventStatus.Upcoming
        now > endsAtMs -> ApiEventStatus.Finished
        else -> ApiEventStatus.Live
    }
    return EventSummary(
        id = id,
        name = name,
        trackId = trackId,
        trackName = trackName,
        startsAt = java.time.Instant.ofEpochMilli(startsAtMs).toString(),
        endsAt = java.time.Instant.ofEpochMilli(endsAtMs).toString(),
        status = status,
        isJoined = true,
        myGroupId = groupId,
    )
}

internal fun window(startsAt: String, endsAt: String): String {
    val start = parseInstant(startsAt) ?: return ""
    val end = parseInstant(endsAt) ?: return ""
    val day = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    return if (day.format(Date(start)) == day.format(Date(end))) {
        "${day.format(Date(start))}, ${time.format(Date(start))}–${time.format(Date(end))}"
    } else {
        "${day.format(Date(start))} – ${day.format(Date(end))}"
    }
}

@Composable
private fun timeLeft(endsAt: String): String {
    val end = parseInstant(endsAt) ?: return ""
    val minutes = ((end - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) stringResource(R.string.events_left_hm, (minutes / 60).toInt(), (minutes % 60).toInt())
        else stringResource(R.string.events_left_m, minutes.toInt())
}

internal const val LIVE_REFRESH_MS = 5_000L
