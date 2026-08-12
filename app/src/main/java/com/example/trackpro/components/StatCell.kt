package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.TrackProType

enum class StatCellSize { Small, Regular, Large }

/**
 * The "muted label over a bold value" readout used everywhere (power/torque/weight,
 * GPS signal rows, lap stats, session metrics). One component instead of the ~9
 * near-identical ones that used to exist per-screen.
 */
@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = TrackProTheme.colors.textPrimary,
    size: StatCellSize = StatCellSize.Regular,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    val (labelSize, valueSize) = when (size) {
        StatCellSize.Small -> 8.sp to 14.sp
        StatCellSize.Regular -> 9.sp to 17.sp
        StatCellSize.Large -> 10.sp to 22.sp
    }
    Column(horizontalAlignment = horizontalAlignment, modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = TrackProType.label.atSize(labelSize),
            color = TrackProTheme.colors.textFaint
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = TrackProType.statValue.atSize(valueSize),
                color = valueColor
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = TrackProType.body.atSize(10.sp),
                    color = TrackProTheme.colors.textMuted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 1.dp)
                )
            }
        }
    }
}

/** A 1dp vertical hairline for separating StatCells laid out in a Row. */
@Composable
fun StatCellDivider(modifier: Modifier = Modifier, height: Dp = 28.dp) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(height)
            .background(TrackProTheme.colors.sectorLine)
    )
}
