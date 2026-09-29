package com.example.trackpro.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Motion
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.accentGlow
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.bezel
import com.example.trackpro.theme.cardShadow
import com.example.trackpro.theme.field
import com.example.trackpro.theme.highlight
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.segmentOff
import kotlin.math.abs

// ── Surfaces ──────────────────────────────────────────────────

/**
 * The raised card every screen is built from: rounded, a soft shadow in the ground's own
 * hue, and a hairline highlight across the top edge that catches light like a pressed panel.
 */
fun Modifier.paddockCard(
    shape: Shape = TrackProShapes.card,
    color: Color? = null,
    elevation: Dp = 10.dp,
): Modifier = composed {
    val colors = TrackProTheme.colors
    Modifier
        .shadow(elevation, shape, clip = false, ambientColor = colors.cardShadow, spotColor = colors.cardShadow)
        .clip(shape)
        .background(color ?: colors.field)
        .border(1.dp, Brush.verticalGradient(listOf(colors.highlight, Color.Transparent)), shape)
}

/** A card container with default padding. */
@Composable
fun PaddockCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    padding: Dp = Spacing.lg,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, onLongClick = onLongClick, scale = 0.98f) else Modifier)
            .paddockCard(color = color)
            .padding(padding),
        content = content
    )
}

/** A section's own title, sentence case, with an optional action on the right. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.gutter, end = Spacing.sm, top = Spacing.xl, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = TrackProType.label.copy(fontWeight = TrackProType.titleMedium.fontWeight),
                color = TrackProTheme.colors.accent,
                modifier = Modifier
                    .pressable(onClick = onAction, scale = 0.96f)
                    .heightIn(min = 44.dp)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = Spacing.sm)
            )
        }
    }
}

/** An icon on a soft circle of its own tint - the category mark of a row or tile. */
@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = TrackProTheme.colors.accent,
    size: Dp = 40.dp,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) tint else tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (filled) TrackProTheme.colors.onAccent else tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ── Bars ──────────────────────────────────────────────────────

/**
 * A rounded bar for a live value.
 *
 * [signedFraction] runs -1..1 in bidirectional mode. **Positive fills right and means
 * better** - faster, gaining - so callers with a lower-is-better value (a lap delta) pass its
 * negation. In unidirectional mode the range is 0..1 from the left. The fill is
 * spring-damped: live GPS is noisy enough that an undamped bar strobes. [segments] is kept
 * for older call sites and no longer changes the drawing.
 */
@Composable
fun SegmentBar(
    signedFraction: Float,
    activeColor: Color,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") segments: Int = 21,
    bidirectional: Boolean = true,
    height: Dp = 12.dp,
    showDatum: Boolean = true
) {
    val reducedMotion = rememberReducedMotion()
    val target = signedFraction.coerceIn(if (bidirectional) -1f else 0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) snap() else Motion.standard(),
        label = "barFill"
    )
    val track = TrackProTheme.colors.segmentOff
    val datum = TrackProTheme.colors.marking

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(TrackProShapes.pill)
            .background(track)
    ) {
        val full = maxWidth
        if (bidirectional) {
            val half = full / 2
            val fill = half * abs(animated)
            Box(
                Modifier
                    .offset(x = if (animated >= 0f) half else half - fill)
                    .width(fill)
                    .fillMaxHeight()
                    .clip(TrackProShapes.pill)
                    .background(activeColor)
            )
            if (showDatum) {
                Box(
                    Modifier
                        .offset(x = half - 1.5.dp)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(datum)
                )
            }
        } else {
            Box(
                Modifier
                    .width(full * animated)
                    .fillMaxHeight()
                    .clip(TrackProShapes.pill)
                    .background(activeColor)
            )
        }
    }
}

// ── Trend ─────────────────────────────────────────────────────

