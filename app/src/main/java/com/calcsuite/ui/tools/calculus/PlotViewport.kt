package com.calcsuite.ui.tools.calculus

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The window a graph is looked at through.
 *
 * The same window is used for every graph in the app, so that a plot means
 * the same thing everywhere.
 *
 * The window is described by its centre and its **width in world units**. The
 * height follows the shape of the picture, so that there are always the same
 * number of world units in a pixel on each axis. That is what makes a slope
 * of one look like forty-five degrees, and what stops the slope field from
 * changing angle from one part of the picture to another.
 */
data class PlotViewport(
    /** The x at the middle of the window. */
    val centreX: Double = 0.0,
    /** The y at the middle of the window. */
    val centreY: Double = 0.0,
    /** The width of the window in world units. */
    val span: Double = 10.0,
) {
    val xMin: Double get() = centreX - span / 2
    val xMax: Double get() = centreX + span / 2

    /**
     * The half-height of the window, for a picture of the given shape.
     *
     * The height is set so that the world units in a pixel are the same on
     * both axes.
     */
    fun halfHeight(heightPx: Double, widthPx: Double): Double {
        if (widthPx <= 0.0) return span / 2
        return span * heightPx / widthPx / 2
    }

    /** The y at the bottom and the top of the window, for a picture of the given shape. */
    fun verticalRange(heightPx: Double, widthPx: Double): Pair<Double, Double> {
        val half = halfHeight(heightPx, widthPx)
        return (centreY - half) to (centreY + half)
    }

    companion object {
        val Default = PlotViewport()

        /** A window centred on the origin. */
        fun centred(span: Double) = PlotViewport(span = span)

        /**
         * The window that shows every one of the given points.
         *
         * The width is the larger of the two ranges, because the scales are
         * the same on both axes: a solution that climbs steeply is drawn
         * steeply, and it is never cut off. A little room is left around
         * the points so that the curve does not touch the edge.
         *
         * @param points the points to show, as x and y.
         * @param margin the room to leave around them, as a fraction.
         */
        fun fitting(
            points: List<Pair<Double, Double>>,
            margin: Double = 0.08,
        ): PlotViewport {
            val usable = points.filter { it.first.isFinite() && it.second.isFinite() }
            if (usable.isEmpty()) return Default
            var lowestX = Double.POSITIVE_INFINITY
            var highestX = Double.NEGATIVE_INFINITY
            var lowestY = Double.POSITIVE_INFINITY
            var highestY = Double.NEGATIVE_INFINITY
            for ((x, y) in usable) {
                lowestX = min(lowestX, x)
                highestX = max(highestX, x)
                lowestY = min(lowestY, y)
                highestY = max(highestY, y)
            }
            val width = highestX - lowestX
            val height = highestY - lowestY
            // The window has to be at least this big, so that a single point
            // or a flat line is still visible.
            val span = max(max(width, height), 1.0) * (1.0 + 2 * margin)
            return PlotViewport(
                centreX = (lowestX + highestX) / 2,
                centreY = (lowestY + highestY) / 2,
                span = span,
            )
        }
    }
}

/**
 * The pan and zoom arithmetic shared by the graphs.
 *
 * The rules are these, and they are the same for every graph:
 *
 * - A **drag moves the picture with the finger**. Dragging to the right
 *   brings what is on the right further to the right, which is what a person
 *   expects when they push on a piece of paper.
 * - A **pinch spreads the picture**. Spreading two fingers apart makes the
 *   picture larger, and the point under the fingers stays under them.
 * - The picture can never collapse to a point or drift so far out that the
 *   grid stops meaning anything.
 */
object PlotViewportMath {

    private const val MIN_SPAN = 1e-4
    private const val MAX_SPAN = 1e7

    /**
     * The window after a drag and a pinch.
     *
     * @param focusX where the gesture is, in pixels from the left.
     * @param focusY where the gesture is, in pixels from the top.
     * @param panX how far the fingers moved to the right, in pixels.
     * @param panY how far the fingers moved down, in pixels.
     * @param zoom the pinch factor; above one spreads the fingers apart.
     * @param widthPx the width of the picture, in pixels.
     * @param heightPx the height of the picture, in pixels.
     */
    fun transform(
        view: PlotViewport,
        focusX: Double,
        focusY: Double,
        panX: Double,
        panY: Double,
        zoom: Double,
        widthPx: Double,
        heightPx: Double,
    ): PlotViewport {
        if (widthPx <= 0.0 || heightPx <= 0.0) return view
        val safeZoom = if (zoom.isFinite() && zoom > 0.0) zoom else 1.0
        val newSpan = (view.span / safeZoom).coerceIn(MIN_SPAN, MAX_SPAN)

        // The world units in a pixel. There are the same number on each axis,
        // which is what keeps the angles true.
        val unitsPerPx = view.span / widthPx

        // The world point under the fingers before the gesture.
        val focusWorldX = view.centreX + (focusX - widthPx / 2) * unitsPerPx
        val focusWorldY = view.centreY - (focusY - heightPx / 2) * unitsPerPx

        // Where the point sits in the window, as a fraction from the left or
        // the bottom. The pinch keeps it in the same place.
        val oldHeight = view.span * heightPx / widthPx
        val newHeight = newSpan * heightPx / widthPx
        val ratioX = if (view.span == 0.0) 0.5 else (focusWorldX - view.xMin) / view.span
        val ratioY = if (oldHeight == 0.0) 0.5 else (focusWorldY - (view.centreY - oldHeight / 2)) / oldHeight

        val newXMin = focusWorldX - ratioX * newSpan
        val newYMin = focusWorldY - ratioY * newHeight

        // The drag moves the picture with the fingers: to the right on the
        // screen means to the right in the world, so the window's centre
        // moves the other way. The vertical sign is the other way round
        // because the y axis points up while the screen's points down.
        val centreX = newXMin + newSpan / 2 - panX * (newSpan / widthPx)
        val centreY = newYMin + newHeight / 2 + panY * (newSpan / widthPx)

        return PlotViewport(centreX, centreY, newSpan)
    }

    /**
     * The window after a turn and a tilt, for the three-dimensional surface.
     *
     * The turn is about the vertical axis and the tilt is how far the surface
     * leans back. Both are clamped so the surface can never be seen edge on
     * or from directly underneath.
     */
    fun transformSurface(
        view: SurfaceView,
        panX: Double,
        panY: Double,
        zoom: Double,
    ): SurfaceView {
        val safeZoom = if (zoom.isFinite() && zoom > 0.0) zoom else 1.0
        val (turned, tipped) = SurfaceProjection.turn(
            view.rotation,
            view.tilt,
            panX,
            panY,
        )
        return view.copy(
            rotation = turned,
            tilt = tipped,
            // Spreading the fingers apart makes the surface larger.
            zoom = (view.zoom * safeZoom).toFloat().coerceIn(0.4f, 4f),
        )
    }
}

/**
 * How a three-dimensional surface is looked at.
 *
 * The rotation is the angle round the vertical axis, the tilt is how far the
 * surface leans back, and the zoom is a scale factor.
 */
data class SurfaceView(
    val rotation: Float = -0.6f,
    val tilt: Float = 0.9f,
    val zoom: Float = 1.0f,
) {
    companion object {
        val Default = SurfaceView()
    }
}
