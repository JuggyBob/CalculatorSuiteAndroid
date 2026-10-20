package com.calcsuite.engine.calculus.multivariable

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Tests for the multivariable calculus.
 *
 * The answers are checked against values worked out by hand, and the "
 * "numerical results are checked against the exact ones.
 */
class MultivariableEngineTest {

    private fun parse(text: String): Node {
        val result = MultivariableEngine.parse(text, listOf("x", "y", "z", "t"))
        assertTrue("the expression should be read", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun render(node: Node): String = MultivariableEngine.render(node)

    // ------------------------------------------------------- partials

    @Test fun thePartialDerivativeHoldsTheOtherVariableStill() {
        // f = x² + 3xy + y², so ∂f/∂x = 2x + 3y.
        val derivative = MultivariableEngine.partialDerivative(
            parse("x^2 + 3xy + y^2"), "x",
        ).valueOrNull()
        assertNotNull(derivative)
        assertEquals("2x + 3y", render(derivative!!))
    }

    @Test fun theSecondPartialDerivativeIsFound() {
        // ∂²f/∂x² = 2.
        val derivative = MultivariableEngine.partialDerivative(
            parse("x^2 + 3xy + y^2"), "x", 2,
        ).valueOrNull()
        assertNotNull(derivative)
        assertEquals("2", render(derivative!!))
    }

    @Test fun theMixedPartialIsFound() {
        // ∂²f/∂x∂y = 3.
        val mixed = MultivariableEngine.mixedPartial(
            parse("x^2 + 3xy + y^2"), "x", "y",
        ).valueOrNull()
        assertNotNull(mixed)
        assertEquals("3", render(mixed!!))
    }

    @Test fun theGradientCollectsThePartialDerivatives() {
        val gradient = MultivariableEngine.gradient(
            parse("x^2 + 3xy + y^2"), listOf("x", "y"),
        ).valueOrNull()
        assertNotNull(gradient)
        assertEquals(2, gradient!!.size)
        assertEquals("2x + 3y", render(gradient[0]))
        assertEquals("3x + 2y", render(gradient[1]))
    }

    @Test fun theGradientIsEvaluatedAtAPoint() {
        val gradient = MultivariableEngine.gradient(
            parse("x^2 + 3xy + y^2"), listOf("x", "y"),
        ).valueOrNull()!!
        // At (1, 2) the gradient is (8, 7).
        assertEquals(8.0, MultivariableEngine.evaluate(gradient[0], listOf("x", "y"), doubleArrayOf(1.0, 2.0))!!, 1e-12)
        assertEquals(7.0, MultivariableEngine.evaluate(gradient[1], listOf("x", "y"), doubleArrayOf(1.0, 2.0))!!, 1e-12)
    }

    // ------------------------------------------------------ the hessian

    @Test fun theHessianHoldsTheSecondDerivatives() {
        val hessian = MultivariableEngine.hessian(
            parse("x^2 + 3xy + y^2"), listOf("x", "y"),
        ).valueOrNull()!!
        assertEquals("2", render(hessian[0][0]))
        assertEquals("3", render(hessian[0][1]))
        assertEquals("3", render(hessian[1][0]))
        assertEquals("2", render(hessian[1][1]))
    }

    @Test fun theLaplacianAddsTheSecondDerivatives() {
        val laplacian = MultivariableEngine.laplacian(
            parse("x^2 + 3xy + y^2"), listOf("x", "y"),
        ).valueOrNull()
        assertNotNull(laplacian)
        // ∂²f/∂x² + ∂²f/∂y² = 2 + 2 = 4.
        assertEquals(4.0, MultivariableEngine.evaluate(laplacian!!, listOf("x", "y"), doubleArrayOf(0.0, 0.0))!!, 1e-12)
    }

    // ------------------------------------------------- divergence and curl

    @Test fun theDivergenceIsTheSumOfTheDiagonalDerivatives() {
        // F = (x², y²) has divergence 2x + 2y.
        val divergence = MultivariableEngine.divergence(
            listOf(parse("x^2"), parse("y^2")), listOf("x", "y"),
        ).valueOrNull()
        assertNotNull(divergence)
        assertEquals(4.0, MultivariableEngine.evaluate(divergence!!, listOf("x", "y"), doubleArrayOf(1.0, 1.0))!!, 1e-12)
    }

    @Test fun theCurlOfARotationFieldIsTheField() {
        // F = (−y, x) has curl (0, 0, 2).
        val curl = MultivariableEngine.curl(
            listOf(parse("-y"), parse("x"), parse("0")), listOf("x", "y", "z"),
        ).valueOrNull()
        assertNotNull(curl)
        assertEquals(2.0, MultivariableEngine.evaluate(curl!![2], listOf("x", "y", "z"), doubleArrayOf(0.0, 0.0, 0.0))!!, 1e-12)
    }

    @Test fun theCurlNeedsThreeDimensions() {
        val curl = MultivariableEngine.curl(
            listOf(parse("x"), parse("y")), listOf("x", "y"),
        )
        assertTrue("the curl should be refused in two dimensions", curl is CalcResult.Err)
    }

    // ------------------------------------------- directional derivatives

    @Test fun theDirectionalDerivativeIsTheDotProduct() {
        val gradient = MultivariableEngine.gradient(
            parse("x^2 + 3xy + y^2"), listOf("x", "y"),
        ).valueOrNull()!!
        val value = MultivariableEngine.directionalDerivative(
            gradient, listOf("x", "y"), doubleArrayOf(1.0, 2.0), doubleArrayOf(1.0, 0.0), false,
        ).valueOrNull()
        assertNotNull(value)
        // The gradient is (8, 7) and the direction is (1, 0), so the answer is 8.
        assertEquals(8.0, value!!, 1e-12)
    }

    @Test fun theDirectionIsNormalisedWhenAsked() {
        val gradient = MultivariableEngine.gradient(
            parse("x^2 + 3xy + y^2"), listOf("x", "y"),
        ).valueOrNull()!!
        val raw = MultivariableEngine.directionalDerivative(
            gradient, listOf("x", "y"), doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 0.0), false,
        ).valueOrNull()!!
        val normalised = MultivariableEngine.directionalDerivative(
            gradient, listOf("x", "y"), doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 0.0), true,
        ).valueOrNull()!!
        assertEquals(raw / 2, normalised, 1e-12)
    }

