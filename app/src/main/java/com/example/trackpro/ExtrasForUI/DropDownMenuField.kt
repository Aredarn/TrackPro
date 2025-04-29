package com.example.trackpro.ExtrasForUI

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
import com.example.trackpro.Models.VehiclePair

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DropdownMenuField(label: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(selectedOption) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF007BFF),
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    selectedText = option
                    expanded = false
                    onOptionSelected(option)
                })
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
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF007BFF),
                unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier
                .fillMaxWidth()
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {

            options.forEach { option ->
                Log.d("trackpro", "ID:" + option.vehicleId)
                DropdownMenuItem(text = { Text(option.manufacturerAndModel) }, onClick = {
                    selectedText = option.manufacturerAndModel
                    expanded = false
                    onOptionSelected(option.vehicleId)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DropdownMenuField(
    label: String,
    options: List<String>,
    selectedOption: String,
    textColor: Color = Color.White,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = textColor) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF2C2C2C))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

