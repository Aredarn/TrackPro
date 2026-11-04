package com.example.trackpro.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType

/**
 * The way into a comparison screen: a card at the head of a history list, where it is seen,
 * rather than a link in the title bar, where it was not.
 */
@Composable
fun CompareEntryCard(title: String, hint: String, onClick: () -> Unit) {
    PaddockCard(onClick = onClick, padding = Spacing.md) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            IconCircle(icon = Icons.Outlined.BarChart, size = 44.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = TrackProType.titleMedium, color = TrackProTheme.colors.textPrimary)
                Text(text = hint, style = TrackProType.label, color = TrackProTheme.colors.textMuted)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TrackProTheme.colors.textMuted
            )
        }
    }
}
