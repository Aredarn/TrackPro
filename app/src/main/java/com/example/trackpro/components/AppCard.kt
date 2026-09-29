package com.example.trackpro.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.bezel

/** A raised card. [borderColor] is accepted for older call sites and no longer drawn. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = Spacing.lg,
    @Suppress("UNUSED_PARAMETER") borderColor: Color = TrackProTheme.colors.bezel,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .paddockCard()
            .padding(padding),
        content = content
    )
}
