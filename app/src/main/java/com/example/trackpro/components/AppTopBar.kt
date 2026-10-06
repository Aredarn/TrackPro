package com.example.trackpro.components

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.fieldLive
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel

/** Height of the bar, exported so [ScreenScaffold] can inset content by it. */
val AppTopBarHeight = 64.dp

/**
 * The screen's header: a round back button, the title in sentence case, and room for one
 * trailing element. It sits on the page ground rather than a bar of its own, so the cards
 * below read as the content and the header as the page's name.
 *
 * [accent] and [showDivider] are accepted for older call sites and no longer drawn.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") accent: Color = TrackProTheme.colors.textMuted,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    containerColor: Color = TrackProTheme.colors.panel,
    @Suppress("UNUSED_PARAMETER") showDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .height(AppTopBarHeight)
            .padding(start = if (onBack != null) Spacing.sm else Spacing.gutter, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pressable(onClick = onBack, scale = 0.92f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TrackProTheme.colors.fieldLive),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = TrackProTheme.colors.marking,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TrackProType.titleLarge,
                color = TrackProTheme.colors.marking,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                trailing()
            }
        }
    }
}
