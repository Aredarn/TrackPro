package com.example.trackpro.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Motion
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.bezel
import com.example.trackpro.theme.field
import com.example.trackpro.theme.fieldLive
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.panel
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.segmentOff
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The dash's one bar language: discrete lit segments, never a gradient or a smooth fill.
 *
 * A purpose-built display uses segments because a driver counts blocks in peripheral
 * vision far faster than they judge the length of a continuous bar - the quantisation is
 * the feature. Unlit segments are drawn rather than omitted so the bar's full range stays
 * visible and a value near zero still reads as "near zero" rather than "no data".
 *
 * [signedFraction] runs -1..1 in bidirectional mode. **Positive fills right and means
 * better** - faster, gaining - which is the orientation this app settled on: gaining
 * pushes the bar forward. Callers with a lower-is-better value (a lap delta) pass its
 * negation. In unidirectional mode the range is 0..1 filling from the left.
 *
 * The fill is spring-damped. Live GPS delta is noisy enough that an undamped bar strobes,
 * and a snapping readout is a lie about how confidently the value is known.
 */
@Composable
fun SegmentBar(
    signedFraction: Float,
    activeColor: Color,
    modifier: Modifier = Modifier,
    segments: Int = 21,
    bidirectional: Boolean = true,
    height: Dp = 14.dp,
    showDatum: Boolean = true
) {
    val reducedMotion = rememberReducedMotion()
    val target = signedFraction.coerceIn(if (bidirectional) -1f else 0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) snap() else Motion.standard(),
        label = "segmentFill"
    )

    // An odd segment count gives a true centre block to sit the datum on.
    val count = if (bidirectional && segments % 2 == 0) segments + 1 else segments
    val centre = count / 2
    val lit = (abs(animated) * (if (bidirectional) centre else count)).roundToInt()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(count) { i ->
            val isCentre = bidirectional && i == centre
            val on = if (bidirectional) {
                if (animated >= 0f) i > centre && i <= centre + lit
                else i < centre && i >= centre - lit
            } else {
                i < lit
            }
            val color = when {
                on -> activeColor
                isCentre && showDatum -> TrackProTheme.colors.marking
                else -> TrackProTheme.colors.segmentOff
            }
            Box(
                modifier = Modifier
                    .weight(if (isCentre && showDatum) 0.6f else 1f)
                    .fillMaxHeight()
                    .background(color)
            )
        }
    }
}

/**
 * Which way a value is moving, with hysteresis.
 *
 * A trend mark that flips every frame is worse than no mark: it reads as noise and trains
 * the eye to ignore that corner of the panel. [threshold] is how far the value must travel
 * from the last committed reading before the direction is allowed to change, so a delta
 * jittering by milliseconds holds Steady while a real gain flips it.
 *
 * This is the six-pack discipline the direction contract took: an instrument that shows
 * only its current value says where you are and not where you are going, and on a lap the
 * second one is what changes your driving.
 */
@Composable
fun rememberTrend(value: Float, threshold: Float): Trend {
    var anchor by remember { mutableFloatStateOf(value) }
    var trend by remember { mutableStateOf(Trend.Steady) }
    if (value - anchor > threshold) {
        trend = Trend.Rising
        anchor = value
    } else if (anchor - value > threshold) {
        trend = Trend.Falling
        anchor = value
    }
    return trend
}

/** Which way a value is moving. Drawn, never a glyph. */
enum class Trend { Rising, Falling, Steady }

/**
 * A trend mark.
 *
 * The six-pack discipline the direction inherited: an instrument that shows only its
 * current value tells you where you are but not where you are going, and on a lap the
 * second one is what changes your driving.
 */
@Composable
private fun TrendMark(trend: Trend, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 8.dp, height = 6.dp)) {
        if (trend == Trend.Steady) {
            drawRect(
                color = color,
                topLeft = Offset(0f, size.height / 2f - 0.75.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx())
            )
            return@Canvas
        }
        val p = Path().apply {
            if (trend == Trend.Rising) {
                moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height)
            } else {
                moveTo(size.width / 2f, size.height); lineTo(size.width, 0f); lineTo(0f, 0f)
            }
            close()
        }
        drawPath(p, color)
    }
}

/**
 * One instrument: a placard cap, a value, and optionally where that value is heading.
 *
 * This is the dash's only container. There are no cards - a field is distinguished by its
 * ground and a bezel hairline, the way a panel is milled rather than the way a web page
 * stacks boxes.
 */
