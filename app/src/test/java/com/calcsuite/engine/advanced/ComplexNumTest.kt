package com.calcsuite.engine.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tests for the complex numbers.
 */
class ComplexNumTest {

    @Test fun additionAndSubtractionAreComponentwise() {
        val a = ComplexNum(3.0, 4.0)
        val b = ComplexNum(1.0, 2.0)
        assertEquals(ComplexNum(4.0, 6.0), a + b)
        assertEquals(ComplexNum(2.0, 2.0), a - b)
    }

    @Test fun multiplicationFollowsTheRuleForI() {
        // (3 + 4i)(1 + 2i) = 3 + 6i + 4i + 8i² = −5 + 10i
        val product = ComplexNum(3.0, 4.0) * ComplexNum(1.0, 2.0)
        assertEquals(-5.0, product.re, 1e-12)
        assertEquals(10.0, product.im, 1e-12)
    }

    @Test fun divisionRationalisesTheDenominator() {
        // (3 + 4i)/(1 + 2i) = (3 + 4i)(1 − 2i)/5 = (11 − 2i)/5
        val quotient = ComplexNum(3.0, 4.0) / ComplexNum(1.0, 2.0)
        assertEquals(2.2, quotient.re, 1e-12)
        assertEquals(-0.4, quotient.im, 1e-12)
    }

    @Test fun theModulusAndTheArgumentAreRight() {
        val z = ComplexNum(3.0, 4.0)
        assertEquals(5.0, z.modulus, 1e-12)
        assertEquals(Math.atan2(4.0, 3.0), z.argument, 1e-12)
        assertEquals(PI, ComplexNum(-1.0, 0.0).argument, 1e-12)
    }

    @Test fun theConjugateFlipsTheSign() {
        assertEquals(ComplexNum(3.0, -4.0), ComplexNum(3.0, 4.0).conjugate)
    }

    @Test fun thePolarFormHasTheSameModulus() {
        val z = ComplexNum(-1.0, 1.0)
        val polar = ComplexNum(z.modulus, 0.0) * ComplexNum.euler(z.argument)
        assertEquals(z.re, polar.re, 1e-12)
        assertEquals(z.im, polar.im, 1e-12)
    }

    @Test fun eulersFormulaHolds() {
        val value = ComplexNum.euler(PI / 3)
        assertEquals(0.5, value.re, 1e-12)
        assertEquals(Math.sqrt(3.0) / 2, value.im, 1e-12)
    }

    @Test fun thePrincipalRootHasANonNegativeImaginaryPart() {
        val root = ComplexNum(-4.0, 0.0).sqrt()
        assertEquals(0.0, root.re, 1e-12)
        assertEquals(2.0, root.im, 1e-12)
    }

    @Test fun allTheNthRootsAreFound() {
        val roots = ComplexNum(1.0, 0.0).roots(4)
        assertEquals(4, roots.size)
        for (root in roots) {
            assertEquals(1.0, root.modulus, 1e-12)
            // Each root raised to the fourth power is 1.
            val power = root.pow(4)
            assertEquals(1.0, power.re, 1e-9)
            assertEquals(0.0, power.im, 1e-9)
        }
    }

    @Test fun thePrincipalLogarithmIsInTheRightBranch() {
        val log = ComplexNum(-1.0, 0.0).ln()
        assertEquals(0.0, log.re, 1e-12)
        assertEquals(PI, log.im, 1e-12)
    }

    @Test fun theTrigonometricFunctionsMatchTheRealOnes() {
        val z = ComplexNum(0.7, 0.0)
        assertEquals(sin(0.7), z.sin().re, 1e-12)
        assertEquals(0.0, z.sin().im, 1e-12)
        assertEquals(cos(0.7), z.cos().re, 1e-12)
    }

    @Test fun theCartesianFormReadsWell() {
        assertEquals("3 + 4i", ComplexNum(3.0, 4.0).cartesian())
        assertEquals("−4i", ComplexNum(0.0, -4.0).cartesian())
        assertEquals("0", ComplexNum(0.0, 0.0).cartesian())
        assertEquals("2.5", ComplexNum(2.5, 0.0).cartesian())
    }

    @Test fun theExponentialAndTheLogarithmAreInverses() {
        val z = ComplexNum(1.2, -0.7)
        val back = z.exp().ln()
        assertEquals(z.re, back.re, 1e-9)
        assertEquals(z.im, back.im, 1e-9)
    }
}