    @Test fun aZeroDirectionIsRefused() {
        val gradient = MultivariableEngine.gradient(
            parse("x^2"), listOf("x"),
        ).valueOrNull()!!
        val value = MultivariableEngine.directionalDerivative(
            gradient, listOf("x"), doubleArrayOf(1.0), doubleArrayOf(0.0), true,
        )
        assertTrue("the zero direction should be refused", value is CalcResult.Err)
    }

    // -------------------------------------------------- critical points

    @Test fun aCriticalPointIsFoundAndClassified() {
        // f = x² + 3xy + y² has a saddle at the origin.
        val points = MultivariableEngine.criticalPoints(
            parse("x^2 + 3xy + y^2"), listOf("x", "y"),
            listOf(doubleArrayOf(-5.0, 5.0), doubleArrayOf(-5.0, 5.0)),
        ).valueOrNull()
        assertNotNull(points)
        assertEquals(1, points!!.size)
        val point = points[0]
        assertEquals(0.0, point.point[0], 1e-6)
        assertEquals(0.0, point.point[1], 1e-6)
        assertEquals(PointClass.SADDLE, point.classification)
    }

    @Test fun aMinimumIsClassifiedAsAMinimum() {
        // f = x² + y² has a minimum at the origin.
        val points = MultivariableEngine.criticalPoints(
            parse("x^2 + y^2"), listOf("x", "y"),
            listOf(doubleArrayOf(-3.0, 3.0), doubleArrayOf(-3.0, 3.0)),
        ).valueOrNull()!!
        assertEquals(1, points.size)
        assertEquals(PointClass.MINIMUM, points[0].classification)
    }

    @Test fun anInconclusivePointIsReportedAsSuch() {
        // f = x³ + y³ has a degenerate critical point at the origin.
        val points = MultivariableEngine.criticalPoints(
            parse("x^3 + y^3"), listOf("x", "y"),
            listOf(doubleArrayOf(-3.0, 3.0), doubleArrayOf(-3.0, 3.0)),
        ).valueOrNull()!!
        assertEquals(1, points.size)
        assertEquals(PointClass.INCONCLUSIVE, points[0].classification)
    }

    // ------------------------------------------------------- integration

