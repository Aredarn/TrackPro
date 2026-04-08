package com.example.trackpro.screens.listViewScreens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.TabRowDefaults.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.dataClasses.SessionData
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.VehicleFULLViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeAttackListViewScreen(
    navController: NavController,
    viewModel: SessionViewModel,
    trackViewModel: TrackViewModel,
    vehicleViewModel: VehicleFULLViewModel,
    database: ESPDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allSessions by viewModel.sessions.collectAsState()
    val trackSessions = allSessions.filter { it.trackId != null }
    val vehicles = vehicleViewModel.vehicles.collectAsState().value
    val tracks by trackViewModel.tracks.collectAsState()

    val BgDeep     = Color(0xFF080A0F)
    val BgCard     = Color(0xFF0E1117)
    val BgElevated = Color(0xFF151922)
    val AccentGreen= Color(0xFF00C853)
    val TextPrimary= Color(0xFFF0F2F5)
    val TextMuted  = Color(0xFF6B7280)
    val SectorLine = Color(0xFF1E2530)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentGreen)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("● TRACK SESSIONS", color = Color.Black, fontSize = 11.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("${trackSessions.size} SESSIONS", color = Color.Black,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }

            if (trackSessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO TRACK SESSIONS", color = TextMuted,
                            fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text("Complete a lap session to see it here",
                            color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(trackSessions) { session ->
                        val track = tracks.find { it.trackId == session.trackId }
                        val vehicle = vehicles.find { it.vehicleId == session.vehicleId }
                        if (track != null && vehicle != null) {
                            TrackSessionCard(
                                session = session,
                                track = track,
                                vehicle = vehicle,
                                navController = navController,
                                bgCard = BgCard,
                                bgElevated = BgElevated,
                                accentGreen = AccentGreen,
                                textPrimary = TextPrimary,
                                textMuted = TextMuted,
                                sectorLine = SectorLine,
                                onDelete = {
                                    scope.launch(Dispatchers.IO) {
                                        // TODO: viewModel.deleteSession(it)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackSessionCard(
    session: SessionData,
    vehicle: VehicleInformationData,
    track: TrackMainData,
    navController: NavController,
    bgCard: Color,
    bgElevated: Color,
    accentGreen: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color,
    onDelete: (SessionData) -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF0E1117),
            titleContentColor = Color(0xFFF0F2F5),
            textContentColor = Color(0xFF6B7280),
            confirmButton = {
                TextButton(onClick = { onDelete(session); showDeleteDialog = false }) {
                    Text("DELETE", color = Color(0xFFE8001C), fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", color = Color(0xFF6B7280))
                }
            },
            title = { Text("Delete Session?", fontWeight = FontWeight.Black) },
            text = { Text("This action cannot be undone.") }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgCard, RoundedCornerShape(12.dp))
            .border(1.dp, sectorLine, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { navController.navigate("timeattacklistitem/${session.id}") },
                onLongClick = { showDeleteDialog = true }
            )
    ) {
        Column {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        accentGreen.copy(alpha = 0.15f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TRACK SESSION", color = accentGreen, fontSize = 9.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    val date = Instant.ofEpochMilli(session.startTime)
                        .atZone(ZoneId.systemDefault()).format(dateFormatter)
                    Text(date, color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Track name + meta
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = track.trackName,
                    color = textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.country} · ${track.type} · ${track.totalLength ?: "?"} km",
                    color = textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Divider(color = sectorLine, thickness = 1.dp)

            // Vehicle + time
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgElevated, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${vehicle.manufacturer} ${vehicle.model} (${vehicle.year})",
                        color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${vehicle.engineType} · ${vehicle.horsepower}hp · ${vehicle.drivetrain}",
                        color = textMuted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val startT = Instant.ofEpochMilli(session.startTime)
                        .atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
                    val endT = session.endTime?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                            .toLocalTime().format(timeFormatter)
                    } ?: "Ongoing"
                    Text("$startT – $endT", color = accentGreen, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    Text("VIEW →", color = accentGreen.copy(alpha = 0.6f),
                        fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}
