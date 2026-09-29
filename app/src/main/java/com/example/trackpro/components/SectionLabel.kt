package com.example.trackpro.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.marking

/** A section heading inside a screen. Sentence case, as written. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TrackProTheme.colors.marking
) {
    Text(text = text, style = TrackProType.titleMedium, color = color, modifier = modifier)
}