    @Test fun aDoubleIntegralOverAUnitSquareIsOne() {
        val value = MultivariableEngine.doubleIntegral(
            parse("1"), listOf("x", "y"),
            listOf(doubleArrayOf(0.0, 1.0), doubleArrayOf(0.0, 1.0)),
        ).valueOrNull()
        assertNotNull(value)
        assertEquals(1.0, value!!, 1e-9)
    }

    @Test fun aDoubleIntegralIsTheVolumeUnderTheSurface() {
        // ∫₀²∫₀³ x dy dx = 6.
        val value = MultivariableEngine.doubleIntegral(
            parse("x"), listOf("x", "y"),
            listOf(doubleArrayOf(0.0, 2.0), doubleArrayOf(0.0, 3.0)),
        ).valueOrNull()
        assertNotNull(value)
        assertEquals(6.0, value!!, 1e-9)
    }

    @Test fun aTripleIntegralIsTheVolumeOfTheBox() {
        val value = MultivariableEngine.tripleIntegral(
            parse("1"), listOf("x", "y", "z"),
            listOf(
                doubleArrayOf(0.0, 2.0),
                doubleArrayOf(0.0, 1.0),
                doubleArrayOf(0.0, 3.0),
            ),
        ).valueOrNull()
        assertNotNull(value)
        assertEquals(6.0, value!!, 1e-9)
    }

    @Test fun aLineIntegralAlongTheUnitCircleIsItsLength() {
        val value = MultivariableEngine.lineIntegral(
            parse("1"), listOf("x", "y"),
            listOf(parse("cos(t)"), parse("sin(t)")), 0.0, 2 * Math.PI,
        ).valueOrNull()
        assertNotNull(value)
        assertEquals(2 * Math.PI, value!!, 1e-6)
    }

    @Test fun aLineIntegralOfOneAlongACurveIsItsLength() {
        // The length of the parabola (t, t²) from 0 to 1 is
        // ∫₀¹ √(1 + 4t²) dt = √5/2 + asinh(2)/4.
        val value = MultivariableEngine.lineIntegral(
            parse("1"), listOf("x", "y"),
            listOf(parse("t"), parse("t^2")), 0.0, 1.0,
        ).valueOrNull()
        assertNotNull(value)
        val expected = Math.sqrt(5.0) / 2 + Math.log(2.0 + Math.sqrt(5.0)) / 4
        assertEquals(expected, value!!, 1e-6)
    }

    @Test fun aLineIntegralOfARealFunctionAlongTheCircleIsZero() {
        // ∮ cos(t)·ds over the unit circle is ∫₀^2π cos t dt = 0, because the
        // arc length element is dt there.
        val value = MultivariableEngine.lineIntegral(
            parse("x"), listOf("x", "y"),
            listOf(parse("cos(t)"), parse("sin(t)")), 0.0, 2 * Math.PI,
        ).valueOrNull()
        assertNotNull(value)
        assertEquals(0.0, value!!, 1e-6)
    }

    // ---------------------------------------------------------- operations

    @Test fun theOperationBuildsTheExplanationFromTheWorking() {
        val result = MultivariableOperations.run(
            MultiOperation.GRADIENT,
            parse("x^2 + 3xy + y^2"),
            listOf("x", "y"),
            doubleArrayOf(1.0, 2.0),
            MultiExtras(),
        ).valueOrNull()
        assertNotNull(result)
        assertEquals("(2x + 3y, 3x + 2y)", result!!.values.first())
        assertTrue("the steps should mention the partials", result.explanation.steps.any { it.contains("2x + 3y") })
        assertTrue("the method should be explained", result.explanation.method != null)
        assertTrue("the limits should be stated", result.explanation.limitations != null)
    }

    @Test fun theDirectionalOperationShowsTheDotProduct() {
        val result = MultivariableOperations.run(
            MultiOperation.DIRECTIONAL,
            parse("x^2 + 3xy + y^2"),
            listOf("x", "y"),
            doubleArrayOf(1.0, 2.0),
            MultiExtras(direction = doubleArrayOf(1.0, 0.0), normalise = false),
        ).valueOrNull()
        assertNotNull(result)
        val steps = result!!.explanation.steps.joinToString(" ")
        assertTrue("the dot product should be shown, but the steps were: $steps", steps.contains("·"))
    }
}
