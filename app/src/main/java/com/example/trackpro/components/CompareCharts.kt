package com.example.trackpro.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The colours the compared cars keep through every table and chart; the first is the reference. */
val CompareColors = listOf(
    Color(0xFFFF6B1A),
    Color(0xFF5AB8FF),
    Color(0xFF3DDC84),
    Color(0xFFFFB020)
)

class ChartSeries(val color: Color, val xs: FloatArray, val ys: FloatArray)

/**
 * Lines against a shared x axis. [marks] are x positions drawn as dashed verticals (sector
 * edges); with [zeroLine] the axis always includes zero and draws it.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    xMax: Float,
    xLabel: (Float) -> String,
    yLabel: (Float) -> String,
    modifier: Modifier = Modifier.fillMaxWidth().height(180.dp),
    marks: List<Float> = emptyList(),
    zeroLine: Boolean = false
) {
    val measurer = rememberTextMeasurer()
    val grid = TrackProTheme.colors.sectorLine
    val faint = TrackProTheme.colors.textFaint
    val labelStyle = TrackProType.label.copy(fontSize = 10.sp, color = TrackProTheme.colors.textMuted)

    Canvas(modifier = modifier) {
        if (series.isEmpty() || xMax <= 0f) return@Canvas
        val left = 44.dp.toPx()
        val right = 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom

        var lo = series.minOf { s -> s.ys.minOrNull() ?: 0f }
        var hi = series.maxOf { s -> s.ys.maxOrNull() ?: 0f }
        if (zeroLine) {
            lo = min(lo, 0f)
            hi = max(hi, 0f)
        }
        if (hi - lo < 1e-3f) {
            lo -= 1f
            hi += 1f
        }
        val step = niceStep((hi - lo) / 4f)
        val yMin = floor(lo / step) * step
        val yMax = ceil(hi / step) * step
        fun px(x: Float) = left + x / xMax * w
        fun py(y: Float) = top + h - (y - yMin) / (yMax - yMin) * h

        var gy = yMin
        while (gy <= yMax + step * 0.01f) {
            val y = py(gy)
            drawLine(grid, Offset(left, y), Offset(left + w, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(yLabel(gy), labelStyle)
            drawText(layout, topLeft = Offset(left - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f))
            gy += step
        }
        for (i in 0..4) {
            val x = xMax * i / 4f
            val layout = measurer.measure(xLabel(x), labelStyle)
            val tx = (px(x) - layout.size.width / 2f).coerceIn(left - 8.dp.toPx(), size.width - layout.size.width)
            drawText(layout, topLeft = Offset(tx, top + h + 4.dp.toPx()))
        }
        marks.forEach { m ->
            drawLine(
                faint, Offset(px(m), top), Offset(px(m), top + h),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }
        if (zeroLine) {
            drawLine(faint, Offset(left, py(0f)), Offset(left + w, py(0f)), strokeWidth = 1.5.dp.toPx())
        }
        clipRect(left, top, left + w, top + h) {
            series.forEach { s ->
                val path = Path()
                for (i in s.xs.indices) {
                    val p = Offset(px(s.xs[i]), py(s.ys[i]))
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private fun niceStep(raw: Float): Float {
    val magnitude = 10f.pow(floor(log10(raw.coerceAtLeast(1e-6f))))
    val n = raw / magnitude
    return magnitude * when {
        n <= 1f -> 1f
        n <= 2f -> 2f
        n <= 5f -> 5f
        else -> 10f
    }
}

/** A path in metres on a flat plane: x east, y north. All lines share one sampling. */
class MapLine(val color: Color, val xs: DoubleArray, val ys: DoubleArray)

/** A labelled point on the map; a null label is a bare dot (a sector gate). */
class MapMark(val x: Double, val y: Double, val label: String?)

/**
 * Lines from above, north up, scaled to fill the frame. Only samples [from]..[to] are shown, so
 * the same view zooms into one corner. Marks outside that stretch are left out.
 */
@Composable
fun LineMap(
    lines: List<MapLine>,
    marks: List<MapMark>,
    from: Int,
    to: Int,
    modifier: Modifier = Modifier.fillMaxWidth().height(300.dp)
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TrackProType.label.copy(fontSize = 11.sp, color = TrackProTheme.colors.textPrimary)
    val dot = TrackProTheme.colors.textPrimary

    Canvas(modifier = modifier) {
        if (lines.isEmpty()) return@Canvas
        val first = from.coerceAtLeast(0)
        val last = min(to, lines.minOf { it.xs.size } - 1)
        if (last - first < 1) return@Canvas

        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        lines.forEach { l ->
            for (i in first..last) {
                minX = min(minX, l.xs[i]); maxX = max(maxX, l.xs[i])
                minY = min(minY, l.ys[i]); maxY = max(maxY, l.ys[i])
            }
        }
        val margin = max(12.0, 0.08 * max(maxX - minX, maxY - minY))
        minX -= margin; maxX += margin; minY -= margin; maxY += margin
        val scale = min(size.width / (maxX - minX), size.height / (maxY - minY)).toFloat()
        val offsetX = (size.width - (maxX - minX).toFloat() * scale) / 2f
        val offsetY = (size.height - (maxY - minY).toFloat() * scale) / 2f
        fun px(x: Double) = offsetX + (x - minX).toFloat() * scale
        fun py(y: Double) = size.height - offsetY - (y - minY).toFloat() * scale

        clipRect(0f, 0f, size.width, size.height) {
            lines.forEach { l ->
                val path = Path()
                for (i in first..last) {
                    if (i == first) path.moveTo(px(l.xs[i]), py(l.ys[i])) else path.lineTo(px(l.xs[i]), py(l.ys[i]))
                }
                drawPath(path, l.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val frame = Rect(0f, 0f, size.width, size.height)
            marks.forEach { m ->
                val p = Offset(px(m.x), py(m.y))
                if (!frame.contains(p)) return@forEach
                if (m.label == null) {
                    drawCircle(dot, 3.5.dp.toPx(), p)
                } else {
                    drawCircle(dot, 2.5.dp.toPx(), p)
                    drawText(measurer.measure(m.label, labelStyle), topLeft = Offset(p.x + 5.dp.toPx(), p.y - 16.dp.toPx()))
                }
            }
        }
    }
}
