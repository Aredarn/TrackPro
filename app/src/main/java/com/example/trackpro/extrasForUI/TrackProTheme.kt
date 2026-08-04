package com.example.trackpro.extrasForUI

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
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

/**
 * Maps our racing-HUD palette onto Material3's ColorScheme so that stock Material3
 * components (OutlinedTextField, DropdownMenu, AlertDialog, etc.) that don't explicitly
 * override every color still fall back to something themed instead of Material3's
 * default light/purple scheme.
 */
private fun TrackProColorScheme.toMaterialColorScheme(dark: Boolean): androidx.compose.material3.ColorScheme =
    if (dark) {
        darkColorScheme(
            // primary/secondary/tertiary intentionally all map to the same accent - the
            // palette has exactly one. Material needs three slots filled; that is not a
            // reason to invent two more brand colors.
            primary = accent,
            onPrimary = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            tertiary = accent,
            onTertiary = onAccent,
            background = bgDeep,
            onBackground = textPrimary,
            surface = bgCard,
            onSurface = textPrimary,
            surfaceVariant = bgElevated,
            onSurfaceVariant = textMuted,
            outline = sectorLine,
            error = deltaBad,
            onError = Color.Black
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = accent,
            onSecondary = onAccent,
            tertiary = accent,
            onTertiary = onAccent,
            background = bgDeep,
            onBackground = textPrimary,
            surface = bgCard,
            onSurface = textPrimary,
            surfaceVariant = bgElevated,
            onSurfaceVariant = textMuted,
            outline = sectorLine,
            error = deltaBad,
            onError = Color.White
        )
    }

@Composable
fun TrackProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkTrackProColors else LightTrackProColors

    CompositionLocalProvider(
        LocalTrackProColors provides colorScheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme.toMaterialColorScheme(darkTheme),
            content = content
        )
    }
}