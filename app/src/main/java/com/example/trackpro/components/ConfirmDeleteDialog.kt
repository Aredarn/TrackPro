package com.example.trackpro.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType

/**
 * Destructive-action confirmation, themed to match the app rather than the Material
 * default surface.
 *
 * Delete is the only tinted control - it carries [TrackProTheme]'s `danger`, while Cancel
 * stays muted. Colouring both would make them read as equal-weight choices, and the
 * safe one should be the easy one to hit by accident.
 *
 * The haptic fires on confirm rather than on the long-press that opened the dialog:
 * [Haptic.Reject] is the app's "destructive" feel, and the moment worth marking is the
 * one where data actually goes away.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Delete",
    dismissLabel: String = "Cancel"
) {
    val haptics = rememberHaptics()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrackProTheme.colors.bgCard,
        titleContentColor = TrackProTheme.colors.textPrimary,
        textContentColor = TrackProTheme.colors.textMuted,
        confirmButton = {
            TextButton(onClick = {
                haptics.perform(Haptic.Reject)
                onConfirm()
            }) {
                Text(
                    confirmLabel,
                    color = TrackProTheme.colors.danger,
                    style = TrackProType.titleMedium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = TrackProTheme.colors.textMuted)
            }
        },
        title = { Text(title) },
        text = { Text(message) }
    )
}
