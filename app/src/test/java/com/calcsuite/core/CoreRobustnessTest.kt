package com.calcsuite.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

/**
 * Regression tests for the robustness of the shared numeric primitives:
 * non-throwing conversions, locale round-trips, Long-safe exponents and a
 * bounded root-isolation recursion.
 */
class CoreRobustnessTest {

    // ---- 4. Rat / Poly checked variants -----------------------------------

    @Test fun ofDecimalNeverThrowsOnBadOrOutOfRangeInput() {
        assertNull(Rat.ofDecimalOrNull("not a number"))
        assertNull(Rat.ofDecimalOrNull("1e20000"))
        assertNull(Rat.ofDecimalOrNull("1e-20000"))
        assertEquals(Rat.ZERO, Rat.ofDecimal("not a number"))
        assertEquals(Rat.of(3L, 2L), Rat.ofDecimal("1.5"))
        assertEquals(Rat.of(10_000_000_000L), Rat.ofDecimal("1e10"))
    }

    @Test fun rationalCheckedPrimitivesReturnNullInsteadOfThrowing() {
        assertNull(Rat.ofOrNull(BigInteger.ONE, BigInteger.ZERO))
        assertNull(Rat.ONE.divOrNull(Rat.ZERO))
        assertEquals(Rat.of(1L, 2L), Rat.ONE.divOrNull(Rat.TWO))
        assertEquals(Rat.of(-1L, 2L), Rat.ofOrNull(BigInteger.ONE, BigInteger.valueOf(-2)))
    }

    @Test fun polynomialCheckedDivisionReturnsNullInsteadOfThrowing() {
        assertNull(Poly.ONE.divmodOrNull(Poly.ZERO))
        val dividend = Poly.of(listOf(Rat.of(-1L), Rat.ZERO, Rat.ONE)) // x² − 1
        val divisor = Poly.of(listOf(Rat.of(-1L), Rat.ONE))            // x − 1
        val (quotient, remainder) = dividend.divmodOrNull(divisor)!!
        assertEquals("x + 1", quotient.toString())
        assertTrue(remainder.isZero())
    }

    // ---- 7. locale decimal marks ------------------------------------------

    @Test fun localeFormattedNumberParsesBack() {
        val locale = Locale.forLanguageTag("ar-EG")
        val formatted = Num.format(BigDecimal("1234567.891"), 12, Num.sepsFor(locale, true))
        assertEquals(0, BigDecimal("1234567.891").compareTo(Num.parseOrNull(formatted)!!))
        // Common non-Latin marks are accepted directly too.
        assertEquals(0, BigDecimal("1.5").compareTo(Num.parseOrNull("1٫5")!!))
        assertEquals(0, BigDecimal("1.5").compareTo(Num.parseOrNull("1٬5")!!))
    }

    @Test fun groupingRulesArePreservedWithLocaleMarks() {
        assertEquals(0, BigDecimal("1234567").compareTo(Num.parseOrNull("1٬234٬567")!!))
        assertEquals(0, BigDecimal("1234567").compareTo(Num.parseOrNull("1٫234٫567")!!))
        assertNull(Num.parseOrNull("12٬345٬67"))
        assertEquals(0, BigDecimal("1.5").compareTo(Num.parseOrNull("1.5")!!))
        assertEquals(0, BigDecimal("1.5").compareTo(Num.parseOrNull("1,5")!!))
    }

    // ---- 8. exponent arithmetic -------------------------------------------

    @Test fun extremeScalesDoNotOverflowExponentArithmetic() {
        val tiny = BigDecimal(BigInteger.ONE, Int.MIN_VALUE)
        assertTrue(Num.format(tiny).isNotEmpty())
        assertTrue(Num.formatForCopy(tiny).isNotEmpty())
        val huge = BigDecimal(BigInteger.ONE, Int.MAX_VALUE)
        assertTrue(Num.format(huge).isNotEmpty())
        assertTrue(Num.formatForCopy(huge).isNotEmpty())
    }

    // ---- 9. root isolation -------------------------------------------------

    @Test fun isolationIsBoundedOnExtremeIntervals() {
        // Cauchy's bound is about 1e100, so isolating the single real root of
        // x³ − 1e100 needs far more than 256 subdivision levels.
        val p = Poly.of(listOf(Rat.ofDecimal("-1e100"), Rat.ZERO, Rat.ZERO, Rat.ONE))
        val roots = PolynomialRoots.numericalRoots(p, tolerance = Double.MIN_VALUE)
        assertEquals(1, roots.size)
        assertTrue(roots[0] > 0 && roots[0].isFinite())
    }

    @Test fun hugeLeadingCoefficientDoesNotThrow() {
        // 2^64 used to truncate to zero in divisorsOf(), which made
        // rationalRootOf() call Rat.of(n, 0) and throw. The rational-root
        // search should skip the oversized candidate and fall through to the
        // quadratic solver instead.
        val p = Poly.of(listOf(Rat.of(-1L), Rat.ZERO, Rat.of(BigInteger.ONE.shiftLeft(64))))
        val roots = PolynomialRoots.realRoots(p)
        assertEquals(2, roots.size)
        assertEquals(-1.0 / (1L shl 32), roots[0].value, 1e-18)
        assertEquals(1.0 / (1L shl 32), roots[1].value, 1e-18)
    }

    @Test fun rationalRootSearchStillHandlesOrdinaryAndNegativeCoefficients() {
        // (1,000,000x − 1)(x − 2): both roots are found at the scan boundary.
        val boundary = Poly.of(listOf(Rat.of(2L), Rat.of(-2_000_001L), Rat.of(1_000_000L)))
        val boundaryRoots = PolynomialRoots.realRoots(boundary)
        assertEquals(2, boundaryRoots.size)
        assertEquals(1.0 / 1_000_000, boundaryRoots[0].value, 1e-18)
        assertEquals(2.0, boundaryRoots[1].value, 1e-18)

        // Negative leading coefficient: −3x − 3 has the root −1.
        val negative = Poly.of(listOf(Rat.of(-3L), Rat.of(-3L)))
        val negativeRoots = PolynomialRoots.realRoots(negative)
        assertEquals(1, negativeRoots.size)
        assertEquals(-1.0, negativeRoots[0].value, 1e-18)
    }
}
