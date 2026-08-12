package com.example.trackpro.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Motion
import com.example.trackpro.theme.Spacing

/**
 * Click-to-expand accordion shell shared by the drag-session and track-session list
 * screens (previously two structurally-identical copies: ExpandableSessionGroup /
 * ExpandableTrackGroup). The border tints towards [accent] while expanded, echoing the
 * same "active state" treatment used on selection/toggle cards elsewhere.
 */
@Composable
fun ExpandableGroup(
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    accent: Color = TrackProTheme.colors.accent,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    // The chevron used to jump 180 degrees and the body used to pop in with no
    // transition at all - the two most obviously "computer" moments in the app.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.standard(),
        label = "chevron"
    )

    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.contentSize()),
        padding = 0.dp,
        borderColor = if (expanded) accent.copy(alpha = 0.5f) else TrackProTheme.colors.sectorLine
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Row highlight rather than a scale: this header has no surface of its
                // own (AppCard draws it), so a transform would shrink only the text.
                .pressableRow(onClick = { expanded = !expanded })
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                header()
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = if (expanded) accent else TrackProTheme.colors.textFaint,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }
        if (expanded) {
            HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
            Column(modifier = Modifier.padding(Spacing.md)) {
                content()
            }
        }
    }
}
