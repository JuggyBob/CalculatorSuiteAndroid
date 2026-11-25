package com.calcsuite.engine.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions found while auditing the interval and measure engines.
 */
class AdvancedSetMeasureRegressionTest {

    @Test fun mergingKeepsAnEndThatWasIncluded() {
        // [0, 1] ∪ (0.5, 2] is [0, 2], not (0, 2].
        val set = RealSet.fromIntervals(
            listOf(Interval.closed(0.0, 1.0), Interval(0.5, 2.0, openFrom = true)),
        )
        assertEquals(1, set.intervals.size)
        assertTrue(set.contains(0.0))
        assertTrue(set.contains(2.0))
        assertEquals(2.0, set.measure, 1e-12)
    }

    @Test fun intervalsThatMissASharedPointStayApart() {
        // (0, 1) ∪ (1, 2) is missing the point 1; it is not the interval (0, 2).
        val set = RealSet.fromIntervals(
            listOf(
                Interval(0.0, 1.0, openTo = true),
                Interval(1.0, 2.0, openFrom = true),
            ),
        )
        assertEquals(2, set.intervals.size)
        assertFalse(set.contains(1.0))
        assertEquals(2.0, set.measure, 1e-12)
    }

    @Test fun anIncludedSharedPointSurvivesMerging() {
        // [0, 1) ∪ [1, 2] is [0, 2], including 1.
        val set = RealSet.fromIntervals(
            listOf(
                Interval(0.0, 1.0, openTo = true),
                Interval.closed(1.0, 2.0),
            ),
        )
        assertEquals(1, set.intervals.size)
        assertTrue(set.contains(1.0))
        assertEquals(2.0, set.measure, 1e-12)
    }

    @Test fun pointIntersectionsSurvive() {
        val point = RealSet.of(doubleArrayOf(0.5, 0.5))
        val segment = RealSet.of(doubleArrayOf(0.0, 1.0))
        val overlap = MeasureEngine.intersect(point, segment)
        assertEquals(1, overlap.intervals.size)
        assertTrue(overlap.contains(0.5))

        val touching = MeasureEngine.intersect(
            RealSet.of(doubleArrayOf(0.0, 1.0)),
            RealSet.of(doubleArrayOf(1.0, 2.0)),
        )
        assertTrue(touching.contains(1.0))
    }

    @Test fun complementIsClippedToTheBoundingInterval() {
        val right = MeasureEngine.complement(
            RealSet.of(doubleArrayOf(2.0, 5.0)), doubleArrayOf(0.0, 3.0),
        )
        assertEquals(1, right.intervals.size)
        assertTrue(right.contains(0.0))
        // The set is closed, so 2 belongs to it and not to its complement.
        assertFalse(right.contains(2.0))
        // Clipping to [0, 3] keeps the part of [2, 5] outside the window out.
        assertFalse(right.contains(3.0))
        assertEquals(2.0, right.measure, 1e-12)

        val left = MeasureEngine.complement(
            RealSet.of(doubleArrayOf(-2.0, 1.0)), doubleArrayOf(0.0, 3.0),
        )
        assertFalse(left.contains(0.0))
        assertEquals(2.0, left.measure, 1e-12)
    }

    @Test fun aMultiPieceComplementLeavesTheSharedPointsOut() {
        val set = RealSet.of(doubleArrayOf(0.0, 1.0), doubleArrayOf(2.0, 3.0))
        val rest = MeasureEngine.complement(set, doubleArrayOf(0.0, 4.0))
        assertEquals(2, rest.intervals.size)
        assertFalse(rest.contains(1.0))
        assertFalse(rest.contains(2.0))
        assertEquals(2.0, rest.measure, 1e-12)
    }

    @Test fun riemannSumsRejectAnEmptySampling() {
        assertNull(MeasureEngine.riemannSums({ 1.0 }, 0.0, 1.0, 4, 0))
        assertNull(MeasureEngine.riemannSums({ Double.NaN }, 0.0, 1.0, 4, 2))
    }

    @Test fun aRectangleHasTheAreaItIsDrawnWith() {
        val region = PlaneRegion(
            PlaneRegion.Kind.RECTANGLE, centreX = 1.0, centreY = 2.0, radius = 3.0,
        )
        assertEquals(36.0, region.area, 1e-12)
    }

    @Test fun anUnboundedSetHasNoInfiniteBoundaryPoint() {
        val halfLine = RealSet.of(doubleArrayOf(0.0, Double.POSITIVE_INFINITY))
        val boundary = halfLine.boundary()
        assertEquals(1, boundary.intervals.size)
        assertEquals(0.0, boundary.intervals[0].from, 1e-12)
        assertTrue(RealSet.WHOLE.boundary().isEmpty)
    }

    @Test fun anIntervalWithNonFiniteBoundsIsRejected() {
        assertNull(parseInterval("[0, Infinity]"))
        assertNull(parseInterval("[NaN, 1]"))
    }
}
