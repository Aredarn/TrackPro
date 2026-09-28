package com.example.trackpro.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType

/**
 * The mark on a discarded run.
 *
 * A stamp, not a deletion and not a strikethrough: the session is still there, still
 * openable, still carrying its trace. It simply stops counting. Outlined rather than
 * filled so it reads as something applied on top of the row rather than as the row's own
 * state colour, which is how a stamp behaves on paper and on a scrutineering sticker.
 */
@Composable
fun VoidStamp(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, TrackProTheme.colors.danger)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = "VOID",
            style = TrackProType.label,
            color = TrackProTheme.colors.danger
        )
    }
}
