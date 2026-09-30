package com.example.trackpro.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.pressable
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.fieldLive
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel

/** Height of a [SessionFilterBar] including its margin. */
val SessionFilterBarHeight = 56.dp

/** One choice in a filter menu: an id to filter on and what to call it. */
data class FilterOption(val id: Long, val label: String)

/**
 * The History list's filters, as a row of dropdown chips under the section switch: car,
 * track, period, and order.
 *
 * A chip that is hiding something is lit in the accent, so a list that looks short always
 * says why. A clear chip appears at the front of the row only then. The row scrolls sideways
 * rather than wrapping, so the header keeps one fixed height and the list never jumps.
 *
 * [cars] and [tracks] should be only those the sessions actually use, so no choice leads to an
 * empty list. [tracks] is null where a track filter makes no sense (drag runs).
 */
@Composable
fun SessionFilterBar(
    filter: SessionFilter,
    onChange: (SessionFilter) -> Unit,
    cars: List<FilterOption>,
    tracks: List<FilterOption>?,
    sorts: List<SessionSort>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SessionFilterBarHeight)
            .background(TrackProTheme.colors.panel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // First, not last: at the end of the row it sat past the screen edge on a phone,
        // exactly when a filter was on and it was wanted.
        if (filter.narrows) {
            ClearFiltersChip(onClick = { onChange(filter.cleared()) })
        }
        FilterMenuChip(
            label = labelFor(filter.vehicleId, cars, all = "All cars", missing = "Removed car"),
            active = filter.vehicleId != null,
            description = "Filter by car",
            options = listOf<Pair<Long?, String>>(null to "All cars") + cars.map { it.id to it.label },
            selected = filter.vehicleId,
            onSelect = { onChange(filter.copy(vehicleId = it)) }
        )
        if (tracks != null) {
            FilterMenuChip(
                label = labelFor(filter.trackId, tracks, all = "All tracks", missing = "Removed track"),
                active = filter.trackId != null,
                description = "Filter by track",
                options = listOf<Pair<Long?, String>>(null to "All tracks") + tracks.map { it.id to it.label },
                selected = filter.trackId,
                onSelect = { onChange(filter.copy(trackId = it)) }
            )
        }
        FilterMenuChip(
            label = filter.period.label,
            active = filter.period != SessionPeriod.ALL,
            description = "Filter by date",
            options = SessionPeriod.entries.map { it to it.label },
            selected = filter.period,
            onSelect = { onChange(filter.copy(period = it)) }
        )
        // Order hides nothing, so it is never lit - only the filters are.
        FilterMenuChip(
            label = filter.sort.label,
            active = false,
            description = "Sort",
            options = sorts.map { it to it.label },
            selected = filter.sort,
            onSelect = { onChange(filter.copy(sort = it)) }
        )
    }
}

/** The chip's text: the chosen option's label, or a stand-in for "any" or a vanished id. */
private fun labelFor(id: Long?, options: List<FilterOption>, all: String, missing: String): String =
    if (id == null) all else options.firstOrNull { it.id == id }?.label ?: missing

/**
 * A chip that opens a menu of choices. Lit in the accent while it is narrowing the list.
 */
@Composable
private fun <T> FilterMenuChip(
    label: String,
    active: Boolean,
    description: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val ink = if (active) TrackProTheme.colors.onAccent else TrackProTheme.colors.marking

    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(TrackProShapes.chip)
                .background(if (active) TrackProTheme.colors.accent else TrackProTheme.colors.fieldLive)
                .pressable(onClick = { open = true }, scale = 0.96f, role = Role.Button)
                .semantics { contentDescription = "$description: $label" }
                .padding(start = Spacing.md, end = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = TrackProType.titleMedium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(TrackProTheme.colors.fieldLive)
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = text,
                            style = TrackProType.titleMedium,
                            color = if (isSelected) TrackProTheme.colors.accent else TrackProTheme.colors.marking
                        )
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = TrackProTheme.colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(value)
                    }
                )
            }
        }
    }
}

/** Resets every filter, keeping the order. */
@Composable
private fun ClearFiltersChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(TrackProShapes.chip)
            .pressable(onClick = onClick, scale = 0.96f, role = Role.Button, haptic = Haptic.Selection)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = null,
            tint = TrackProTheme.colors.markingDim,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Spacing.xs))
        Text("Clear", style = TrackProType.titleMedium, color = TrackProTheme.colors.markingDim)
    }
}
