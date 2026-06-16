package com.example.trackpro.extrasForUI

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.trackpro.theme.DarkTrackProColors
import com.example.trackpro.theme.LightTrackProColors
import com.example.trackpro.theme.TrackProColorScheme

private val LocalTrackProColors = staticCompositionLocalOf { DarkTrackProColors }

// 2. Create an elegant accessor object for UI code
object TrackProTheme {
    val colors: TrackProColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalTrackProColors.current
}

@Composable
fun TrackProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkTrackProColors else LightTrackProColors

    CompositionLocalProvider(
        LocalTrackProColors provides colorScheme,
        content = content
    )
}