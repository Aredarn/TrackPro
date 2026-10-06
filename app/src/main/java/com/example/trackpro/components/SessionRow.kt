package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One session in a history list: a date badge in place of a bullet, what the session was,
 * when, and where tapping leads. Voided sessions carry their mark instead of the chevron.
 * Hold to delete.
 */
@Composable
fun SessionRow(
    startTime: Long,
    title: String,
    subtitle: String,
    voided: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val zoned = Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TrackProShapes.control)
            .pressable(onClick = onClick, onLongClick = onLongClick, scale = 0.98f)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .size(48.dp)
                .clip(TrackProShapes.chip)
                .background(TrackProTheme.colors.bgElevated),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text(
                zoned.dayOfMonth.toString(),
                style = TrackProType.titleMedium,
                color = TrackProTheme.colors.marking
            )
            Text(
                zoned.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())),
                style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold),
                color = TrackProTheme.colors.accent
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = TrackProType.label, color = TrackProTheme.colors.markingDim, maxLines = 1)
        }
        if (voided) {
            VoidStamp()
        } else {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TrackProTheme.colors.markingDim)
        }
    }
}
