package com.example.trackpro.screens.listViewScreens

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
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.DateFormatterUtil
import com.example.trackpro.models.DragSessionWithVehicle
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
import com.example.trackpro.viewModels.DragSessionViewModel
import java.time.ZonedDateTime
import java.util.Date

/**
 * What drag runs can be ordered by. No "fastest": a run's times are derived from its GPS trace
 * when it is opened, so ranking the list would mean replaying every run first.
 */
private val DRAG_SORTS = listOf(SessionSort.NEWEST, SessionSort.OLDEST)

@Composable
fun DragTimesListView(
    viewModel: DragSessionViewModel,
    navController: NavController,
    /** Null when shown as a tab root, where there is nothing to go back to. */
    onBack: (() -> Unit)? = { navController.popBackStack() },
    header: (@Composable () -> Unit)? = null,
    title: String = stringResource(R.string.history_drag_records),
    filter: SessionFilter = SessionFilter(),
    /** Null hides the filter bar. */
    onFilterChange: ((SessionFilter) -> Unit)? = null,
) {
    val dragSessions by viewModel.dragSessions.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    // A track filter never applies here, and a stale "fastest" from elsewhere falls back.
    val effective = filter.copy(
        trackId = null,
        sort = if (filter.sort in DRAG_SORTS) filter.sort else SessionSort.NEWEST
    )
    val keyOf: (DragSessionWithVehicle) -> SessionKey = { SessionKey(it.vehicleId, null, it.startTime) }
    val shown = remember(dragSessions, effective) {
        dragSessions.applySessionFilter(effective, ZonedDateTime.now(), keyOf)
    }
    val groups = remember(shown, effective.sort) {
        shown.groupSessions(effective.sort, keyOf) { session ->
            val date = DateFormatterUtil.getDateFormat().format(Date(session.startTime))
            "$date | ${session.manufacturer} ${session.model}"
        }
    }
    val carOptions = remember(dragSessions) {
        dragSessions.distinctBy { it.vehicleId }
            .map { FilterOption(it.vehicleId, "${it.manufacturer} ${it.model}") }
            .sortedBy { it.label }
    }

    val showFilters = onFilterChange != null && dragSessions.isNotEmpty()
    val fullHeader: (@Composable () -> Unit)? = if (header == null && !showFilters) null else {
        {
            Column {
                if (header != null) Box(Modifier.fillMaxWidth().height(SectionSwitchHeight)) { header() }
                if (showFilters) {
                    SessionFilterBar(
                        filter = effective,
                        onChange = onFilterChange!!,
                        cars = carOptions,
                        tracks = null,
                        sorts = DRAG_SORTS
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
                text = if (effective.narrows) {
                    stringResource(R.string.history_shown_of, shown.size, countLabel(dragSessions.size, R.plurals.count_runs))
                } else {
                    countLabel(dragSessions.size, R.plurals.count_runs)
                },
                style = TrackProType.label,
                color = TrackProTheme.colors.textMuted
            )
        },
        contentScrolled = scrolled
    ) { contentPadding ->
        // Gated on every run, not the filtered ones: "no runs yet" and "none match these
        // filters" are different situations with different ways out.
        DataGate(
            state = loadState,
            items = dragSessions,
            emptyMessage = stringResource(R.string.history_no_drag_runs),
            emptyHint = stringResource(R.string.history_no_drag_runs_hint),
            loadingLabel = stringResource(R.string.history_reading_runs),
            emptyActionLabel = stringResource(R.string.history_start_drag),
            onEmptyAction = { navController.navigate("drag") }
        ) { _ ->
            if (shown.isEmpty()) {
                EmptyState(
                    message = stringResource(R.string.history_no_runs_match),
                    hint = stringResource(R.string.history_no_match_hint),
                    actionLabel = stringResource(R.string.history_clear_filters),
                    onAction = { onFilterChange?.invoke(effective.cleared()) },
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
                    // A lone group opens by itself; see TimeAttackListViewScreen.
                    val alone = groups.size == 1
                    groups.forEach { (groupKey, sessions) ->
                        item(key = "$groupKey|$alone") {
                            ExpandableSessionGroup(
                                groupTitle = groupKey,
                                sessions = sessions,
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

/**
 * One day's runs in one car. [sessions] arrive already in the chosen order and are shown as
 * given - re-sorting here would quietly undo the filter bar's sort.
 */
@Composable
fun ExpandableSessionGroup(
    groupTitle: String,
    sessions: List<DragSessionWithVehicle>,
    navController: NavController,
    onDelete: (DragSessionWithVehicle) -> Unit,
    initiallyExpanded: Boolean = false,
) {
    // One slot per group rather than per row: only one dialog can be open at a time, and
    // holding the pending session here keeps each row from carrying its own state.
    var pendingDelete by remember { mutableStateOf<DragSessionWithVehicle?>(null) }

    pendingDelete?.let { session ->
        val time = DateFormatterUtil.getTimeFormat().format(Date(session.startTime))
        ConfirmDeleteDialog(
            title = stringResource(R.string.history_delete_session),
            message = stringResource(R.string.history_delete_run_msg, time),
            onConfirm = { onDelete(session); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }

    ExpandableGroup(
        accent = TrackProTheme.colors.accent,
        initiallyExpanded = initiallyExpanded,
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text(groupTitle, style = TrackProType.titleMedium, color = TrackProTheme.colors.textPrimary)
                Text(
                    countLabel(sessions.size, R.plurals.count_runs),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.textMuted
                )
            }
        }
    ) {
        Column {
            sessions.forEach { session ->
                SessionRow(
                    startTime = session.startTime,
                    title = stringResource(R.string.history_run_at, DateFormatterUtil.getTimeFormat().format(Date(session.startTime))),
                    subtitle = "${session.manufacturer} ${session.model}",
                    voided = session.voided,
                    onClick = { navController.navigate("graph/${session.sessionId}") },
                    onLongClick = { pendingDelete = session }
                )
            }
        }
    }
}
