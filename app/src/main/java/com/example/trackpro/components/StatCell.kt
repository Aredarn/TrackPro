package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.bezel
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim

enum class StatCellSize { Small, Regular, Large }

/** A caption over a value, with an optional unit. The value is always the louder of the two. */
@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = TrackProTheme.colors.marking,
    size: StatCellSize = StatCellSize.Regular,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    val valueSize = when (size) {
        StatCellSize.Small -> 15.sp
        StatCellSize.Regular -> 20.sp
        StatCellSize.Large -> 20.sp
    }
    Column(horizontalAlignment = horizontalAlignment, modifier = modifier) {
        Text(text = label, style = TrackProType.label, color = TrackProTheme.colors.markingDim, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = TrackProType.statValue.atSize(valueSize), color = valueColor, maxLines = 1)
            if (unit != null) {
                Text(
                    text = unit,
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
                )
            }
        }
    }
}

/** A short vertical hairline between StatCells in a Row. */
@Composable
fun StatCellDivider(modifier: Modifier = Modifier, height: Dp = 28.dp) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(height)
            .background(TrackProTheme.colors.bezel)
    )
}
