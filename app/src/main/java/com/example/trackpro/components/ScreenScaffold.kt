package com.example.trackpro.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme

/**
 * Standard screen shell: content runs full-bleed beneath an opaque chrome strip.
 *
 * **On the material.** The chrome is opaque. Translucency belonged to the previous visual
 * direction; a milled panel does not show what is behind it, and a half-transparent bar
 * over live telemetry costs legibility for nothing. This doc used to describe a floating
 * translucent scrim long after the alpha had been set to 1 - if the material changes
 * again, change both.
 *
 * **On the edge.** There is no permanent divider. The bezel appears only once content is
 * actually scrolled underneath, so a short screen carries no line at all and a scrolled
 * one gets the same 1dp hairline that separates every other field on the panel. It was a
 * vertical gradient until the world went hard-edged; a gradient is the one thing the
 * segment language explicitly refuses.
 */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = TrackProTheme.colors.accent,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentScrolled: Boolean = false,
    bottomBar: (@Composable () -> Unit)? = null,
    /**
     * A fixed strip under the bar, e.g. the [SectionSwitch] that splits a tab in two. It is
     * part of the chrome, so content is inset by [headerHeight] as well as the bar.
     */
    header: (@Composable () -> Unit)? = null,
    headerHeight: Dp = if (header != null) SectionSwitchHeight else 0.dp,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        content(PaddingValues(top = AppTopBarHeight + headerHeight))

        Column(modifier = Modifier.align(Alignment.TopStart)) {
            AppTopBar(
                title = title,
                accent = accent,
                subtitle = subtitle,
                onBack = onBack,
                trailing = trailing,
                // The scaffold owns the material and the edge treatment.
                containerColor = TrackProTheme.colors.bgCard.copy(alpha = TranslucentChromeAlpha),
                showDivider = false
            )
            if (header != null) {
                Box(modifier = Modifier.fillMaxWidth().height(headerHeight)) { header() }
            }
            ScrollEdgeFade(visible = contentScrolled)
        }

        if (bottomBar != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.bgCard.copy(alpha = TranslucentChromeAlpha))
            ) {
                bottomBar()
            }
        }
    }
}

/**
 * Content dissolving under floating chrome, instead of being cut off by a rule.
 * Renders nothing at all when there's no overlap to soften.
 */
@Composable
fun ScrollEdgeFade(visible: Boolean) {
    if (!visible) return
    Bezel()
}

/**
 * Kept at 1 and kept named so the call sites read unchanged. The chrome is opaque; see
 * the composable's note on the material.
 */
private const val TranslucentChromeAlpha = 1f

/** True once the list has moved at all - drives the scroll-edge bezel. */
@Composable
fun LazyListState.isScrolledUnderChrome(): State<Boolean> = remember(this) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 }
}

/** True once the scroll container has moved at all - drives the scroll-edge fade. */
@Composable
fun ScrollState.isScrolledUnderChrome(): State<Boolean> = remember(this) {
    derivedStateOf { value > 0 }
}
