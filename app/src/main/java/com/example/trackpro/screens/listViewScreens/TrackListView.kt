package com.example.trackpro.screens.listViewScreens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.managerClasses.ESPDatabase
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.theme.TrackProColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrackListView : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
        }
    }
}

@Composable
fun TrackListScreen(navController: NavController, viewModel: TrackViewModel) {
    val tracks by viewModel.tracks.collectAsState()
    val context = LocalContext.current
    val database = remember { ESPDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProColors.AccentAmber)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "● MY TRACKS",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "${tracks.size} TRACKS",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            if (tracks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO TRACKS YET", color = TrackProColors.TextMuted,
                            fontSize = 14.sp, letterSpacing = 3.sp,
                            fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text("Build a track to see it here",
                            color = TrackProColors.TextMuted.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tracks) { track ->
                        TrackCard(
                            track = track,
                            navController = navController,
                            bgCard = TrackProColors.BgCard,
                            bgElevated = TrackProColors.BgElevated,
                            accentAmber = TrackProColors.AccentAmber,
                            accentRed = TrackProColors.AccentRed,
                            textPrimary = TrackProColors.TextPrimary,
                            textMuted = TrackProColors.TextMuted,
                            sectorLine = TrackProColors.SectorLine,
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
    bgCard: Color,
    bgElevated: Color,
    accentAmber: Color,
    accentRed: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color,
    onDelete: (TrackMainData) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF0E1117),
            titleContentColor = Color(0xFFF0F2F5),
            textContentColor = Color(0xFF6B7280),
            confirmButton = {
                TextButton(onClick = { onDelete(track); showDeleteDialog = false }) {
                    Text("DELETE", color = accentRed, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", color = textMuted)
                }
            },
            title = { Text("Delete Track?", fontWeight = FontWeight.Black) },
            text = { Text("${track.trackName} will be permanently removed.") }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgCard, RoundedCornerShape(12.dp))
            .border(1.dp, sectorLine, RoundedCornerShape(12.dp))
            .clickable { navController.navigate("track/${track.trackId}") }
    ) {
        Column {
            // ── Header ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        accentAmber.copy(alpha = 0.12f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accentAmber.copy(alpha = 0.15f),
                                    RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = track.type.uppercase(),
                                color = accentAmber,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = track.country.uppercase(),
                            color = textMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(accentRed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = accentRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // ── Track name ────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = track.trackName,
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = sectorLine, thickness = 1.dp)

            // ── Stats row ─────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgElevated,
                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("LENGTH", color = textMuted, fontSize = 9.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${track.totalLength ?: "?"} km",
                        color = accentAmber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LAP RECORD", color = textMuted, fontSize = 9.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Text(
                        // TODO: wire up actual lap record from DB
                        text = "—",
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "VIEW →",
                    color = accentAmber.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

