package com.example.trackpro.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Motion
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import kotlin.math.abs

/**
 * Assetto Corsa-style delta readout: a large signed number over a bar that fills out from
 * a fixed centre - right and green when you're up on the reference, left and red when
 * you're down. Gaining pushes the bar forward.
 *
 * Why a bar and not just the number: at speed a driver reads this in peripheral vision
 * during a corner exit, and *length and side* register far faster than parsing three
 * decimal digits. The number is for the moments you can actually look at it.
 *
 * [range] is the delta in seconds that fills the bar completely. 2s is a sensible default
 * for circuit work; beyond it the bar simply pins, which is honest - once you're two
 * seconds off, the exact figure has stopped being actionable.
 *
 * The fill is spring-animated rather than snapped because raw live delta is noisy: GPS
 * jitter would make an unsmoothed bar strobe. Under reduced-motion the spring is dropped,
 * since a bar that jumps is still perfectly readable.
 */
@Composable
fun DeltaBar(
    delta: Double,
    modifier: Modifier = Modifier,
    range: Double = 2.0,
    isLive: Boolean = false,
    valueSize: TextUnit = 40.sp,
    barHeight: Dp = 18.dp,
    label: String = "DELTA"
) {
    val faster = delta <= 0.0
    val color = if (faster) TrackProTheme.colors.deltaGood else TrackProTheme.colors.deltaBad

    val target = (abs(delta) / range).coerceIn(0.0, 1.0).toFloat()
    val reducedMotion = rememberReducedMotion()
    val fill by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) snap() else Motion.standard(),
        label = "deltaFill"
    )

    Column(modifier = modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = label,
                style = TrackProType.label,
                color = TrackProTheme.colors.textMuted
            )
            if (isLive) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "LIVE",
                    style = TrackProType.label,
                    color = color.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = String.format("%+.3f", delta),
                style = TrackProType.displayNumeric.atSize(valueSize),
                color = color
            )
        }

        Spacer(Modifier.height(Spacing.xs))

        // ── The bar ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(TrackProTheme.colors.bgDeep, RoundedCornerShape(3.dp))
        ) {
            // Slower half - grows leftwards from the centre.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                HalfTicks()
                if (!faster && fill > 0.004f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp))
                    )
                }
            }

            // Centre datum. Always visible, so zero has a fixed anchor to read against.
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(TrackProTheme.colors.textPrimary)
            )

            // Faster half - grows rightwards from the centre.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                HalfTicks()
                if (faster && fill > 0.004f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fill)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "+${trimZero(range)}s",
                style = TrackProType.label.atSize(9.sp),
                color = TrackProTheme.colors.textFaint
            )
            Text(
                text = if (faster) "FASTER" else "SLOWER",
                style = TrackProType.label.atSize(9.sp),
                color = color.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "-${trimZero(range)}s",
                style = TrackProType.label.atSize(9.sp),
                color = TrackProTheme.colors.textFaint
            )
        }
    }
}

/**
 * Quarter/half gridlines behind the fill, so bar length carries a magnitude rather than
 * just a direction. Drawn under the fill so a filled bar reads as one solid block.
 */
@Composable
private fun HalfTicks() {
    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Three evenly-spaced marks per half; the outermost edge is the bar end itself.
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(TrackProTheme.colors.sectorLine.copy(alpha = 0.6f))
            )
        }
    }
}

private fun trimZero(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
