package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.bezel
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.TrackProType
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected

/**
 * Solid action button — the accent fills only this small control, never a whole bar.
 * Pass [contentColor] to override the default onAccent foreground, e.g. for a
 * secondary/neutral button (accent = bgElevated) where black text wouldn't read.
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
    Box(
        modifier = modifier
            // pressable first: the transform has to wrap the filled surface below it.
            .pressable(
                onClick = onClick,
                enabled = enabled,
                scale = 0.96f,
                haptic = haptic,
                role = Role.Button
            )
            .background(if (enabled) accent else TrackProTheme.colors.bgElevated)
            // A lit block with a milled edge, not a pill. Disabled keeps the block and
            // drops to the bezel, so an unavailable action still shows where it is.
            .border(1.dp, if (enabled) accent else TrackProTheme.colors.bezel)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = TrackProType.titleLarge.atSize(14.sp),
            color = contentColor
                ?: if (enabled) TrackProTheme.colors.onAccent else TrackProTheme.colors.markingDim
        )
    }
}

/** Selectable pill used for mode/unit/sector-count pickers. */
@Composable
fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TrackProTheme.colors.accent
) {
    // Aliased because inside the semantics block the bare name `selected` resolves to the
    // property being assigned, not to this parameter.
    val isSelected = selected
    Box(
        modifier = modifier
            // Selection is a discrete commit, so this is one of the few taps that earns
            // a haptic - it fires on the same frame the fill flips.
            .pressable(
                onClick = onClick,
                scale = 0.96f,
                haptic = Haptic.Selection,
                role = Role.RadioButton
            )
            // Role.RadioButton alone announces "radio button" without saying which one is
            // chosen; the fill was the only thing carrying that, and a screen reader cannot
            // see a fill.
            .semantics { this.selected = isSelected }
            // 13sp over 8dp of padding measured about 32dp. This is the sector-count
            // picker and both Settings toggles, so it is worth the extra height.
            .heightIn(min = 48.dp)
            .background(
                if (selected) accent else TrackProTheme.colors.bgElevated,
                TrackProShapes.control
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else TrackProTheme.colors.sectorLine,
                shape = TrackProShapes.control
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TrackProType.titleMedium.atSize(13.sp),
            color = if (selected) TrackProTheme.colors.onAccent else TrackProTheme.colors.textPrimary
        )
    }
}
