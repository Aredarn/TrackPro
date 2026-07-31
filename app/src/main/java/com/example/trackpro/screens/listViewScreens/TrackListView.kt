package com.example.trackpro.screens.listViewScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.StatCell
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.managerClasses.utilities.UnitFormatter
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            AppTopBar(
                title = "My Tracks",
                accent = TrackProTheme.colors.accentAmber,
                trailing = {
                    Text(
                        text = "${tracks.size} tracks",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.textMuted
                    )
                }
            )

            if (tracks.isEmpty()) {
                EmptyState(message = "No tracks yet", hint = "Build a track to see it here")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(tracks) { track ->
                        TrackCard(
                            track = track,
                            navController = navController,
                            database = database,
                            useMetric = useMetric,
                            bgCard = TrackProTheme.colors.bgCard,
                            bgElevated = TrackProTheme.colors.bgElevated,
                            accentAmber = TrackProTheme.colors.accentAmber,
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
    accentAmber: Color,
    dangerColor: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color,
    onDelete: (TrackMainData) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var bestLapTime by remember(track.trackId) { mutableStateOf<String?>(null) }

    LaunchedEffect(track.trackId) {
        bestLapTime = withContext(Dispatchers.IO) {
            database.lapTimeDataDAO().getBestLapForTrack(track.trackId)?.laptime
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = TrackProTheme.colors.bgCard,
            titleContentColor = TrackProTheme.colors.textPrimary,
            textContentColor = TrackProTheme.colors.textMuted,
            confirmButton = {
                TextButton(onClick = { onDelete(track); showDeleteDialog = false }) {
                    Text("Delete", color = dangerColor, style = TrackProType.titleMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = textMuted)
                }
            },
            title = { Text("Delete Track?") },
            text = { Text("${track.trackName} will be permanently removed.") }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgCard, TrackProShapes.card)
            .border(1.dp, sectorLine, TrackProShapes.card)
            .clickable { navController.navigate("track/${track.trackId}") }
    ) {
        Column {
            // ── Header ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        accentAmber.copy(alpha = 0.12f),
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accentAmber.copy(alpha = 0.15f), TrackProShapes.badge)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = track.type.uppercase(),
                                style = TrackProType.label.copy(fontSize = 9.sp),
                                color = accentAmber
                            )
                        }
                        Text(
                            text = track.country.uppercase(),
                            style = TrackProType.label.copy(fontSize = 9.sp),
                            color = textMuted
                        )
                    }

                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(dangerColor.copy(alpha = 0.1f), TrackProShapes.badge)
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = dangerColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // ── Track name ────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.trackName,
                    style = TrackProType.titleMedium,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = sectorLine, thickness = 1.dp)

            // ── Stats row ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgElevated, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatCell(
                    label = "Length",
                    // totalLength is stored in km; formatDistance takes meters.
                    value = track.totalLength?.let { UnitFormatter.formatDistance(it * 1000.0, useMetric) } ?: "?",
                    valueColor = accentAmber
                )
                StatCell(
                    label = "Lap Record",
                    value = bestLapTime ?: "—",
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                Text(
                    text = "View",
                    style = TrackProType.label,
                    color = accentAmber.copy(alpha = 0.8f)
                )
            }
        }
    }
}

