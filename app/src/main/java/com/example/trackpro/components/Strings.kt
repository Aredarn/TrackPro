package com.example.trackpro.components

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * A quantity string from resources. English says "1 lap" and "3 laps"; Hungarian keeps the
 * noun singular after a number ("3 kör"), and the plurals resource carries both rules.
 * The count is passed as the first format argument unless others are given.
 */
@Composable
@ReadOnlyComposable
fun pluralResource(@PluralsRes id: Int, count: Int, vararg args: Any): String {
    LocalConfiguration.current
    val resources = LocalContext.current.resources
    return if (args.isEmpty()) resources.getQuantityString(id, count, count)
    else resources.getQuantityString(id, count, *args)
}
