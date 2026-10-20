package com.calcsuite.ui.tools.calculus

import com.calcsuite.data.ToolCategory
import com.calcsuite.data.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Calculus category.
 *
 * The category was made by splitting one overloaded screen into separate
 * tools, so the tests check that every tool still reaches the operation it is
 * named after, and that the operations the old screen offered are all still
 * there.
 */
class CalculusToolTest {

    @Test
    fun everyCalculusToolIsRegistered() {
        for (tool in CalculusTool.entries) {
            val registered = ToolRegistry.byId(tool.toolId)
            assertNotNull("${tool.toolId} should be in the registry", registered)
            assertEquals(ToolCategory.CALCULUS, registered!!.category)
            assertEquals(tool.title, registered.title)
        }
    }

    @Test
    fun everyToolOpensItsOwnOperation() {
        // The tool preselects the operation it is named after, so opening
        // "Integrals" lands on the integral panel rather than on an empty
        // screen.
        assertEquals(CalcOperation.DERIVATIVE, CalculusTool.DERIVATIVES.operation)
        assertEquals(CalcOperation.INTEGRAL, CalculusTool.INTEGRALS.operation)
        assertEquals(CalcOperation.LIMIT, CalculusTool.LIMITS.operation)
        assertEquals(CalcOperation.TAYLOR, CalculusTool.TAYLOR.operation)
        assertEquals(CalcOperation.SUM, CalculusTool.SUMMATIONS.operation)
        assertEquals(CalcOperation.SERIES, CalculusTool.SERIES.operation)
        assertEquals(CalcOperation.EQUATION, CalculusTool.ROOTS.operation)
        assertEquals(CalcOperation.EXTREMA, CalculusTool.EXTREMA.operation)
        assertEquals(CalcOperation.GRAPH, CalculusTool.GRAPH.operation)
        assertEquals(CalcOperation.ODE, CalculusTool.DIFFERENTIAL_EQUATIONS.operation)
    }

    @Test
    fun theToolIdsAreUnique() {
        val ids = CalculusTool.entries.map { it.toolId }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun theOldCalculusScreenIsNotLeftBehind() {
        // The old single tool was replaced by the category. Its id still has
        // to resolve, because favourites, recents and history keep it.
        val legacy = ToolRegistry.byId("algebra.calculus")
        assertNotNull("the old id should still resolve", legacy)
        assertEquals("calculus.derivatives", legacy!!.id)
        assertTrue(
            "the old id should not be listed as a tool of its own",
            ToolRegistry.all.none { it.id == "algebra.calculus" },
        )
    }

    @Test
    fun everyOperationTheOldScreenOfferedIsStillReachable() {
        val operations = CalcOperation.entries.map { it.name }.toSet()
        // These are the ten operations the single calculus screen offered.
        val expected = setOf(
            "DERIVATIVE", "INTEGRAL", "LIMIT", "SERIES", "TAYLOR",
            "SUM", "EQUATION", "EXTREMA", "ODE", "GRAPH",
        )
        assertTrue(
            "the operations should all still be there; missing: ${expected - operations}",
            operations.containsAll(expected),
        )
    }

    @Test
    fun eachToolHasItsOwnDefaultExpression() {
        // The tool's default expression suits the operation, so the screen is
        // never empty when it opens.
        for (tool in CalculusTool.entries) {
            assertTrue("${tool.toolId} should start with an expression", tool.defaultExpression.isNotBlank())
            assertTrue(
                "${tool.toolId} should describe itself",
                tool.description.isNotBlank(),
            )
        }
    }

    @Test
    fun theToolLookupFindsEveryTool() {
        for (tool in CalculusTool.entries) {
            assertEquals(tool, CalculusTool.forId(tool.toolId))
        }
        assertEquals(null, CalculusTool.forId("algebra.inequalities"))
    }
}
