package com.calcsuite.engine.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the measure computations and the topological properties.
 */
class MeasureAndTopologyTest {

    // ------------------------------------------------------------- measure

    @Test fun theMeasureOfAnIntervalIsItsLength() {
        assertEquals(2.0, MeasureEngine.measureOfInterval(0.0, 2.0), 1e-12)
        assertEquals(0.0, MeasureEngine.measureOfInterval(1.0, 1.0), 1e-12)
        assertEquals(
            Double.POSITIVE_INFINITY,
            MeasureEngine.measureOfInterval(0.0, Double.POSITIVE_INFINITY),
            1e-12,
        )
    }

    @Test fun theUnionIsTheSumLessTheOverlap() {
        val first = RealSet.of(doubleArrayOf(0.0, 2.0))
        val second = RealSet.of(doubleArrayOf(1.0, 3.0))
        val result = MeasureEngine.union(first, second)
        assertEquals(3.0, result.unionMeasure, 1e-12)
        assertEquals(1.0, result.overlapMeasure, 1e-12)
        assertEquals(4.0, result.firstMeasure + result.secondMeasure, 1e-12)
    }

    @Test fun theGeometricSeriesAddsUpToOne() {
        val series = MeasureEngine.geometricSum(0.5, 0.5, 30)
        assertEquals(1.0, series.limit, 1e-12)
        // The partial sums approach the limit from below.
        assertTrue(series.partialSums.first() < series.limit)
        assertTrue(series.partialSums.last() > series.partialSums[10])
    }

    @Test fun theIntervalsOfTheSeriesFillTheUnitInterval() {
        val intervals = MeasureEngine.geometricIntervals(12)
        val set = RealSet.fromIntervals(intervals)
        // The intervals do not overlap, so their total is the sum of their
        // lengths, which is just under 1.
        assertTrue("the total should be under 1", set.measure < 1.0)
        assertTrue("the total should be close to 1", set.measure > 0.99)
    }

    @Test fun theRiemannSumsComeTogetherForAContinuousFunction() {
        // The integral of sin from 0 to 1 is 1 − cos(1).
        val exact = 1.0 - kotlin.math.cos(1.0)
        val coarse = MeasureEngine.riemannSums({ x -> kotlin.math.sin(x) }, 0.0, 1.0, 32, 8)!!
        val fine = MeasureEngine.riemannSums({ x -> kotlin.math.sin(x) }, 0.0, 1.0, 512, 8)!!
        assertTrue("the lower sum should be under the integral", coarse.lower < exact)
        assertTrue("the upper sum should be over the integral", coarse.upper > exact)
        // The gap between the sums shrinks as the partition is refined.
        assertTrue(
            "the gap should shrink (${coarse.upper - coarse.lower} against " +
                "${fine.upper - fine.lower})",
            (fine.upper - fine.lower) < (coarse.upper - coarse.lower),
        )
        assertTrue("the fine sums should be close to the integral", fine.lower > exact - 0.01)
    }

    @Test fun theRiemannSumsDoNotComeTogetherForTheRulerFunction() {
        // The upper sums of the ruler function are large, but the lower sums
        // are small; the gap narrows as the partition is refined, which is
        // what makes the function integrable.
        val ruler: (Double) -> Double = { x ->
            val fraction = x - kotlin.math.floor(x)
            if (fraction <= 1e-9) {
                1.0
            } else {
                val q = (1.0 / fraction).toInt()
                if (kotlin.math.abs(q * fraction - 1.0) < 1e-6) 1.0 / q else 0.0
            }
        }
        val coarse = MeasureEngine.riemannSums(ruler, 0.0, 1.0, 16, 16)!!
        val fine = MeasureEngine.riemannSums(ruler, 0.0, 1.0, 256, 16)!!
        // The lower sums are almost zero, because the function is zero almost
        // everywhere, while the upper sums fall as the partition is refined.
        assertTrue("the lower sums are almost zero", coarse.lower < 0.2)
        assertTrue(
            "the upper sums should fall (${coarse.upper} against ${fine.upper})",
            fine.upper < coarse.upper,
        )
    }

    @Test fun aFiniteSetIsANullSet() {
        assertTrue(MeasureEngine.isNull(RealSet.of(doubleArrayOf(0.5, 0.5))))
        assertFalse(MeasureEngine.isNull(RealSet.of(doubleArrayOf(0.0, 1.0))))
    }

    // ------------------------------------------------------------- topology

    @Test fun aClosedIntervalIsClosedButNotOpen() {
        val set = RealSet.of(doubleArrayOf(0.0, 1.0))
        assertTrue("a closed interval is closed", set.isClosed())
        assertFalse("a closed interval is not open", set.isOpen())
    }

    @Test fun anOpenIntervalIsOpenButNotClosed() {
        val set = RealSet.fromIntervals(listOf(Interval(0.0, 1.0, openFrom = true, openTo = true)))
        assertTrue("an open interval is open", set.isOpen())
        assertFalse("an open interval is not closed", set.isClosed())
        assertTrue("the closure of an open interval is closed", set.closure().isClosed())
        assertEquals(1.0, set.measure, 1e-12)
    }

