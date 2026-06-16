package com.example.trackpro.screens.listViewScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.extrasForUI.TrackProTheme
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


    Box(modifier = Modifier.fillMaxSize().background(TrackProTheme.colors.bgDeep)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Box(
                modifier = Modifier.fillMaxWidth().background(TrackProTheme.colors.accentCyan).padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("● TRACK RECORDS", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("${trackSessions.size} SESSIONS", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (trackSessions.isEmpty()) {
                // ... Empty State ...
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
}

@Composable
fun ExpandableTrackGroup(
    trackName: String,
    trackMeta: String,
    sessions: List<SessionData>,
    vehicles: List<VehicleInformationData>,
    navController: NavController
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.bgCard, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isExpanded) TrackProTheme.colors.accentCyan.copy(alpha = 0.4f) else TrackProTheme.colors.sectorLine,
                RoundedCornerShape(12.dp)
            )
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    trackName.uppercase(),
                    color = if (isExpanded) TrackProTheme.colors.accentCyan else TrackProTheme.colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "$trackMeta · ${sessions.size} SESSIONS",
                    color = TrackProTheme.colors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (isExpanded) "CLOSE —" else "VIEW ALL +",
                color = if (isExpanded) TrackProTheme.colors.accentCyan else TrackProTheme.colors.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }

        // --- EXPANDED SESSIONS ---
        if (isExpanded) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sessions.forEach { session ->
                    val vehicle = vehicles.find { it.vehicleId == session.vehicleId }
                    val date = Instant.ofEpochMilli(session.startTime)
                        .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM"))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, TrackProTheme.colors.sectorLine.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .background(TrackProTheme.colors.bgElevated.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable { navController.navigate("timeattacklistitem/${session.id}") }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Vertical "Pillar" accent
                            Box(modifier = Modifier.width(3.dp).height(24.dp).background(TrackProTheme.colors.accentCyan, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "${vehicle?.manufacturer} ${vehicle?.model}",
                                    color = TrackProTheme.colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "SESSION DATE: $date",
                                    color = TrackProTheme.colors.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("TELEMETRY", color = TrackProTheme.colors.accentCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text("→", color = TrackProTheme.colors.accentCyan, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
