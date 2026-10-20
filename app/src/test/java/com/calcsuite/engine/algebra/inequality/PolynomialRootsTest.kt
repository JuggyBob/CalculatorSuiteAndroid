package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.Poly
import com.calcsuite.core.PolynomialRoots
import com.calcsuite.core.Rat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** Tests for the exact arithmetic and the root finding that sits on top of it. */
class PolynomialRootsTest {

    private fun rat(value: Long) = Rat.of(value)

    /**
     * The coefficients are given lowest degree first, which is how a polynomial
     * stores them: `poly(6, -5, 1)` is x^2 - 5x + 6.
     */
    private fun poly(vararg coefficients: Long): Poly =
        Poly.of(coefficients.map { rat(it) })

    @Test fun rationalArithmeticIsExact() {
        val third = Rat.of(1L, 3L)
        assertEquals("1/3", third.toString())
        assertEquals(0, (third + third + third - Rat.ONE).compareTo(Rat.ZERO))
        assertEquals("1/2", (Rat.of(1L, 3L) + Rat.of(1L, 6L)).toString())
        assertEquals("0", (Rat.of(2L, 4L) - Rat.of(1L, 2L)).toString())
    }

    @Test fun aNegativeDenominatorIsNormalised() {
        assertEquals("-1/2", Rat.of(1L, -2L).toString())
        assertEquals("-1/2", Rat.of(-1L, 2L).toString())
    }

    @Test fun polynomialMultiplicationIsExact() {
        val product = poly(6, -5, 1) * poly(-2, 1)
        // (x^2 - 5x + 6)(x - 2) = x^3 - 7x^2 + 16x - 12
        assertEquals(listOf(rat(-12), rat(16), rat(-7), rat(1)), product.coeffs)
    }

    @Test fun polynomialDivisionIsExact() {
        val (quotient, remainder) = poly(6, -5, 1).divmod(poly(-2, 1))
        assertEquals("x − 3", quotient.toString())
        assertTrue("the remainder must be zero", remainder.isZero())
    }

    @Test fun divisionLeavesTheRightRemainder() {
        val (quotient, remainder) = poly(1, 0, 1).divmod(poly(-1, 1))
        assertEquals("x + 1", quotient.toString())
        assertEquals(listOf(rat(2)), remainder.coeffs)
    }

    @Test fun theDerivativeIsExact() {
        assertEquals("2·x − 5", poly(6, -5, 1).derivative().toString())
    }

    @Test fun rationalRootsAreFoundExactly() {
        // x^2 - 5x + 6 = (x - 2)(x - 3)
        val roots = PolynomialRoots.realRoots(poly(6, -5, 1))
        assertEquals(listOf("2", "3"), roots.map { it.display })
        assertTrue(roots.all { it.isExact })
    }

    @Test fun aCubicIsFactoredByItsRationalRoots() {
        // x^3 - 6x^2 + 11x - 6 = (x - 1)(x - 2)(x - 3)
        val roots = PolynomialRoots.realRoots(poly(-6, 11, -6, 1))
        assertEquals(listOf("1", "2", "3"), roots.map { it.display })
        assertTrue(roots.all { it.isExact })
    }

    @Test fun aZeroRootIsFound() {
        val roots = PolynomialRoots.realRoots(poly(0, 0, 1))
        assertEquals(listOf("0"), roots.map { it.display })
    }

    @Test fun aQuadraticWithIrrationalRootsIsGivenInRadicalForm() {
        val roots = PolynomialRoots.realRoots(poly(-2, 0, 1))
        assertEquals(2, roots.size)
        assertTrue("a radical root is an exact answer", roots.all { it.isExact })
        assertTrue(roots.all { it.display.contains("√2") })
        assertEquals(listOf("−√2", "√2"), roots.map { it.display })
    }

    @Test fun aPolynomialWithNoRealRootsHasNone() {
        assertTrue(PolynomialRoots.realRoots(poly(1, 0, 1)).isEmpty())
    }

    @Test fun numericalRootsAreFoundForACubicWithNoRationalRoot() {
        // x^3 - 2x - 5 has one real root near 2.09455.
        val roots = PolynomialRoots.realRoots(poly(-5, -2, 0, 1))
        assertEquals(1, roots.size)
        assertFalse(roots.single().isExact)
        assertEquals(2.0945514815423265, roots.single().value, 1e-9)
    }

    @Test fun theSturmSequenceCountsTheRootsInAnInterval() {
        val sequence = PolynomialRoots.sturmSequence(poly(6, -5, 1))
        assertEquals(2, PolynomialRoots.countRootsIn(sequence, 0.0, 10.0))
        assertEquals(1, PolynomialRoots.countRootsIn(sequence, 2.5, 10.0))
        assertEquals(0, PolynomialRoots.countRootsIn(sequence, 3.5, 10.0))
        assertEquals(1, PolynomialRoots.countRootsIn(sequence, 0.0, 2.5))
    }

    @Test fun aPerfectSquareIsDetected() {
        assertEquals("3", Rat.of(9L).perfectSquareRoot()?.toString())
        assertEquals("2/3", Rat.of(4L, 9L).perfectSquareRoot()?.toString())
        assertEquals(null, Rat.of(8L).perfectSquareRoot())
        assertEquals(null, Rat.of(-1L).perfectSquareRoot())
    }

    @Test fun bigIntegersKeepTheirPrecision() {
        val big = Rat.of(BigInteger("123456789012345678901234567890"))
        assertEquals("123456789012345678901234567890", big.toString())
    }
}
