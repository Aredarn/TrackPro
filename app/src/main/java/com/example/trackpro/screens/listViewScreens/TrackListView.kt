package com.example.trackpro.screens.listViewScreens

import androidx.compose.ui.res.stringResource
import com.example.trackpro.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.trackpro.components.localizedCountry
import com.example.trackpro.components.trackTypeLabel
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.Spacing
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.IconCircle
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.Icons
import com.example.trackpro.components.DashAction
import androidx.compose.foundation.layout.Box
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
import com.example.trackpro.components.ConfirmDeleteDialog
import com.example.trackpro.components.DataGate
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.countLabel
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.pressableRow
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.viewModels.TrackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrackListScreen(
    navController: NavController,
    viewModel: TrackViewModel,
    /** Null when shown as a tab root, where there is nothing to go back to. */
    onBack: (() -> Unit)? = { navController.popBackStack() },
    header: (@Composable () -> Unit)? = null,
    /** When set, the list opens with the way to build a new track. */
    onBuildTrack: (() -> Unit)? = null,
    title: String = stringResource(R.string.tracks_my),
) {
    val tracks by viewModel.tracks.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
    val database = remember { ESPDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()

    ScreenScaffold(
            title = title,
            onBack = onBack,
            header = header,
            accent = TrackProTheme.colors.accent,
            trailing = {
                Text(
                    text = countLabel(tracks.size, R.plurals.count_tracks),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.textMuted
                )
            },
        contentScrolled = scrolled
    ) { contentPadding ->
      Column(modifier = Modifier.fillMaxSize()) {
        if (onBuildTrack != null) {
            // The builder lives with the tracks it makes. It used to be an instrument on the
            // home panel, one tap from the modes you drive with.
            Box(
                modifier = Modifier
                    .padding(top = contentPadding.calculateTopPadding() + Spacing.xs)
                    .padding(horizontal = Spacing.gutter)
            ) {
                DashAction(
                    label = stringResource(R.string.tracks_build),
                    detail = stringResource(R.string.tracks_build_hint),
                    onClick = onBuildTrack,
                    icon = Icons.Default.AddLocationAlt
                )
            }
        }
        val listTop = if (onBuildTrack != null) 0.dp else contentPadding.calculateTopPadding()
        DataGate(
            state = loadState,
            items = tracks,
            emptyMessage = stringResource(R.string.tracks_none),
            emptyHint = stringResource(R.string.tracks_none_hint),
            loadingLabel = stringResource(R.string.tracks_reading)
        ) { _ ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        top = listTop + Spacing.md,
                        bottom = Spacing.xl
                    ),
                    // Each track is its own aperture in the panel; the gap between them is the
                    // panel showing through, which is what gives the list air without inventing
                    // a card.
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    items(tracks) { track ->
                        TrackCard(
                            track = track,
                            navController = navController,
                            database = database,
                            useMetric = useMetric,
                            bgCard = TrackProTheme.colors.bgCard,
                            bgElevated = TrackProTheme.colors.bgElevated,
                            accent = TrackProTheme.colors.accent,
                            dangerColor = TrackProTheme.colors.danger,
                            textPrimary = TrackProTheme.colors.textPrimary,
                            textMuted = TrackProTheme.colors.textMuted,
                            sectorLine = TrackProTheme.colors.sectorLine,
                            onDelete = { trackToDelete ->
                                scope.launch(Dispatchers.IO) {
                                    database.trackMainDao().deleteTrack(trackToDelete.trackId)
                                }
                            }
                        )
                    }
                }
        
        }
      }
    }
}

@Composable
fun TrackCard(
    track: TrackMainData,
    navController: NavController,
    database: ESPDatabase,
    useMetric: Boolean,
    bgCard: Color,
    bgElevated: Color,
    accent: Color,
    dangerColor: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color,
    onDelete: (TrackMainData) -> Unit
) {
    // Hold to delete, matching the session lists. The old 26dp close button was the
    // smallest target in the app and sat one stray tap from a navigation action.
    var showDeleteDialog by remember { mutableStateOf(false) }
    var bestLapTime by remember(track.trackId) { mutableStateOf<String?>(null) }

    LaunchedEffect(track.trackId) {
        bestLapTime = withContext(Dispatchers.IO) {
            database.lapTimeDataDAO().getBestLapForTrack(track.trackId)?.laptime
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.tracks_delete_title),
            message = stringResource(R.string.tracks_delete_msg, track.trackName),
            onConfirm = { onDelete(track); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false }
        )
    }

    PaddockCard(
        onClick = { navController.navigate("track/${track.trackId}") },
        onLongClick = { showDeleteDialog = true },
        padding = Spacing.lg
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon = Icons.Default.Flag, size = 44.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.trackName,
                    style = TrackProType.titleMedium,
                    color = TrackProTheme.colors.marking,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(trackTypeLabel(track.type), localizedCountry(track.country)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TrackProShapes.control)
                .background(TrackProTheme.colors.bgElevated)
        ) {
            Instrument(
                label = stringResource(R.string.tracks_length),
                // totalLength is stored in km; formatDistance takes meters.
                value = track.totalLength?.let { UnitFormatter.formatDistance(it * 1000.0, useMetric) } ?: "—",
                valueSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Instrument(
                label = stringResource(R.string.tracks_your_record),
                value = bestLapTime ?: "—",
                valueColor = if (bestLapTime != null) TrackProTheme.colors.accent else TrackProTheme.colors.markingDim,
                valueSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}



