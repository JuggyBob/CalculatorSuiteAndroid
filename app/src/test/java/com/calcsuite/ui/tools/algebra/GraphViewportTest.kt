package com.calcsuite.ui.tools.algebra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pan/zoom arithmetic of the calculus plot.
 *
 * A two-finger pinch cannot be injected over adb, so the gesture maths is
 * tested here instead; the single-finger path through the same code is what
 * the on-device check exercises.
 */
class GraphViewportTest {

    private val width = 1000.0
    private val height = 600.0

    private fun pan(view: GraphView, dx: Double, dy: Double) =
        GraphViewport.transform(view, width / 2, height / 2, dx, dy, 1.0, width, height)

    private fun zoom(view: GraphView, factor: Double, fx: Double = width / 2, fy: Double = height / 2) =
        GraphViewport.transform(view, fx, fy, 0.0, 0.0, factor, width, height)

    @Test fun zoomInHalvesTheSpan() {
        val v = zoom(GraphView.Default, 2.0)
        assertEquals(10.0, v.xMax - v.xMin, 1e-9)
        assertEquals(10.0, v.yMax - v.yMin, 1e-9)
    }

    @Test fun zoomOutDoublesTheSpan() {
        val v = zoom(GraphView.Default, 0.5)
        assertEquals(40.0, v.xMax - v.xMin, 1e-9)
    }

    @Test fun zoomKeepsTheFocusPointFixed() {
        // On the default [-10, 10] window a 1000 px canvas spans 20 units, so
        // 750 px from the left is world x = 5 and 300 px down is world y = 0.
        // Zooming 4x about that point must leave x = 5 at 75% of the width and
        // y = 0 at the centre.
        val v = zoom(GraphView.Default, 4.0, fx = 750.0, fy = 300.0)
        assertEquals(1.25, v.xMin, 1e-9)
        assertEquals(6.25, v.xMax, 1e-9)
        assertEquals(-2.5, v.yMin, 1e-9)
        assertEquals(2.5, v.yMax, 1e-9)
        // The focused point is unchanged in world coordinates.
        val focusWorldX = v.xMin + 0.75 * (v.xMax - v.xMin)
        val focusWorldY = v.yMax - 0.5 * (v.yMax - v.yMin)
        assertEquals(5.0, focusWorldX, 1e-9)
        assertEquals(0.0, focusWorldY, 1e-9)
    }

    @Test fun panShiftsTheWindowWithoutChangingTheSpan() {
        val before = GraphView.Default
        val after = pan(before, 100.0, 0.0)
        assertEquals(before.xMax - before.xMin, after.xMax - after.xMin, 1e-9)
        // 100 px of a 1000 px canvas is a tenth of the window, i.e. 2 units.
        // The content follows the finger, as it does when panning a map, so
        // dragging right brings smaller x into view.
        assertEquals(-12.0, after.xMin, 1e-9)
        assertEquals(8.0, after.xMax, 1e-9)
    }

    @Test fun panDownMovesTheWindowUpInWorldCoordinates() {
        val after = pan(GraphView.Default, 0.0, 60.0)
        // Dragging down reveals lower y, so the maximum decreases.
        assertEquals(8.0, after.yMax, 1e-9)
        assertEquals(-12.0, after.yMin, 1e-9)
    }

    @Test fun spanIsClampedSoTheViewCannotCollapse() {
        val collapsed = zoom(GraphView.Default, 1e12)
        assertTrue("span must not collapse", collapsed.xMax - collapsed.xMin >= 1e-4)
        val exploded = zoom(GraphView.Default, 1e-12)
        assertTrue("span must not explode", exploded.xMax - exploded.xMin <= 1e7)
    }

    @Test fun degenerateZoomFactorsAreIgnored() {
        val zero = GraphViewport.transform(GraphView.Default, 500.0, 300.0, 0.0, 0.0, 0.0, width, height)
        val negative = GraphViewport.transform(GraphView.Default, 500.0, 300.0, 0.0, 0.0, -3.0, width, height)
        val nan = GraphViewport.transform(GraphView.Default, 500.0, 300.0, 0.0, 0.0, Double.NaN, width, height)
        for (v in listOf(zero, negative, nan)) {
            assertEquals(20.0, v.xMax - v.xMin, 1e-9)
        }
    }

    @Test fun zeroSizedCanvasDoesNotDivideByZero() {
        val v = GraphViewport.transform(GraphView.Default, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0)
        assertEquals(10.0, v.xMax - v.xMin, 1e-9)
        assertTrue(v.xMin.isFinite() && v.xMax.isFinite())
    }

    @Test fun layerTogglesSurviveATransform() {
        val layered = GraphView.Default.copy(showDerivative = true, showIntegral = true)
        val after = zoom(layered, 2.0)
        assertTrue(after.showDerivative)
        assertTrue(after.showIntegral)
        assertTrue(after.showFunction)
    }
}
