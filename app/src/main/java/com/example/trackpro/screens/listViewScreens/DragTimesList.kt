package com.example.trackpro.screens.listViewScreens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.AppTopBar
import com.example.trackpro.components.EmptyState
import com.example.trackpro.components.ExpandableGroup
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.managerClasses.utilities.DateFormatterUtil
import com.example.trackpro.models.DragSessionWithVehicle
import com.example.trackpro.viewModels.DragSessionViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DragTimesListView(
    viewModel: DragSessionViewModel,
    navController: NavController
) {
    val dragSessions by viewModel.dragSessions.collectAsState()

    val groupedSessions = remember(dragSessions) {
        dragSessions.groupBy { session ->
            val date = DateFormatterUtil.getDateFormat().format(Date(session.startTime))
            "$date | ${session.manufacturer} ${session.model}"
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = "Drag Records",
                accent = TrackProTheme.colors.accentCyan,
                trailing = {
                    Text(
                        "${dragSessions.size} sessions",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.textMuted
                    )
                }
            )

            if (dragSessions.isEmpty()) {
                EmptyState(message = "No sessions recorded", hint = "Run a drag session to see it here")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
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
    }
}
@Composable
fun ExpandableSessionGroup(
    groupTitle: String,
    sessions: List<DragSessionWithVehicle>,
    navController: NavController
) {
    ExpandableGroup(
        accent = TrackProTheme.colors.accentCyan,
        header = {
            Column(modifier = Modifier.weight(1f)) {
                Text(groupTitle, style = TrackProType.titleMedium.copy(fontSize = 13.sp), color = TrackProTheme.colors.textPrimary)
                Text(
                    "${sessions.size} runs completed",
                    style = TrackProType.body.copy(fontSize = 11.sp),
                    color = TrackProTheme.colors.textMuted
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            sessions.forEach { session ->
                val time = DateFormatterUtil.getTimeFormat().format(Date(session.startTime))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrackProTheme.colors.bgElevated, TrackProShapes.control)
                        .clickable { navController.navigate("graph/${session.sessionId}") }
                        .padding(Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(6.dp).background(TrackProTheme.colors.accentCyan, RoundedCornerShape(100))
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "Run at $time",
                            style = TrackProType.body,
                            color = TrackProTheme.colors.textPrimary
                        )
                    }

                    Text(
                        "Details",
                        style = TrackProType.label,
                        color = TrackProTheme.colors.textMuted
                    )
                }
            }
        }
    }
}