/**
 * Which way a value is moving, with hysteresis: [threshold] is how far it must travel from
 * the last committed reading before the direction may change, so millisecond jitter holds
 * Steady while a real gain flips it.
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

enum class Trend { Rising, Falling, Steady }

@Composable
private fun TrendMark(trend: Trend, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 10.dp, height = 8.dp)) {
        if (trend == Trend.Steady) {
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, size.height / 2f - 1.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx())
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

// ── Stats ─────────────────────────────────────────────────────

/**
 * A stat: a small caption and its value. The value leads visually - it is the larger, brighter
 * of the two - so a screen never makes "Laps" louder than "342".
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
        modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(text = label, style = TrackProType.label, color = TrackProTheme.colors.markingDim, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trend != null && alignEnd) {
                TrendMark(trend, valueColor ?: TrackProTheme.colors.marking)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = value,
                style = TrackProType.statValue.atSize(valueSize),
                color = valueColor ?: TrackProTheme.colors.marking,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                maxLines = 1
            )
            if (trend != null && !alignEnd) {
                Spacer(Modifier.width(6.dp))
                TrendMark(trend, valueColor ?: TrackProTheme.colors.marking)
            }
        }
    }
}

/** A hairline divider inside a card. */
@Composable
fun Bezel(modifier: Modifier = Modifier, vertical: Boolean = false) {
    Box(
        modifier = modifier
            .then(if (vertical) Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp) else Modifier.fillMaxWidth().height(1.dp))
            .background(TrackProTheme.colors.bezel)
    )
}

/** The screen's one headline number, with its caption beneath. */
@Composable
fun Readout(
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    valueSize: TextUnit = 40.sp,
    trend: Trend? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(text = caption, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (trend != null) {
                TrendMark(
                    trend = trend,
                    color = valueColor ?: TrackProTheme.colors.marking,
                    modifier = Modifier.padding(end = 8.dp, bottom = 12.dp)
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
    }
}

// ── Actions ───────────────────────────────────────────────────

/**
 * An action tile.
 *
 * [primary] is the screen's one orange, filled block with a soft orange glow beneath it -
 * at most one per screen. Everything else is secondary: a card-toned tile whose icon and
 * label carry the accent at low strength. [accent] overrides the tint, e.g. red for a stop.
 * [compact] is a 52dp button for toolbars and pairs; the full tile is 96dp with room for an
 * icon and a detail line.
 */
@Composable
fun DashAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") labelSize: TextUnit = 20.sp,
    accent: Color? = null,
    compact: Boolean = false,
    haptic: Haptic? = null,
    primary: Boolean = false,
    icon: ImageVector? = null,
) {
    val tint = accent ?: TrackProTheme.colors.accent
    val onTint = if (accent == null) TrackProTheme.colors.onAccent else Color.White
    val shape = if (compact) TrackProShapes.control else TrackProShapes.card
    val glow = TrackProTheme.colors.accentGlow
    val ink = if (primary) onTint else TrackProTheme.colors.marking
    val sub = if (primary) onTint.copy(alpha = 0.72f) else TrackProTheme.colors.markingDim

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(onClick = onClick, enabled = enabled, haptic = haptic, scale = 0.97f)
            .then(
                if (primary) Modifier
                    .shadow(if (enabled) 18.dp else 0.dp, shape, clip = false, ambientColor = glow, spotColor = glow)
                    .clip(shape)
                    .background(tint)
                else Modifier.paddockCard(shape, elevation = if (compact) 4.dp else 10.dp)
            )
            .heightIn(min = if (compact) 52.dp else 96.dp)
            .padding(horizontal = if (compact) Spacing.lg else Spacing.xl, vertical = if (compact) Spacing.md else Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start
    ) {
        if (icon != null) {
            if (compact) {
                Icon(icon, contentDescription = null, tint = if (primary) onTint else tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (primary) onTint.copy(alpha = 0.14f) else tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = if (primary) onTint else tint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(Spacing.lg))
            }
        }
        Column(modifier = if (compact) Modifier else Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (compact) TrackProType.titleMedium else TrackProType.titleLarge,
                color = ink,
                textAlign = if (compact) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null && !compact) {
                Spacer(Modifier.height(2.dp))
                Text(text = detail, style = TrackProType.label, color = sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!compact) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (primary) onTint else TrackProTheme.colors.markingDim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A titled card section: the title in the page margin, the contents in one raised card.
 */
@Composable
fun DashGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(title)
        PaddockCard(modifier = Modifier.padding(horizontal = Spacing.gutter), content = content)
    }
}
