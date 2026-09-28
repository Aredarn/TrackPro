package com.example.trackpro.screens.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import com.example.trackpro.components.SectionSwitch
import com.example.trackpro.screens.listViewScreens.DragTimesListView
import com.example.trackpro.screens.listViewScreens.TimeAttackListViewScreen
import com.example.trackpro.viewModels.DragSessionViewModel
import com.example.trackpro.viewModels.SessionViewModel
import com.example.trackpro.viewModels.TrackViewModel
import com.example.trackpro.viewModels.VehicleFULLViewModel

enum class HistorySection { Track, Drag }

/**
 * The History tab: every session, split the way the two modes are. Before this they were two
 * drawer entries and two board instruments, and nowhere said they were the same kind of thing.
 */
@Composable
fun HistoryScreen(
    navController: NavController,
    sessionViewModel: SessionViewModel,
    dragSessionViewModel: DragSessionViewModel,
    trackViewModel: TrackViewModel,
    vehicleViewModel: VehicleFULLViewModel,
    initial: HistorySection = HistorySection.Track,
) {
    var section by rememberSaveable(initial) { mutableStateOf(initial) }
    val header: @Composable () -> Unit = {
        SectionSwitch(
            options = listOf(HistorySection.Track to "Track", HistorySection.Drag to "Drag"),
            selected = section,
            onSelect = { section = it }
        )
    }
    when (section) {
        HistorySection.Track -> TimeAttackListViewScreen(
            navController = navController,
            viewModel = sessionViewModel,
            trackViewModel = trackViewModel,
            vehicleViewModel = vehicleViewModel,
            onBack = null,
            header = header,
            title = "History",
        )
        HistorySection.Drag -> DragTimesListView(
            viewModel = dragSessionViewModel,
            navController = navController,
            onBack = null,
            header = header,
            title = "History",
        )
    }
}
