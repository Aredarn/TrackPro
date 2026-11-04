package com.example.trackpro.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.trackpro.R
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.ChartSeries
import com.example.trackpro.components.CompareCard
import com.example.trackpro.components.CompareCell
import com.example.trackpro.components.CompareColors
import com.example.trackpro.components.CompareHeader
import com.example.trackpro.components.CompareRow
import com.example.trackpro.components.DataGate
import com.example.trackpro.components.LineChart
import com.example.trackpro.components.LineMap
import com.example.trackpro.components.MapLine
import com.example.trackpro.components.MapMark
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.compareNumber
import com.example.trackpro.components.compareSigned
import com.example.trackpro.components.compareValueRow
import com.example.trackpro.components.countLabel
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.calculationClasses.Corner
import com.example.trackpro.managerClasses.calculationClasses.TrackComparison
import com.example.trackpro.managerClasses.calculationClasses.TrackCompare
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.markingDim
import com.example.trackpro.viewModels.TrackCompareState
import com.example.trackpro.viewModels.TrackCompareViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Corners listed in the table and offered for zooming; a long road has far more bends than fit. */
private const val MAX_CORNERS = 12

/** Samples shown either side of a corner when zoomed to it: 100 m at the 5 m step. */
private const val ZOOM_MARGIN = 20

/**
 * Laps and runs on one track, car against car or lap against lap: times, sectors, corners, the
 * line each took, and where along the lap one gained on the other. The first pick is the
 * reference; every difference and the chart zero are measured against it.
 */
