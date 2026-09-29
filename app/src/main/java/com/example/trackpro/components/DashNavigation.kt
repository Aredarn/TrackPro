package com.example.trackpro.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.accentSoft
import com.example.trackpro.theme.fieldLive
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel

/** One destination on the [DashTabBar]. */
data class DashTab(val route: String, val label: String, val icon: ImageVector)

/** Height of the floating bar itself, excluding its margin and the system inset. */
val DashTabBarHeight = 68.dp

/**
 * The four tabs, as a floating rounded bar in the thumb zone.
 *
 * The selected tab lights up in orange with a soft orange pill behind its icon; the rest stay
 * quiet. Hidden on the two HUDs: nothing on a driving surface may be one mis-tap from
 * leaving it.
 */
@Composable
fun DashTabBar(
    tabs: List<DashTab>,
    selectedRoute: String?,
    onSelect: (DashTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.panel)
            .navigationBarsPadding()
            .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.xs, bottom = Spacing.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DashTabBarHeight)
                .paddockCard(shape = TrackProShapes.card, elevation = 16.dp)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                DashTabCell(
                    tab = tab,
                    isSelected = tab.route == selectedRoute,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DashTabCell(
    tab: DashTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink by animateColorAsState(
        if (isSelected) TrackProTheme.colors.accent else TrackProTheme.colors.markingDim,
        animationSpec = tween(160),
        label = "tabInk"
    )
    val pill by animateColorAsState(
        if (isSelected) TrackProTheme.colors.accentSoft else TrackProTheme.colors.accentSoft.copy(alpha = 0f),
        animationSpec = tween(160),
        label = "tabPill"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .pressable(onClick = onClick, haptic = if (isSelected) null else Haptic.Selection, role = Role.Tab, scale = 0.94f)
            .semantics { selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 30.dp)
                .clip(TrackProShapes.pill)
                .background(pill),
            contentAlignment = Alignment.Center
        ) {
            Icon(tab.icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label,
            style = TrackProType.label.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isSelected) TrackProTheme.colors.marking else TrackProTheme.colors.markingDim,
            maxLines = 1
        )
    }
}

/** Height of a [SectionSwitch] including its margin. */
val SectionSwitchHeight = 64.dp

/**
 * Splits one tab into sections - Track | Drag, Cars | Tracks - as a segmented control: a
 * rounded track with the selected segment raised on it.
 */
@Composable
fun <T> SectionSwitch(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.panel)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .paddockCard(shape = TrackProShapes.control, elevation = 0.dp)
                .padding(4.dp)
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val bg by animateColorAsState(
                    if (isSelected) TrackProTheme.colors.fieldLive else TrackProTheme.colors.fieldLive.copy(alpha = 0f),
                    animationSpec = tween(160),
                    label = "segment"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(TrackProShapes.chip)
                        .background(bg)
                        .pressable(
                            onClick = { onSelect(value) },
                            haptic = if (isSelected) null else Haptic.Selection,
                            role = Role.Tab,
                            scale = 0.97f
                        )
                        .semantics { this.selected = isSelected },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = TrackProType.titleMedium,
                        color = if (isSelected) TrackProTheme.colors.marking else TrackProTheme.colors.markingDim
                    )
                }
            }
        }
    }
}

/** "1 car", "7 tracks": a count, singular when there is one. */
fun countLabel(count: Int, noun: String): String =
    "$count ${if (count == 1) noun else noun + "s"}"
