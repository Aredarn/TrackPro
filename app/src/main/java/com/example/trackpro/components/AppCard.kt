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
import com.example.trackpro.theme.bezel
import com.example.trackpro.theme.field

/**
 * A field on the panel.
 *
 * Not a card: no radius, no elevation, no shadow. An instrument field is distinguished
 * from the panel by its ground and a milled hairline, which is the only containment the
 * dash world has. The name is kept so the screens that already call it inherit the world
 * without a rename sweep.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.md,
    borderColor: Color = TrackProTheme.colors.bezel,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(TrackProTheme.colors.field)
            .border(width = 1.dp, color = borderColor)
            .padding(padding),
        content = content
    )
}
