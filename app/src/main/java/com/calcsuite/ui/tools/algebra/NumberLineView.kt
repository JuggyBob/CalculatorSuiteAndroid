package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuite.engine.algebra.inequality.CriticalPoint
import com.calcsuite.engine.algebra.inequality.Interval

/**
 * A number line showing a solution set.
 *
 * The line is scaled to the critical points with a margin, so the important
 * features are always visible. Solution intervals are drawn as a thick bar in
 * the theme's accent, with a filled disc where the interval includes its
 * endpoint and a hollow ring where it does not. A point that is outside the
 * domain — a pole — is drawn as a hollow ring in the error colour, so it is
 * never mistaken for a solution.
 */
@Composable
fun NumberLineView(
    intervals: List<Interval>,
    criticalPoints: List<CriticalPoint>,
    accent: Color,
    poleColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    val bounds = numberLineBounds(intervals, criticalPoints)
    val measurer = rememberTextMeasurer()
    val description = describeNumberLine(intervals)

    Box(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .clipToBounds()
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val width = size.width
            val axisY = size.height * 0.42f
            val radius = 7.dp.toPx()
            val strokeWidth = 2.5.dp.toPx()
            val barHeight = 9.dp.toPx()
            val span = bounds.endInclusive - bounds.start
            val leftPad = 14.dp.toPx()
            val usable = (width - 2 * leftPad).coerceAtLeast(1f)

            fun xOf(value: Double): Float =
                if (span <= 0.0) width / 2f
                else leftPad + ((value - bounds.start) / span * usable).toFloat()

            // The axis, with an arrow head so the line reads as endless.
            drawLine(
                color = labelColor.copy(alpha = 0.4f),
                start = Offset(0f, axisY),
                end = Offset(width - 10.dp.toPx(), axisY),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = labelColor.copy(alpha = 0.4f),
                start = Offset(width - 11.dp.toPx(), axisY - 5.dp.toPx()),
                end = Offset(width, axisY),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = labelColor.copy(alpha = 0.4f),
                start = Offset(width - 11.dp.toPx(), axisY + 5.dp.toPx()),
                end = Offset(width, axisY),
                strokeWidth = strokeWidth,
            )

            // The solution bars.
            //
            // At an open endpoint the bar stops at the edge of the circle, so
            // it never covers the circle's hollow middle. At a closed endpoint
            // the circle is filled and drawn over the end of the bar, so the
            // bar may run right up to its centre.
            val cap = barHeight / 2f
            val inset = radius + cap
            for (interval in intervals) {
                val left = when {
                    interval.lo == null -> leftPad
                    interval.lo.closed -> xOf(interval.lo.value)
                    else -> xOf(interval.lo.value) + inset
                }
                val right = when {
                    interval.hi == null -> width - leftPad
                    interval.hi.closed -> xOf(interval.hi.value)
                    else -> xOf(interval.hi.value) - inset
                }
                drawLine(
                    color = accent,
                    start = Offset(left, axisY),
                    end = Offset(right.coerceAtLeast(left), axisY),
                    strokeWidth = barHeight,
                    cap = StrokeCap.Round,
                )
            }

            // Endpoints: filled disc when included, hollow ring when excluded.
            for (interval in intervals) {
                interval.lo?.let {
                    drawEndpoint(xOf(it.value), axisY, radius, accent, labelColor, it.closed)
                }
                interval.hi?.let {
                    drawEndpoint(xOf(it.value), axisY, radius, accent, labelColor, it.closed)
                }
            }

            // Critical points, with their labels underneath.
            val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
            for (point in criticalPoints) {
                val x = xOf(point.value)
                if (point.isPole) {
                    // A pole is never part of the solution.
                    drawCircle(
                        color = poleColor,
                        radius = radius,
                        center = Offset(x, axisY),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                } else {
                    drawCircle(
                        color = labelColor.copy(alpha = 0.6f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(x, axisY),
                    )
                }
                val label = measurer.measure(point.display, labelStyle)
                val labelX = (x - label.size.width / 2f)
                    .coerceIn(0f, (width - label.size.width).coerceAtLeast(0f))
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(labelX, axisY + radius + 8.dp.toPx()),
                )
            }
        }
    }
}

/** Draws one endpoint: a filled disc when the value is included, a ring when it is not. */
private fun DrawScope.drawEndpoint(
    x: Float,
    axisY: Float,
    radius: Float,
    accent: Color,
    outline: Color,
    filled: Boolean,
) {
    if (filled) {
        drawCircle(color = accent, radius = radius, center = Offset(x, axisY))
    } else {
        drawCircle(
            color = outline,
            radius = radius,
            center = Offset(x, axisY),
            style = Stroke(width = 2.5.dp.toPx()),
        )
    }
}

/**
 * The visible range of the number line.
 *
 * It always contains every critical point and every interval endpoint, with a
 * margin so the ends are not flush against the edges.
 */
fun numberLineBounds(
    intervals: List<Interval>,
    criticalPoints: List<CriticalPoint>,
): ClosedFloatingPointRange<Double> {
    val values = mutableListOf<Double>()
    for (point in criticalPoints) values.add(point.value)
    for (interval in intervals) {
        interval.lo?.let { values.add(it.value) }
        interval.hi?.let { values.add(it.value) }
    }
    if (values.isEmpty()) return -10.0..10.0
    var minValue = values.min()
    var maxValue = values.max()
    if (minValue == maxValue) {
        minValue -= 1.0
        maxValue += 1.0
    }
    val margin = (maxValue - minValue) * 0.14
    return (minValue - margin)..(maxValue + margin)
}

/** A spoken description of the number line, for screen readers. */
fun describeNumberLine(intervals: List<Interval>): String = when {
    intervals.isEmpty() -> "Number line: no values satisfy the inequality."
    else -> "Number line showing the solution set: " + intervals.joinToString(", ") { interval ->
        val lo = interval.lo?.display() ?: "minus infinity"
        val hi = interval.hi?.display() ?: "plus infinity"
        val left = if (interval.lo?.closed == true) "including" else "excluding"
        val right = if (interval.hi?.closed == true) "including" else "excluding"
        "from $lo $left to $hi $right"
    }
}
