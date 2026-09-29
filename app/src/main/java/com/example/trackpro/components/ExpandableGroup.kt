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
import com.example.trackpro.theme.markingDim

/** A card that opens to show its rows. Shared by the drag and track session lists. */
@Composable
fun ExpandableGroup(
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    accent: Color = TrackProTheme.colors.accent,
    header: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.standard(),
        label = "chevron"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .paddockCard()
            .animateContentSize(animationSpec = Motion.contentSize())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressableRow(onClick = { expanded = !expanded })
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { header() }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = if (expanded) accent else TrackProTheme.colors.markingDim,
                modifier = Modifier.size(22.dp).rotate(chevronRotation)
            )
        }
        if (expanded) {
            Bezel(modifier = Modifier.padding(horizontal = Spacing.lg))
            Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) { content() }
        }
    }
}
