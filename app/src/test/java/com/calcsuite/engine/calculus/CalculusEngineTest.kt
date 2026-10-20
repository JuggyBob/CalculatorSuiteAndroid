package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CalculusEngineTest {

    private fun <T> val_(r: CalcResult<T>): T {
        assertTrue("Expected success but was $r", r is CalcResult.Ok)
        return (r as CalcResult.Ok).value
    }

    private fun nodeOf(expr: String): Node = val_(CalculusParser.parse(expr))

    /** Evaluates a derivative expression at a point for a numeric check. */
    private fun derivativeAt(expr: String, varName: String, at: Double): Double {
        val d = val_(DifferentiationEngine.derivative(expr, varName)).derivative
        val node = val_(CalculusParser.parse(d))
        return val_(Evaluator.eval(node, mapOf(varName to at)))
    }

    // --------------------------------------------------------------- parsing

    @Test fun parsesArithmeticWithCorrectPrecedence() {
        val n = val_(CalculusParser.parse("2 + 3 * 4"))
        assertEquals(14.0, Evaluator.eval(n, emptyMap()).let { (it as CalcResult.Ok).value }, 1e-12)
    }

    @Test fun parsesImplicitMultiplication() {
        val a = val_(CalculusParser.parse("2x"))
        val b = val_(CalculusParser.parse("3(4+5)"))
        val c = val_(CalculusParser.parse("2pi"))
        val d = val_(CalculusParser.parse("2sin(0)"))
        assertEquals(10.0, (Evaluator.eval(a, mapOf("x" to 5.0)) as CalcResult.Ok).value, 1e-12)
        assertEquals(27.0, (Evaluator.eval(b, emptyMap()) as CalcResult.Ok).value, 1e-12)
        assertEquals(2 * Math.PI, (Evaluator.eval(c, emptyMap()) as CalcResult.Ok).value, 1e-12)
        assertEquals(0.0, (Evaluator.eval(d, emptyMap()) as CalcResult.Ok).value, 1e-12)
    }

    @Test fun parsesPowerAsRightAssociative() {
        val n = val_(CalculusParser.parse("2^3^2"))
        assertEquals(512.0, Evaluator.eval(n, emptyMap()).let { (it as CalcResult.Ok).value }, 1e-9)
    }

    @Test fun parsesConstants() {
        val n = val_(CalculusParser.parse("2pi"))
        assertEquals(2 * Math.PI, Evaluator.eval(n, emptyMap()).let { (it as CalcResult.Ok).value }, 1e-12)
    }

    @Test fun rejectsGarbage() {
        assertTrue(CalculusParser.parse("2 +") is CalcResult.Err)
        assertTrue(CalculusParser.parse("(2 + 3") is CalcResult.Err)
        assertTrue(CalculusParser.parse("bogus(3)") is CalcResult.Err)
    }

    @Test fun formatsNumbersWithoutTruncatingDecimals() {
        // Regression: an integer test that was always true made every value go
        // through toLong(), so 0.5 printed as "0" and 1.9999 printed as "1".
        assertEquals("0.5", NodePrinter.format(0.5))
        assertEquals("1.9999", NodePrinter.format(1.9999))
        assertEquals("2", NodePrinter.format(2.0))
        assertEquals("-4", NodePrinter.format(-4.0))
        assertEquals("-1.25", NodePrinter.format(-1.25))
        assertEquals("0.1", NodePrinter.format(0.1))
        assertEquals("undefined", NodePrinter.format(Double.NaN))
        assertEquals("∞", NodePrinter.format(Double.POSITIVE_INFINITY))
    }

    @Test fun iteratedOperationsUseTheIndexPresentInTheExpression() {
        // Summing `k` while the screen's variable is `x` must still work: the
        // index is taken from the expression, not from the differentiation
        // variable.
        assertEquals(55.0, val_(CalculusEngine.summation("k", "x", "1", "10")).verdict.toDouble(), 1e-9)
        assertEquals(120.0, val_(CalculusEngine.product("k", "x", "1", "5")).verdict.toDouble(), 1e-9)
        // 0.5^k summed over k, again with x as the surrounding variable.
        assertEquals(
            2.0 - 1.0 / 1048576.0,
            val_(CalculusEngine.summation("0.5^k", "x", "0", "20")).verdict.toDouble(),
            1e-9,
        )
        assertEquals("Converges", val_(CalculusEngine.series("0.5^k", "x", 1L, 40L)).verdict)
    }

    @Test fun geometricSeriesOnlyAppliesToTheIndexVariable() {
        // x^2 raised on x is not a geometric series in x, so it must not be
        // reported as one.
        val r = val_(CalculusEngine.series("x^2", "x", 0L, 10L))
        assertTrue("x^2 is not geometric: ${r.verdict}", r.verdict.contains("Diverges"))
    }

    @Test fun partialSumKeepsItsDecimalPlaces() {
        // ∑ 0.5^k from 0 to 20 is 1.9999990463…, and must not display as "1".
        val text = val_(CalculusEngine.summation("0.5^k", "x", "0", "20")).verdict
        assertEquals("1.9999990463", text)
    }

    @Test fun printedExpressionsRoundTripThroughTheParser() {
        // The engines print expressions and then re-parse them (extrema scans
        // f', the series test re-evaluates a geometric term), so printing must
        // not change the meaning.
        val env = mapOf("x" to 1.7, "y" to 2.3)
        for (src in listOf(
            "x^2 - 4*x", "4*x - x^2", "x^2*y^3", "x^3", "x*x^2", "1/x",
            "tan(x)", "sin(x^2)", "cos(x)/x", "ln(x)", "e^x", "asin(x)",
            "acos(x)", "atan(x)", "1/(x^2 + 1)", "(x + 1)/(x - 2)", "-x^3 + 2",
            "sqrt(x^2 + 1)", "(x + 1)^3", "1 - 1/x", "x/(x^2 + 1)^2",
            // A negative factor in a product has to be bracketed, or the
            // printed form would be read as a difference.
            "x*-2", "-3*x^2", "(-x)^3", "2*(-x)*(-1)", "-x^2", "-x^2 + 2",
        )) {
            val node = val_(CalculusParser.parse(src))
            val printed = NodePrinter.print(node)
            val reparsed = CalculusParser.parse(printed)
            assertTrue("printed form did not parse: $src -> $printed", reparsed is CalcResult.Ok)
            val a = val_(Evaluator.eval(node, env))
            val b = val_(Evaluator.eval((reparsed as CalcResult.Ok).value, env))
            assertEquals("$src printed as $printed", a, b, 1e-12)
        }
    }

    // --------------------------------------------------------- differentiation

    // Derivatives are verified numerically against the analytic value at a
    // point, which tests the mathematics rather than the exact formatting.

    @Test fun differentiatesPolynomials() {
        assertEquals(2 * 3.0, derivativeAt("x^2", "x", 3.0), 1e-9)
        assertEquals(3 * 4.0, derivativeAt("x^3", "x", 2.0), 1e-9)
    }

    @Test fun appliesProductRule() {
        // d/dx (x · x^2) = d/dx x^3 = 3x^2
        assertEquals(3 * 9.0, derivativeAt("x*x^2", "x", 3.0), 1e-9)
    }

    @Test fun appliesQuotientRule() {
        // d/dx (1/x) = -1/x^2
        assertEquals(-1.0 / 9.0, derivativeAt("1/x", "x", 3.0), 1e-9)
    }

    @Test fun appliesChainRule() {
        // d/dx sin(x^2) = cos(x^2) * 2x
        val expected = Math.cos(4.0) * 2 * 2.0
        assertEquals(expected, derivativeAt("sin(x^2)", "x", 2.0), 1e-9)
    }

    @Test fun differentiatesLogarithm() {
        assertEquals(1.0 / 4.0, derivativeAt("ln(x)", "x", 4.0), 1e-9)
    }

    @Test fun differentiatesExponential() {
        assertEquals(Math.exp(1.5), derivativeAt("e^x", "x", 1.5), 1e-9)
    }

    @Test fun differentiatesTangentAndInverseTrig() {
        // sec^2 = 1/cos^2
        assertEquals(1.0 / (Math.cos(0.4) * Math.cos(0.4)), derivativeAt("tan(x)", "x", 0.4), 1e-9)
        assertEquals(1.0 / Math.sqrt(1 - 0.16), derivativeAt("asin(x)", "x", 0.4), 1e-9)
        assertEquals(-1.0 / Math.sqrt(1 - 0.36), derivativeAt("acos(x)", "x", 0.6), 1e-9)
        assertEquals(1.0 / (1 + 0.25), derivativeAt("atan(x)", "x", 0.5), 1e-9)
    }

    @Test fun differentiatesNestedAndHyperbolicFunctions() {
        assertEquals(Math.cosh(0.8), derivativeAt("sinh(x)", "x", 0.8), 1e-9)
        assertEquals(Math.sinh(0.6), derivativeAt("cosh(x)", "x", 0.6), 1e-9)
        // chain rule through a composite: d/dx exp(3x) = 3e^(3x)
        assertEquals(3 * Math.exp(3 * 0.5), derivativeAt("exp(3*x)", "x", 0.5), 1e-9)
    }

    @Test fun partialDerivativeWithRespectToOneVariable() {
        // d/dx (x*y) = y and d/dy (x*y) = x
        val dx = val_(DifferentiationEngine.derivative("x*y", "x")).derivative
        assertEquals("y", dx)
        val dy = val_(DifferentiationEngine.derivative("x*y", "y")).derivative
        assertEquals("x", dy)
    }

    @Test fun partialDerivativeOfMultivariableFunction() {
        // f(x, y) = x^2 · y^3  ->  df/dx = 2x·y^3 and df/dy = 3x^2·y^2
        val env = mapOf("x" to 2.0, "y" to 3.0)

        val dxText = val_(DifferentiationEngine.derivative("x^2*y^3", "x")).derivative
        val dxVal = val_(Evaluator.eval(val_(CalculusParser.parse(dxText)), env))
        assertEquals("df/dx = 2·2·27 at (x=2, y=3)", 108.0, dxVal, 1e-9)

        val dyText = val_(DifferentiationEngine.derivative("x^2*y^3", "y")).derivative
        val dyVal = val_(Evaluator.eval(val_(CalculusParser.parse(dyText)), env))
        assertEquals("df/dy = 3·4·9 at (x=2, y=3)", 108.0, dyVal, 1e-9)
    }

    @Test fun numericalDerivativeMatchesAnalytic() {
        val node = val_(CalculusParser.parse("x^3"))
        val numeric = val_(DifferentiationEngine.numericalDerivative(node, "x", 2.0))
        assertEquals(12.0, numeric, 1e-4) // 3x^2 at x=2
    }

    // -------------------------------------------------------------- integration

    @Test fun integratesPolynomialsExactly() {
        val r = val_(IntegrationEngine.indefinite("x^2", "x"))
        assertTrue(r.exact)
        assertTrue(r.value.contains("x^3"))
    }

    @Test fun integratesOneOverX() {
        val r = val_(IntegrationEngine.indefinite("1/x", "x"))
        assertTrue(r.exact)
        assertTrue(r.value.contains("ln"))
    }

    @Test fun integratesKnownPolynomialNumerically() {
        // ∫0..3 x^2 dx = 9
        val r = val_(IntegrationEngine.definite("x^2", "x", "0", "3"))
        assertEquals(9.0, r.value.toDouble(), 1e-6)
    }

    @Test fun integratesTrigonometricFunctionNumerically() {
        // ∫0..π sin(x) dx = 2
        val r = val_(IntegrationEngine.definite("sin(x)", "x", "0", "3.141592653589793"))
        assertEquals(2.0, r.value.toDouble(), 1e-6)
    }

    @Test fun definiteIntegralHandlesReversedLimits() {
        val forward = val_(IntegrationEngine.definite("x^2", "x", "0", "3"))
        val reverse = val_(IntegrationEngine.definite("x^2", "x", "3", "0"))
        assertEquals(-forward.value.toDouble(), reverse.value.toDouble(), 1e-6)
    }

    // ------------------------------------------------------------------- limits

    @Test fun limitByDirectSubstitution() {
        assertEquals("2", val_(CalculusEngine.limit("x + 1", "x", "1", true, true)).value)
    }

    @Test fun limitOfIndeterminateFormIsComputedNumerically() {
        // lim(x→0) (x^2 − 1)/(x − 1) = 1  (removable discontinuity)
        val r = val_(CalculusEngine.limit("(x^2 - 1)/(x - 1)", "x", "0", true, true))
        assertEquals(1.0, r.value.toDouble(), 1e-3)
    }

    @Test fun limitAtInfinity() {
        val r = val_(CalculusEngine.limit("1/x", "x", "Infinity", true, true))
        assertEquals("0", r.value)
    }

    // ------------------------------------------------------------------ series

    @Test fun geometricSeriesConvergesWhenRatioBelowOne() {
        val r = val_(CalculusEngine.series("0.5^k", "k", 0L, 40L))
        assertEquals("Converges", r.verdict)
    }

    @Test fun geometricSeriesDivergesWhenRatioAtLeastOne() {
        val r = val_(CalculusEngine.series("2^k", "k", 0L, 10L))
        assertTrue(r.verdict.contains("Diverges"))
    }

    @Test fun taylorSeriesProducesCoefficients() {
        // e^x at 0: f(0)=1, f'(0)=1, f''(0)/2=0.5
        val r = val_(CalculusEngine.taylor("e^x", "x", "0", "3"))
        assertTrue(r.steps.any { it.contains("f(a) = 1") })
        assertTrue(r.steps.any { it.contains("1") })
    }

    // ---------------------------------------------- reported calculus bugs

    /**
     * Regression: `x^3-5/2^1/10` was reported as having an undefined 4th
     * derivative at x=0. The parser builds it as x^3 - ((5 / 2^1) / 10), and
     * differentiating that constant through the quotient rule leaves a
     * removable singularity that evaluates as undefined. Simplifying each
     * derivative collapses it to exactly 0, which is the true answer.
     */
    @Test fun taylorOfPolynomialWithFractionalConstantHasNoUndefinedDerivatives() {
        val r = val_(CalculusEngine.taylor("x^3-5/2^1/10", "x", "0", "6"))
        assertFalse(
            "the 4th derivative must not be reported as undefined: ${r.steps}",
            r.steps.any { it.contains("undefined") },
        )
        assertTrue("f(0) = -0.25", r.steps.any { it.contains("f(a) = -0.25") })
        // x^3 has coefficient 1 at degree 3; everything above degree 3 is zero.
        assertTrue("x^3 coefficient is 1", r.steps.any { it.contains("(f^3 / 3!) = 1") })
        for (k in 4..6) {
            assertTrue(
                "the $k-th derivative must be reported as 0",
                r.steps.any { it.contains("(f^$k / $k!) = 0") },
            )
        }
    }

    @Test fun derivativesOfAConstantExpressionAreExactlyZero() {
        // The underlying failure: (5/2^1)/10 is a constant, so its derivative
        // is 0 — not undefined.
        val d = val_(DifferentiationEngine.derivative("5/2^1/10", "x"))
        assertEquals("0", d.derivative)
        assertEquals(0.25, Evaluator.eval(nodeOf("5/2^1/10"), mapOf("x" to 0.0)).valueOrNull()!!, 1e-12)
        assertEquals(0.0, Evaluator.eval(nodeOf(d.derivative), mapOf("x" to 0.0)).valueOrNull()!!, 1e-12)
    }

    @Test fun higherDerivativesOfAPolynomialAreExactlyZero() {
        // x^5 - 1/4: the 5th derivative is 120, the 6th is exactly 0.
        var current: Node = nodeOf("x^5 - 1/4")
        repeat(5) {
            current = (DifferentiationEngine.differentiate(current, "x") as CalcResult.Ok).value
        }
        assertEquals("the 5th derivative of x^5 is 120", 120.0,
            (current as? Node.Num)?.value ?: Double.NaN, 1e-9)
        val sixth = (DifferentiationEngine.differentiate(current, "x") as CalcResult.Ok).value
        assertTrue("6th derivative of a quintic is 0, got ${NodePrinter.print(sixth)}",
            sixth is Node.Num && sixth.value == 0.0)
    }

    @Test fun indefiniteIntegralOfPolynomialWithFractionalConstant() {
        // Regression: x^3 - (5/2^1)/10 has an elementary antiderivative, but the
        // integrator only handled Add, not Sub.
        val r = val_(IntegrationEngine.indefinite("x^3-5/2^1/10", "x"))
        assertTrue("must be exact: ${r.value}", r.exact)
        // The minus sign the printer emits is U+2212, not a hyphen.
        assertEquals("x^4 ÷ 4 − 0.25x + C", r.value)
    }

    @Test fun indefiniteIntegralHandlesConstantTimesPower() {
        // 3x^2 integrates to x^3, not "3(x^3 ÷ 3)".
        val r = val_(IntegrationEngine.indefinite("3x^2", "x"))
        assertTrue(r.exact)
        assertEquals("x^3 + C", r.value)
        // 0.5x^3 -> x^4 ÷ 8
        val r2 = val_(IntegrationEngine.indefinite("0.5x^3", "x"))
        assertTrue(r2.exact)
        assertEquals("x^4 ÷ 8 + C", r2.value)
    }

    @Test fun indefiniteIntegralHandlesDifferencesOfPolynomials() {
        val r = val_(IntegrationEngine.indefinite("x^3 - 1/4", "x"))
        assertTrue(r.exact)
        // x^3/3 - x/4 + C, up to how the printer writes the coefficients.
        assertTrue("unexpected value ${r.value}", r.value.contains("x^4"))
        assertTrue("unexpected value ${r.value}", r.value.contains("x"))
        assertTrue(r.value.endsWith("+ C"))
    }

    @Test fun differentiatingAnAntiderivativeRecoversTheOriginal() {
        // The definitive check on the symbolic integrator: F'(x) must equal f(x).
        val cases = listOf(
            "x^3-5/2^1/10",
            "x^3 - 1/4",
            "3x^2 - 2x + 1",
            // Written as 0.5x^3 rather than 1/2x^3: the parser binds "/" tighter
            // than implicit multiplication, so 1/2x^3 is 1/(2x^3).
            "0.5x^3 - (1/3)x",
            "x^4 - 2x^2 + 1",
            "7/3",
            "2x^5 - x^3",
            "x^2 - 3x + 2",
            "-4x + 1/2",
            "6",
            "5x^4 + 2x",
        )
        for (expr in cases) {
            val f = nodeOf(expr)
            val anti = IntegrationEngine.antiderivative(f, "x")
            assertNotNull("no antiderivative for $expr", anti)
            val dAnti = (DifferentiationEngine.differentiate(anti!!, "x") as CalcResult.Ok).value
            for (x in listOf(-2.0, -0.5, 0.0, 0.5, 1.0, 2.0)) {
                val original = Evaluator.eval(f, mapOf("x" to x)).valueOrNull()
                val roundTrip = Evaluator.eval(dAnti, mapOf("x" to x)).valueOrNull()
                assertNotNull("F' undefined at x=$x for $expr", roundTrip)
                assertEquals("F'(x) != f(x) for $expr at x=$x", original!!, roundTrip!!, 1e-9)
            }
        }
    }

    @Test fun genuinelyUndefinedDerivativesAreStillReported() {
        // The fix must not mask a real singularity. 1/x is undefined at x=0, so
        // the expansion must fail rather than invent coefficients.
        val r = CalculusEngine.taylor("1/x", "x", "0", "3")
        assertTrue(
            "1/x must not produce a Maclaurin series at 0, got $r",
            r is CalcResult.Err || (r as CalcResult.Ok).value.steps.any { it.contains("undefined") },
        )
    }

    // ------------------------------------------------------------ sum/product

    @Test fun summationOfIntegers() {
        // ∑1..10 k = 55
        val r = val_(CalculusEngine.summation("k", "k", "1", "10"))
        assertEquals(55.0, r.verdict.toDouble(), 1e-9)
    }

    @Test fun productFactorial() {
        // ∏1..5 k = 120
        val r = val_(CalculusEngine.product("k", "k", "1", "5"))
        assertEquals(120.0, r.verdict.toDouble(), 1e-9)
    }

    // ---------------------------------------------------------- roots/extrema

    @Test fun findsRootsOfQuadratic() {
        // x^2 - 4 = 0  ->  -2 and 2
        val r = val_(EquationEngine.solveRoots("x^2 - 4", "-5", "5"))
        assertEquals(2, r.roots.size)
        assertTrue(r.roots.any { abs(it - 2.0) < 1e-4 })
        assertTrue(r.roots.any { abs(it + 2.0) < 1e-4 })
    }

    @Test fun findsMaximumOfParabola() {
        // f(x) = x^2 − 4x has a minimum at x = 2 (f(2) = −4)
        val r = val_(EquationEngine.extrema("x^2 - 4*x", "-1", "5"))
        assertTrue(
            "expected a minimum at x=2 but got points=${r.points} steps=${r.steps}",
            r.points.any { abs(it.first - 2.0) < 1e-3 && it.third.contains("minimum") },
        )
    }

    @Test fun findsMaximumOfInvertedParabola() {
        // f(x) = 4x − x^2 has a maximum at x = 2 (f(2) = 4)
        val r = val_(EquationEngine.extrema("4*x - x^2", "-1", "5"))
        assertTrue(
            "expected a maximum at x=2 but got points=${r.points}",
            r.points.any { abs(it.first - 2.0) < 1e-3 && it.third.contains("maximum") },
        )
    }

    @Test fun classifiesCubicStationaryPointsCorrectly() {
        // f(x) = x^3 − 3x has f'(x) = 3x^2 − 3, so the stationary points are
        // x = ±1. Since f″(x) = 6x, x = −1 (f″ < 0) is a local maximum and
        // x = 1 (f″ > 0) a local minimum.
        val r = val_(EquationEngine.extrema("x^3 - 3*x", "-5", "5"))
        val at1 = r.points.firstOrNull { abs(it.first - 1.0) < 1e-3 }
        val atMinus1 = r.points.firstOrNull { abs(it.first + 1.0) < 1e-3 }
        assertNotNull("x = 1 should be a stationary point, got ${r.points}", at1)
        assertNotNull("x = -1 should be a stationary point, got ${r.points}", atMinus1)
        assertEquals("x = 1 is a local minimum", "Local minimum", at1!!.third)
        assertEquals("x = -1 is a local maximum", "Local maximum", atMinus1!!.third)
    }

    @Test fun classifiesSineStationaryPointsCorrectly() {
        // sin(x) is at a maximum at π/2 and a minimum at 3π/2.
        val r = val_(EquationEngine.extrema("sin(x)", "0", "6.3"))
        val atPiOver2 = r.points.firstOrNull { abs(it.first - Math.PI / 2) < 1e-3 }
        val at3PiOver2 = r.points.firstOrNull { abs(it.first - 3 * Math.PI / 2) < 1e-3 }
        assertNotNull("expected a maximum at pi/2, got ${r.points}", atPiOver2)
        assertNotNull("expected a minimum at 3pi/2, got ${r.points}", at3PiOver2)
        assertEquals("Local maximum", atPiOver2!!.third)
        assertEquals("Local minimum", at3PiOver2!!.third)
    }

    @Test fun secondDerivativeOfFirstDerivativeIsUsed() {
        // Regression: the second-difference formula on f' measures f''', so
        // every quadratic used to come back as an inflection point.
        val first = val_(CalculusParser.parse("2x - 4"))
        assertEquals(2.0, DifferentiateNumeric.secondDerivative(first, "x", 2.0)!!, 1e-3)
        // f'(x) = cos(x) means f(x) = sin(x), so f'' = −sin(x).
        val sinFirst = val_(CalculusParser.parse("cos(x)"))
        assertEquals(-Math.sin(0.3), DifferentiateNumeric.secondDerivative(sinFirst, "x", 0.3)!!, 1e-3)
    }

    // -------------------------------------------------------------- ODE solve

    @Test fun solvesExponentialDifferentialEquation() {
        // y' = y, y(0) = 1, h = 0.01 × 200 steps → y(2) ≈ e²
        val r = val_(EquationEngine.solveODE("y", "x", "0", "1", "200", "0.01"))
        assertEquals(Math.exp(2.0), r.finalY, 1e-3)
    }

    @Test fun solvesLinearDifferentialEquation() {
        // y' = 1, y(0)=0 -> y(2)=2
        val r = val_(EquationEngine.solveODE("1", "x", "0", "0", "100", "0.02"))
        assertEquals(2.0, r.finalY, 1e-6)
    }
}