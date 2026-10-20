package com.calcsuite.engine.advanced

import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/**
 * Tests for the complex analysis: the evaluation of complex functions, the
 * Cauchy–Riemann check, the contour integrals and the residues.
 */
class ComplexAnalysisTest {

    private fun parse(text: String) =
        (CalculusParser.parse(text) as CalcResult.Ok).value

    @Test fun aComplexFunctionIsEvaluated() {
        // z² at 1 + i is 2i.
        val value = ComplexAnalysis.evaluate(parse("z^2"), "z", ComplexNum(1.0, 1.0))
        assertNotNull(value)
        assertEquals(0.0, value!!.re, 1e-12)
        assertEquals(2.0, value.im, 1e-12)
    }

    @Test fun theCauchyRiemannEquationsHoldForAFunctionThatIsHolomorphic() {
        // z² is holomorphic everywhere, so the residual is zero.
        val check = ComplexAnalysis.cauchyRiemann(parse("z^2"), "z", ComplexNum(1.0, 1.0))
        assertNotNull(check)
        assertTrue("the residual should be small, but it was ${check!!.residual}", check.residual < 1e-6)
        assertTrue("the check should pass", check.holds)
    }

    @Test fun theCauchyRiemannEquationsFailForAFunctionThatIsNotHolomorphic() {
        // The modulus of z is not holomorphic, so the residual is not zero.
        val check = ComplexAnalysis.cauchyRiemann(parse("abs(z)"), "z", ComplexNum(1.0, 1.0))
        assertNotNull(check)
        assertTrue(
            "the residual should not be zero, but it was ${check!!.residual}",
            check.residual > 1e-3,
        )
        assertTrue("the check should fail", !check.holds)
    }

    @Test fun theContourIntegralOfOneOverZIsTwoPiI() {
        // The integral of 1/z around the unit circle, once, is 2πi.
        val contour = ComplexAnalysis.Contour(
            x = parse("cos(t)"),
            y = parse("sin(t)"),
            from = 0.0,
            to = 2 * PI,
            description = "the unit circle",
        )
        val value = ComplexAnalysis.contourIntegral(parse("1/z"), "z", contour)
        assertNotNull(value)
        assertEquals(0.0, value!!.re, 1e-6)
        assertEquals(2 * PI, value.im, 1e-6)
    }

    @Test fun theContourIntegralOfAPolynomialIsZero() {
        // The integral of a function with no pole inside is zero.
        val contour = ComplexAnalysis.Contour(
            x = parse("cos(t)"),
            y = parse("sin(t)"),
            from = 0.0,
            to = 2 * PI,
            description = "the unit circle",
        )
        val value = ComplexAnalysis.contourIntegral(parse("z"), "z", contour)
        assertNotNull(value)
        assertEquals(0.0, value!!.re, 1e-6)
        assertEquals(0.0, value.im, 1e-6)
    }

    @Test fun aSimplePoleIsFoundWithItsResidue() {
        val result = ComplexAnalysis.singularity(parse("1/z"), "z", ComplexNum(0.0, 0.0))
        assertEquals(ComplexAnalysis.Singularity.SIMPLE_POLE, result.classification)
        assertEquals(1, result.order)
        assertNotNull(result.residue)
        assertEquals(1.0, result.residue!!.re, 1e-6)
        assertEquals(0.0, result.residue.im, 1e-6)
    }

    @Test fun aPoleOfHigherOrderIsFound() {
        // 1/z² has a pole of order two, and its residue is zero.
        val result = ComplexAnalysis.singularity(parse("1/z^2"), "z", ComplexNum(0.0, 0.0))
        assertEquals(ComplexAnalysis.Singularity.POLE, result.classification)
        assertEquals(2, result.order)
    }

    @Test fun aRegularPointIsRecognised() {
        val result = ComplexAnalysis.singularity(parse("z"), "z", ComplexNum(1.0, 1.0))
        assertEquals(ComplexAnalysis.Singularity.REGULAR, result.classification)
    }

    @Test fun theResidueTheoremGivesTwoPiITimesTheSum() {
        val residues = listOf(ComplexNum(0.0, 0.0) to ComplexNum(1.0, 0.0))
        val value = ComplexAnalysis.residueTheorem(residues)
        assertEquals(0.0, value.re, 1e-12)
        assertEquals(2 * PI, value.im, 1e-12)
    }

    @Test fun theComplexEvaluatorHandlesTheFunctions() {
        val environment = mapOf("z" to ComplexNum(0.5, 0.25))
        val value = ComplexEvaluator.eval(parse("exp(z)"), environment)
        assertNotNull(value)
        // e^(0.5 + 0.25i) = e^0.5·(cos 0.25 + i·sin 0.25).
        assertEquals(Math.exp(0.5) * Math.cos(0.25), value!!.re, 1e-9)
        assertEquals(Math.exp(0.5) * Math.sin(0.25), value.im, 1e-9)
    }

    @Test fun theInverseSinesTakeThePrincipalValues() {
        // The arcsine of a real number is real.
        val value = ComplexEvaluator.eval(parse("asin(z)"), mapOf("z" to ComplexNum(0.5, 0.0)))
        assertNotNull(value)
        assertEquals(Math.asin(0.5), value!!.re, 1e-9)
        assertEquals(0.0, value.im, 1e-9)

        // The arccosine of a real number is real as well.
        val other = ComplexEvaluator.eval(parse("acos(z)"), mapOf("z" to ComplexNum(0.5, 0.0)))
        assertNotNull(other)
        assertEquals(Math.acos(0.5), other!!.re, 1e-9)
        assertEquals(0.0, other.im, 1e-9)
    }

    @Test fun theArctangentMatchesTheRealOne() {
        val value = ComplexEvaluator.eval(parse("atan(z)"), mapOf("z" to ComplexNum(0.3, 0.0)))
        assertNotNull(value)
        assertEquals(Math.atan(0.3), value!!.re, 1e-9)
        assertEquals(0.0, value.im, 1e-9)
    }

    @Test fun theCubeRootIsThePrincipalOne() {
        val value = ComplexEvaluator.eval(parse("cbrt(z)"), mapOf("z" to ComplexNum(8.0, 0.0)))
        assertNotNull(value)
        assertEquals(2.0, value!!.re, 1e-9)
        assertEquals(0.0, value.im, 1e-9)
    }
}
