package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType

/** Marks a voided session: kept, but out of every best. A soft red pill. */
@Composable
fun VoidStamp(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(TrackProShapes.pill)
            .background(TrackProTheme.colors.danger.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = "Voided",
            style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold),
            color = TrackProTheme.colors.danger
        )
    }
}
