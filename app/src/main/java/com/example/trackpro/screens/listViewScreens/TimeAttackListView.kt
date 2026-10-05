package com.example.trackpro.screens.listViewScreens

import com.example.trackpro.managerClasses.utilities.DateFormatterUtil
import androidx.compose.ui.res.stringResource
import com.example.trackpro.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.trackpro.components.localizedCountry
import com.example.trackpro.components.trackTypeLabel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.trackpro.components.ConfirmDeleteDialog
import com.example.trackpro.components.DataGate
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.ExpandableGroup
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.SectionSwitchHeight
import com.example.trackpro.components.SessionRow
import com.example.trackpro.components.countLabel
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.screens.history.FilterOption
import com.example.trackpro.screens.history.SessionFilter
import com.example.trackpro.screens.history.SessionFilterBar
import com.example.trackpro.screens.history.SessionFilterBarHeight
import com.example.trackpro.screens.history.SessionKey
import com.example.trackpro.screens.history.SessionSort
import com.example.trackpro.screens.history.applySessionFilter
import com.example.trackpro.screens.history.groupSessions
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.VehicleFULLViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun TimeAttackListViewScreen(
    navController: NavController,
    viewModel: SessionViewModel,
    trackViewModel: TrackViewModel,
    vehicleViewModel: VehicleFULLViewModel,
    /** Null when shown as a tab root, where there is nothing to go back to. */
    onBack: (() -> Unit)? = { navController.popBackStack() },
    header: (@Composable () -> Unit)? = null,
    title: String = stringResource(R.string.history_track_records),
    filter: SessionFilter = SessionFilter(),
    /** Null hides the filter bar. */
    onFilterChange: ((SessionFilter) -> Unit)? = null,
) {
    val allSessions by viewModel.sessions.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val bestLaps by viewModel.bestLapMs.collectAsState()
    val trackSessions = remember(allSessions) { allSessions.filter { it.trackId != null } }
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val tracks by trackViewModel.tracks.collectAsState()

    val keyOf: (SessionData) -> SessionKey = { session ->
        SessionKey(session.vehicleId, session.trackId, session.startTime, bestLaps[session.id])
    }
    val shown = remember(trackSessions, filter, bestLaps) {
        trackSessions.applySessionFilter(filter, ZonedDateTime.now(), keyOf)
    }
    val groups = remember(shown, filter.sort, bestLaps) {
        shown.groupSessions(filter.sort, keyOf) { it.trackId }
    }

    // Only the cars and tracks these sessions actually use, so every choice shows something.
    val removedCar = stringResource(R.string.filter_removed_car)
    val removedTrack = stringResource(R.string.filter_removed_track)
    val carOptions = remember(trackSessions, vehicles, removedCar) {
        trackSessions.mapNotNull { it.vehicleId }.distinct().map { id ->
            FilterOption(id, vehicles.find { it.vehicleId == id }?.displayName() ?: removedCar)
        }.sortedBy { it.label }
    }
    val trackOptions = remember(trackSessions, tracks, removedTrack) {
        trackSessions.mapNotNull { it.trackId }.distinct().map { id ->
            FilterOption(id, tracks.find { it.trackId == id }?.trackName ?: removedTrack)
        }.sortedBy { it.label }
    }

    // Nothing to narrow in an empty history, so the bar only appears once there is something.
    val showFilters = onFilterChange != null && trackSessions.isNotEmpty()
    val fullHeader: (@Composable () -> Unit)? = if (header == null && !showFilters) null else {
        {
            Column {
                if (header != null) Box(Modifier.fillMaxWidth().height(SectionSwitchHeight)) { header() }
                if (showFilters) {
                    SessionFilterBar(
                        filter = filter,
                        onChange = onFilterChange!!,
                        cars = carOptions,
                        tracks = trackOptions,
                        sorts = SessionSort.entries
                    )
                }
            }
        }
    }
    val headerHeight = (if (header != null) SectionSwitchHeight else 0.dp) +
            (if (showFilters) SessionFilterBarHeight else 0.dp)

    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()

    ScreenScaffold(
        title = title,
        onBack = onBack,
        header = fullHeader,
        headerHeight = headerHeight,
        accent = TrackProTheme.colors.accent,
        trailing = {
            Text(
                text = if (filter.narrows) {
                    stringResource(R.string.history_shown_of, shown.size, countLabel(trackSessions.size, R.plurals.count_sessions))
                } else {
                    countLabel(trackSessions.size, R.plurals.count_sessions)
                },
                style = TrackProType.label,
                color = TrackProTheme.colors.textMuted
            )
        },
        contentScrolled = scrolled
    ) { contentPadding ->
        // Gated on every track session, not the filtered ones: "no sessions yet" and "none
        // match these filters" are different situations with different ways out.
        DataGate(
            state = loadState,
            items = trackSessions,
            emptyMessage = stringResource(R.string.history_no_track_sessions),
            emptyHint = stringResource(R.string.history_no_track_sessions_hint),
            loadingLabel = stringResource(R.string.history_reading_sessions),
            emptyActionLabel = stringResource(R.string.history_start_track),
            onEmptyAction = { navController.navigate("trackandvehicle") }
        ) { _ ->
            if (shown.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.history_no_sessions_match),
                    hint = stringResource(R.string.history_no_match_hint),
                    actionLabel = stringResource(R.string.history_clear_filters),
                    onAction = { onFilterChange?.invoke(filter.cleared()) },
                    modifier = Modifier.padding(top = contentPadding.calculateTopPadding())
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = contentPadding.calculateTopPadding() + Spacing.md,
                        bottom = Spacing.md,
                        start = Spacing.gutter,
                        end = Spacing.gutter
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // A single group - one track picked, or only one driven - opens by itself;
                    // collapsed, the filter would seem to have produced an empty card. The key
                    // carries that, so the group resets when it becomes or stops being alone.
                    val alone = groups.size == 1
                    groups.forEach { (trackId, sessions) ->
                        item(key = "$trackId|$alone") {
                            val track = tracks.find { it.trackId == trackId }
                            ExpandableTrackGroup(
                                trackName = track?.trackName ?: stringResource(R.string.history_unknown_track),
                                trackMeta = track?.meta() ?: "",
                                sessions = sessions,
                                vehicles = vehicles,
                                bestLaps = bestLaps,
                                initiallyExpanded = alone,
                                navController = navController,
                                onDelete = { viewModel.deleteSession(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun VehicleInformationData.displayName() = "$manufacturer $model"

@Composable
private fun TrackMainData.meta() = "${localizedCountry(country)} · ${trackTypeLabel(type)}"

/**
 * One track's sessions. [sessions] arrive already in the chosen order and are shown as given -
 * re-sorting here would quietly undo the filter bar's sort.
 */
@Composable
fun ExpandableTrackGroup(
    trackName: String,
    trackMeta: String,
    sessions: List<SessionData>,
    vehicles: List<VehicleInformationData>,
    navController: NavController,
    onDelete: (SessionData) -> Unit,
    bestLaps: Map<Long, Long> = emptyMap(),
    initiallyExpanded: Boolean = false,
) {
    // One slot per group rather than per row: only one dialog can be open at a time, and
    // holding the pending session here keeps each row from carrying its own state.
    var pendingDelete by remember { mutableStateOf<SessionData?>(null) }

    pendingDelete?.let { session ->
        val date = Instant.ofEpochMilli(session.startTime)
            .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DateFormatterUtil.dayMonthPattern()))
        ConfirmDeleteDialog(
            title = stringResource(R.string.history_delete_session),
            message = stringResource(R.string.history_delete_track_session_msg, trackName, date),
            onConfirm = { onDelete(session); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    ExpandableGroup(
        accent = TrackProTheme.colors.accent,
        initiallyExpanded = initiallyExpanded,
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text(trackName, style = TrackProType.titleMedium, color = TrackProTheme.colors.textPrimary)
                Text(
                    listOf(trackMeta, countLabel(sessions.size, R.plurals.count_sessions))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.textMuted
                )
            }
        }
    ) {
        Column {
            sessions.forEach { session ->
                val vehicle = vehicles.find { it.vehicleId == session.vehicleId }
                val startedAt = Instant.ofEpochMilli(session.startTime).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern(DateFormatterUtil.weekdayTimePattern()))
                SessionRow(
                    startTime = session.startTime,
                    title = vehicle?.displayName() ?: stringResource(R.string.history_unknown_car),
                    // The best lap is on the row so ordering by pace can be seen, not trusted.
                    subtitle = bestLaps[session.id]
                        ?.let { stringResource(R.string.history_session_best, startedAt, it.toLapTimeString()) }
                        ?: startedAt,
                    voided = session.voided,
                    onClick = { navController.navigate("timeattacklistitem/${session.id}") },
                    onLongClick = { pendingDelete = session }
                )
            }
        }
    }
}
