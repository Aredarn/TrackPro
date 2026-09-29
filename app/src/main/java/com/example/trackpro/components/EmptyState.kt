package com.example.trackpro.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim

/**
 * An empty screen as an invitation: an icon, what belongs here, why, and the action that
 * fills it. Never a bare "nothing here".
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            IconCircle(icon = icon, size = 64.dp)
            Spacer(Modifier.height(Spacing.lg))
            Text(text = message, style = TrackProType.titleLarge, color = TrackProTheme.colors.marking, textAlign = TextAlign.Center)
            if (hint != null) {
                Spacer(Modifier.height(Spacing.sm))
                Text(text = hint, style = TrackProType.body, color = TrackProTheme.colors.markingDim, textAlign = TextAlign.Center)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}
