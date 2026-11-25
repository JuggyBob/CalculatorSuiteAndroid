package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for crashes, overflow and non-finite leaks in calculus.
 *
 * Every hostile case has a timeout: the fix must reject the input quickly,
 * not merely finish eventually.
 */
class CalculusRobustnessTest {

    private fun nested(): String = "(".repeat(5000) + "1" + ")".repeat(5000)

    private fun <T> assertErr(result: CalcResult<T>) {
        assertTrue("expected an error but got $result", result is CalcResult.Err)
    }

    // --------------------------------------------------------- parser depth

    @Test(timeout = 5000)
    fun fiveThousandNestedParenthesesAreRejectedByTheParser() {
        val result = CalculusParser.parse(nested())
        assertErr(result)
        assertEquals(ErrorKind.Syntax, (result as CalcResult.Err).error.kind)
    }

    @Test(timeout = 5000)
    fun nestedExpressionsAreRejectedByEveryPublicEntry() {
        val deep = nested()
        assertErr(CalculusParser.parse(deep))
        assertErr(DifferentiationEngine.derivative(deep, "x"))
        assertErr(CalculusEngine.limit(deep, "x", "0", true, true))
        assertErr(CalculusEngine.series(deep, "k", 0L, 10L))
        assertErr(CalculusEngine.taylor(deep, "x", "0", "2"))
        assertErr(CalculusEngine.summation(deep, "k", "1", "10"))
        assertErr(CalculusEngine.product(deep, "k", "1", "10"))
        assertErr(IntegrationEngine.definite(deep, "x", "0", "1"))
        assertErr(EquationEngine.solveRoots(deep, "0", "1"))
    }

    @Test
    fun ordinaryScientificNotationStillParses() {
        val n = (CalculusParser.parse("1e3") as CalcResult.Ok).value
        assertEquals(1000.0, Evaluator.eval(n, emptyMap()).valueOrNull()!!, 1e-12)
    }

    // -------------------------------------------------------- index overflow

    @Test(timeout = 5000)
    fun extremeSumAndProductRangesAreRejectedQuickly() {
        assertErr(CalculusEngine.summation("1", "k", Int.MIN_VALUE.toString(), Int.MAX_VALUE.toString()))
        assertErr(CalculusEngine.product("1", "k", Int.MIN_VALUE.toString(), Int.MAX_VALUE.toString()))
        // Reversed endpoints used to wrap `b - a` into a small positive number.
        assertErr(CalculusEngine.summation("1", "k", Int.MAX_VALUE.toString(), Int.MIN_VALUE.toString()))
        assertErr(CalculusEngine.product("1", "k", Int.MAX_VALUE.toString(), Int.MIN_VALUE.toString()))
    }

    @Test(timeout = 5000)
    fun extremeSeriesIndicesAreHandledWithoutWrapping() {
        assertErr(CalculusEngine.series("k", "k", 10L, 1L))
        assertErr(CalculusEngine.series("0.5^k", "k", 0L, Long.MAX_VALUE))
        // A huge but finite index must not loop; the engine reports it instead.
        val verdict = (CalculusEngine.series("k", "k", Long.MAX_VALUE - 1, Long.MAX_VALUE) as CalcResult.Ok).value.verdict
        assertTrue(verdict.isNotEmpty())
    }

    // ------------------------------------------------------- non-finite leaks

    @Test(timeout = 5000)
    fun nonFiniteLiteralsAndEnvironmentValuesAreRejected() {
        assertErr(CalculusParser.parse("1e400"))
        assertErr(CalculusParser.parse("-1e400"))

        val node = (CalculusParser.parse("x") as CalcResult.Ok).value
        val withInfinity = Evaluator.eval(node, mapOf("x" to Double.POSITIVE_INFINITY))
        assertErr(withInfinity)
        assertEquals(ErrorKind.Overflow, (withInfinity as CalcResult.Err).error.kind)

        val productOverflow = (CalculusParser.parse("1e308*10") as CalcResult.Ok).value
        assertErr(Evaluator.eval(productOverflow, emptyMap()))
    }

    @Test(timeout = 5000)
    fun summationProductAndGeometricSumsNeverPrintInfinity() {
        val sum = CalculusEngine.summation("1e308", "k", "1", "10")
        assertErr(sum)
        assertEquals(ErrorKind.Overflow, (sum as CalcResult.Err).error.kind)

        val product = CalculusEngine.product("10", "k", "1", "400")
        assertErr(product)
        assertEquals(ErrorKind.Overflow, (product as CalcResult.Err).error.kind)

        assertErr(CalculusEngine.series("0.5^k", "k", Long.MIN_VALUE, 0L))
    }

    @Test
    fun nonFiniteIntegralIsAnError() {
        val result = IntegrationEngine.definite("1e308*1e308", "x", "0", "1")
        assertErr(result)
        val text = when (result) {
            is CalcResult.Err -> result.error.message
            is CalcResult.Ok -> result.value.value
        }
        assertTrue("must not display an infinity: $text", !text.contains("∞"))
    }

    // -------------------------------------------------------- NaN sentinel

    @Test
    fun unsupportedSymbolicDerivativeReturnsAnErrorNotNaN() {
        val result = DifferentiationEngine.derivative("sign(x)", "x")
        assertErr(result)
        assertEquals(ErrorKind.Domain, (result as CalcResult.Err).error.kind)
        assertTrue(result.error.message.contains("sign"))
    }
}
