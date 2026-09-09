package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.markingDim

/**
 * A section placard.
 *
 * On a panel a section is announced by a struck mark and a caption, not by a larger
 * heading - the type scale is reserved for readouts. The short accent tick is that mark:
 * it gives the caption something to hang from without borrowing weight from the values
 * underneath it.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TrackProTheme.colors.markingDim
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(TrackProTheme.colors.accent)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = text.uppercase(),
            style = TrackProType.label,
            color = color
        )
    }
}
