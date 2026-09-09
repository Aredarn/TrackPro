package com.example.trackpro.screens.listViewScreens

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
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.ScreenScaffold
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
fun TrackListScreen(navController: NavController, viewModel: TrackViewModel) {
    val tracks by viewModel.tracks.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
    val database = remember { ESPDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()

    ScreenScaffold(
            title = "My Tracks",
            onBack = { navController.popBackStack() },
            accent = TrackProTheme.colors.accent,
            trailing = {
                Text(
                    text = "${tracks.size} tracks",
                    style = TrackProType.label,
                    color = TrackProTheme.colors.textMuted
                )
            },
        contentScrolled = scrolled
    ) { contentPadding ->
        if (tracks.isEmpty()) {
            EmptyState(message = "No tracks yet", hint = "Build a track to see it here")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 10.dp,
                    bottom = 24.dp
                ),
                // Each track is its own aperture in the panel; the gap between them is the
                // panel showing through, which is what gives the list air without inventing
                // a card.
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
            title = "Delete track?",
            message = "${track.trackName} and every lap recorded on it will be " +
                "permanently removed.",
            onConfirm = { onDelete(track); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pressableRow(onClick = { navController.navigate("track/${track.trackId}") })
            .background(TrackProTheme.colors.field)
    ) {
        // The name leads by a clear margin - 26sp against 17sp readouts. The previous
        // pass set it at 19sp beside 18sp values, so nothing led and every row read as
        // one undifferentiated block.
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp)
        ) {
            Text(
                text = track.trackName,
                style = TrackProType.titleLarge.atSize(26.sp),
                color = TrackProTheme.colors.marking,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOf(track.type, track.country)
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  ")
                    .uppercase(),
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim
            )
        }

        Bezel()

        Row(modifier = Modifier.fillMaxWidth()) {
            Instrument(
                label = "Length",
                // totalLength is stored in km; formatDistance takes meters.
                value = track.totalLength?.let {
                    UnitFormatter.formatDistance(it * 1000.0, useMetric)
                } ?: "—",
                valueSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
            Bezel(vertical = true, modifier = Modifier.height(56.dp))
            Instrument(
                label = "Lap record",
                value = bestLapTime ?: "—",
                valueColor = if (bestLapTime != null) TrackProTheme.colors.accent
                else TrackProTheme.colors.markingDim,
                valueSize = 17.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}



