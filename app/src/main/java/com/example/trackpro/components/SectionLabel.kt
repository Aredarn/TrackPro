package com.example.trackpro.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TrackProTheme.colors.textFaint
) {
    Text(
        text = text.uppercase(),
        style = TrackProType.label,
        color = color,
        modifier = modifier
    )
}
