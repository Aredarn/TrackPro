package com.example.trackpro.screens.listViewScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.pressable
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.ExpandableGroup
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.VehicleFULLViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TimeAttackListViewScreen(
    navController: NavController,
    viewModel: SessionViewModel,
    trackViewModel: TrackViewModel,
    vehicleViewModel: VehicleFULLViewModel,
) {
    val allSessions by viewModel.sessions.collectAsState()
    val trackSessions = allSessions.filter { it.trackId != null }
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val tracks by trackViewModel.tracks.collectAsState()

    val groupedByTrack = remember(trackSessions, tracks) {
        trackSessions.groupBy { session ->
            tracks.find { it.trackId == session.trackId }?.trackName ?: "Unknown Track"
        }
    }


    val listState = rememberLazyListState()
    val scrolled by listState.isScrolledUnderChrome()

    ScreenScaffold(
            title = "Track Records",
            onBack = { navController.popBackStack() },
            accent = TrackProTheme.colors.accent,
            trailing = {
                Text("${trackSessions.size} sessions", style = TrackProType.label, color = TrackProTheme.colors.textMuted)
            },
        contentScrolled = scrolled
    ) { contentPadding ->
        if (trackSessions.isEmpty()) {
            EmptyState(message = "No sessions recorded", hint = "Run a track session to see it here")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + Spacing.md,
                    bottom = Spacing.md,
                    start = Spacing.md,
                    end = Spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                groupedByTrack.forEach { (trackName, sessions) ->
                    item(key = trackName) {
                        val track = tracks.find { it.trackName == trackName }
                        ExpandableTrackGroup(
                            trackName = trackName,
                            trackMeta = "${track?.country} · ${track?.type}",
                            sessions = sessions,
                            vehicles = vehicles,
                            navController = navController
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableTrackGroup(
    trackName: String,
    trackMeta: String,
    sessions: List<SessionData>,
    vehicles: List<VehicleInformationData>,
    navController: NavController
) {
    ExpandableGroup(
        accent = TrackProTheme.colors.accent,
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text(trackName, style = TrackProType.titleMedium.atSize(14.sp), color = TrackProTheme.colors.textPrimary)
                Text(
                    "$trackMeta · ${sessions.size} sessions",
                    style = TrackProType.body.atSize(11.sp),
                    color = TrackProTheme.colors.textMuted
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            sessions.forEach { session ->
                val vehicle = vehicles.find { it.vehicleId == session.vehicleId }
                val date = Instant.ofEpochMilli(session.startTime)
                    .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM"))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = { navController.navigate("timeattacklistitem/${session.id}") })
                        .background(TrackProTheme.colors.bgElevated.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Vertical "Pillar" accent
                        Box(modifier = Modifier.width(2.dp).height(24.dp).background(TrackProTheme.colors.accentMuted, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(
                                "${vehicle?.manufacturer} ${vehicle?.model}",
                                style = TrackProType.body,
                                color = TrackProTheme.colors.textPrimary
                            )
                            Text(
                                "Session date: $date",
                                style = TrackProType.body.atSize(10.sp),
                                color = TrackProTheme.colors.textMuted
                            )
                        }
                    }

                    Text("Telemetry", style = TrackProType.label, color = TrackProTheme.colors.textMuted)
                }
            }
        }
    }
}
