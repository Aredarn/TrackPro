package com.example.trackpro.extrasForUI

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.trackpro.dataClasses.TrackMainData
import com.example.trackpro.models.VehiclePair



/**
 * Generic replacement for [DropdownMenuFieldMulti] / [TrackDropdownMenu] below (originally
 * a third, string-only copy existed too - migrated and removed). New call sites should use
 * this one; the remaining two are migrated screen-by-screen and then removed.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T> AppDropdownField(
    label: String,
    items: List<T>,
    selectedLabel: String,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TrackProTheme.colors.accent,
    emptyMessage: String = "No options available"
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = TrackProTheme.colors.textMuted) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TrackProTheme.colors.textPrimary,
                unfocusedTextColor = TrackProTheme.colors.textPrimary,
                focusedBorderColor = accent,
                unfocusedBorderColor = TrackProTheme.colors.sectorLine
            ),
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(TrackProTheme.colors.bgElevated)
        ) {
            if (items.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(emptyMessage, color = TrackProTheme.colors.textMuted) },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item), color = TrackProTheme.colors.textPrimary) },
                        onClick = {
                            expanded = false
                            onSelect(item)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DropdownMenuFieldMulti(label: String, options: List<VehiclePair>, selectedOption: String, onOptionSelected: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(selectedOption) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = TrackProTheme.colors.textMuted) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TrackProTheme.colors.textPrimary,
                unfocusedTextColor = TrackProTheme.colors.textPrimary,
                focusedBorderColor = TrackProTheme.colors.accent,
                unfocusedBorderColor = TrackProTheme.colors.sectorLine
            ),
            modifier = Modifier
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(TrackProTheme.colors.bgElevated)
        ) {
            options.forEach { option ->
                Log.d("trackpro", "ID:" + option.vehicleId)
                DropdownMenuItem(
                    text = { Text(option.manufacturerAndModel, color = TrackProTheme.colors.textPrimary) },
                    onClick = {
                        selectedText = option.manufacturerAndModel
                        expanded = false
                        onOptionSelected(option.vehicleId)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun TrackDropdownMenu(
    label: String,
    tracks: List<TrackMainData>,
    selectedTrackName: String,
    onTrackSelected: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(selectedTrackName) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = TrackProTheme.colors.textMuted) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TrackProTheme.colors.textPrimary,
                unfocusedTextColor = TrackProTheme.colors.textPrimary,
                focusedBorderColor = TrackProTheme.colors.accent,
                unfocusedBorderColor = TrackProTheme.colors.sectorLine
            ),
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(TrackProTheme.colors.bgElevated)
        ) {
            if (tracks.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No tracks found", color = TrackProTheme.colors.textMuted) },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                tracks.forEach { track ->
                    DropdownMenuItem(
                        text = { Text(track.trackName, color = TrackProTheme.colors.textPrimary) },
                        onClick = {
                            selectedText = track.trackName
                            expanded = false
                            onTrackSelected(track.trackId)
                        }
                    )
                }
            }
        }
    }
}



