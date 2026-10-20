package com.calcsuite.engine.advanced

import com.calcsuite.core.Poly
import com.calcsuite.core.Rat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tests for the shared polynomial root finder.
 */
class PolyRootsTest {

    private fun poly(vararg coefficients: Double): Poly = Poly.of(
        coefficients.map { Rat.approximate(it) },
    )

    @Test fun theRootsOfAQuadraticAreFound() {
        // x² − 3x + 2 = (x − 1)(x − 2). The coefficients are given lowest
        // degree first, as the polynomial type stores them.
        val roots = PolyRoots.complexRoots(poly(2.0, -3.0, 1.0))
        assertEquals(2, roots.size)
        val values = roots.map { it.re }.sorted()
        assertEquals(1.0, values[0], 1e-9)
        assertEquals(2.0, values[1], 1e-9)
    }

    @Test fun theRootsOfALinearPolynomialAreFound() {
        val roots = PolyRoots.complexRoots(poly(-3.0, 1.0))
        assertEquals(1, roots.size)
        assertEquals(3.0, roots[0].re, 1e-9)
    }

    @Test fun aRepeatedRootIsFoundMoreThanOnce() {
        // (x − 1)³ = x³ − 3x² + 3x − 1.
        val roots = PolyRoots.complexRoots(poly(-1.0, 3.0, -3.0, 1.0))
        assertEquals(3, roots.size)
        for (root in roots) {
            assertEquals(1.0, root.re, 1e-6)
        }
    }

    @Test fun theComplexRootsOfAQuadraticAreFound() {
        // x² + 1 = 0, so the roots are ±i.
        val roots = PolyRoots.complexRoots(poly(1.0, 0.0, 1.0))
        assertEquals(2, roots.size)
        assertTrue("one root should be above the axis", roots.any { it.im > 0.5 })
        assertTrue("one root should be below the axis", roots.any { it.im < -0.5 })
        for (root in roots) {
            assertEquals(0.0, root.re, 1e-9)
            assertEquals(1.0, abs(root.im), 1e-9)
        }
    }

    @Test fun theRootsOfACubicWithNoRationalRootAreFound() {
        // x³ − x − 1 has one real root and a conjugate pair. The
        // coefficients are given lowest degree first.
        val roots = PolyRoots.complexRoots(poly(-1.0, -1.0, 0.0, 1.0))
        assertEquals(3, roots.size)
        // Every root satisfies the polynomial.
        for (root in roots) {
            val value = root.pow(3) - root - ComplexNum(1.0, 0.0)
            assertEquals(0.0, value.re, 1e-6)
            assertEquals(0.0, value.im, 1e-6)
        }
        // The real root is the plastic number, 1.324717957…
        val real = roots.minByOrNull { abs(it.im) }!!
        assertEquals(1.3247179572447458, real.re, 1e-6)
    }

    @Test fun theRealRootsAreFilteredOut() {
        val roots = PolyRoots.realRoots(poly(1.0, 0.0, 1.0))
        assertTrue("a polynomial with no real roots has none", roots.isEmpty())
    }

    @Test fun aConstantPolynomialHasNoRoots() {
        assertTrue(PolyRoots.complexRoots(poly(5.0)).isEmpty())
    }

    @Test fun theZeroPolynomialHasNoRoots() {
        assertTrue(PolyRoots.complexRoots(Poly.ZERO).isEmpty())
    }
}
