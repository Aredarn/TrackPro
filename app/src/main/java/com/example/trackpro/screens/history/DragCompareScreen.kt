package com.example.trackpro.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.trackpro.R
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.CompareCard
import com.example.trackpro.components.CompareCell
import com.example.trackpro.components.CompareHeader
import com.example.trackpro.components.CompareRow
import com.example.trackpro.components.DataGate
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.compareNumber
import com.example.trackpro.components.compareValueRow
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.countLabel
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.calculationClasses.DragCarSummary
import com.example.trackpro.managerClasses.calculationClasses.DragCompareScale
import com.example.trackpro.managerClasses.calculationClasses.DragCompareSpans
import com.example.trackpro.managerClasses.calculationClasses.SpanStat
import com.example.trackpro.managerClasses.utilities.UnitFormatter
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.viewModels.DragCompareViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Drag runs of the picked cars side by side: acceleration times, where the time goes, gear
 * changes, the theoretical best and power per kg. The first car picked is the reference the
 * others' differences are measured against.
 */
@Composable
fun DragCompareScreen(viewModel: DragCompareViewModel, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val useMetric by app.useMetricUnits.collectAsState()
    LaunchedEffect(useMetric) { viewModel.setMetric(useMetric) }

    val cars by viewModel.cars.collectAsState()
    val loadState by viewModel.loadState.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val occupants by viewModel.occupants.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val working by viewModel.working.collectAsState()
    var showBest by rememberSaveable { mutableStateOf(false) }

    val scroll = rememberScrollState()
    val scrolled by scroll.isScrolledUnderChrome()
    val scale = remember(useMetric) { DragCompareScale.of(useMetric) }
    val unit = UnitFormatter.speedUnitLabel(useMetric)

    ScreenScaffold(
        title = stringResource(R.string.compare_title),
        onBack = onBack,
        contentScrolled = scrolled
    ) { contentPadding ->
        DataGate(
            state = loadState,
            items = cars,
            emptyMessage = stringResource(R.string.history_no_drag_runs),
            emptyHint = stringResource(R.string.history_no_drag_runs_hint),
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
                // ── Picking ──
                SectionLabel(stringResource(R.string.compare_cars))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    available.forEach { car ->
                        ToggleChip(
                            text = car.name,
                            selected = car.vehicleId in selected,
                            onClick = { viewModel.toggle(car.vehicleId) }
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.compare_cars_hint, DragCompareViewModel.MAX_CARS),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )

                SectionLabel(stringResource(R.string.compare_people))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    (1..DragCompareViewModel.MAX_OCCUPANTS).forEach { n ->
                        ToggleChip(
                            text = n.toString(),
                            selected = n == occupants,
                            onClick = { viewModel.setOccupants(n) }
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.compare_people_note),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )

                if (summaries.isEmpty()) {
                    Text(
                        text = if (working) stringResource(R.string.compare_working) else stringResource(R.string.compare_pick_one),
                        style = TrackProType.titleMedium,
                        color = TrackProTheme.colors.markingDim,
                        modifier = Modifier.padding(top = Spacing.md)
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        ToggleChip(
                            text = stringResource(R.string.compare_mean),
                            selected = !showBest,
                            onClick = { showBest = false },
                            modifier = Modifier.weight(1f)
                        )
                        ToggleChip(
                            text = stringResource(R.string.compare_best),
                            selected = showBest,
                            onClick = { showBest = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (summaries.size >= 2) {
                        Text(
                            text = stringResource(R.string.compare_vs, summaries.first().name),
                            style = TrackProType.label,
                            color = TrackProTheme.colors.markingDim
                        )
                    }
                    CompareSections(summaries, scale, unit, showBest)
                }
            }
        }
    }
}

@Composable
private fun CompareSections(
    cars: List<DragCarSummary>,
    scale: DragCompareScale,
    unit: String,
    showBest: Boolean
) {
    val headers = cars.map { CompareHeader(it.name, countLabel(it.runCount, R.plurals.count_runs)) }
    fun pick(stat: SpanStat): Double? = if (showBest) stat.best else stat.mean
    fun spanRows(spans: List<Pair<Int, Int>>, withUnit: Boolean, stats: (DragCarSummary) -> List<SpanStat>) =
        spans.mapIndexed { i, span ->
            val label = "${span.first}–${span.second}" + if (withUnit) " $unit" else ""
            compareValueRow(label, cars.map { pick(stats(it)[i]) })
        }

    CompareCard(
        title = stringResource(R.string.compare_splits),
        note = stringResource(R.string.compare_splits_note),
        headers = headers,
        rows = spanRows(DragCompareSpans.splits, true) { it.splits }
    )
    CompareCard(
        title = stringResource(R.string.compare_segments),
        note = stringResource(R.string.compare_segments_note),
        headers = headers,
        rows = spanRows(DragCompareSpans.segments, true) { it.segments }
    )
    CompareCard(
        title = stringResource(R.string.compare_bands, unit),
        note = stringResource(R.string.compare_bands_note, unit),
        headers = headers,
        rows = spanRows(DragCompareSpans.bands, false) { it.bands }
    )

    CompareCard(
        title = stringResource(R.string.compare_theory),
        note = stringResource(R.string.compare_theory_note),
        headers = headers,
        rows = listOf(
            compareValueRow(stringResource(R.string.compare_theory_row), cars.map { it.theoretical?.seconds }),
            compareValueRow(stringResource(R.string.compare_actual_row), cars.map { it.theoretical?.actualBest }),
            compareValueRow(
                stringResource(R.string.compare_gain_row),
                cars.map { c -> c.theoretical?.let { it.actualBest - it.seconds } },
                withDelta = false
            )
        )
    )

    val clusters = cars.maxOfOrNull { it.shifts.size } ?: 0
    CompareCard(
        title = stringResource(R.string.compare_shifts),
        note = stringResource(R.string.compare_shifts_note),
        headers = headers,
        rows = (0 until clusters).map { i ->
            CompareRow(
                label = "${i + 1}→${i + 2}",
                cells = cars.map { car ->
                    val c = car.shifts.getOrNull(i)
                    if (c == null) CompareCell("–") else CompareCell(
                        text = compareNumber(c.meanLost) + " s",
                        note = stringResource(
                            R.string.compare_shift_detail,
                            scale.displayUnits(c.meanSpeedKmh).roundToInt(),
                            unit,
                            c.count
                        )
                    )
                }
            )
        },
        emptyText = stringResource(R.string.compare_shifts_none)
    )

    val powerRows = DragCompareSpans.power.mapIndexed { i, span ->
        compareValueRow(
            "${span.first}–${span.second} $unit",
            cars.map { it.powerWkg[i] },
            digits = 0,
            lowerIsBetter = false
        )
    } + compareValueRow(stringResource(R.string.compare_mass_row), cars.map { it.massKg }, digits = 0, withDelta = false) +
        compareValueRow(
            stringResource(R.string.compare_hp_row),
            cars.map { c -> c.massKg?.takeIf { c.horsepower > 0 }?.let { c.horsepower / it * 1000.0 } },
            digits = 0,
            withDelta = false
        )
    CompareCard(
        title = stringResource(R.string.compare_power),
        note = stringResource(R.string.compare_power_note) +
            if (cars.any { it.massKg == null }) " " + stringResource(R.string.compare_no_weight) else "",
        headers = headers,
        rows = powerRows
    )
}
