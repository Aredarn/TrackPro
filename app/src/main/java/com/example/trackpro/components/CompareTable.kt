package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import java.util.Locale
import kotlin.math.abs

/**
 * One figure in a comparison table, with how far it is from the reference's. A missing figure
 * is a dash and takes no part in a difference.
 */
data class CompareCell(
    val text: String,
    val delta: Double? = null,
    val lowerIsBetter: Boolean = true,
    val deltaDigits: Int = 2,
    val note: String? = null
)

data class CompareRow(val label: String, val cells: List<CompareCell>)

/** A column heading: a name, a line under it, and the colour that car has in the charts. */
data class CompareHeader(val title: String, val subtitle: String? = null, val color: Color? = null)

private val LabelWidth = 96.dp
private val ColumnWidth = 96.dp

/**
 * One value per column, each column's gap to the first (the reference) when [withDelta] is
 * set. A missing value stays a dash.
 */
fun compareValueRow(
    label: String,
    values: List<Double?>,
    digits: Int = 2,
    lowerIsBetter: Boolean = true,
    withDelta: Boolean = true,
    suffix: String = ""
): CompareRow {
    val reference = values.firstOrNull()
    return CompareRow(
        label = label,
        cells = values.mapIndexed { i, v ->
            when {
                v == null -> CompareCell("–")
                withDelta && i > 0 && reference != null ->
                    CompareCell(compareNumber(v, digits) + suffix, delta = v - reference, lowerIsBetter = lowerIsBetter, deltaDigits = digits)
                else -> CompareCell(compareNumber(v, digits) + suffix)
            }
        }
    )
}

fun compareNumber(value: Double, digits: Int = 2): String =
    String.format(Locale.getDefault(), "%.${digits}f", value)

/** A difference with its sign shown, and no sign at all when it rounds to nothing. */
fun compareSigned(value: Double, digits: Int): String {
    val shown = String.format(Locale.getDefault(), "%.${digits}f", abs(value))
    val zero = shown.all { it == '0' || it == '.' || it == ',' }
    return if (zero) shown else (if (value < 0) "−" else "+") + shown
}

@Composable
fun CompareCard(
    title: String,
    note: String,
    headers: List<CompareHeader>,
    rows: List<CompareRow>,
    emptyText: String? = null
) {
    PaddockCard(padding = Spacing.md) {
        SectionLabel(title)
        Spacer(Modifier.height(Spacing.xs))
        Text(text = note, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
        Spacer(Modifier.height(Spacing.sm))
        if (rows.isEmpty() && emptyText != null) {
            Text(text = emptyText, style = TrackProType.body, color = TrackProTheme.colors.markingDim)
        } else {
            CompareTable(headers, rows)
        }
    }
}

@Composable
fun CompareTable(headers: List<CompareHeader>, rows: List<CompareRow>) {
    val tableWidth = LabelWidth + ColumnWidth * headers.size
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width(LabelWidth))
            headers.forEach { header ->
                Column(
                    modifier = Modifier.width(ColumnWidth).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        header.color?.let {
                            Spacer(Modifier.size(8.dp).clip(CircleShape).background(it))
                        }
                        Text(
                            text = header.title,
                            style = TrackProType.label,
                            color = TrackProTheme.colors.marking,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    header.subtitle?.let {
                        Text(text = it, style = TrackProType.label, color = TrackProTheme.colors.markingDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        rows.forEach { row ->
            Spacer(
                Modifier
                    .padding(top = Spacing.xs)
                    .width(tableWidth)
                    .height(1.dp)
                    .background(TrackProTheme.colors.sectorLine)
            )
            Row(modifier = Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.label,
                    style = TrackProType.body,
                    color = TrackProTheme.colors.markingDim,
                    modifier = Modifier.width(LabelWidth)
                )
                row.cells.forEach { cell ->
                    Column(
                        modifier = Modifier.width(ColumnWidth).padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(text = cell.text, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
                        cell.delta?.let { d ->
                            val better = if (cell.lowerIsBetter) d < 0 else d > 0
                            val neutral = abs(d) < 0.005
                            Text(
                                text = compareSigned(d, cell.deltaDigits),
                                style = TrackProType.label,
                                color = when {
                                    neutral -> TrackProTheme.colors.markingDim
                                    better -> TrackProTheme.colors.deltaGood
                                    else -> TrackProTheme.colors.deltaBad
                                }
                            )
                        }
                        cell.note?.let {
                            Text(text = it, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                        }
                    }
                }
            }
        }
    }
}
