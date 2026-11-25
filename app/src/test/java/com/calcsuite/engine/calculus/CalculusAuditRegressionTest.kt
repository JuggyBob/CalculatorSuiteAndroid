package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regressions found while auditing the calculus engine. */
class CalculusAuditRegressionTest {

    private fun <T> okValue(result: CalcResult<T>): T {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok<T>).value
    }

    private fun nodeOf(expr: String): Node = okValue(CalculusParser.parse(expr))

    private fun valueOf(expr: String, env: Map<String, Double> = emptyMap()): Double =
        okValue(Evaluator.eval(nodeOf(expr), env))

    private fun errorKindOf(expr: String): ErrorKind? =
        (Evaluator.eval(nodeOf(expr), emptyMap()) as? CalcResult.Err)?.error?.kind

    // ----------------------------------------------------------- integration

    @Test
    fun singularityDiscoveredDuringRefinementIsAnError() {
        // 1/(x − 0.25) is defined at the three initial samples 0, 0.5 and 1,
        // but the first refinement evaluates the midpoint 0.25. That must be
        // reported as a singularity, not replaced by the coarse Simpson value.
        val result = IntegrationEngine.definite("1/(x - 0.25)", "x", "0", "1")
        assertTrue("expected an error but got $result", result is CalcResult.Err)
        assertEquals(ErrorKind.Domain, (result as CalcResult.Err).error.kind)
    }

    @Test
    fun unconvergedIntegralIsMarkedAsAnEstimate() {
        // A zero refinement limit cannot meet the error target, even though
        // Simpson's rule happens to be exact for a linear integrand.
        val estimate = okValue(IntegrationEngine.definite("x", "x", "0", "1", 0))
        assertTrue("method was ${estimate.method}", estimate.method.contains("not fully converged"))
        assertTrue(
            "steps were ${estimate.steps}",
            estimate.steps.any { it.contains("depth limit") && it.contains("estimate only") },
        )
        assertEquals(0.5, estimate.value.toDouble(), 1e-12)

        val normal = okValue(IntegrationEngine.definite("x", "x", "0", "1"))
        assertFalse(normal.method.contains("not fully converged"))
    }

    // ----------------------------------------------------------- evaluation

    @Test
    fun logarithmsAndExponentialsRejectNonFiniteValues() {
        assertEquals(ErrorKind.Domain, errorKindOf("ln(0)"))
        assertEquals(ErrorKind.Domain, errorKindOf("ln(-1)"))
        assertEquals(ErrorKind.Domain, errorKindOf("log(10, 1)"))
        assertEquals(ErrorKind.Domain, errorKindOf("log(-5)"))
        assertEquals(ErrorKind.Overflow, errorKindOf("exp(1000)"))
        assertEquals(ErrorKind.Overflow, errorKindOf("10^400"))
        assertEquals(Math.E, valueOf("exp(1)"), 1e-12)
        assertEquals(1.0, valueOf("ln(e)"), 1e-12)
    }

    @Test
    fun tangentPolesAndInverseFunctionDomainsAreRejected() {
        assertEquals(ErrorKind.Domain, errorKindOf("tan(pi/2)"))
        assertEquals(ErrorKind.Domain, errorKindOf("sec(pi/2)"))
        assertEquals(ErrorKind.Domain, errorKindOf("asin(2)"))
        assertEquals(ErrorKind.Domain, errorKindOf("sqrt(-1)"))
        assertEquals(0.0, valueOf("tan(0)"), 1e-12)
    }

    @Test
    fun oddRootOfANegativeValueHasARealResult() {
        assertEquals(-2.0, valueOf("root(-8, 3)"), 1e-12)
        assertEquals(ErrorKind.Domain, errorKindOf("root(-8, 2)"))
    }

    // ------------------------------------------------------- limits & series

    @Test
    fun exponentialLimitAtInfinityIsStillReportedAsInfinity() {
        assertEquals(
            "∞",
            okValue(CalculusEngine.limit("e^x", "x", "Infinity", true, true)).value,
        )
        assertEquals(
            "0",
            okValue(CalculusEngine.limit("1/x", "x", "Infinity", true, true)).value,
        )
    }

    @Test
    fun geometricVerdictIsReturnedWithoutARedundantNullCheck() {
        val convergent = okValue(CalculusEngine.series("0.5^k", "k", 0L, 40L))
        assertEquals("Converges", convergent.verdict)
        val divergent = okValue(CalculusEngine.series("2^k", "k", 0L, 10L))
        assertTrue(divergent.verdict.contains("Diverges"))
    }

    @Test
    fun pSeriesUsesTheExponentRatherThanTwoSampledTerms() {
        val harmonic = okValue(CalculusEngine.series("k^-1", "k", 1L, 40L))
        assertTrue(
            "the harmonic series must diverge: ${harmonic.verdict}",
            harmonic.verdict.contains("Diverges"),
        )
        val squared = okValue(CalculusEngine.series("k^-2", "k", 1L, 40L))
        assertTrue(
            "a p-series with p = 2 converges: ${squared.verdict}",
            squared.verdict.contains("Converges"),
        )
    }

    @Test
    fun rootStepsDescribeTheBisectionThatActuallyRuns() {
        val steps = okValue(EquationEngine.solveRoots("x^2 - 4", "-5", "5")).steps.joinToString(" ")
        assertTrue(steps.contains("bisection"))
        assertFalse("no Newton polish is performed", steps.contains("Newton"))
    }
}
