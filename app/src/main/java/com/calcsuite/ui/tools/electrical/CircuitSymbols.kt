package com.calcsuite.ui.tools.electrical

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.calcsuite.engine.electrical.ComponentKind
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The symbols of the parts.
 *
 * The symbols are drawn in one place, so that the palette shows the very
 * same picture as the canvas: the resistor is the box of the standard
 * drawing, the sources are circles, the switch is a hinged arm and the
 * ground is the three lines of the earth symbol, the widest on top.
 */

/**
 * Draws the symbol of a part.
 *
 * @param kind the part.
 * @param centre the middle of the symbol.
 * @param length how long the symbol is along its axis.
 * @param width how wide the symbol is across its axis.
 * @param horizontal true when the axis runs from left to right.
 * @param colour the colour of the lines.
 * @param stroke how thick the lines are.
 * @param closed for a switch, whether it is closed.
 */
fun DrawScope.drawSymbol(
    kind: ComponentKind,
    centre: Offset,
    length: Float,
    width: Float,
    horizontal: Boolean,
    colour: Color,
    stroke: Float,
    closed: Boolean = true,
) {
    // The direction along the axis, and the direction across it.
    val dx = if (horizontal) 1f else 0f
    val dy = if (horizontal) 0f else 1f
    val ax = -dy
    val ay = dx
    val half = length / 2f
    val halfWidth = width / 2f

    when (kind) {
        ComponentKind.RESISTOR -> {
            // The box of the standard drawing.
            val a = Offset(centre.x + dx * half, centre.y + dy * half)
            val b = Offset(centre.x - dx * half, centre.y - dy * half)
            val path = Path().apply {
                moveTo(a.x + ax * halfWidth, a.y + ay * halfWidth)
                lineTo(b.x + ax * halfWidth, b.y + ay * halfWidth)
                lineTo(b.x - ax * halfWidth, b.y - ay * halfWidth)
                lineTo(a.x - ax * halfWidth, a.y - ay * halfWidth)
                close()
            }
            drawPath(path, colour, style = Stroke(width = stroke))
        }
        ComponentKind.VOLTAGE_SOURCE -> {
            val radius = halfWidth
            drawCircle(colour, radius = radius, center = centre, style = Stroke(width = stroke))
            // The plus at the first end and the minus at the second.
            val arm = radius * 0.34f
            val plus = Offset(centre.x + dx * radius * 0.45f, centre.y + dy * radius * 0.45f)
            drawLine(
                colour,
                Offset(plus.x - ax * arm, plus.y - ay * arm),
                Offset(plus.x + ax * arm, plus.y + ay * arm),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                colour,
                Offset(plus.x - dx * arm, plus.y - dy * arm),
                Offset(plus.x + dx * arm, plus.y + dy * arm),
                stroke,
                StrokeCap.Round,
            )
            val minus = Offset(centre.x - dx * radius * 0.45f, centre.y - dy * radius * 0.45f)
            drawLine(
                colour,
                Offset(minus.x - ax * arm, minus.y - ay * arm),
                Offset(minus.x + ax * arm, minus.y + ay * arm),
                stroke,
                StrokeCap.Round,
            )
        }
        ComponentKind.CURRENT_SOURCE -> {
            val radius = halfWidth
            drawCircle(colour, radius = radius, center = centre, style = Stroke(width = stroke))
            // The arrow along the axis.
            val head = radius * 0.34f
            val tip = Offset(centre.x + dx * radius * 0.55f, centre.y + dy * radius * 0.55f)
            val tail = Offset(centre.x - dx * radius * 0.55f, centre.y - dy * radius * 0.55f)
            drawLine(colour, tail, tip, stroke, StrokeCap.Round)
            drawLine(
                colour,
                tip,
                Offset(
                    tip.x - dx * head + ax * head * 0.7f,
                    tip.y - dy * head + ay * head * 0.7f,
                ),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                colour,
                tip,
                Offset(
                    tip.x - dx * head - ax * head * 0.7f,
                    tip.y - dy * head - ay * head * 0.7f,
                ),
                stroke,
                StrokeCap.Round,
            )
        }
        ComponentKind.SWITCH -> {
            // The two ends, and the arm between them.
            val first = Offset(centre.x - dx * half, centre.y - dy * half)
            val second = Offset(centre.x + dx * half, centre.y + dy * half)
            drawCircle(colour, radius = stroke * 1.6f, center = first, style = Stroke(width = stroke))
            drawCircle(colour, radius = stroke * 1.6f, center = second, style = Stroke(width = stroke))
            if (closed) {
                drawLine(colour, first, second, stroke, StrokeCap.Round)
            } else {
                val lift = half * 0.5f
                drawLine(
                    colour,
                    first,
                    Offset(second.x, second.y - lift),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
        ComponentKind.GROUND -> {
            // The earth symbol: three lines, the widest on top.
            val top = halfWidth
            drawLine(
                colour,
                centre,
                Offset(centre.x, centre.y + top * 0.4f),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                colour,
                Offset(centre.x - top, centre.y + top * 0.4f),
                Offset(centre.x + top, centre.y + top * 0.4f),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                colour,
                Offset(centre.x - top * 0.62f, centre.y + top * 0.75f),
                Offset(centre.x + top * 0.62f, centre.y + top * 0.75f),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                colour,
                Offset(centre.x - top * 0.26f, centre.y + top * 1.1f),
                Offset(centre.x + top * 0.26f, centre.y + top * 1.1f),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

/**
 * Draws a part with its leads, the way it is drawn on the canvas.
 *
 * @param kind the part.
 * @param first the place of its first end.
 * @param second the place of its second end.
 * @param colour the colour of the lines.
 * @param stroke how thick the lines are.
 * @param closed for a switch, whether it is closed.
 * @param unit how many pixels there are in a world unit, so that the symbol
 *   is as big as the part it belongs to at any zoom.
 */
fun DrawScope.drawPart(
    kind: ComponentKind,
    first: Offset,
    second: Offset,
    colour: Color,
    stroke: Float,
    closed: Boolean = true,
    unit: Float = 1f,
) {
    if (kind == ComponentKind.GROUND) {
        drawSymbol(
            kind = kind,
            centre = first,
            length = 0f,
            width = 18f * unit,
            horizontal = true,
            colour = colour,
            stroke = stroke,
        )
        return
    }
    var dx = second.x - first.x
    var dy = second.y - first.y
    val span = sqrt(dx * dx + dy * dy)
    if (span < 0.001f) {
        dx = 1f
        dy = 0f
    } else {
        dx /= span
        dy /= span
    }
    val centre = Offset((first.x + second.x) / 2f, (first.y + second.y) / 2f)
    val halfSymbol = 22f * unit
    val leadA = Offset(centre.x - dx * halfSymbol, centre.y - dy * halfSymbol)
    val leadB = Offset(centre.x + dx * halfSymbol, centre.y + dy * halfSymbol)
    drawLine(colour, first, leadA, stroke, StrokeCap.Round)
    drawLine(colour, leadB, second, stroke, StrokeCap.Round)
    drawSymbol(
        kind = kind,
        centre = centre,
        length = halfSymbol * 2f,
        width = 20f * unit,
        horizontal = abs(dy) < 0.5f,
        colour = colour,
        stroke = stroke,
        closed = closed,
    )
}
