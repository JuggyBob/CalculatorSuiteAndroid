package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.core.Poly
import com.calcsuite.core.Rat
import com.calcsuite.engine.calculus.CalculusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI

/**
 * Regressions found while auditing the complex-number engines.
 */
class AdvancedComplexRegressionTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    private fun poly(vararg coefficients: Double): Poly = Poly.of(
        coefficients.map { Rat.approximate(it) },
    )

    @Test fun theComplexLogMatchesTheRealConventions() {
        val common = ComplexEvaluator.eval(parse("log(100)"), emptyMap())
        assertNotNull(common)
        assertEquals(2.0, common!!.re, 1e-9)
        assertEquals(0.0, common.im, 1e-9)

        val withBase = ComplexEvaluator.eval(parse("log(8,2)"), emptyMap())
        assertNotNull(withBase)
        assertEquals(3.0, withBase!!.re, 1e-9)
        assertEquals(0.0, withBase.im, 1e-9)
    }

    @Test fun log2UsesTheNaturalLogarithm() {
        val value = ComplexEvaluator.eval(parse("log2(z)"), mapOf("z" to ComplexNum(8.0, 0.0)))
        assertNotNull(value)
        assertEquals(3.0, value!!.re, 1e-9)
        assertEquals(0.0, value.im, 1e-9)
    }

    @Test fun aContourIntegralNeedsAnEvenNumberOfSteps() {
        val contour = ComplexAnalysis.Contour(
            x = parse("cos(t)"),
            y = parse("sin(t)"),
            from = 0.0,
            to = 2 * PI,
            description = "the unit circle",
        )
        assertNull(ComplexAnalysis.contourIntegral(parse("1/z"), "z", contour, samples = 1999))
        assertNull(ComplexAnalysis.contourIntegral(parse("1/z"), "z", contour, samples = 0))
        val value = ComplexAnalysis.contourIntegral(parse("1/z"), "z", contour, samples = 200)
        assertNotNull(value)
        assertEquals(0.0, value!!.re, 1e-6)
        assertEquals(2 * PI, value.im, 1e-6)
    }

    @Test fun theResidueOfAThirdOrderPoleIsFound() {
        // 1/z³ + 2/z² + 3/z has order three and residue three.
        val result = ComplexAnalysis.singularity(
            parse("1/z^3 + 2/z^2 + 3/z"), "z", ComplexNum.ZERO,
        )
        assertEquals(ComplexAnalysis.Singularity.POLE, result.classification)
        assertEquals(3, result.order)
        assertNotNull(result.residue)
        assertEquals(3.0, result.residue!!.re, 1e-3)
        assertEquals(0.0, result.residue.im, 1e-3)
    }

    @Test fun theResidueOfAFourthOrderPoleIsFound() {
        val result = ComplexAnalysis.singularity(parse("1/z^4 + 7/z"), "z", ComplexNum.ZERO)
        assertEquals(4, result.order)
        assertNotNull(result.residue)
        assertEquals(7.0, result.residue!!.re, 1e-3)
        assertEquals(0.0, result.residue.im, 1e-3)
    }

    @Test fun aShortIterationStillPolishesToTheRoots() {
        val roots = PolyRoots.numericalRoots(poly(-1.0, -1.0, 0.0, 1.0), iterations = 1)
        assertEquals(3, roots.size)
        for (root in roots) {
            val value = root.pow(3) - root - ComplexNum.ONE
            assertEquals(0.0, value.re, 1e-6)
            assertEquals(0.0, value.im, 1e-6)
        }
    }

    @Test fun aQuadraticKeepsItsSmallRoot() {
        // x² + 10⁸x + 1 has roots −10⁸ and −10⁻⁸. The textbook formula
        // subtracts two nearly equal numbers and loses the small one.
        val roots = PolyRoots.complexRoots(poly(1.0, 1e8, 1.0))
        assertEquals(2, roots.size)
        val values = roots.map { it.re }.sorted()
        assertEquals(-1e8, values[0], 1.0)
        assertEquals(-1e-8, values[1], 1e-12)
    }
}
