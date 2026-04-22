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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.trackpro.models.DragSessionWithVehicle
import com.example.trackpro.theme.TrackProColors
import com.example.trackpro.viewModels.DragSessionViewModel
import com.example.trackpro.viewModels.DragSessionViewModelFactory
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
            val viewModel: DragSessionViewModel = viewModel(
                factory = DragSessionViewModelFactory(this)
            )

            DragTimesListView(viewModel = viewModel, navController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DragTimesListView(
    viewModel: DragSessionViewModel,
    navController: NavController
) {
    val scope = rememberCoroutineScope()
    val dragSessions by viewModel.dragSessions.collectAsState()

    val groupedSessions = remember(dragSessions) {
        dragSessions.groupBy { session ->
            val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(session.startTime))
            "$date | ${session.manufacturer} ${session.model}"
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProColors.BgDeep)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groupedSessions.forEach { (groupKey, sessions) ->
                item(key = groupKey) {
                    ExpandableSessionGroup(
                        groupTitle = groupKey,
                        sessions = sessions,
                        navController = navController
                    )
                }
            }
        }
    }
}
@Composable
fun ExpandableSessionGroup(
    groupTitle: String,
    sessions: List<DragSessionWithVehicle>,
    navController: NavController
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProColors.BgCard, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isExpanded) TrackProColors.AccentRed.copy(alpha = 0.5f) else TrackProColors.SectorLine,
                RoundedCornerShape(12.dp)
            )
    ) {
        // --- THE HEADER (Always Visible) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    groupTitle.uppercase(),
                    color = if (isExpanded) TrackProColors.AccentRed else TrackProColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "${sessions.size} RUNS COMPLETED",
                    color = TrackProColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Minimalist Toggle Icon
            Text(
                if (isExpanded) "CLOSE —" else "VIEW ALL +",
                color = if (isExpanded) TrackProColors.AccentRed else TrackProColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }

        // --- THE CONTENT (Visible when Expanded) ---
        if (isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sessions.forEach { session ->
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTime))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrackProColors.BgElevated, RoundedCornerShape(6.dp))
                            .clickable { navController.navigate("graph/${session.sessionId}") }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(6.dp).background(TrackProColors.AccentRed, RoundedCornerShape(100))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "RUN AT $time",
                                color = TrackProColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "DETAILS →",
                            color = TrackProColors.TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}