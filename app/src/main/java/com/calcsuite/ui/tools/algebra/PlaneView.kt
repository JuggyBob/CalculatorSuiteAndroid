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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calcsuite.engine.algebra.inequality.HalfPlane
import com.calcsuite.engine.algebra.inequality.PlanePoint
import kotlin.math.abs
import kotlin.math.roundToInt

/** The visible window of the coordinate plane. */
data class PlaneView(
    val xMin: Double,
    val xMax: Double,
    val yMin: Double,
    val yMax: Double,
) {
    companion object {
        val Default = PlaneView(-10.0, 10.0, -10.0, 10.0)
    }
}

/**
 * A coordinate plane for a system of linear inequalities in two variables.
 *
 * Every boundary line is drawn, solid for a non-strict comparison and dashed
 * for a strict one, and the feasible region — the overlap of the required
 * sides — is filled. The region is computed by clipping, so the shading always
 * agrees with the calculation rather than being decorative.
 *
 * Dragging pans and pinching zooms, with the same clamping the calculus graph
 * uses so the view can never collapse to a single point.
 */
@Composable
fun PlaneView(
    halfPlanes: List<HalfPlane>,
    region: List<PlanePoint>,
    accent: Color,
    gridColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    view: PlaneView = PlaneView.Default,
    onViewChange: (PlaneView) -> Unit = {},
    probe: PlanePoint? = null,
) {
    var liveView by remember { mutableStateOf(view) }
    if (view != liveView) liveView = view
    val measurer = rememberTextMeasurer()
    val description = describePlane(halfPlanes, region)

    Box(
        modifier
            .clipToBounds()
            .then(
                Modifier.pointerInput(halfPlanes) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        liveView = PlaneViewport.transform(
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
                },
            )
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val spanX = liveView.xMax - liveView.xMin
            val spanY = liveView.yMax - liveView.yMin

            fun xOf(value: Double): Float =
                (((value - liveView.xMin) / spanX) * width).toFloat()

            fun yOf(value: Double): Float =
                (height - ((value - liveView.yMin) / spanY) * height).toFloat()

            // The grid.
            val step = gridStep(spanX)
            var gx = kotlin.math.ceil(liveView.xMin / step) * step
            while (gx < liveView.xMax) {
                val x = xOf(gx)
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f,
                )
                gx += step
            }
            var gy = kotlin.math.ceil(liveView.yMin / step) * step
            while (gy < liveView.yMax) {
                val y = yOf(gy)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                )
                gy += step
            }

            // The axes.
            val axisColor = labelColor.copy(alpha = 0.55f)
            val zeroX = xOf(0.0)
            val zeroY = yOf(0.0)
            if (zeroX in 0f..width) {
                drawLine(
                    color = axisColor,
                    start = Offset(zeroX, 0f),
                    end = Offset(zeroX, height),
                    strokeWidth = 1.8f,
                )
            }
            if (zeroY in 0f..height) {
                drawLine(
                    color = axisColor,
                    start = Offset(0f, zeroY),
                    end = Offset(width, zeroY),
                    strokeWidth = 1.8f,
                )
            }

            // The feasible region.
            if (region.size >= 3) {
                val path = Path()
                region.forEachIndexed { index, point ->
                    val x = xOf(point.x)
                    val y = yOf(point.y)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = accent.copy(alpha = 0.22f))
            }

            // The boundary lines.
            for (plane in halfPlanes) {
                val ends = boundaryEnds(plane, liveView)
                if (ends == null) continue
                val path = Path()
                path.moveTo(xOf(ends.first.x), yOf(ends.first.y))
                path.lineTo(xOf(ends.second.x), yOf(ends.second.y))
                drawPath(
                    path = path,
                    color = accent,
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        pathEffect = if (plane.strict) {
                            PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                        } else {
                            null
                        },
                    ),
                )
            }

            // Tick labels.
            val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor.copy(alpha = 0.75f))
            var tx = kotlin.math.ceil(liveView.xMin / step) * step
            while (tx < liveView.xMax) {
                val label = measurer.measure(formatTick(tx), labelStyle)
                val x = xOf(tx) - label.size.width / 2f
                if (x > -label.size.width && x < width) {
                    drawText(label, topLeft = Offset(x, height - label.size.height - 2f))
                }
                tx += step
            }
            var ty = kotlin.math.ceil(liveView.yMin / step) * step
            while (ty < liveView.yMax) {
                val label = measurer.measure(formatTick(ty), labelStyle)
                val y = yOf(ty) - label.size.height / 2f
                if (y > 0 && y < height) {
                    drawText(label, topLeft = Offset(2f, y))
                }
                ty += step
            }

            // The probe point, when the user has asked about one.
            probe?.let {
                val x = xOf(it.x)
                val y = yOf(it.y)
                drawCircle(accent, radius = 6.dp.toPx(), center = Offset(x, y))
                drawCircle(
                    color = labelColor,
                    radius = 8.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

/**
 * The two ends of a boundary line, in world coordinates, chosen so the line
 * crosses the visible window.
 */
private fun boundaryEnds(plane: HalfPlane, view: PlaneView): Pair<PlanePoint, PlanePoint>? {
    val a = plane.a.toDouble()
    val b = plane.b.toDouble()
    val c = plane.c.toDouble()
    return when {
        abs(b) > 1e-12 -> {
            val x1 = view.xMin
            val x2 = view.xMax
            PlanePoint(x1, -(a * x1 + c) / b) to PlanePoint(x2, -(a * x2 + c) / b)
        }
        abs(a) > 1e-12 -> {
            val y1 = view.yMin
            val y2 = view.yMax
            PlanePoint(-(b * y1 + c) / a, y1) to PlanePoint(-(b * y2 + c) / a, y2)
        }
        else -> null
    }
}

/** A sensible grid spacing for a span. */
private fun gridStep(span: Double): Double {
    val raw = span / 8.0
    if (raw <= 0.0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val normalised = raw / magnitude
    val step = when {
        normalised <= 1.0 -> 1.0
        normalised <= 2.0 -> 2.0
        normalised <= 5.0 -> 5.0
        else -> 10.0
    }
    return step * magnitude
}

/** Formats a tick label without a pointless trailing zero. */
private fun formatTick(value: Double): String {
    if (value == 0.0) return "0"
    val rounded = value.roundToInt().toDouble()
    return if (abs(value - rounded) < 1e-9) rounded.toInt().toString() else String.format("%.1f", value)
}

/** A spoken description of the plane, for screen readers. */
fun describePlane(halfPlanes: List<HalfPlane>, region: List<PlanePoint>): String = buildString {
    append("Coordinate plane with ${halfPlanes.size} boundary ")
    append(if (halfPlanes.size == 1) "line" else "lines")
    append(". ")
    if (region.isEmpty()) {
        append("The shaded sides do not overlap, so the system has no solution.")
    } else {
        append("The feasible region has ${region.size} corner points: ")
        append(region.joinToString(", ") { it.toString() })
        append(".")
    }
}

/**
 * Pan and zoom arithmetic for the plane, kept free of Compose so it can be
 * tested directly.
 */
object PlaneViewport {

    private const val MIN_SPAN = 1e-4
    private const val MAX_SPAN = 1e7

    fun transform(
        view: PlaneView,
        focusX: Double,
        focusY: Double,
        panX: Double,
        panY: Double,
        zoom: Double,
        widthPx: Double,
        heightPx: Double,
    ): PlaneView {
        if (widthPx <= 0.0 || heightPx <= 0.0) return view
        val spanX = view.xMax - view.xMin
        val spanY = view.yMax - view.yMin
        val factor = if (zoom > 0) 1.0 / zoom else 1.0
        val newSpanX = (spanX * factor).coerceIn(MIN_SPAN, MAX_SPAN)
        val newSpanY = (spanY * factor).coerceIn(MIN_SPAN, MAX_SPAN)
        // Keep the point under the finger where it can.
        val anchorX = view.xMin + (focusX / widthPx) * spanX
        val anchorY = view.yMax - (focusY / heightPx) * spanY
        val unitsPerPxX = newSpanX / widthPx
        val unitsPerPxY = newSpanY / heightPx
        // The picture moves with the finger in both directions. The sign is
        // different on the two axes only because the y axis points up while
        // the screen's does not.
        var newXMin = anchorX - (focusX * unitsPerPxX) - panX * unitsPerPxX
        var newYMax = anchorY + (focusY * unitsPerPxY) + panY * unitsPerPxY
        var newXMax = newXMin + newSpanX
        var newYMin = newYMax - newSpanY
        if (newXMax < newXMin) {
            newXMin = newXMin - newSpanX
            newXMax = newXMin
        }
        if (newYMin > newYMax) {
            newYMax = newYMax + newSpanY
            newYMin = newYMax
        }
        return PlaneView(newXMin, newXMax, newYMin, newYMax)
    }
}
