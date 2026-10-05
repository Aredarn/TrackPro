package com.example.trackpro.screens.history

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
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
    // One filter per section, held here rather than in each list so switching between Track
    // and Drag keeps both, and saved so opening a session and coming back keeps them too.
    var trackFilter by rememberSaveable(stateSaver = SessionFilterSaver) { mutableStateOf(SessionFilter()) }
    var dragFilter by rememberSaveable(stateSaver = SessionFilterSaver) { mutableStateOf(SessionFilter()) }
    val header: @Composable () -> Unit = {
        SectionSwitch(
            options = listOf(HistorySection.Track to stringResource(R.string.mode_track), HistorySection.Drag to stringResource(R.string.mode_drag)),
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
            title = stringResource(R.string.history_title),
            filter = trackFilter,
            onFilterChange = { trackFilter = it },
        )
        HistorySection.Drag -> DragTimesListView(
            viewModel = dragSessionViewModel,
            navController = navController,
            onBack = null,
            header = header,
            title = stringResource(R.string.history_title),
            filter = dragFilter,
            onFilterChange = { dragFilter = it },
        )
    }
}
