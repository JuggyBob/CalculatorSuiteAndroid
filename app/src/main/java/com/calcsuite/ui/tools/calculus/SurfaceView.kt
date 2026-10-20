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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * An interactive plot of a surface `z = f(x, y)`.
 *
 * The surface is drawn as a mesh of quadrilaterals, shaded by height, and the
 * quads are painted back to front so that the near ones cover the far ones.
 * Dragging turns the surface, pinching makes it larger, and the reset button
 * puts it back where it started.
 */
@Composable
fun SurfaceView(
    function: Node?,
    xName: String,
    yName: String,
    view: SurfaceView,
    onViewChange: (SurfaceView) -> Unit,
    modifier: Modifier = Modifier,
    /** The number of cells across the surface. */
    resolution: Int = 40,
    /** The window of the function that is drawn. */
    xMin: Double = -1.0,
    xMax: Double = 1.0,
    yMin: Double = -1.0,
    yMax: Double = 1.0,
) {
    var liveView by remember { mutableStateOf(view) }
    if (view != liveView) liveView = view

    val describe = buildString {
        append("Three-dimensional plot of ")
        append(function?.let { com.calcsuite.engine.calculus.NodePrinter.print(it) } ?: "nothing")
        append(". Drag to turn the surface, drag down to tip it, and pinch to zoom.")
    }

    Box(
        modifier
            .clipToBounds()
            .then(Modifier.pointerInput(function) {
                detectTransformGestures { _, pan, zoom, _ ->
                    liveView = PlotViewportMath.transformSurface(
                        liveView,
                        panX = pan.x.toDouble(),
                        panY = pan.y.toDouble(),
                        zoom = zoom.toDouble(),
                    )
                    onViewChange(liveView)
                }
            })
            .semantics { contentDescription = describe },
    ) {
        val accent = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            drawSurface(function, xName, yName, liveView, resolution, accent, xMin, xMax, yMin, yMax)
        }
    }
}

/** One quadrilateral of the mesh, with the depth used to sort the painting. */
private class Quad(
    val a: Offset,
    val b: Offset,
    val c: Offset,
    val d: Offset,
    val depth: Float,
)

/**
 * Draws the surface as a mesh of shaded quadrilaterals.
 */
private fun DrawScope.drawSurface(
    function: Node?,
    xName: String,
    yName: String,
    view: SurfaceView,
    resolution: Int,
    accent: Color,
    xMin: Double,
    xMax: Double,
    yMin: Double,
    yMax: Double,
) {
    if (function == null) return
    val n = resolution.coerceIn(8, 80)

    // The values on the grid, and the range of the heights.
    val values = Array(n + 1) { DoubleArray(n + 1) }
    var lowest = Double.POSITIVE_INFINITY
    var highest = Double.NEGATIVE_INFINITY
    val stepX = (xMax - xMin) / n
    val stepY = (yMax - yMin) / n
    for (i in 0..n) {
        for (j in 0..n) {
            val x = xMin + stepX * i
            val y = yMin + stepY * j
            val z = Evaluator.eval(
                function,
                mapOf(xName to x, yName to y),
            ).valueOrNull()
            // A value that is not finite, or that is far outside the range of
            // the rest, would tear the mesh apart, so it is clipped to the
            // range instead.
            val usable = z != null && z.isFinite() && abs(z) < 1e12
            values[i][j] = if (usable) z!! else 0.0
            if (usable) {
                if (z!! < lowest) lowest = z
                if (z!! > highest) highest = z
            }
        }
    }
    if (!lowest.isFinite() || !highest.isFinite()) return
    val span = (highest - lowest).takeIf { it > 1e-9 } ?: 1.0

    val quads = mutableListOf<Quad>()
    val centre = size.minDimension / 2f
    val scale = centre * 0.55f * view.zoom

    // The window is turned into a square of side one, so the surface is not
    // stretched by the window being wider than it is tall.
    val halfWidth = max(abs(xMin), abs(xMax)).takeIf { it > 1e-9 } ?: 1.0
    val halfHeight = max(abs(yMin), abs(yMax)).takeIf { it > 1e-9 } ?: 1.0

    fun project(x: Double, y: Double, z: Double): Triple<Float, Float, Float> {
        // The world axes: x and y across, z up.
        val px = x / halfWidth
        val py = y / halfHeight
        val w = (z - (lowest + highest) / 2) / span
        val (u, height, depth) = SurfaceProjection.project(
            px,
            py,
            w,
            view.rotation,
            view.tilt,
        )
        return Triple(
            (centre + (u * scale).toFloat()),
            (centre - (height * scale).toFloat()),
            depth.toFloat(),
        )
    }

    for (i in 0 until n) {
        for (j in 0 until n) {
            val x0 = xMin + stepX * i
            val x1 = x0 + stepX
            val y0 = yMin + stepY * j
            val y1 = y0 + stepY
            val a = project(x0, y0, values[i][j])
            val b = project(x1, y0, values[i + 1][j])
            val c = project(x1, y1, values[i + 1][j + 1])
            val d = project(x0, y1, values[i][j + 1])
            val depth = (a.third + b.third + c.third + d.third) / 4f
            quads.add(
                Quad(
                    Offset(a.first, a.second),
                    Offset(b.first, b.second),
                    Offset(c.first, c.second),
                    Offset(d.first, d.second),
                    depth,
                ),
            )
        }
    }

    // Paint the far quads first, so the near ones cover them.
    val nearest = quads.minOfOrNull { it.depth } ?: 0f
    val farthest = quads.maxOfOrNull { it.depth } ?: 0f
    val depthRange = (farthest - nearest).takeIf { it > 1e-6f } ?: 1f
    for (quad in quads.sortedByDescending { it.depth }) {
        // The shade is darker the further away the piece is.
        val shade = ((farthest - quad.depth) / depthRange).coerceIn(0f, 1f)
        val path = Path().apply {
            moveTo(quad.a.x, quad.a.y)
            lineTo(quad.b.x, quad.b.y)
            lineTo(quad.c.x, quad.c.y)
            lineTo(quad.d.x, quad.d.y)
            close()
        }
        // The shading runs from a dark shade of the accent to a light one.
        drawPath(
            path,
            Color(
                (accent.red * (0.35f + 0.75f * shade)).coerceIn(0f, 1f),
                (accent.green * (0.35f + 0.75f * shade)).coerceIn(0f, 1f),
                (accent.blue * (0.35f + 0.75f * shade)).coerceIn(0f, 1f),
                1f,
            ),
        )
    }
}