@Composable
fun TrackCompareScreen(viewModel: TrackCompareViewModel, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()

    val tracks by viewModel.tracks.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val selectedTrack by viewModel.selectedTrack.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val state by viewModel.state.collectAsState()

    val scroll = rememberScrollState()
    val scrolled by scroll.isScrolledUnderChrome()

    ScreenScaffold(
        title = stringResource(R.string.track_compare_title),
        onBack = onBack,
        contentScrolled = scrolled
    ) { contentPadding ->
        DataGate(
            state = loadState,
            items = tracks,
            emptyMessage = stringResource(R.string.track_compare_empty),
            emptyHint = stringResource(R.string.track_compare_empty_hint),
            loadingLabel = stringResource(R.string.history_reading_runs)
        ) { available ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(
                        top = contentPadding.calculateTopPadding() + Spacing.md,
                        bottom = Spacing.xl,
                        start = Spacing.gutter,
                        end = Spacing.gutter
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SectionLabel(stringResource(R.string.track_compare_track))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    available.forEach { track ->
                        ToggleChip(
                            text = track.name,
                            selected = track.trackId == selectedTrack,
                            onClick = { viewModel.selectTrack(track.trackId) }
                        )
                    }
                }

                if (selectedTrack != null) {
                    SectionLabel(stringResource(R.string.track_compare_cars))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        entries.filter { it.isCar }.forEach { entry ->
                            ToggleChip(
                                text = entry.label,
                                selected = entry.key in selected,
                                onClick = { viewModel.toggle(entry.key) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.track_compare_cars_hint, TrackCompareViewModel.MAX_PICKS),
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim
                    )
                    val singles = entries.filter { !it.isCar }
                    if (singles.isNotEmpty()) {
                        SectionLabel(stringResource(R.string.track_compare_runs))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            singles.forEach { entry ->
                                ToggleChip(
                                    text = "${entry.label} · ${entry.detail}",
                                    selected = entry.key in selected,
                                    onClick = { viewModel.toggle(entry.key) }
                                )
                            }
                        }
                    }

                    when (val current = state) {
                        TrackCompareState.Idle -> Hint(stringResource(R.string.track_compare_pick))
                        TrackCompareState.Working -> Hint(stringResource(R.string.compare_working))
                        TrackCompareState.NoLaps -> Hint(stringResource(R.string.track_compare_no_laps))
                        is TrackCompareState.Ready -> TrackCompareResults(current.comparison, useMetric)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = TrackProType.titleMedium,
        color = TrackProTheme.colors.markingDim,
        modifier = Modifier.padding(top = Spacing.md)
    )
}

@Composable
private fun TrackCompareResults(comparison: TrackComparison, useMetric: Boolean) {
    val cars = comparison.cars
    val reference = comparison.reference
    val unit = UnitFormatter.speedUnitLabel(useMetric)
    var bySectors by rememberSaveable { mutableStateOf(false) }
    var focus by remember(comparison) { mutableStateOf<Int?>(null) }

    val headers = cars.mapIndexed { i, c ->
        CompareHeader(c.name, countLabel(c.validLaps, R.plurals.count_runs), CompareColors[i % CompareColors.size])
    }
    val shown = remember(comparison) { importantCorners(comparison) }

    if (cars.size >= 2) {
        Text(
            text = stringResource(R.string.track_compare_vs, cars.first().name),
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim
        )
    }
    val skipped = cars.sumOf { it.skippedLaps }
    if (skipped > 0) {
        Text(
            text = stringResource(R.string.track_compare_skipped, skipped),
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim
        )
    }

    // ── Times ──
    CompareCard(
        title = stringResource(R.string.track_compare_times),
        note = stringResource(R.string.track_compare_times_note),
        headers = headers,
        rows = listOf(
            clockRow(stringResource(R.string.track_compare_row_best), cars.map { it.bestLapMs?.let { ms -> ms / 1000.0 } }),
            clockRow(stringResource(R.string.track_compare_row_average), cars.map { it.averageLapMs?.let { ms -> ms / 1000.0 } }),
            clockRow(stringResource(R.string.track_compare_row_theory), cars.map { it.theoreticalSeconds }),
            compareValueRow(stringResource(R.string.track_compare_row_laps), cars.map { it.validLaps.toDouble() }, digits = 0, withDelta = false)
        )
    )

    // ── Sectors ──
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ToggleChip(
            text = stringResource(R.string.track_compare_mode_lap),
            selected = !bySectors,
            onClick = { bySectors = false },
            modifier = Modifier.weight(1f)
        )
        ToggleChip(
            text = stringResource(R.string.track_compare_mode_sectors),
            selected = bySectors,
            onClick = { bySectors = true },
            modifier = Modifier.weight(1f)
        )
    }
    val sectorCount = comparison.sectorEdges.size - 1
    CompareCard(
        title = stringResource(R.string.track_compare_sectors),
        note = stringResource(if (comparison.equalSectors) R.string.track_compare_sectors_equal else R.string.track_compare_sectors_gates),
        headers = headers,
        rows = (0 until sectorCount).map { s ->
            compareValueRow(
                stringResource(R.string.track_compare_sector_row, s + 1),
                cars.map { c -> (if (bySectors) c.bestSectors else c.bestLapSectors).getOrNull(s) },
                suffix = " s"
            )
        }
    )

    // ── Corners ──
    CompareCard(
        title = stringResource(R.string.track_compare_corners),
        note = stringResource(R.string.track_compare_corners_note),
        headers = headers,
        rows = shown.map { (i, corner) ->
            val side = stringResource(if (corner.direction > 0) R.string.track_compare_left else R.string.track_compare_right)
            val refSpeed = cars.first().corners.getOrNull(i)?.let { UnitFormatter.convertSpeed(it.apexKmh, useMetric) }
            CompareRow(
                label = stringResource(R.string.track_compare_corner_row, corner.index, side),
                cells = cars.mapIndexed { ci, car ->
                    val stat = car.corners.getOrNull(i) ?: return@mapIndexed CompareCell("–")
                    val speed = UnitFormatter.convertSpeed(stat.apexKmh, useMetric)
                    CompareCell(
                        text = compareNumber(speed, 0),
                        delta = if (ci > 0 && refSpeed != null) speed - refSpeed else null,
                        lowerIsBetter = false,
                        deltaDigits = 0,
                        note = stringResource(
                            R.string.track_compare_corner_cell,
                            stat.minRadius?.roundToInt()?.toString() ?: "–",
                            compareSigned(stat.insideOffset, 1)
                        )
                    )
                }
            )
        },
        emptyText = stringResource(R.string.track_compare_corner_none)
    )

    // ── The line ──
    PaddockCard(padding = Spacing.md) {
        SectionLabel(stringResource(R.string.track_compare_line))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.track_compare_line_note),
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim
        )
        Spacer(Modifier.height(Spacing.sm))
        Legend(headers)
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ToggleChip(
                text = stringResource(R.string.track_compare_whole),
                selected = focus == null,
                onClick = { focus = null }
            )
            shown.forEach { (i, corner) ->
                ToggleChip(text = "C${corner.index}", selected = focus == i, onClick = { focus = i })
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        val zoom = focus?.let { comparison.corners[it] }
        LineMap(
            lines = cars.mapIndexedNotNull { i, c ->
                c.best?.let { MapLine(CompareColors[i % CompareColors.size], it.x, it.y) }
            },
            marks = mapMarks(comparison, shown.map { it.second }),
            from = zoom?.let { it.startIndex - ZOOM_MARGIN } ?: 0,
            to = zoom?.let { it.endIndex + ZOOM_MARGIN } ?: (reference.size - 1)
        )
    }

    // ── Gap and speed along the lap ──
    val ds = TrackCompare.STEP_METERS.toFloat()
    val lengthM = reference.lengthM.toFloat()
    val marks = comparison.sectorEdges.drop(1).dropLast(1).map { (it * ds).toFloat() }
    val first = cars.firstOrNull()?.best
    if (cars.size >= 2 && first != null) {
        ChartCard(
            title = stringResource(R.string.track_compare_gap),
            note = stringResource(R.string.track_compare_gap_note),
            headers = headers
        ) {
            LineChart(
                series = cars.mapIndexedNotNull { i, c ->
                    if (i == 0) return@mapIndexedNotNull null
                    c.best?.let { best ->
                        ChartSeries(
                            CompareColors[i % CompareColors.size],
                            FloatArray(reference.size) { k -> k * ds },
                            FloatArray(reference.size) { k -> (best.t[k] - first.t[k]).toFloat() }
                        )
                    }
                },
                xMax = lengthM,
                xLabel = ::distanceLabel,
                yLabel = { compareNumber(it.toDouble(), 1) + " s" },
                marks = marks,
                zeroLine = true
            )
        }
    }
    ChartCard(
        title = stringResource(R.string.track_compare_speed) + " ($unit)",
        note = stringResource(R.string.track_compare_speed_note),
        headers = headers
    ) {
        LineChart(
            series = cars.mapIndexedNotNull { i, c ->
                c.best?.let { best ->
                    ChartSeries(
                        CompareColors[i % CompareColors.size],
                        FloatArray(reference.size) { k -> k * ds },
                        FloatArray(reference.size) { k -> UnitFormatter.convertSpeed(best.v[k], useMetric).toFloat() }
                    )
                }
            },
            xMax = lengthM,
            xLabel = ::distanceLabel,
            yLabel = { compareNumber(it.toDouble(), 0) },
            marks = marks
        )
    }
}

@Composable
private fun ChartCard(title: String, note: String, headers: List<CompareHeader>, chart: @Composable () -> Unit) {
    PaddockCard(padding = Spacing.md) {
        SectionLabel(title)
        Spacer(Modifier.height(Spacing.xs))
        Text(text = note, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
        Spacer(Modifier.height(Spacing.sm))
        Legend(headers)
        Spacer(Modifier.height(Spacing.sm))
        chart()
    }
}

/** Who is which colour: the same dots as in the table headings. */
@Composable
private fun Legend(headers: List<CompareHeader>) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        headers.forEach { header ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.size(10.dp).clip(CircleShape).background(header.color ?: TrackProTheme.colors.markingDim))
                Text(text = header.title, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
            }
        }
    }
}

