package com.example.trackpro.extrasForUI

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.example.trackpro.theme.PaddockNight
import com.example.trackpro.theme.PaddockSunlight
import com.example.trackpro.theme.TrackProColorScheme

private val LocalTrackProColors = staticCompositionLocalOf { PaddockNight }

object TrackProTheme {
    val colors: TrackProColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalTrackProColors.current
}

/**
 * Stock Material components (text fields, dialogs, menus, switches) read these, so they land
 * in Paddock night without per-call overrides. Both contrasts are dark schemes.
 */
private fun TrackProColorScheme.toMaterialColorScheme() = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accent.copy(alpha = 0.16f),
    onPrimaryContainer = accent,
    secondary = accent,
    onSecondary = onAccent,
    tertiary = deltaGood,
    onTertiary = onAccent,
    background = bgDeep,
    onBackground = textPrimary,
    surface = bgCard,
    onSurface = textPrimary,
    surfaceVariant = bgElevated,
    onSurfaceVariant = textMuted,
    surfaceContainerHigh = bgCard,
    surfaceContainerHighest = bgElevated,
    outline = sectorLine,
    outlineVariant = sectorLine,
    error = danger,
    onError = textPrimary
)

private val PaddockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Always dark. [sunlight] swaps in [PaddockSunlight], the high-contrast variant for a phone
 * on a mount in direct sun. [darkTheme] is ignored and only kept so older call sites compile.
 */
@Composable
fun TrackProTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    sunlight: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (sunlight) PaddockSunlight else PaddockNight

    CompositionLocalProvider(LocalTrackProColors provides colorScheme) {
        MaterialTheme(
            colorScheme = colorScheme.toMaterialColorScheme(),
            shapes = PaddockShapes,
            content = content
        )
    }
}
