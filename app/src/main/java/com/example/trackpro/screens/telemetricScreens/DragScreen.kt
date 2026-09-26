package com.example.trackpro.screens.telemetricScreens

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
            headlineCaption = if (currentMetrics.quarterMileTime != null) "Quarter mile"
            else "Best split",
            rows = listOf(
                "Runs" to "${currentMetrics.runCount}",
                "Top speed" to "${UnitFormatter.formatSpeed(currentMetrics.maxSpeed, useMetric)} ${UnitFormatter.speedUnitLabel(useMetric)}",
                "Distance" to UnitFormatter.formatDistance(currentMetrics.totalDistance.toDouble(), useMetric)
            ),
            saveFailed = saveError,
            onKeep = { recorder.clearLastRun(); onBack() },
            onVoid = { recorder.voidLastRun(); onBack() }
        )
        return
    }

    if (confirmStop) {
        ConfirmDeleteDialog(
            title = "Stop recording?",
            message = "The run is written to the archive. You can void it on the next " +
                "screen if it should not count.",
            confirmLabel = "Stop",
            dismissLabel = "Keep recording",
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
            title = "Drag Mode",
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
                            "RUN ${currentMetrics.runCount}",
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                    // The elapsed clock used to be here AND in the readout below. One is
                    // enough, and the readout is where the eye already is.
                    Text(
                        if (isConnected) "GPS LOCKED" else "NO SIGNAL",
                        style = TrackProType.label,
                        // A fault is never dimmer than health.
                        color = if (isConnected) TrackProTheme.colors.deltaGood
                        else TrackProTheme.colors.deltaBad
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        "DATA",
                        style = TrackProType.label,
                        color = if (showData) TrackProTheme.colors.marking
                        else TrackProTheme.colors.markingDim,
                        modifier = Modifier
                            .pressable(onClick = { showData = !showData }, scale = 0.94f)
                            .padding(horizontal = Spacing.sm, vertical = 12.dp)
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
                        .padding(Spacing.md)
                        .pressable(onClick = { showVehicleDropdown = true })
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Vehicle", style = TrackProType.label, color = TrackProTheme.colors.textMuted)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                selectedVehicle?.let { "${it.manufacturer} ${it.model}" }
                                    ?: "Select Vehicle",
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
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .background(
                            TrackProTheme.colors.bgCard.copy(alpha = 0.5f),
                            TrackProShapes.control
                        )
                        .padding(Spacing.sm)
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
                    currentMetrics.quarterMileTime?.let { "1/4 MILE" to it },
                    currentMetrics.halfMileTime?.let { "1/2 MILE" to it }
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
                        .background(TrackProTheme.colors.field)
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Readout(
                        value = latestSplit?.second
                            ?: (gpsData?.speed?.let { UnitFormatter.formatSpeed(it, useMetric) } ?: "0"),
                        caption = latestSplit?.let { "${it.first}  \u00b7  SEC" }
                            ?: "Live speed \u00b7 ${UnitFormatter.speedUnitLabel(useMetric)}",
                        valueColor = if (latestSplit != null) TrackProTheme.colors.accent
                        else TrackProTheme.colors.marking,
                        valueSize = 72.sp,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isSessionActive) "REC" else "IDLE",
                                    style = TrackProType.label,
                                    color = if (isSessionActive) TrackProTheme.colors.danger
                                    else TrackProTheme.colors.markingDim
                                )
                                Text(
                                    text = elapsedTime,
                                    style = TrackProType.statValue.atSize(18.sp),
                                    color = TrackProTheme.colors.marking
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
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

            Bezel()

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
                "1/4 mile" to currentMetrics.quarterMileTime?.let { formatTime(it) },
                // Trap speed is the one cell here holding a speed rather than a time, so it
                // carries its unit in the placard - every other cell is seconds.
                "1/4 trap ${UnitFormatter.speedUnitLabel(useMetric)}" to
                        currentMetrics.quarterMileSpeed?.let {
                            UnitFormatter.formatSpeed(it, useMetric)
                        },
                "1/2 mile" to currentMetrics.halfMileTime?.let { formatTime(it) }
            )

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
                if (rowIndex < (splitCells.size - 1) / 3) Bezel()
            }

            Bezel()
            Spacer(Modifier.height(12.dp))

            // 5. SPEED CHART (LARGE)
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = Spacing.md)
            ) {
                Column {
                    SectionLabel("Speed Profile")
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
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            DashAction(
                // A running session is always stoppable, whatever the vehicle list is
                // doing: it reloads asynchronously after the screen is recreated, and
                // resolving the name first left Stop disabled mid-run until it arrived.
                label = when {
                    isSessionActive -> "Stop"
                    selectedVehicleId == null -> "Select a vehicle"
                    else -> "Start drag"
                },
                detail = when {
                    isSessionActive -> "Recording \u00b7 ${currentMetrics.runCount} run(s)"
                    selectedVehicleId == null -> "Every run is recorded against a car"
                    else -> "Arm the timer and launch"
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
                accent = if (isSessionActive) TrackProTheme.colors.danger
                else TrackProTheme.colors.accent,
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
                    metric.label.uppercase(),
                    style = TrackProType.label,
                    color = if (metric.achieved) TrackProTheme.colors.textMuted else TrackProTheme.colors.textFaint
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
    return String.format("%.2f", seconds)
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
            Bezel(vertical = true, modifier = Modifier.fillMaxHeight())
            Box(Modifier.weight(0.42f)) { secondary() }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            primary()
            Bezel()
            secondary()
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
        Triple("Speed \u00b7 $speedUnit", speedText, speedTrend),
        Triple("Max", maxText, null),
        Triple("Distance", distanceText, null)
    )
    if (landscape) {
        Column(Modifier.fillMaxWidth()) {
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
        Row(modifier = Modifier.fillMaxWidth()) {
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
