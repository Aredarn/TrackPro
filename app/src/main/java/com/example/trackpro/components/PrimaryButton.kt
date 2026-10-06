package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.accentGlow
import com.example.trackpro.theme.fieldLive
import com.example.trackpro.theme.marking

/**
 * The filled button: rounded, the accent with a soft glow of its own colour beneath it.
 * One per screen. Pass [accent] and [contentColor] for a neutral or destructive variant.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TrackProTheme.colors.accent,
    contentColor: Color? = null,
    enabled: Boolean = true,
    /** Only for genuine commits - starting a session, saving, marking a sector. */
    haptic: Haptic? = null
) {
    val isAccent = accent == TrackProTheme.colors.accent
    val glow = if (isAccent) TrackProTheme.colors.accentGlow else Color.Transparent
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(onClick = onClick, enabled = enabled, scale = 0.96f, haptic = haptic, role = Role.Button)
            .shadow(if (enabled && isAccent) 14.dp else 0.dp, TrackProShapes.control, clip = false, ambientColor = glow, spotColor = glow)
            .clip(TrackProShapes.control)
            .background(accent)
            .heightIn(min = 52.dp)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TrackProType.titleMedium,
            color = contentColor ?: if (isAccent) TrackProTheme.colors.onAccent else TrackProTheme.colors.marking
        )
    }
}

/** A selectable pill: mode, unit and count pickers. Selected fills with the accent. */
@Composable
fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TrackProTheme.colors.accent
) {
    // Aliased: inside the semantics block the bare name `selected` is the property.
    val isSelected = selected
    Box(
        modifier = modifier
            .pressable(onClick = onClick, scale = 0.96f, haptic = Haptic.Selection, role = Role.RadioButton)
            .semantics { this.selected = isSelected }
            .heightIn(min = 48.dp)
            .clip(TrackProShapes.chip)
            .background(if (selected) accent else TrackProTheme.colors.fieldLive)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TrackProType.titleMedium,
            color = if (selected) TrackProTheme.colors.onAccent else TrackProTheme.colors.marking
        )
    }
}
