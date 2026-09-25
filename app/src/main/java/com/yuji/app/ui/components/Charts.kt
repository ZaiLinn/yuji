package com.yuji.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuji.app.ui.theme.LocalHideAmounts
import com.yuji.app.ui.theme.TNUM
import com.yuji.app.ui.theme.LocalYujiColors
import com.yuji.app.ui.theme.YujiColors
import kotlin.math.abs
import kotlin.math.max

data class ChartPoint(val t: Long, val v: Double)

/**
 * Line chart with a real time axis. Drag or tap to inspect a point.
 * [axes] = false renders a bare sparkline.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    goal: Double? = null,
    axes: Boolean = true,
    interactive: Boolean = true,
    valueLabel: (Double) -> String,
    dateLabel: (Long) -> String,
) {
    val measurer = rememberTextMeasurer()
    val hidden = LocalHideAmounts.current
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val yc = LocalYujiColors.current
    val effectiveColor = if (color == Color.Unspecified) yc.Mint else color
    val labelStyle = TextStyle(color = yc.TextFaint, fontSize = 10.sp, fontFeatureSettings = TNUM)
    val tipStyle = TextStyle(color = yc.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val tipSub = TextStyle(color = yc.TextMuted, fontSize = 10.sp)

    Canvas(
        modifier.then(
            if (!interactive || points.size < 2) Modifier
            else Modifier
                .pointerInput(points) {
                    detectTapGestures { selected = nearest(points, it.x, size.width.toFloat(), axes, density) }
                }
                .pointerInput(points) {
                    detectDragGestures(
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                    ) { change, _ -> selected = nearest(points, change.position.x, size.width.toFloat(), axes, density) }
                },
        ),
    ) {
        if (points.size < 2) return@Canvas
        val left = if (axes) 52.dp.toPx() else 2.dp.toPx()
        val right = size.width - if (axes) 8.dp.toPx() else 2.dp.toPx()
        val top = if (axes) 12.dp.toPx() else 4.dp.toPx()
        val bottom = size.height - if (axes) 24.dp.toPx() else 4.dp.toPx()

        var lo = points.minOf { it.v }
        var hi = points.maxOf { it.v }
        if (goal != null && axes) { lo = minOf(lo, goal); hi = maxOf(hi, goal) }
        if (hi == lo) { hi += 1.0; lo -= 1.0 }
        val margin = (hi - lo) * 0.08
        lo -= margin; hi += margin

        val t0 = points.first().t
        val t1 = points.last().t
        fun x(i: Int): Float = if (t1 > t0) left + (points[i].t - t0).toFloat() / (t1 - t0) * (right - left)
        else left + i.toFloat() / (points.size - 1) * (right - left)
        fun y(v: Double): Float = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()

        if (axes) {
            for (k in 0..2) {
                val v = lo + (hi - lo) * k / 2
                val yy = y(v)
                drawLine(yc.Outline, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
                val text = if (hidden) "••" else valueLabel(v)
                val layout = measurer.measure(text, labelStyle)
                drawText(layout, topLeft = Offset(0f, yy - layout.size.height / 2f))
            }
            val start = measurer.measure(dateLabel(t0), labelStyle)
            val end = measurer.measure(dateLabel(t1), labelStyle)
            drawText(start, topLeft = Offset(left, bottom + 8.dp.toPx()))
            drawText(end, topLeft = Offset(right - end.size.width, bottom + 8.dp.toPx()))
            if (goal != null) {
                val gy = y(goal)
                drawLine(
                    yc.Amber.copy(alpha = 0.7f), Offset(left, gy), Offset(right, gy), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
                drawText(measurer.measure("目标", labelStyle.copy(color = yc.Amber)), topLeft = Offset(right - 28.dp.toPx(), gy - 16.dp.toPx()))
            }
        }

        val line = Path()
        points.indices.forEach { i -> if (i == 0) line.moveTo(x(i), y(points[i].v)) else line.lineTo(x(i), y(points[i].v)) }
        val fill = Path().apply {
            addPath(line)
            lineTo(x(points.lastIndex), bottom)
            lineTo(x(0), bottom)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(effectiveColor.copy(alpha = 0.28f), effectiveColor.copy(alpha = 0f)), startY = top, endY = bottom))
        drawPath(line, effectiveColor, style = Stroke(width = (if (axes) 2.5 else 2.0).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val sel = selected
        if (sel != null) {
            val sx = x(sel)
            val sy = y(points[sel].v)
            drawLine(yc.TextMuted.copy(alpha = 0.5f), Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx())
            drawCircle(yc.Background, 6.dp.toPx(), Offset(sx, sy))
            drawCircle(effectiveColor, 4.dp.toPx(), Offset(sx, sy))
            tooltip(
                measurer.measure(if (hidden) MASK else valueLabel(points[sel].v), tipStyle),
                measurer.measure(dateLabel(points[sel].t), tipSub),
                sx, top, left, right, yc.CardHigh,
            )
        } else if (!axes) {
            drawCircle(effectiveColor, 3.dp.toPx(), Offset(x(points.lastIndex), y(points.last().v)))
        }
    }
}

private fun DrawScope.tooltip(
    main: androidx.compose.ui.text.TextLayoutResult,
    sub: androidx.compose.ui.text.TextLayoutResult,
    cx: Float, top: Float, left: Float, right: Float, cardHighColor: Color,
) {
    val pad = 8.dp.toPx()
    val w = max(main.size.width, sub.size.width) + pad * 2
    val h = main.size.height + sub.size.height + pad * 1.5f
    val x = (cx - w / 2).coerceIn(left, right - w)
    drawRoundRect(cardHighColor, Offset(x, top), Size(w, h), CornerRadius(10.dp.toPx()))
    drawText(sub, topLeft = Offset(x + pad, top + pad * 0.6f))
    drawText(main, topLeft = Offset(x + pad, top + pad * 0.6f + sub.size.height))
}

private fun nearest(points: List<ChartPoint>, px: Float, width: Float, axes: Boolean, density: Float): Int {
    val left = if (axes) 52 * density else 2 * density
    val right = width - if (axes) 8 * density else 2 * density
    val t0 = points.first().t
    val t1 = points.last().t
    var best = 0
    var bestD = Float.MAX_VALUE
    for (i in points.indices) {
        val x = if (t1 > t0) left + (points[i].t - t0).toFloat() / (t1 - t0) * (right - left)
        else left + i.toFloat() / (points.size - 1) * (right - left)
        val d = abs(x - px)
        if (d < bestD) { bestD = d; best = i }
    }
    return best
}

data class Slice(val label: String, val value: Double, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, center: @Composable BoxScope.() -> Unit = {}) {
    val yc = LocalYujiColors.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val total = slices.sumOf { it.value }
            val stroke = size.minDimension * 0.14f
            val arcSize = Size(size.minDimension - stroke, size.minDimension - stroke)
            val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
            if (total <= 0) {
                drawArc(yc.CardHigh, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                return@Canvas
            }
            val gap = if (slices.size > 1) 2.5f else 0f
            var start = -90f
            for (s in slices) {
                val sweep = (s.value / total * 360f).toFloat()
                if (sweep > gap) {
                    drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        center()
    }
}
