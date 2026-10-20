package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One solution curve, already sampled.
 *
 * The curve is held as pieces, because a function can be undefined at some
 * points — `1/z` is not defined at zero — and the line must break there
 * rather than be drawn straight through the gap.
 */
data class OdeCurve(
    val pieces: List<List<Pair<Double, Double>>>,
    val color: Color,
    val label: String,
    val dashed: Boolean = false,
) {
    /** The number of points in the curve. */
    val pointCount: Int get() = pieces.sumOf { it.size }
}

/**
 * An interactive picture of a differential equation.
 *
 * The slope field shows the derivative of the right-hand side at sampled
 * points: a short line segment at each one, tilted by the value of `y′`
 * there. The solution curves are drawn over the top, so the two can be
 * compared directly.
 *
 * The x and y scales are the same, so a segment really is at the angle the
 * equation gives. Dragging pans the picture and pinching makes it larger.
 */
@Composable
fun OdeGraph(
    rhs: Node?,
    xName: String,
    yName: String,
    curves: List<OdeCurve>,
    view: PlotViewport,
    onViewChange: (PlotViewport) -> Unit,
    modifier: Modifier = Modifier,
    showField: Boolean = true,
    fieldColor: Color = Color(0xFF6FD3A8),
) {
    var liveView by remember { mutableStateOf(view) }
    if (view != liveView) liveView = view

    val describe = buildString {
        append("The slope field of the differential equation")
        rhs?.let { append(" y' = ").append(com.calcsuite.engine.calculus.NodePrinter.print(it)) }
        if (curves.isNotEmpty()) {
            append(". Showing ")
            append(curves.size)
            append(if (curves.size == 1) " solution curve" else " solution curves")
            curves.forEach { append(", ").append(it.label) }
        }
        append(". Drag to pan and pinch to zoom.")
    }

    Box(
        modifier
            .clipToBounds()
            .then(Modifier.pointerInput(rhs) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    liveView = PlotViewportMath.transform(
                        liveView,
                        focusX = centroid.x.toDouble(),
                        focusY = centroid.y.toDouble(),
                        panX = pan.x.toDouble(),
                        panY = pan.y.toDouble(),
                        zoom = zoom.toDouble(),
                        widthPx = size.width.toDouble().coerceAtLeast(1.0),
                        heightPx = size.height.toDouble().coerceAtLeast(1.0),
                    )
                    onViewChange(liveView)
                }
            })
            .semantics { contentDescription = describe },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawOdeGrid(liveView)
            if (showField && rhs != null) {
                drawSlopeField(rhs, xName, yName, liveView, fieldColor)
            }
            for (curve in curves) {
                drawOdeCurve(curve, liveView)
            }
        }
    }
}

/** Draws the grid lines and the axes. */
private fun DrawScope.drawOdeGrid(view: PlotViewport) {
    val step = niceStep(view.span / 4.0)
    val unitsPerPx = view.span / size.width
    val (yMin, yMax) = view.verticalRange(size.height.toDouble(), size.width.toDouble())

    var gx = floor(view.xMin / step) * step
    while (gx <= view.xMax) {
        val px = ((gx - view.centreX) / unitsPerPx + size.width / 2).toFloat()
        val onAxis = abs(gx) < step * 1e-9
        drawLine(
            color = if (onAxis) Color(0xFF9E9E9E) else Color(0x1A808080),
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = if (onAxis) 1.2f * density else 0.5f * density,
        )
        gx += step
    }
    var gy = floor(yMin / step) * step
    while (gy <= yMax) {
        val py = (size.height / 2 - (gy - view.centreY) / unitsPerPx).toFloat()
        val onAxis = abs(gy) < step * 1e-9
        drawLine(
            color = if (onAxis) Color(0xFF9E9E9E) else Color(0x1A808080),
            start = Offset(0f, py),
            end = Offset(size.width, py),
            strokeWidth = if (onAxis) 1.2f * density else 0.5f * density,
        )
        gy += step
    }
}

/**
 * The direction of the segment that stands for a slope.
 *
 * The slope is measured upwards, while the screen's y axis points down, so
 * the direction is `(1, −slope)`, normalised. A slope of zero gives a
 * horizontal segment and a large slope gives a vertical one.
 */
fun slopeDirection(slope: Double): Pair<Double, Double> {
    if (!slope.isFinite()) return 1.0 to 0.0
    val length = sqrt(1.0 + slope * slope)
    return 1.0 / length to (-slope / length)
}

/**
 * Draws the slope field.
 *
 * At each sampled point the right-hand side is evaluated and a short segment
 * is drawn with that slope. The direction is `(1, −slope)` in screen
 * coordinates, because the screen's y axis points down while the slope is
 * measured upwards, and the x and y scales are the same, so the angle of the
 * segment is the angle of the slope.
 */
private fun DrawScope.drawSlopeField(
    rhs: Node,
    xName: String,
    yName: String,
    view: PlotViewport,
    color: Color,
) {
    val columns = 16
    val rows = 16
    // The segments are short, so that they read as separate marks rather
    // than joining up into a line.
    val halfLength = 5f * density
    val unitsPerPx = view.span / size.width
    val (yMin, yMax) = view.verticalRange(size.height.toDouble(), size.width.toDouble())

    for (i in 0..columns) {
        val x = view.xMin + view.span * i / columns
        for (j in 0..rows) {
            val y = yMin + (yMax - yMin) * j / rows
            val slope = Evaluator.eval(rhs, mapOf(xName to x, yName to y)).valueOrNull() ?: continue
            if (!slope.isFinite()) continue
            val (ux, uy) = slopeDirection(slope)
            val cx = size.width / 2 + ((x - view.centreX) / unitsPerPx).toFloat()
            val cy = size.height / 2 - ((y - view.centreY) / unitsPerPx).toFloat()
            drawLine(
                color = color,
                start = Offset(
                    (cx - ux * halfLength).toFloat(),
                    (cy - uy * halfLength).toFloat(),
                ),
                end = Offset(
                    (cx + ux * halfLength).toFloat(),
                    (cy + uy * halfLength).toFloat(),
                ),
                strokeWidth = 1.1f * density,
            )
        }
    }
}

/** Draws one solution curve, piece by piece. */
private fun DrawScope.drawOdeCurve(curve: OdeCurve, view: PlotViewport) {
    if (curve.pointCount < 2) return
    val unitsPerPx = view.span / size.width
    for (piece in curve.pieces) {
        if (piece.size < 2) continue
        val path = Path()
        piece.forEachIndexed { index, (x, y) ->
            val px = (size.width / 2 + ((x - view.centreX) / unitsPerPx)).toFloat()
            val py = (size.height / 2 - ((y - view.centreY) / unitsPerPx)).toFloat()
            if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(
            path,
            curve.color,
            style = Stroke(width = 2.4f * density),
        )
    }
}

/** A grid step of 1, 2 or 5 times a power of ten. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val step = 10.0.pow(floor(log10(raw)))
    val normalised = raw / step
    val factor = when {
        normalised <= 1.0 -> 1.0
        normalised <= 2.0 -> 2.0
        normalised <= 5.0 -> 5.0
        else -> 10.0
    }
    return factor * step
}
