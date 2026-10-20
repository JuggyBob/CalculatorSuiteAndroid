package com.calcsuite.ui.tools.calculus

import kotlin.math.cos
import kotlin.math.sin

/**
 * How a surface `z = f(x, y)` is turned into a picture.
 *
 * The surface is turned about its vertical axis by the rotation, and the
 * camera stands at an angle above the flat plane: the tilt is how high it
 * stands. A small tilt looks at the surface from the side, and a large one
 * looks down on it.
 *
 * The two directions the picture is drawn in are the height on the screen and
 * the depth into it. They are the two parts of one turn, so for a point
 * `(v, w)` of the turned surface they are
 *
 *     height = v·sin(tilt) + w·cos(tilt)
 *     depth  = v·cos(tilt) − w·sin(tilt)
 *
 * The depth grows with the distance into the screen, so the pieces are drawn
 * from the far ones to the near ones.
 *
 * The height of the function, `w`, keeps its positive sign in the height for
 * every tilt below a right angle, so a point that is higher in the function
 * is always higher on the screen: the surface is never drawn upside down.
 */
object SurfaceProjection {

    /**
     * Where a point of the surface lands.
     *
     * @param x the x of the point.
     * @param y the y of the point.
     * @param w the height of the function, already measured from the middle
     *   of the range of the function and scaled to the range.
     * @param rotation the turn about the vertical axis, in radians.
     * @param tilt the height of the camera above the flat plane, in radians.
     * @return the position across the picture, the height on it, and the
     *   depth into it.
     */
    fun project(
        x: Double,
        y: Double,
        w: Double,
        rotation: Float,
        tilt: Float,
    ): Triple<Double, Double, Double> {
        val u = x * cos(rotation.toDouble()) - y * sin(rotation.toDouble())
        val v = x * sin(rotation.toDouble()) + y * cos(rotation.toDouble())
        val height = v * sin(tilt.toDouble()) + w * cos(tilt.toDouble())
        val depth = v * cos(tilt.toDouble()) - w * sin(tilt.toDouble())
        return Triple(u, height, depth)
    }

    /**
     * The turn that carries a drag of the given size.
     *
     * Dragging to the right turns the surface so that the part of it nearest
     * the viewer moves to the right with the finger, and dragging down tips
     * it so that the near part moves down with the finger.
     */
    fun turn(rotation: Float, tilt: Float, panX: Double, panY: Double): Pair<Float, Float> {
        val turned = (rotation + panX * 0.01).toFloat()
        val tipped = (tilt + panY * 0.01).toFloat().coerceIn(0.15f, 1.5f)
        return turned to tipped
    }
}
