package com.example.trackpro.screens.telemetricScreens

import com.example.trackpro.components.pluralResource
import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.trackpro.components.paddockCard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.trackpro.TrackProApp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackpro.viewModels.DragRecordingViewModel
import com.example.trackpro.viewModels.DragRecordingViewModelFactory
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.viewModels.VehicleFULLViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import androidx.core.graphics.toColorInt
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.components.pressable
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.KeepScreenOn
import com.example.trackpro.components.AppCard
import com.example.trackpro.components.AppTopBar
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.trackpro.components.Bezel
import com.example.trackpro.components.ConfirmDeleteDialog
import com.example.trackpro.components.DashAction
import com.example.trackpro.components.SegmentBar
import com.example.trackpro.components.SessionSummary
import com.example.trackpro.components.Trend
import com.example.trackpro.components.rememberTrend
import com.example.trackpro.theme.segmentOff
import com.example.trackpro.components.Instrument
import com.example.trackpro.components.Readout
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.DataVizColors
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import java.util.Locale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

data class DragMetricDisplay(
    val label: String,
    val value: String,
    val unit: String,
    val achieved: Boolean = false
)

@Composable
fun DragRaceScreen(
    vehicleViewModel: VehicleFULLViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()

    // The recording itself lives here, not in this composition. The activity declares no
    // configChanges, so rotating the phone destroys and recreates it - which used to take
    // the buffered trace, the calculator and the start time with it while the saveable
    // "still recording" flag survived, leaving a screen that claimed to be recording with
    // nothing recorded. Scoped to the nav entry, so it outlives recreation and is cleared
    // only when this screen is popped for good.
    val recorder: DragRecordingViewModel = viewModel(
        factory = DragRecordingViewModelFactory(context)
    )
    val isSessionActive by recorder.isRecording.collectAsState()
    val currentMetrics by recorder.metrics.collectAsState()
    val elapsedTime by recorder.elapsedTime.collectAsState()
    val speedSamples by recorder.speedSamples.collectAsState()
    val selectedVehicleId by recorder.selectedVehicleId.collectAsState()

    // Only while recording: waiting on the start screen should not hold the display on.
    KeepScreenOn(isSessionActive)

    // --- GPS & CONNECTION STATE ---
    val isConnected by app.gpsManager.connectionStatus.collectAsState(initial = false)
    // For display only - the speed readout and the weather anchor, both of which want just
    // the newest fix. The recorder subscribes to the flow itself and never reads this.
    val gpsData by app.gpsManager.activeGpsFlow.collectAsState(initial = null)

    // --- VEHICLE SELECTION ---
    val vehicles by vehicleViewModel.vehicles.collectAsState()
    val selectedVehicle = vehicles.firstOrNull { it.vehicleId == selectedVehicleId }
    var showVehicleDropdown by remember { mutableStateOf(false) }

    // The tile table and the speed chart are reference material, not run material. During a
    // launch the panel collapses to the split and the live instruments; everything else is
    // one deliberate toggle away. Saved so rotating in a mount does not undo the choice.
    var showData by rememberSaveable { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    val lastRunSessionId by recorder.lastRunSessionId.collectAsState()
    val saveError by recorder.saveError.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Ladder progress: how far through the run's splits you are. Discrete by nature, which
    // is exactly what a segment bar is for - and it pairs with the split readout above it.
    val ladderTotal = (currentMetrics.standing.size + 2).coerceAtLeast(1)

    if (lastRunSessionId != null) {
        SessionSummary(
            headline = currentMetrics.quarterMileTime?.let { formatTime(it) }
                ?: currentMetrics.standing.lastOrNull { it.seconds != null }?.seconds
                    ?.let { formatTime(it) } ?: "\u2014",
            headlineCaption = if (currentMetrics.quarterMileTime != null) stringResource(R.string.drag_quarter_mile_caption)
            else stringResource(R.string.drag_best_split),
            rows = listOf(
                stringResource(R.string.hud_runs) to "${currentMetrics.runCount}",
                stringResource(R.string.car_top_speed) to "${UnitFormatter.formatSpeed(currentMetrics.maxSpeed, useMetric)} ${UnitFormatter.speedUnitLabel(useMetric)}",
                stringResource(R.string.drag_distance) to UnitFormatter.formatDistance(currentMetrics.totalDistance.toDouble(), useMetric)
            ),
            saveFailed = saveError,
            onKeep = { recorder.clearLastRun(); onBack() },
            onVoid = { recorder.voidLastRun(); onBack() }
        )
        return
    }

    if (confirmStop) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.drag_stop_title),
            message = stringResource(R.string.drag_stop_msg),
            confirmLabel = stringResource(R.string.drag_stop),
            dismissLabel = stringResource(R.string.drag_keep_recording),
            onConfirm = { confirmStop = false; recorder.stop() },
            onDismiss = { confirmStop = false }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {

        // 1. TOP STATUS BAR
        AppTopBar(
            title = stringResource(R.string.mode_drag),
            // Guarded while live: Stop is a commit, and this arrow sits where a hand lands
            // adjusting a mount.
            onBack = { if (isSessionActive) confirmStop = true else onBack() },
            accent = TrackProTheme.colors.accent,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (isSessionActive && currentMetrics.runCount > 0) {
                        // Which launch is being timed. The counter re-arms when the car
                        // comes back to a standstill, and this is what tells the driver it
                        // did - the next launch is measured too.
                        Text(
                            stringResource(R.string.drag_run_n, currentMetrics.runCount),
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                    // The elapsed clock used to be here AND in the readout below. One is
                    // enough, and the readout is where the eye already is.
                    Text(
                        if (isConnected) stringResource(R.string.drag_gps_locked) else stringResource(R.string.drive_no_signal),
                        style = TrackProType.label,
                        // A fault is never dimmer than health.
                        color = if (isConnected) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.danger
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    val dataState = if (showData) stringResource(R.string.drag_shown) else stringResource(R.string.drag_hidden)
                    Text(
                        stringResource(R.string.drag_data),
                        style = TrackProType.label,
                        color = if (showData) TrackProTheme.colors.marking
                        else TrackProTheme.colors.markingDim,
                        modifier = Modifier
                            .pressable(
                                onClick = { showData = !showData },
                                scale = 0.94f,
                                // It is a switch, not a button: it has an on state that
                                // persists, and colour was the only thing saying which.
                                role = Role.Switch
                            )
                            .semantics {
                                stateDescription = dataState
                            }
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = Spacing.sm)
                    )
                }
            }
        )

        // A HUD with a scroll position is a HUD whose readout can be anywhere. While a run
        // is live and the data panel is closed, the surface is fixed; everywhere else it
        // scrolls because there is genuinely more than a screenful.
        val bodyScroll = rememberScrollState()
        val fixedSurface = isSessionActive && !showData
        Column(
            Modifier
                .weight(1f)
                .then(if (fixedSurface) Modifier else Modifier.verticalScroll(bodyScroll))
        ) {

            // 2. VEHICLE SELECTOR
            if (!isSessionActive) {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
                        .pressable(onClick = { showVehicleDropdown = true })
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.car_title), style = TrackProType.label, color = TrackProTheme.colors.textMuted)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                selectedVehicle?.let { "${it.manufacturer} ${it.model}" }
                                    ?: stringResource(R.string.drag_select_car),
                                style = TrackProType.titleMedium,
                                color = selectedVehicle?.let { TrackProTheme.colors.textPrimary }
                                    ?: TrackProTheme.colors.textMuted.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            selectedVehicle?.let {
                                Text(
                                    "${it.horsepower}hp · ${it.drivetrain} · ${it.year}",
                                    style = TrackProType.body.atSize(11.sp),
                                    color = TrackProTheme.colors.textMuted
                                )
                            }
                        }
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TrackProTheme.colors.textMuted
                        )
                    }

                    DropdownMenu(
                        expanded = showVehicleDropdown,
                        onDismissRequest = { showVehicleDropdown = false },
                        modifier = Modifier.background(TrackProTheme.colors.bgElevated)
                    ) {
                        vehicles.forEach { vehicle ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "${vehicle.manufacturer} ${vehicle.model}",
                                            style = TrackProType.body,
                                            color = TrackProTheme.colors.textPrimary
                                        )
                                        Text(
                                            "${vehicle.horsepower}hp · ${vehicle.year}",
                                            style = TrackProType.body.atSize(12.sp),
                                            color = TrackProTheme.colors.textMuted
                                        )
                                    }
                                },
                                onClick = {
                                    recorder.selectVehicle(vehicle.vehicleId)
                                    showVehicleDropdown = false
                                }
                            )
                        }
                    }
                }
            } else {
                // Show selected vehicle during session
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
                        .background(TrackProTheme.colors.bgCard, TrackProShapes.control)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                ) {
                    selectedVehicle?.let {
                        Text(
                            "${it.manufacturer} ${it.model} · ${it.horsepower}hp",
                            style = TrackProType.body,
                            color = TrackProTheme.colors.textPrimary
                        )
                    }
                }
            }

            // 3. THE SPLIT JUST HIT
            // The dominant readout is the furthest split reached, held at full scale until
            // a further one lands. Before the first split there is nothing to hold, so live
            // speed takes the slot - which is what you would be watching anyway on the
            // launch. Like every split on this screen it is the session's best, so a second
            // run improving on the first updates it in place rather than resetting it.
            val reachedSplits: List<Pair<String, Double>> =
                currentMetrics.standing.mapNotNull { split ->
                    split.seconds?.let { split.label to it }
                } + listOfNotNull(
                    currentMetrics.quarterMileTime?.let { stringResource(R.string.drag_quarter_upper) to it },
                    currentMetrics.halfMileTime?.let { stringResource(R.string.drag_half_upper) to it }
                )
            val latestSplit: Pair<String, String>? = reachedSplits.lastOrNull()
                ?.let { (label, seconds) -> label to formatTime(seconds) }

            // Portrait stacks the split over the instruments; landscape sets them side by
            // side so the split keeps its full scale instead of being pushed off a short
            // screen. Both are first-class, which the product record requires and this
            // screen previously ignored entirely.
            AdaptiveSplit(
                landscape = landscape,
                primary = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.gutter)
                        .paddockCard()
                        .padding(Spacing.lg)
                ) {
                    Readout(
                        value = latestSplit?.second
                            ?: (gpsData?.speed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "0"),
                        caption = latestSplit?.let { stringResource(R.string.drag_split_seconds, it.first) }
                            ?: stringResource(R.string.drag_live_speed, UnitFormatter.speedUnitLabel(useMetric)),
                        valueColor = if (latestSplit != null) TrackProTheme.colors.accent
                        else TrackProTheme.colors.marking,
                        valueSize = 72.sp,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                val recTone = if (isSessionActive) TrackProTheme.colors.danger else TrackProTheme.colors.markingDim
                                Text(
                                    text = if (isSessionActive) stringResource(R.string.drag_recording) else stringResource(R.string.drag_idle),
                                    style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold),
                                    color = recTone,
                                    modifier = Modifier
                                        .clip(TrackProShapes.pill)
                                        .background(recTone.copy(alpha = 0.14f))
                                        .padding(horizontal = Spacing.sm, vertical = 2.dp)
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    text = elapsedTime,
                                    style = TrackProType.statValue.atSize(18.sp),
                                    color = TrackProTheme.colors.marking
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(Spacing.md))
                    // How far through the run's split ladder you are. Discrete by nature, which
                    // is what a segment bar is for, and it pairs with the split held above it:
                    // big number is the last one reached, bar is where that sits in the run.
                    SegmentBar(
                        signedFraction = reachedSplits.size.toFloat() / ladderTotal,
                        activeColor = TrackProTheme.colors.accent,
                        bidirectional = false,
                        segments = ladderTotal,
                        height = if (landscape) 16.dp else 22.dp
                    )
                }
                },
                secondary = {
                    DragLiveInstruments(
                        landscape = landscape,
                        speedText = gpsData?.speed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "0",
                        speedUnit = UnitFormatter.speedUnitLabel(useMetric),
                        speedTrend = rememberTrend(
                            UnitFormatter.convertSpeed(gpsData?.speed ?: 0f, useMetric).toFloat(),
                            threshold = 2f
                        ),
                        maxText = UnitFormatter.formatSpeed(currentMetrics.maxSpeed, useMetric),
                        distanceText = UnitFormatter.formatDistance(
                            currentMetrics.totalDistance.toDouble(), useMetric
                        )
                    )
                }
            )

            Spacer(Modifier.height(Spacing.md))

            // Everything below is reference, not run material: hidden while recording
            // unless the driver asks for it with DATA. At the strip the panel is the split
            // and the instruments, and nothing else competes for the glance.
            if (!isSessionActive || showData) {

            // 4. SPLIT TABLE
            // Every split the run can produce, always present so the range is visible.
            // Unreached splits sit dim rather than absent - the same discipline the
            // segment bar uses for its unlit blocks.
            // Speed milestones come from the calculator already labelled, so the tile can
            // never claim a threshold the timer was not actually measuring. The two mile
            // splits are appended here because they are distances, not speeds, and are
            // imperial in both unit systems.
            val speedCells = (currentMetrics.standing + currentMetrics.rolling).map { split ->
                split.label to split.seconds?.let { formatTime(it) }
            }
            val splitCells = speedCells + listOf(
                stringResource(R.string.drag_quarter_lower) to currentMetrics.quarterMileTime?.let { formatTime(it) },
                // Trap speed is the one cell here holding a speed rather than a time, so it
                // carries its unit in the placard - every other cell is seconds.
                stringResource(R.string.drag_quarter_trap, UnitFormatter.speedUnitLabel(useMetric)) to
                        currentMetrics.quarterMileSpeed?.let {
                            UnitFormatter.formatSpeed(it, useMetric)
                        },
                stringResource(R.string.drag_half_lower) to currentMetrics.halfMileTime?.let { formatTime(it) }
            )

            Column(
                Modifier
                    .padding(horizontal = Spacing.gutter)
                    .paddockCard()
            ) {
            splitCells.chunked(3).forEachIndexed { rowIndex, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { i, (label, value) ->
                        Instrument(
                            label = label,
                            value = value ?: "\u2013\u2013.\u2013",
                            valueColor = if (value != null) TrackProTheme.colors.marking
                            else TrackProTheme.colors.markingDim,
                            valueSize = 22.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (i < row.lastIndex) {
                            Bezel(vertical = true, modifier = Modifier.height(58.dp))
                        }
                    }
                }
                if (rowIndex < (splitCells.size - 1) / 3) Bezel(Modifier.padding(horizontal = Spacing.lg))
            }
            }

            Spacer(Modifier.height(Spacing.md))

            // 5. SPEED CHART (LARGE)
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = Spacing.gutter)
            ) {
                Column {
                    SectionLabel(stringResource(R.string.drag_speed_profile))
                    Spacer(Modifier.height(8.dp))

                    AndroidView(
                        factory = { ctx ->
                            LineChart(ctx).apply {
                                description.isEnabled = false
                                legend.isEnabled = false
                                xAxis.position = XAxis.XAxisPosition.BOTTOM
                                xAxis.setDrawGridLines(true)
                                xAxis.gridColor = DataVizColors.chartGrid.toColorInt()
                                xAxis.textColor = DataVizColors.chartAxisText.toColorInt()
                                axisLeft.textColor = DataVizColors.chartAxisText.toColorInt()
                                axisLeft.setDrawGridLines(true)
                                axisLeft.gridColor = DataVizColors.chartGrid.toColorInt()
                                axisLeft.axisMinimum = 0f
                                axisRight.isEnabled = false
                                setTouchEnabled(true)
                                setPinchZoom(true)
                                setDrawBorders(false)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                // The library's default is a yellow sentence that reads as
                                // a warning. An empty chart before a run is not one.
                                setNoDataText(ctx.getString(R.string.drag_chart_empty))
                                setNoDataTextColor(DataVizColors.chartAxisText.toColorInt())
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { chart ->
                            if (speedSamples.isNotEmpty()) {
                                // x is the position in the rolling window rather than an
                                // absolute sample counter: the window is what is drawn, and
                                // the axis carries no other meaning.
                                val entries = speedSamples.mapIndexed { i, speed ->
                                    Entry(i.toFloat(), speed)
                                }
                                val dataSet = LineDataSet(entries, "Speed").apply {
                                    color = DataVizColors.chartLine.toColorInt()
                                    lineWidth = 3f
                                    setDrawCircles(false)
                                    setDrawValues(false)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    setDrawFilled(true)
                                    fillColor = DataVizColors.chartLine.toColorInt()
                                    fillAlpha = 40
                                }
                                chart.data = LineData(dataSet)
                                chart.notifyDataSetChanged()
                                chart.invalidate()
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            } // end reference panel

        }

        // 6. CONTROLS (Fixed at bottom)
        Row(
            Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.bgDeep)
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            DashAction(
                // A running session is always stoppable, whatever the vehicle list is
                // doing: it reloads asynchronously after the screen is recreated, and
                // resolving the name first left Stop disabled mid-run until it arrived.
                label = when {
                    isSessionActive -> stringResource(R.string.drag_stop)
                    selectedVehicleId == null -> stringResource(R.string.drag_select_vehicle)
                    else -> stringResource(R.string.drag_start)
                },
                detail = when {
                    isSessionActive -> pluralResource(R.plurals.drag_recording_runs, currentMetrics.runCount)
                    selectedVehicleId == null -> stringResource(R.string.drag_every_run_car)
                    else -> stringResource(R.string.drag_arm)
                },
                onClick = {
                    // Both sides open and close the session, write the trace and manage the
                    // GPS subscription in the recorder, on a scope that outlives this screen.
                    // Doing it here meant Back straight after Stop - the natural gesture -
                    // cancelled the insert mid-write and lost the run.
                    if (isSessionActive) {
                        confirmStop = true
                    } else if (selectedVehicleId != null) {
                        // The current fix is the only position a drag session has to anchor
                        // its conditions to; without one it simply records no weather.
                        recorder.start(weatherAnchor = gpsData)
                    }
                },
                enabled = isSessionActive || selectedVehicleId != null,
                haptic = Haptic.Confirm,
                // Recording takes the danger accent: it is the one state that must be
                // unmistakable at a glance from outside the car.
                accent = if (isSessionActive) TrackProTheme.colors.danger else null,
                primary = true,
                icon = if (isSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DragMetricCard(
    metric: DragMetricDisplay,
    modifier: Modifier = Modifier
) {
    // "Achieved" is signalled by a tinted border plus a small accent dot, and the value
    // brightening from dim to full. An earlier version also washed the whole tile in
    // accent and colored the label - four cues on eleven tiles at once, which is what
    // made this grid read as noisy.
    AppCard(
        modifier = modifier,
        borderColor = if (metric.achieved) TrackProTheme.colors.accent.copy(alpha = 0.45f)
                      else TrackProTheme.colors.sectorLine
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    metric.label,
                    style = TrackProType.label,
                    // Unreached splits stay quieter than reached ones, but both clear AA;
                    // textFaint measured 3.35:1 and this is a 10sp placard.
                    color = if (metric.achieved) TrackProTheme.colors.marking
                    else TrackProTheme.colors.markingDim
                )
                if (metric.achieved) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(TrackProTheme.colors.accent, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    metric.value,
                    style = TrackProType.statValue.atSize(19.sp),
                    color = if (metric.achieved) TrackProTheme.colors.textPrimary
                    else TrackProTheme.colors.textMuted.copy(alpha = 0.5f)
                )
                Text(
                    metric.unit,
                    style = TrackProType.body.atSize(10.sp),
                    color = TrackProTheme.colors.textMuted,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    return String.format(Locale.US, "%.2f", seconds)
}

/**
 * Arranges the drag HUD's two blocks for the orientation the phone is actually mounted in.
 *
 * Landscape is not a reflow here: side by side is what keeps the split readout at full
 * size on a short screen, which is the entire reason the number is large.
 */
@Composable
private fun AdaptiveSplit(
    landscape: Boolean,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit
) {
    if (landscape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(Modifier.weight(0.58f)) { primary() }
            Box(Modifier.weight(0.42f).padding(end = Spacing.gutter)) { secondary() }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            primary()
            Box(Modifier.padding(horizontal = Spacing.gutter)) { secondary() }
        }
    }
}

/** Speed, max and distance - stacked beside the split in landscape, in a row beneath it otherwise. */
@Composable
private fun DragLiveInstruments(
    landscape: Boolean,
    speedText: String,
    speedUnit: String,
    speedTrend: Trend,
    maxText: String,
    distanceText: String
) {
    val cells: List<Triple<String, String, Trend?>> = listOf(
        Triple(stringResource(R.string.drag_speed_unit, speedUnit), speedText, speedTrend),
        Triple(stringResource(R.string.drag_max), maxText, null),
        Triple(stringResource(R.string.drag_distance), distanceText, null)
    )
    if (landscape) {
        Column(Modifier.fillMaxWidth().paddockCard()) {
            cells.forEachIndexed { i, (label, value, trend) ->
                Instrument(
                    label = label,
                    value = value,
                    valueSize = 20.sp,
                    trend = trend,
                    modifier = Modifier.fillMaxWidth()
                )
                if (i < cells.lastIndex) Bezel()
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().paddockCard()) {
            cells.forEachIndexed { i, (label, value, trend) ->
                Instrument(
                    label = label,
                    value = value,
                    valueSize = 24.sp,
                    trend = trend,
                    modifier = Modifier.weight(1f)
                )
                if (i < cells.lastIndex) {
                    Bezel(vertical = true, modifier = Modifier.height(62.dp))
                }
            }
        }
    }
}
