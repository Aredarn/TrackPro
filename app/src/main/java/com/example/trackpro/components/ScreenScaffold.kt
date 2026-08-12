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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme

/**
 * Standard screen shell: content runs full-bleed and the chrome floats over it as a
 * translucent layer, rather than the chrome being an opaque strip that permanently eats
 * the top of the screen.
 *
 * **On the material.** Compose has no backdrop blur - `Modifier.blur` blurs the content
 * it is applied to, not what sits behind it, and there is no `backdrop-filter`
 * equivalent without a third-party library. So the material here is a translucent scrim
 * over the real content plus a soft edge where content passes underneath. That reads as
 * a floating layer, works identically on every API level from 26 up, and needs no new
 * dependency; if true blur is wanted later, this composable is the only place that has
 * to change.
 *
 * **On the edge.** There is deliberately no permanent 1dp divider. A hard rule under the
 * bar is only honest when the bar is opaque and content genuinely stops there. Here the
 * fade appears only once content is actually scrolled underneath - so a short screen has
 * no line at all, and a scrolled one gets a gradient that content dissolves into.
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
    content: @Composable (PaddingValues) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.bgDeep)
    ) {
        content(PaddingValues(top = AppTopBarHeight))

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
fun ScrollEdgeFade(visible: Boolean, height: androidx.compose.ui.unit.Dp = 14.dp) {
    if (!visible) return
    val top = TrackProTheme.colors.bgCard.copy(alpha = TranslucentChromeAlpha)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(top, Color.Transparent)))
    )
}

/**
 * Opaque enough that small uppercase labels stay legible over an arbitrary map or a
 * dense table underneath, translucent enough to read as a layer rather than a lid.
 */
private const val TranslucentChromeAlpha = 0.86f

/** True once the list has moved at all - drives the scroll-edge fade. */
@Composable
fun LazyListState.isScrolledUnderChrome(): State<Boolean> = remember(this) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 }
}

/** True once the scroll container has moved at all - drives the scroll-edge fade. */
@Composable
fun ScrollState.isScrolledUnderChrome(): State<Boolean> = remember(this) {
    derivedStateOf { value > 0 }
}
