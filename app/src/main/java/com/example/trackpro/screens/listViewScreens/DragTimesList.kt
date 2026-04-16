package com.example.trackpro.screens.listViewScreens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.Divider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.trackpro.models.DragSessionWithVehicle
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.SessionViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DragTimesList : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val viewModel: SessionViewModel = viewModel(factory = SessionViewModelFactory(this))

            DragTimesListView(viewModel = viewModel, navController = navController)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DragTimesListView(
    viewModel: SessionViewModel,
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    // Using the combined model list from your original code
    val sessionsWithVehicle by viewModel.sessionsWithVehicle.collectAsState()

    val BgDeep      = Color(0xFF080A0F)
    val BgCard      = Color(0xFF0E1117)
    val BgElevated  = Color(0xFF151922)
    val AccentRed   = Color(0xFFE8001C)
    val TextPrimary = Color(0xFFF0F2F5)
    val TextMuted   = Color(0xFF6B7280)
    val SectorLine  = Color(0xFF1E2530)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentRed)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "● DRAG RUNS",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "${sessionsWithVehicle.size} RUNS",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            if (sessionsWithVehicle.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO DRAG RUNS", color = TextMuted,
                            fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text("Complete a drag session to see it here",
                            color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessionsWithVehicle) { session ->
                        DragSessionCard(
                            session = session,
                            navController = navController,
                            bgCard = BgCard,
                            bgElevated = BgElevated,
                            accentRed = AccentRed,
                            textPrimary = TextPrimary,
                            textMuted = TextMuted,
                            sectorLine = SectorLine,
                            onDelete = { sessionToDelete ->
                                scope.launch(Dispatchers.IO) {
                                    viewModel.deleteSession(sessionToDelete)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DragSessionCard(
    session: DragSessionWithVehicle,
    navController: NavController,
    bgCard: Color,
    bgElevated: Color,
    accentRed: Color,
    textPrimary: Color,
    textMuted: Color,
    sectorLine: Color,
    onDelete: (DragSessionWithVehicle) -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Reusable Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF0E1117),
            titleContentColor = Color(0xFFF0F2F5),
            textContentColor = Color(0xFF6B7280),
            confirmButton = {
                TextButton(onClick = { onDelete(session); showDeleteDialog = false }) {
                    Text("DELETE", color = accentRed, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", color = textMuted)
                }
            },
            title = { Text("Delete Run?", fontWeight = FontWeight.Black) },
            text = { Text("This will permanently remove this drag record.") }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgCard, RoundedCornerShape(12.dp))
            .border(1.dp, sectorLine, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { navController.navigate("graph/${session.sessionId}") },
                onLongClick = { showDeleteDialog = true }
            )
    ) {
        Column {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        accentRed.copy(alpha = 0.15f),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("DRAG RUN", color = accentRed, fontSize = 9.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    val date = dateFormat.format(Date(session.startTime))
                    Text(date, color = textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Vehicle Details
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "${session.manufacturer} ${session.model}",
                    color = textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Production Year: ${session.year}",
                    color = textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Divider(color = sectorLine, thickness = 1.dp)

            // Times + Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgElevated, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val startTime = timeFormat.format(Date(session.startTime))
                    val endTime = session.endTime?.let { timeFormat.format(Date(it)) } ?: "—"

                    Text("TIME WINDOW", color = textMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("$startTime – $endTime", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("VIEW →", color = accentRed,
                        fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}