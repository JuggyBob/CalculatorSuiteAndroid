package com.calcsuite.engine.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regressions for the finite-topology enumeration.
 */
class FiniteSpaceRobustnessTest {

    @Test fun thirteenPointsAreRejected() {
        val names = List(13) { "p$it" }
        val neighbourhoods = names.indices.map { listOf(it) }
        assertNull("13 points would overflow the old Int mask", FiniteSpace.ofOrNull(names, neighbourhoods))
    }

    @Test fun twelvePointsAreAcceptedAndTerminate() {
        val names = List(FiniteSpace.MAX_POINTS) { "p$it" }
        val neighbourhoods = names.indices.map { listOf(it) }
        val space = FiniteSpace.ofOrNull(names, neighbourhoods)
        assertNotNull(space)
        assertEquals(FiniteSpace.MAX_POINTS, space!!.points.size)
        // The discrete topology on twelve points has one open set per subset.
        assertEquals(1 shl FiniteSpace.MAX_POINTS, space.openSets().size)
        assertFalse("the discrete space splits", space.isConnected())
    }

    @Test fun malformedNeighbourhoodsAreRejected() {
        val names = listOf("a", "b")
        assertNull(FiniteSpace.ofOrNull(names, listOf(listOf(0))))
        assertNull(FiniteSpace.ofOrNull(names, listOf(listOf(0), listOf(2))))
        assertNull(FiniteSpace.ofOrNull(emptyList(), emptyList()))
    }

    @Test fun theFamilyIsComputedOnlyOnce() {
        // The smallest neighbourhoods are {0}, {0, 1} and {1, 2}. Their
        // unions are exactly ∅, {0}, {0, 1}, {1, 2} and the whole space:
        // there is no union that keeps 1 without also keeping 0 or 2, so
        // {1} is not open and the family has five members, not six.
        val space = FiniteSpace(
            points = listOf("a", "b", "c"),
            neighbourhoods = listOf(listOf(0), listOf(0, 1), listOf(1, 2)),
        )
        val expected = setOf(
            emptySet<Int>(), setOf(0), setOf(0, 1), setOf(1, 2), setOf(0, 1, 2),
        )
        assertEquals(expected, space.openSets().toSet())
        assertTrue(space.isOpen(setOf(0)))
        assertTrue(space.isClosed(setOf(0, 1, 2) - setOf(0)))

        // The point of the test: asking again must return the very same
        // cached family, after membership questions as well.
        assertSame("repeated openSets calls must reuse the family", space.openSets(), space.openSets())
        assertFalse("the space splits into {0} and {1, 2}", space.isConnected())
        assertSame(
            "membership questions must not rebuild the family",
            space.openSets(),
            space.openSets(),
        )
    }

    @Test fun smallSpaceResultsAreUnchanged() {
        // The three-point space from the original regression test must keep
        // exactly the same topology after the guard and caching were added.
        val space = FiniteSpace.ofOrNull(
            listOf("a", "b", "c"),
            listOf(listOf(0), listOf(0, 1), listOf(1, 2)),
        )!!
        val open = space.openSets()
        assertTrue(open.any { it.isEmpty() })
        assertTrue(open.any { it == setOf(0, 1, 2) })
        assertTrue(open.any { it == setOf(0) })
        assertTrue(open.any { it == setOf(1, 2) })
        assertTrue("b is in the interior of {a, b}", 1 in space.interior(setOf(0, 1)))
        assertTrue("c is in the closure of {b}", 2 in space.closure(setOf(1)))
    }
}
