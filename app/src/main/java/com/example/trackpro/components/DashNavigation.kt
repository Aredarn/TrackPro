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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.bezel
import com.example.trackpro.theme.field
import com.example.trackpro.theme.fieldLive
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import com.example.trackpro.theme.segmentOff

/** One destination on the [DashTabBar]. */
data class DashTab(val route: String, val label: String, val icon: ImageVector)

/** Tall enough for a 48dp target plus the lit strip above it. */
val DashTabBarHeight = 60.dp

/**
 * The four tabs, drawn as the bottom row of switches on a dash.
 *
 * Each tab is an aperture; the one you are on is lit — field-live ground, marking ink, and a
 * strip of accent segments across its top edge, the same segment language the lit blocks and
 * the delta bar speak. The others are unlit switches: present, readable, not shouting.
 * No pill indicator and no ripple; the panel has neither.
 *
 * Hidden on the two HUDs: nothing on a driving surface may be one mis-tap from leaving it.
 */
@Composable
fun DashTabBar(
    tabs: List<DashTab>,
    selectedRoute: String?,
    onSelect: (DashTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.panel)
            .navigationBarsPadding()
    ) {
        Bezel()
        Row(modifier = Modifier.fillMaxWidth().height(DashTabBarHeight)) {
            tabs.forEachIndexed { index, tab ->
                if (index > 0) Bezel(vertical = true, modifier = Modifier.fillMaxHeight())
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
        if (isSelected) TrackProTheme.colors.marking else TrackProTheme.colors.markingDim,
        animationSpec = tween(120),
        label = "tabInk"
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .pressableRow(onClick = onClick, haptic = if (isSelected) null else Haptic.Selection, role = Role.Tab)
            .semantics { selected = isSelected }
            .background(if (isSelected) TrackProTheme.colors.fieldLive else TrackProTheme.colors.panel),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The lit strip. Unlit segments are drawn, never omitted, so every tab keeps its edge.
        Row(
            modifier = Modifier.fillMaxWidth().height(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(4) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (isSelected) TrackProTheme.colors.accent else TrackProTheme.colors.segmentOff)
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(tab.icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(tab.label.uppercase(), style = TrackProType.label, color = ink, maxLines = 1)
        }
    }
}

/** Height of a [SectionSwitch]: one 48dp target row and its closing bezel. */
val SectionSwitchHeight = 49.dp

/**
 * Splits one tab into two or three sections — Track | Drag, Cars | Tracks.
 *
 * Deliberately quieter than the tab bar above it: the selected section is lit field with a
 * two-segment accent underline, not a filled block, so the eye still ranks "which tab" over
 * "which half of it". Full bleed, like every aperture on the panel.
 */
@Composable
fun <T> SectionSwitch(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().background(TrackProTheme.colors.field)) {
        Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = value == selected
                if (index > 0) Bezel(vertical = true, modifier = Modifier.fillMaxHeight())
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressableRow(
                            onClick = { onSelect(value) },
                            haptic = if (isSelected) null else Haptic.Selection,
                            role = Role.Tab
                        )
                        .semantics { this.selected = isSelected }
                        .background(if (isSelected) TrackProTheme.colors.fieldLive else TrackProTheme.colors.field),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.uppercase(),
                        style = TrackProType.titleMedium,
                        color = if (isSelected) TrackProTheme.colors.marking else TrackProTheme.colors.markingDim
                    )
                    if (isSelected) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(width = 38.dp, height = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            repeat(2) {
                                Box(Modifier.weight(1f).fillMaxHeight().background(TrackProTheme.colors.accent))
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TrackProTheme.colors.bezel))
    }
}

/** "1 CAR", "7 TRACKS": a count as a placard, singular when there is one. */
fun countLabel(count: Int, noun: String): String =
    "$count ${if (count == 1) noun else noun + "s"}".uppercase()
