package com.calcsuite.ui.tools.algebra

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
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * The view of a calculus curve: x-range, y-range and whether each layer is drawn.
 * Held by the caller so the graph can be reset or shared with the read-out.
 */
data class GraphView(
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
    val showFunction: Boolean,
    val showDerivative: Boolean,
    val showIntegral: Boolean,
) {
    companion object {
        val Default = GraphView(-10.0, 10.0, -10.0, 10.0, true, false, false)
    }
}

/**
 * Interactive plot of a function, optionally with its derivative and a shaded
 * area under the curve.
 *
 * Pinch zooms about the gesture focus, dragging pans, and double tap resets to
 * [GraphView.Default]. Zoom is clamped so the view can never collapse to a
 * single point or drift so far out that the grid stops being meaningful.
 */
@Composable
fun CalculusGraph(
    function: Node?,
    derivative: Node?,
    integralArea: Pair<Double, Double>?,
    view: GraphView,
    onViewChange: (GraphView) -> Unit,
    curveColor: Color,
    derivativeColor: Color,
    modifier: Modifier = Modifier,
) {
    var liveView by remember { mutableStateOf(view) }
    // Keep in step when the caller resets the view from outside.
    if (view != liveView) liveView = view

    val describe = buildString {
        append("Graph of ${function?.let { NodePrinter.print(it) } ?: "nothing"}")
        if (derivative != null) append(" and its derivative")
        integralArea?.let { append(", shaded area from ${NodePrinter.format(it.first)} to ${NodePrinter.format(it.second)}") }
        append(". x from ${NodePrinter.format(view.xMin)} to ${NodePrinter.format(view.xMax)}")
    }

    Box(
        modifier
            // Curve points are sampled across the full width and steep
            // sections routinely produce coordinates far outside the visible
            // window. Without this clip the plot would paint over whatever sits
            // around it, so the caller must see the graph stay inside its box.
            .clipToBounds()
            .then(Modifier.pointerInput(function, derivative) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    liveView = GraphViewport.transform(
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
            drawGrid(liveView)
            integralArea?.let { (from, to) ->
                drawIntegral(function, from, to, liveView, curveColor.copy(alpha = 0.22f))
            }
            if (liveView.showFunction && function != null) drawCurve(function, liveView, curveColor, strokeWidth = 2.5f * density)
            if (liveView.showDerivative && derivative != null) drawCurve(derivative, liveView, derivativeColor, strokeWidth = 1.8f * density, dashed = true)
        }
    }
}

/**
 * Pan/zoom arithmetic for the plot, kept free of Compose so it can be tested
 * directly — a two-finger pinch cannot be injected through adb, but the maths
 * behind it can.
 */
object GraphViewport {

    private const val MIN_SPAN = 1e-4
    private const val MAX_SPAN = 1e7

    fun transform(
        view: GraphView,
        focusX: Double,
        focusY: Double,
        panX: Double,
        panY: Double,
        zoom: Double,
        widthPx: Double,
        heightPx: Double,
    ): GraphView {
        val spanX = view.xMax - view.xMin
        val spanY = view.yMax - view.yMin
        val safeWidth = widthPx.coerceAtLeast(1.0)
        val safeHeight = heightPx.coerceAtLeast(1.0)
        val unitsPerPxX = spanX / safeWidth
        val unitsPerPxY = spanY / safeHeight

        // The point under the fingers stays under the fingers while zooming.
        val focusWorldX = view.xMin + focusX * unitsPerPxX
        val focusWorldY = view.yMax - focusY * unitsPerPxY
        val safeZoom = if (zoom.isFinite() && zoom > 0.0) zoom else 1.0
        val newSpanX = (spanX / safeZoom).coerceIn(MIN_SPAN, MAX_SPAN)
        val newSpanY = (spanY / safeZoom).coerceIn(MIN_SPAN, MAX_SPAN)

        val ratioX = if (spanX == 0.0) 0.5 else (focusWorldX - view.xMin) / spanX
        val ratioY = if (spanY == 0.0) 0.5 else (focusWorldY - view.yMin) / spanY

        val newMinX = focusWorldX - ratioX * newSpanX - panX * unitsPerPxX
        val newMaxY = focusWorldY + (1 - ratioY) * newSpanY - panY * unitsPerPxY
        return view.copy(
            xMin = newMinX,
            xMax = newMinX + newSpanX,
            yMin = newMaxY - newSpanY,
            yMax = newMaxY,
        )
    }
}

/** Draws grid lines and axes for the visible window. */
private fun DrawScope.drawGrid(view: GraphView) {
    val stepX = niceStep((view.xMax - view.xMin) / 6.0)
    val stepY = niceStep((view.yMax - view.yMin) / 6.0)
    val minor = 0.5f * density

    var gx = floor(view.xMin / stepX) * stepX
    while (gx <= view.xMax) {
        val px = xToPx(gx, view).toFloat()
        drawLine(
            color = if (abs(gx) < stepX * 1e-9) Color(0xFF9E9E9E) else Color(0x1A808080),
            start = Offset(px, 0f),
            end = Offset(px, size.height),
            strokeWidth = if (abs(gx) < stepX * 1e-9) 1.2f * density else minor,
        )
        gx += stepX
    }

    var gy = floor(view.yMin / stepY) * stepY
    while (gy <= view.yMax) {
        val py = yToPx(gy, view).toFloat()
        drawLine(
            color = if (abs(gy) < stepY * 1e-9) Color(0xFF9E9E9E) else Color(0x1A808080),
            start = Offset(0f, py),
            end = Offset(size.width, py),
            strokeWidth = if (abs(gy) < stepY * 1e-9) 1.2f * density else minor,
        )
        gy += stepY
    }
}

/** Fills the region under the curve between two x values. */
private fun DrawScope.drawIntegral(
    function: Node?,
    from: Double,
    to: Double,
    view: GraphView,
    color: Color,
) {
    if (function == null) return
    val left = maxOf(from.coerceIn(view.xMin, view.xMax), view.xMin)
    val right = minOf(to.coerceIn(view.xMin, view.xMax), view.xMax)
    if (right <= left) return
    val baseline = yToPx(0.0, view).coerceIn(0f, size.height)

    val path = Path()
    path.moveTo(xToPx(left, view).toFloat(), baseline)
    val steps = 240
    for (i in 0..steps) {
        val x = left + (right - left) * i / steps
        val y = Evaluator.eval(function, mapOf("x" to x)).valueOrNull() ?: continue
        path.lineTo(xToPx(x, view).toFloat(), yToPx(y, view).toFloat())
    }
    path.lineTo(xToPx(right, view).toFloat(), baseline)
    path.close()
    drawPath(path, color)
}

/**
 * Samples a curve across the visible width. Breaks the path whenever the
 * function is undefined so poles appear as jumps instead of tall spikes.
 */
private fun DrawScope.drawCurve(
    node: Node,
    view: GraphView,
    color: Color,
    strokeWidth: Float,
    dashed: Boolean = false,
) {
    val path = Path()
    val steps = (size.width / 1.5f).toInt().coerceIn(120, 900)
    var penDown = false
    for (i in 0..steps) {
        val x = view.xMin + (view.xMax - view.xMin) * i / steps
        val y = Evaluator.eval(node, mapOf("x" to x)).valueOrNull()
        val usable = y != null && !y.isNaN() && !y.isInfinite() && abs(y) < 1e12
        if (!usable) {
            penDown = false
            continue
        }
        val px = xToPx(x, view).toFloat()
        val py = yToPx(y, view).toFloat()
        // Points far outside the window still need a clipped segment.
        if (py < -size.height || py > size.height * 2) {
            penDown = false
            continue
        }
        if (penDown) path.lineTo(px, py) else path.moveTo(px, py)
        penDown = true
    }
    val effect = if (dashed) {
        androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f * density, 6f * density))
    } else {
        null
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = effect))
}

private fun DrawScope.xToPx(x: Double, view: GraphView): Float =
    ((x - view.xMin) / (view.xMax - view.xMin) * size.width).toFloat()

private fun DrawScope.yToPx(y: Double, view: GraphView): Float =
    ((view.yMax - y) / (view.yMax - view.yMin) * size.height).toFloat()

/** A grid step of 1, 2 or 5 times a power of ten, so labels stay readable. */
private fun niceStep(raw: Double): Double {
    val step = 10.0.pow(floor(log10(abs(raw).coerceAtLeast(1e-12))))
    val normalised = raw / step
    val factor = when {
        normalised <= 1.0 -> 1.0
        normalised <= 2.0 -> 2.0
        normalised <= 5.0 -> 5.0
        else -> 10.0
    }
    return factor * step
}

/** Convenience so callers can size the graph without repeating dp units. */
val GraphHeight = 240.dp
