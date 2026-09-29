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
import com.example.trackpro.theme.panel

/**
 * Standard screen shell: the header floats over content on the page ground, content scrolls
 * beneath it inset by the header's height, and a hairline appears under the header only
 * once something has actually scrolled under it.
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
     * A fixed strip under the header, e.g. the [SectionSwitch] that splits a tab in two.
     * Content is inset by [headerHeight] as well as the header.
     */
    header: (@Composable () -> Unit)? = null,
    headerHeight: Dp = if (header != null) SectionSwitchHeight else 0.dp,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
    ) {
        content(PaddingValues(top = AppTopBarHeight + headerHeight))

        Column(modifier = Modifier.align(Alignment.TopStart).background(TrackProTheme.colors.panel)) {
            AppTopBar(title = title, accent = accent, subtitle = subtitle, onBack = onBack, trailing = trailing)
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
                    .background(TrackProTheme.colors.panel)
            ) {
                bottomBar()
            }
        }
    }
}

/** A hairline under the header, drawn only once content has scrolled beneath it. */
@Composable
fun ScrollEdgeFade(visible: Boolean) {
    if (!visible) return
    Bezel()
}

/** True once the list has moved at all. */
@Composable
fun LazyListState.isScrolledUnderChrome(): State<Boolean> = remember(this) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0 }
}

/** True once the scroll container has moved at all. */
@Composable
fun ScrollState.isScrolledUnderChrome(): State<Boolean> = remember(this) {
    derivedStateOf { value > 0 }
}
