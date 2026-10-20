package com.calcsuite.ui.tools.calculus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for the pan and zoom arithmetic the graphs share.
 *
 * The rules are pinned here so that every graph behaves the same way: a drag
 * moves the picture with the finger, and a pinch spreads it while keeping
 * the point under the fingers where it was.
 */
class PlotViewportMathTest {

    private val view = PlotViewport(centreX = 0.0, centreY = 0.0, span = 10.0)

    @Test fun aDragToTheRightMovesThePictureToTheRight() {
        // The picture follows the finger, so the world point that was under
        // the finger moves to the right of it.
        val moved = PlotViewportMath.transform(
            view = view,
            focusX = 500.0,
            focusY = 500.0,
            panX = 100.0,
            panY = 0.0,
            zoom = 1.0,
            widthPx = 1000.0,
            heightPx = 1000.0,
        )
        assertTrue(
            "the picture should move to the right, but the centre went to ${moved.centreX}",
            moved.centreX < view.centreX,
        )
        // The amount is the drag in world units: 100 pixels of 1000 over a
        // span of 10 is one world unit.
        assertEquals(-1.0, moved.centreX, 1e-9)
    }

    @Test fun aDragDownMovesThePictureDown() {
        val moved = PlotViewportMath.transform(
            view = view,
            focusX = 500.0,
            focusY = 500.0,
            panX = 0.0,
            panY = 100.0,
            zoom = 1.0,
            widthPx = 1000.0,
            heightPx = 1000.0,
        )
        // The y axis points up while the screen's points down, so the window's
        // centre moves up in the world when the picture is dragged down.
        assertTrue(
            "the picture should move down, but the centre went to ${moved.centreY}",
            moved.centreY > view.centreY,
        )
        // The amount is the drag in world units: 100 pixels of 1000 over a
        // span of 10 is one world unit.
        assertEquals(1.0, moved.centreY, 1e-9)
    }

    @Test fun aPinchSpreadsThePicture() {
        val zoomed = PlotViewportMath.transform(
            view = view,
            focusX = 500.0,
            focusY = 500.0,
            panX = 0.0,
            panY = 0.0,
            zoom = 2.0,
            widthPx = 1000.0,
            heightPx = 1000.0,
        )
        assertEquals("the picture should be twice as large", 5.0, zoomed.span, 1e-9)
    }

    @Test fun aPinchTogetherShrinksThePicture() {
        val zoomed = PlotViewportMath.transform(
            view = view,
            focusX = 500.0,
            focusY = 500.0,
            panX = 0.0,
            panY = 0.0,
            zoom = 0.5,
            widthPx = 1000.0,
            heightPx = 1000.0,
        )
        assertEquals("the picture should be half as large", 20.0, zoomed.span, 1e-9)
    }

    @Test fun thePointUnderTheFingersStaysUnderTheFingers() {
        val focusX = 250.0
        val focusY = 700.0
        val zoomed = PlotViewportMath.transform(
            view = view,
            focusX = focusX,
            focusY = focusY,
            panX = 0.0,
            panY = 0.0,
            zoom = 3.0,
            widthPx = 1000.0,
            heightPx = 1000.0,
        )
        // The world point under the fingers before the pinch.
        val unitsPerPx = view.span / 1000.0
        val before = (focusX - 500.0) * unitsPerPx
        // And where it is after it.
        val after = (focusX - 500.0) * (zoomed.span / 1000.0) + zoomed.centreX
        assertEquals("the point under the fingers should not move", before, after, 1e-9)
    }

    @Test fun theWindowCannotCollapseOrRunAway() {
        val tiny = PlotViewportMath.transform(
            view = view, focusX = 0.0, focusY = 0.0, panX = 0.0, panY = 0.0,
            zoom = 1e12, widthPx = 1000.0, heightPx = 1000.0,
        )
        assertTrue("the window must not collapse", tiny.span > 0.0)
        val huge = PlotViewportMath.transform(
            view = view, focusX = 0.0, focusY = 0.0, panX = 0.0, panY = 0.0,
            zoom = 1e-12, widthPx = 1000.0, heightPx = 1000.0,
        )
        assertTrue("the window must not run away", huge.span < 1e9)
    }

    @Test fun theScalesAreTheSameInBothDirections() {
        // A slope of one has to be drawn at forty-five degrees, which needs
        // the same number of world units per pixel on each axis. The window
        // is as wide as the span and as tall as the shape of the picture
        // allows.
        val width = 1000.0
        val height = 800.0
        val range = view.verticalRange(height, width)
        val xUnits = view.span / width
        val yUnits = (range.second - range.first) / height
        assertEquals("the scales must agree", xUnits, yUnits, 1e-12)
    }

    @Test fun theSurfaceTurnsAndZoomsWithTheGestures() {
        val surface = SurfaceView.Default
        val turned = PlotViewportMath.transformSurface(surface, panX = 100.0, panY = 0.0, zoom = 1.0)
        assertTrue("the surface should turn to the right", turned.rotation > surface.rotation)
        val tipped = PlotViewportMath.transformSurface(surface, panX = 0.0, panY = 100.0, zoom = 1.0)
        assertTrue("the surface should tip towards the viewer", tipped.tilt > surface.tilt)
        val zoomed = PlotViewportMath.transformSurface(surface, panX = 0.0, panY = 0.0, zoom = 2.0)
        assertEquals("the surface should be twice as large", surface.zoom * 2f, zoomed.zoom, 1e-6f)
    }

    @Test fun theSurfaceKeepsItsTiltWithinSaneBounds() {
        var surface = SurfaceView.Default
        repeat(20) { surface = PlotViewportMath.transformSurface(surface, 0.0, 1000.0, 1.0) }
        assertTrue("the tilt must not go past the top", surface.tilt <= 1.5f)
        repeat(40) { surface = PlotViewportMath.transformSurface(surface, 0.0, -1000.0, 1.0) }
        assertTrue("the tilt must not go past the side", surface.tilt >= 0.15f)
    }

    @Test fun aSlopeSegmentIsDrawnAtTheAngleOfTheSlope() {
        // A slope of zero is a horizontal segment, and a large slope is a
        // vertical one. The screen's y axis points down, so the segment leans
        // the other way from the way the slope is measured.
        val flat = slopeDirection(0.0)
        assertEquals(1.0, flat.first, 1e-12)
        assertEquals(0.0, flat.second, 1e-12)

        val steep = slopeDirection(10.0)
        assertTrue("a steep slope is nearly vertical", abs(steep.second) > 0.99)
        assertTrue("a steep slope leans the right way", steep.first > 0.0)

        val negative = slopeDirection(-2.0)
        assertTrue("a falling slope leans down", negative.second > 0.0)

        // The direction is always a unit vector.
        for (slope in listOf(-5.0, -1.0, 0.0, 0.5, 3.0, 20.0)) {
            val (x, y) = slopeDirection(slope)
            assertEquals("the direction must be a unit vector", 1.0, kotlin.math.hypot(x, y), 1e-12)
        }
    }
}