    @Test fun aClosedBoundedSetIsCompact() {
        val set = RealSet.of(doubleArrayOf(0.0, 1.0))
        assertTrue("a closed bounded set is compact", set.isCompact())
        assertFalse(
            "an unbounded set is not compact",
            RealSet.of(doubleArrayOf(0.0, Double.POSITIVE_INFINITY)).isCompact(),
        )
    }

    @Test fun anIntervalIsConnectedButTwoPiecesAreNot() {
        assertTrue("one interval is connected", RealSet.of(doubleArrayOf(0.0, 1.0)).isConnected())
        assertFalse(
            "two separated pieces are not connected",
            RealSet.fromIntervals(
                listOf(Interval.closed(0.0, 1.0), Interval.closed(2.0, 3.0)),
            ).isConnected(),
        )
    }

    @Test fun theInteriorAndTheClosureAreRight() {
        val set = RealSet.fromIntervals(
            listOf(Interval.closed(0.0, 1.0), Interval.closed(2.0, 3.0)),
        )
        assertEquals(2, set.interior().intervals.size)
        assertEquals(0.0, set.interior().intervals[0].from, 1e-12)
        assertEquals(1.0, set.interior().intervals[0].to, 1e-12)
        // The boundary is the four ends.
        assertEquals(4, set.boundary().intervals.size)
    }

    @Test fun aFiniteSpaceHasItsOpenSets() {
        // The three-point space with the smallest neighbourhoods {a}, {a, b} and
        // {b, c} has the open sets ∅, {a}, {b}, {a, b}, {b, c} and the whole
        // space.
        val space = FiniteSpace(
            points = listOf("a", "b", "c"),
            neighbourhoods = listOf(listOf(0), listOf(0, 1), listOf(1, 2)),
        )
        val open = space.openSets()
        assertTrue("the empty set is open", open.any { it.isEmpty() })
        assertTrue("the whole space is open", open.any { it.size == 3 })
        assertTrue("the smallest neighbourhood is open", open.any { it == setOf(0) })
    }

    @Test fun aFiniteSpaceCanBeDisconnected() {
        // The two-point space with the discrete topology splits into two open
        // parts, so it is not connected.
        val space = FiniteSpace(
            points = listOf("a", "b"),
            neighbourhoods = listOf(listOf(0), listOf(1)),
        )
        assertFalse("the discrete two-point space is not connected", space.isConnected())
        assertEquals(4, space.openSets().size)
    }

    @Test fun aFiniteSpaceFindsTheInteriorAndTheClosure() {
        val space = FiniteSpace(
            points = listOf("a", "b", "c"),
            neighbourhoods = listOf(listOf(0), listOf(0, 1), listOf(1, 2)),
        )
        // The interior of {a, b} contains {a, b}, which is open.
        val interior = space.interior(setOf(0, 1))
        assertTrue("a is in the interior", 0 in interior)
        assertTrue("b is in the interior", 1 in interior)
        // The closure of {b} is {b, c}: every neighbourhood of c contains b.
        val closure = space.closure(setOf(1))
        assertTrue("b is in the closure of b", 1 in closure)
        assertTrue("c is in the closure of b", 2 in closure)
    }

    @Test fun theComplementOfASetIsRight() {
        val inside = RealSet.of(doubleArrayOf(1.0, 2.0))
        val outside = MeasureEngine.complement(inside, doubleArrayOf(0.0, 3.0))
        assertEquals(2, outside.intervals.size)
        assertTrue("the complement should not contain 1", !outside.contains(1.0))
        assertTrue("the complement should contain 0", outside.contains(0.0))
        assertTrue("the complement should contain 3", outside.contains(3.0))
        assertEquals(2.0, outside.measure, 1e-12)
    }

    @Test fun theHomeomorphismsCanBeUndone() {
        for (example in Homeomorphism.examples()) {
            if (example.name.startsWith("A circle")) continue
            for (u in listOf(0.1, 0.35, 0.5, 0.72, 0.9)) {
                val back = example.map(example.inverse(u))
                assertEquals(
                    "the map and its inverse should undo each other for $u",
                    u,
                    back,
                    1e-9,
                )
            }
        }
    }

    @Test fun theFirstHomeomorphismLandsInTheOpenInterval() {
        val example = Homeomorphism.examples().first()
        for (x in listOf(-5.0, -1.0, 0.0, 1.0, 5.0)) {
            val u = example.map(x)
            assertTrue("the image should be inside (0, 1), but it was $u", u > 0.0 && u < 1.0)
        }
    }

    @Test fun aDiscIsOpenButNotClosed() {
        val region = PlaneRegion(PlaneRegion.Kind.DISC, radius = 2.0)
        assertTrue("a disc is open", region.isOpen())
        assertFalse("a disc is not closed", region.isClosed())
        assertTrue("a disc is connected", region.isConnected())
        assertEquals(4 * Math.PI, region.area, 1e-12)
    }
}