@Composable
fun Instrument(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    valueSize: TextUnit = 20.sp,
    trend: Trend? = null,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier
            .background(TrackProTheme.colors.field)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label.uppercase(),
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trend != null && alignEnd) {
                TrendMark(trend, valueColor ?: TrackProTheme.colors.marking)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = value,
                style = TrackProType.statValue.atSize(valueSize),
                color = valueColor ?: TrackProTheme.colors.marking,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
            )
            if (trend != null && !alignEnd) {
                Spacer(Modifier.width(5.dp))
                TrendMark(trend, valueColor ?: TrackProTheme.colors.marking)
            }
        }
    }
}

/** A milled hairline between fields. The panel's only divider. */
@Composable
fun Bezel(modifier: Modifier = Modifier, vertical: Boolean = false) {
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(1.dp).fillMaxHeight() else Modifier.fillMaxWidth().height(1.dp))
            .background(TrackProTheme.colors.bezel)
    )
}

/**
 * The dominant readout: one number, sized to be caught peripherally.
 *
 * [caption] is the placard beneath it. The number is never centred under a heading - it
 * leads, and the placard explains it afterwards, because at speed the value is read first
 * and identified second.
 */
@Composable
fun Readout(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    valueSize: TextUnit = 56.sp,
    trend: Trend? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (trend != null) {
                TrendMark(
                    trend = trend,
                    color = valueColor ?: TrackProTheme.colors.marking,
                    modifier = Modifier.padding(end = 8.dp, bottom = 10.dp)
                )
            }
            Text(
                text = value,
                style = TrackProType.displayNumeric.atSize(valueSize),
                color = valueColor ?: TrackProTheme.colors.marking,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        Text(
            text = caption.uppercase(),
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim
        )
    }
}

/**
 * A lit block: the dash's only promoted control.
 *
 * Everything else on a panel is a readout you look at; this is the one thing you press.
 * It earns its weight from a lit ground plus a hairline in the accent, not from a shadow
 * or a rounded card - the world has neither. Corners stay square because a milled panel
 * has square apertures.
 *
 * Disabled is drawn, not hidden: the block stays, the border drops to the bezel, and the
 * segment strip unlights. A control that vanishes when unavailable teaches nothing about
 * what would make it available.
 */
@Composable
fun DashAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    labelSize: TextUnit = 34.sp,
    accent: Color? = null,
    /** Inline control: shorter, centred, no segment strip. For toolbars and pairs. */
    compact: Boolean = false,
    /** Only for genuine commits - recording started, a sector marked, a track saved. */
    haptic: Haptic? = null
) {
    val lit = accent ?: TrackProTheme.colors.accent
    val edge = if (enabled) lit else TrackProTheme.colors.bezel
    val ink = if (enabled) TrackProTheme.colors.marking else TrackProTheme.colors.markingDim

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressableRow(onClick = onClick, enabled = enabled, haptic = haptic)
            .background(if (enabled) TrackProTheme.colors.fieldLive else TrackProTheme.colors.field)
            .border(1.dp, edge)
            .heightIn(min = if (compact) 48.dp else 72.dp)
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.uppercase(),
                // Compact controls take the title face, not the display face: a numeral
                // face shrunk to button size is a display face pretending to be a label.
                style = if (compact) TrackProType.titleLarge.atSize(15.sp)
                else TrackProType.displayNumeric.atSize(labelSize),
                color = ink,
                textAlign = if (compact) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier
            )
            if (detail != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = detail.uppercase(),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    textAlign = if (compact) TextAlign.Center else TextAlign.Start,
                    maxLines = 1,
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            SegmentBar(
                signedFraction = if (enabled) 1f else 0f,
                activeColor = lit,
                bidirectional = false,
                segments = 4,
                height = 26.dp,
                modifier = Modifier.width(34.dp)
            )
        }
    }
}

/**
 * A flat titled group.
 *
 * The dash has no cards, so a section is a placard on the panel with its contents on a
 * field ground, closed top and bottom by bezels. Full bleed - a milled panel does not
 * inset its apertures from the edge.
 */
@Composable
fun DashGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = TrackProType.label,
            color = TrackProTheme.colors.markingDim,
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.panel)
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 6.dp)
        )
        Bezel()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrackProTheme.colors.field)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            content = content
        )
        Bezel()
    }
}
