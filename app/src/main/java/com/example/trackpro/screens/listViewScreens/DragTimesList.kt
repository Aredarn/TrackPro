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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.trackpro.models.DragSessionWithVehicle
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.SessionViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DragTimesList : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()

            //To get all data dynamically from the DB
            val viewModel: SessionViewModel = viewModel(factory = SessionViewModelFactory(this))

            DragTimesListView(viewModel = viewModel, navController = navController)
        }
    }
}

@Composable
fun DragTimesListView(viewModel: SessionViewModel, navController: NavController) {
    val sessionWithVehicleList by viewModel.sessionsWithVehicle.collectAsState()
    SessionListScreen(navController = navController, sessionsWithVehicles = sessionWithVehicleList)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    navController: NavController,
    sessionsWithVehicles: List<DragSessionWithVehicle>
) {
    val BgDeep     = Color(0xFF080A0F)
    val BgCard     = Color(0xFF0E1117)
    val BgElevated = Color(0xFF151922)
    val AccentRed  = Color(0xFFE8001C)
    val TextPrimary= Color(0xFFF0F2F5)
    val TextMuted  = Color(0xFF6B7280)
    val DeltaGood  = Color(0xFF00E676)
    val SectorLine = Color(0xFF1E2530)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
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
                        text = "● DRAG SESSIONS",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                    Text(
                        text = "${sessionsWithVehicles.size} RUNS",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            if (sessionsWithVehicles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO SESSIONS YET", color = TextMuted,
                            fontSize = 14.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text("Start a drag session to see results here",
                            color = TextMuted.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessionsWithVehicles) { session ->
                        DragSessionCard(
                            session = session,
                            navController = navController,
                            accentRed = AccentRed,
                            bgCard = BgCard,
                            bgElevated = BgElevated,
                            textPrimary = TextPrimary,
                            textMuted = TextMuted,
                            deltaGood = DeltaGood,
                            sectorLine = SectorLine
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DragSessionCard(
    session: DragSessionWithVehicle,
    navController: NavController?,
    accentRed: Color,
    bgCard: Color,
    bgElevated: Color,
    textPrimary: Color,
    textMuted: Color,
    deltaGood: Color,
    sectorLine: Color
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val date = dateFormat.format(Date(session.startTime))
    val startTime = timeFormat.format(Date(session.startTime))
    val endTime = session.endTime?.let { timeFormat.format(Date(it)) } ?: "—"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgCard, RoundedCornerShape(12.dp))
            .border(1.dp, sectorLine, RoundedCornerShape(12.dp))
            .clickable { navController?.navigate("graph/${session.sessionId}") }
    ) {
        Column {
            // Card top accent
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
                    Text(
                        text = "DRAG RUN",
                        color = accentRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = date,
                        color = textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Vehicle info
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${session.manufacturer} ${session.model}",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${session.year}",
                    color = textMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Divider(color = sectorLine, thickness = 1.dp)

            // Time info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgElevated, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("START", color = textMuted, fontSize = 9.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Text(startTime, color = textPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("END", color = textMuted, fontSize = 9.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                    Text(endTime, color = textPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                }
                // Tap hint
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .background(accentRed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("VIEW →", color = accentRed, fontSize = 10.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}