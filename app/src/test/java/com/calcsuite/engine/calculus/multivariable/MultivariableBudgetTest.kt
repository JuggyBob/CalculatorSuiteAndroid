package com.calcsuite.engine.calculus.multivariable

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Nested adaptive Simpson must stop after a bounded number of evaluations. */
class MultivariableBudgetTest {

    private fun parse(text: String) =
        (MultivariableEngine.parse(text, listOf("x", "y", "z")) as CalcResult.Ok).value

    @Test(timeout = 5000)
    fun aHostileDoubleIntegralIsRefusedQuickly() {
        val function = parse("sin(1000000*x*y)")
        val result = MultivariableEngine.doubleIntegral(
            function, listOf("x", "y"),
            listOf(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, 1.0)),
        )
        assertTrue("expected an error but got $result", result is CalcResult.Err)
        assertTrue(
            "the budget should be named: ${(result as CalcResult.Err).error.message}",
            result.error.message.contains("evaluations"),
        )
    }

    @Test(timeout = 5000)
    fun aHostileTripleIntegralIsRefusedQuickly() {
        val function = parse("sin(1000000*x*y*z)")
        val result = MultivariableEngine.tripleIntegral(
            function, listOf("x", "y", "z"),
            listOf(
                doubleArrayOf(0.0, 1.0),
                doubleArrayOf(0.0, 1.0),
                doubleArrayOf(0.0, 1.0),
            ),
        )
        assertTrue("expected an error but got $result", result is CalcResult.Err)
    }

    @Test
    fun anOrdinaryNestedIntegralStillFinishesWithinTheBudget() {
        val result = MultivariableEngine.doubleIntegral(
            parse("x"), listOf("x", "y"),
            listOf(doubleArrayOf(0.0, 2.0), doubleArrayOf(0.0, 3.0)),
        )
        assertTrue("expected a value but got $result", result is CalcResult.Ok)
        assertEquals(6.0, (result as CalcResult.Ok).value, 1e-9)
    }
}
