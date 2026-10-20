package com.calcsuite.engine.algebra.inequality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the interval representation and the set operations over it. */
class SolutionSetTest {

    private fun closed(value: Double) = Endpoint(value, value.toInt().toString(), true)
    private fun open(value: Double) = Endpoint(value, value.toInt().toString(), false)

    @Test fun anEmptySetIsWrittenAsTheEmptySet() {
        assertEquals("∅", SolutionSet.EMPTY.toIntervalNotation())
        assertTrue(SolutionSet.EMPTY.isEmpty)
    }

    @Test fun theWholeLineIsWrittenWithInfinities() {
        assertEquals("(−∞, ∞)", SolutionSet.EVERYTHING.toIntervalNotation())
        assertTrue(SolutionSet.EVERYTHING.isEverything)
    }

    @Test fun anIntervalRemembersWhetherItsEndsAreIncluded() {
        val interval = Interval(open(1.0), closed(2.0))
        assertTrue(interval.contains(1.5))
        assertFalse("an open end is not in the set", interval.contains(1.0))
        assertTrue("a closed end is in the set", interval.contains(2.0))
        assertFalse(interval.contains(2.5))
    }

    @Test fun unionMergesTouchingIntervals() {
        val a = listOf(Interval(closed(0.0), open(1.0)))
        val b = listOf(Interval(closed(1.0), closed(2.0)))
        val merged = unionOf(a, b)
        assertEquals(1, merged.size)
        assertTrue(merged.single().contains(1.0))
    }

    @Test fun unionKeepsApartWhatItShould() {
        val a = listOf(Interval(closed(0.0), open(1.0)))
        val b = listOf(Interval(closed(2.0), closed(3.0)))
        val merged = unionOf(a, b)
        assertEquals(2, merged.size)
        assertFalse("the gap must stay out", merged.any { it.contains(1.5) })
    }

    @Test fun intersectionKeepsOnlyWhatIsCommon() {
        val a = listOf(Interval(closed(0.0), closed(5.0)))
        val b = listOf(Interval(closed(3.0), closed(9.0)))
        val common = intersectOf(a, b)
        assertEquals(1, common.size)
        assertTrue(common.single().contains(4.0))
        assertFalse(common.single().contains(2.0))
    }

    @Test fun intersectingDisjointSetsGivesNothing() {
        val a = listOf(Interval(closed(0.0), closed(1.0)))
        val b = listOf(Interval(closed(2.0), closed(3.0)))
        assertTrue(intersectOf(a, b).isEmpty())
    }

    @Test fun anIntersectionThatOnlySharesAnExcludedPointIsEmpty() {
        // (-1, 1) and (1, 3) share the point 1, which neither includes.
        val a = listOf(Interval(open(-1.0), open(1.0)))
        val b = listOf(Interval(open(1.0), closed(3.0)))
        assertTrue(intersectOf(a, b).isEmpty())
    }

    @Test fun theSetBuilderFormReadsAsAnInequality() {
        val set = SolutionSet(listOf(Interval(open(1.0), closed(4.0))))
        assertEquals("{ x ∈ ℝ | 1 < x ≤ 4 }", set.toSetBuilder("x"))
    }

    @Test fun theSetBuilderFormHandlesUnboundedEnds() {
        assertEquals(
            "{ x ∈ ℝ | x < 4 }",
            SolutionSet(listOf(Interval(null, open(4.0)))).toSetBuilder("x"),
        )
        assertEquals(
            "{ x ∈ ℝ | x ≥ 2 }",
            SolutionSet(listOf(Interval(closed(2.0), null))).toSetBuilder("x"),
        )
    }

    @Test fun anExactEndpointKeepsItsExactText() {
        val set = SolutionSet(
            listOf(Interval(Endpoint(0.3333333333333333, "1/3", true), null)),
        )
        assertEquals("{ x ∈ ℝ | x ≥ 1/3 }", set.toSetBuilder("x"))
    }
}