/** A time as m:ss.hh with its gap to the reference in seconds, or a dash for a missing one. */
private fun clockRow(label: String, seconds: List<Double?>): CompareRow {
    val reference = seconds.firstOrNull()
    return CompareRow(
        label = label,
        cells = seconds.mapIndexed { i, s ->
            when {
                s == null -> CompareCell("–")
                i > 0 && reference != null -> CompareCell(clock(s), delta = s - reference)
                else -> CompareCell(clock(s))
            }
        }
    )
}

private fun clock(seconds: Double): String = (seconds * 1000.0).roundToLong().toLapTimeString()

private fun distanceLabel(meters: Float): String =
    if (meters >= 2000f) compareNumber(meters / 1000.0, 1) + " km" else compareNumber(meters.toDouble(), 0) + " m"

/**
 * The corners worth a row. On a long road there can be dozens; with several cars the ones they
 * took most differently, with one car the biggest turns, up to [MAX_CORNERS] in track order.
 * Each is paired with its place in the comparison's corner list.
 */
private fun importantCorners(comparison: TrackComparison): List<Pair<Int, Corner>> {
    val withStats = comparison.cars.filter { it.corners.isNotEmpty() }
    val scored = comparison.corners.mapIndexed { i, corner ->
        val score = if (withStats.size >= 2) {
            val speeds = withStats.mapNotNull { it.corners.getOrNull(i)?.apexKmh }
            if (speeds.size >= 2) speeds.max() - speeds.min() else 0.0
        } else {
            corner.angleDegrees
        }
        Triple(i, corner, score)
    }
    return scored.sortedByDescending { it.third }.take(MAX_CORNERS).sortedBy { it.first }.map { it.first to it.second }
}

/** Sector gates as bare dots, and each shown corner's number at its apex, on the reference line. */
private fun mapMarks(comparison: TrackComparison, corners: List<Corner>): List<MapMark> {
    val reference = comparison.reference
    val gates = comparison.sectorEdges.drop(1).dropLast(1).map {
        val k = min(max(it.roundToInt(), 0), reference.size - 1)
        MapMark(reference.x[k], reference.y[k], null)
    }
    val labels = corners.map { MapMark(reference.x[it.apexIndex], reference.y[it.apexIndex], "C${it.index}") }
    return gates + labels
}
