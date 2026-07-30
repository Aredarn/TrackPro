package com.example.trackpro.extrasForUI

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun CustomTextField(
    label: String,
    value: String,
    isNumber: Boolean = false,
    leadingIcon: ImageVector? = null,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TrackProTheme.colors.textMuted) },
        keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = TrackProTheme.colors.textMuted
                )
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TrackProTheme.colors.textPrimary,
            unfocusedTextColor = TrackProTheme.colors.textPrimary,
            focusedBorderColor = TrackProTheme.colors.accentCyan,
            unfocusedBorderColor = TrackProTheme.colors.sectorLine,
            focusedLabelColor = TrackProTheme.colors.accentCyan,
            cursorColor = TrackProTheme.colors.accentCyan
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

