package com.example.trackpro.extrasForUI

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import com.example.trackpro.theme.TrackProType

@Composable
fun CustomTextField(
    label: String,
    value: String,
    isNumber: Boolean = false,
    leadingIcon: ImageVector? = null,
    accent: Color = TrackProTheme.colors.accentCyan,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TrackProType.body,
        label = { Text(label, style = TrackProType.body, color = TrackProTheme.colors.textMuted) },
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
            focusedBorderColor = accent,
            unfocusedBorderColor = TrackProTheme.colors.sectorLine,
            focusedLabelColor = accent,
            cursorColor = accent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

