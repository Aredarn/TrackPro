package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.md,
    borderColor: Color = TrackProTheme.colors.sectorLine,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(TrackProTheme.colors.bgCard, TrackProShapes.card)
            .border(width = 1.dp, color = borderColor, shape = TrackProShapes.card)
            .padding(padding),
        content = content
    )
}
