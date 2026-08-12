package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.TrackProType

/** Height of the bar itself, exported so [ScreenScaffold] can inset content by it. */
val AppTopBarHeight = 48.dp

/**
 * Compact 48dp header shared by every screen. The section accent shows up only as a
 * small dot next to the title, never as a full-bleed fill.
 *
 * [containerColor] and [showDivider] exist so [ScreenScaffold] can host this as a
 * floating translucent layer - it draws the material and the scroll-edge fade itself, so
 * it hands the bar a transparent container and suppresses the hard divider. Screens
 * should generally use [ScreenScaffold] rather than placing this directly.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = TrackProTheme.colors.textMuted,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    containerColor: Color = TrackProTheme.colors.bgCard,
    showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .height(AppTopBarHeight)
                // Back is the primary nav control on nearly every screen, so it gets a
                // full 48dp target. The bar is exactly 48dp tall, so IconButton's default
                // size fits flush; the reduced start inset keeps the arrow optically in
                // the same place it sat when the button was a (too small) 32dp box.
                .padding(start = if (onBack != null) 0.dp else 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TrackProTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape)
            )

            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                Text(
                    text = title.uppercase(),
                    style = TrackProType.label.atSize(12.sp),
                    color = TrackProTheme.colors.textPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = TrackProType.body.atSize(11.sp),
                        color = TrackProTheme.colors.textFaint
                    )
                }
            }

            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    trailing()
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
        }
    }
}
