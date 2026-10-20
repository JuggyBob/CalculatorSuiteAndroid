package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.calcsuite.engine.advanced.ComplexNum
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * An interactive picture of the complex plane.
 *
 * The real axis runs across and the imaginary axis up, and the points and
 * curves are drawn over them. Dragging pans the plane and pinching makes it
 * larger.
 *
 * The x and y scales are the same, so a circle really is drawn as a circle.
 */
@Composable
fun ComplexPlaneView(
    points: List<Pair<ComplexNum, Color>>,
    curves: List<List<ComplexNum>>,
    view: PlotViewport,
    onViewChange: (PlotViewport) -> Unit,
    modifier: Modifier = Modifier,
) {
    var liveView by remember { mutableStateOf(view) }
    if (view != liveView) liveView = view

    val describe = buildString {
        append("The complex plane, centred at ")
        append(view.centreX)
        append(" and ")
        append(view.centreY)
        append(", spanning ")
        append(view.span)
        append(" on each side. ")
        append(points.size)
        append(" points are marked.")
    }

    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .clipToBounds()
            .then(Modifier.pointerInput(Unit) {
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
            val centre = Offset(size.width / 2f, size.height / 2f)
            val unitsPerPx = (liveView.span / size.width).toFloat()
            val (yMin, yMax) = liveView.verticalRange(
                size.height.toDouble(),
                size.width.toDouble(),
            )

            fun toPx(z: ComplexNum) = Offset(
                centre.x + ((z.re - liveView.centreX) / unitsPerPx).toFloat(),
                centre.y - ((z.im - liveView.centreY) / unitsPerPx).toFloat(),
            )

            // The grid and the axes.
            val step = niceStep(liveView.span / 4.0)
            var gx = floor(liveView.centreX / step) * step
            while (gx <= liveView.xMax) {
                val px = centre.x + ((gx - liveView.centreX) / unitsPerPx).toFloat()
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
                val py = centre.y - ((gy - liveView.centreY) / unitsPerPx).toFloat()
                val onAxis = abs(gy) < step * 1e-9
                drawLine(
                    color = if (onAxis) Color(0xFF9E9E9E) else Color(0x1A808080),
                    start = Offset(0f, py),
                    end = Offset(size.width, py),
                    strokeWidth = if (onAxis) 1.2f * density else 0.5f * density,
                )
                gy += step
            }

            // The curves.
            for (curve in curves) {
                if (curve.size < 2) continue
                val path = Path()
                curve.forEachIndexed { index, z ->
                    val point = toPx(z)
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path,
                    accent,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f * density),
                )
            }

            // The points.
            for ((z, colour) in points) {
                val point = toPx(z)
                drawCircle(colour, radius = 6f * density, center = point)
                drawCircle(Color(0xFF101410), radius = 2.5f * density, center = point)
            }
        }
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
