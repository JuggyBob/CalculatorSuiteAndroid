package com.calcsuite.core

import java.math.BigInteger

/**
 * A real root of a polynomial.
 *
 * [exact] holds the root when it is a rational number, [display] is how the
 * root should be written, and [isExact] says whether the value is a proven
 * exact answer or a numerical approximation.
 */
data class CriticalRoot(
    val value: Double,
    val exact: Rat?,
    val display: String,
    val isExact: Boolean,
)

/**
 * Finds the real roots of a polynomial.
 *
 * The strategy is exact where the mathematics allows it:
 *
 * 1. A factor of `x` gives the root zero.
 * 2. The rational-root theorem finds every rational root; each one is divided
 *    out exactly, which often leaves a polynomial of low degree.
 * 3. A remaining quadratic is solved with the quadratic formula — exactly when
 *    the discriminant is a perfect square, otherwise in radical form.
 * 4. Anything left over has no rational root, so its roots are found
 *    numerically. That path uses a **Sturm sequence** to count the roots in an
 *    interval, which guarantees no root is missed, and the square-free part of
 *    the polynomial so that every root is simple and bisection is valid.
 *    Those roots are always labelled as approximations.
 */
object PolynomialRoots {

    /**
     * All distinct real roots of [p], sorted ascending.
     *
     * The zero polynomial has every real number as a root; that is reported as
     * an empty list here and handled by the caller.
     */
    fun realRoots(p: Poly): List<CriticalRoot> {
        if (p.isZero()) return emptyList()
        val found = mutableListOf<CriticalRoot>()

        // A factor of x means zero is a root. Every factor is removed, so the
        // root is reported once however many times it occurs.
        var work = p
        if (!work.isZero() && work.constantTerm().isZero()) {
            found.add(CriticalRoot(0.0, Rat.ZERO, "0", true))
            while (!work.isZero() && work.constantTerm().isZero()) {
                work = work.divmod(Poly.monomial(1)).first
            }
        }
        if (work.isZero() || work.isConstant()) return found.sortedBy { it.value }

        // Rational roots, divided out one at a time.
        while (work.degree >= 1) {
            val candidate = rationalRootOf(work)
            if (candidate == null) break
            found.add(CriticalRoot(candidate.toDouble(), candidate, candidate.display(), true))
            // Divide out the factor (x - r). For r = 0 that factor is just x.
            val factor = if (candidate.isZero()) {
                Poly.monomial(1)
            } else {
                Poly.monomial(1) - Poly.constant(candidate)
            }
            work = work.divmod(factor).first
        }
        if (work.isZero() || work.isConstant()) return found.sortedBy { it.value }

        // A remaining quadratic is solved exactly, or in radical form.
        if (work.degree == 2) {
            found.addAll(quadraticRoots(work))
            return found.sortedBy { it.value }
        }

        // Anything else is genuinely numeric.
        found.addAll(numericalRoots(work).map { CriticalRoot(it, null, "≈" + formatNumber(it), false) })
        return found.sortedBy { it.value }
    }

    // ------------------------------------------------------- rational roots

    /**
     * One rational root of [p], or null when it has none.
     *
     * Uses the rational-root theorem: a root `p/q` in lowest terms must have
     * `p` dividing the constant term and `q` dividing the leading
     * coefficient. The candidates are tested exactly, so a hit is proven.
     */
    private fun rationalRootOf(p: Poly): Rat? {
        if (p.isConstant()) return null
        val integer = integerScaled(p) ?: return null
        val constantTerm = integer.first()
        val leading = integer.last()
        if (constantTerm.signum() == 0) return Rat.ZERO
        val numerators = divisorsOf(constantTerm.abs())
        val denominators = divisorsOf(leading.abs())
        for (n in numerators) {
            for (d in denominators) {
                for (sign in intArrayOf(1, -1)) {
                    val candidate = Rat.of(BigInteger.valueOf(sign.toLong()) * BigInteger.valueOf(n), BigInteger.valueOf(d))
                    if (p.evaluate(candidate).isZero()) return candidate
                }
            }
        }
        return null
    }

    /** Scales a polynomial with rational coefficients to integer coefficients. */
    private fun integerScaled(p: Poly): List<BigInteger>? {
        var lcm = BigInteger.ONE
        for (c in p.coeffs) {
            lcm = lcm.divide(lcm.gcd(c.den)).multiply(c.den)
        }
        val out = ArrayList<BigInteger>(p.coeffs.size)
        for (c in p.coeffs) {
            val scaled = c.num.multiply(lcm.divide(c.den))
            out.add(if (scaled.signum() == 0) BigInteger.ZERO else scaled)
        }
        // Strip a common factor so the divisor lists stay small.
        var g = BigInteger.ZERO
        for (v in out) g = g.gcd(v)
        if (g > BigInteger.ONE) for (i in out.indices) out[i] = out[i].divide(g)
        return out
    }

    private fun divisorsOf(value: BigInteger): List<Long> {
        if (value.signum() == 0) return listOf(1L)
        val n = value.abs()
        if (n > BigInteger.valueOf(1_000_000L)) return listOf(1L, n.toLong())
        val out = sortedSetOf<Long>()
        var d = 1L
        while (d * d <= n.toLong()) {
            if (n.mod(BigInteger.valueOf(d)).signum() == 0) {
                out.add(d)
                out.add(n.divide(BigInteger.valueOf(d)).toLong())
            }
            d++
        }
        return out.toList()
    }

    // ------------------------------------------------------ quadratic roots

    /** The two roots of a quadratic, exactly when the discriminant is a square. */
    fun quadraticRoots(p: Poly): List<CriticalRoot> {
        if (p.degree != 2) return emptyList()
        val a = p.coefficient(2)
        val b = p.coefficient(1)
        val c = p.coefficient(0)
        val discriminant = b * b - Rat.of(4L) * a * c
        if (discriminant.signum() < 0) return emptyList()
        val square = discriminant.perfectSquareRoot()
        if (square != null) {
            val twoA = Rat.of(2L) * a
            val minus = (-b - square) / twoA
            val plus = (-b + square) / twoA
            return listOf(
                CriticalRoot(minus.toDouble(), minus, minus.display(), true),
                CriticalRoot(plus.toDouble(), plus, plus.display(), true),
            ).sortedBy { it.value }
        }
        // Not a perfect square: the roots are irrational, so they are given in
        // radical form and their value is computed numerically.
        val twoA = Rat.of(2L) * a
        val spread = discriminant.squareRootAsDouble() / twoA.toDouble()
        val spreadSquared = discriminant / (Rat.of(4L) * a * a)
        val radical = if (spreadSquared.isInteger()) {
            spreadSquared.toString()
        } else {
            "(${spreadSquared.toString()})"
        }
        val centre = (-b) / twoA
        // Each root gets its own text, so a number line can label them apart.
        val minusText = if (centre.isZero()) {
            "−√$radical"
        } else {
            "${centre.display()} − √$radical"
        }
        val plusText = if (centre.isZero()) {
            "√$radical"
        } else {
            "${centre.display()} + √$radical"
        }
        // A radical is an exact answer, not a numerical approximation.
        return listOf(
            CriticalRoot(centre.toDouble() - spread, null, minusText, true),
            CriticalRoot(centre.toDouble() + spread, null, plusText, true),
        ).sortedBy { it.value }
    }

    // ---------------------------------------------------- numerical roots

    /**
     * The distinct real roots of a polynomial with no rational root, found
     * numerically.
     *
     * The polynomial is first made square-free so that every root is simple:
     * then a Sturm sequence counts the roots in an interval, which isolates
     * each one, and bisection finishes the job. A Sturm count of zero in an
     * interval is a proof that no root was missed.
     */
    fun numericalRoots(p: Poly, tolerance: Double = 1e-10): List<Double> {
        if (p.degree < 3) return quadraticNumeric(p)
        val squareFree = squareFreePart(p)
        if (squareFree.degree < 1) return emptyList()
        val sequence = sturmSequence(squareFree)
        val bound = cauchyBound(squareFree)
        val roots = mutableListOf<Double>()
        isolate(sequence, -bound, bound, tolerance, roots)
        return roots.sorted()
    }

    private fun quadraticNumeric(p: Poly): List<Double> {
        if (p.degree == 1) {
            val a = p.coefficient(1)
            if (a.isZero()) return emptyList()
            return listOf((-p.coefficient(0) / a).toDouble())
        }
        if (p.degree != 2) return emptyList()
        val a = p.coefficient(2)
        val b = p.coefficient(1)
        val c = p.coefficient(0)
        val discriminant = (b * b - Rat.of(4L) * a * c).toDouble()
        if (discriminant < 0) return emptyList()
        val root = kotlin.math.sqrt(discriminant)
        val denominator = 2 * a.toDouble()
        val out = listOf((-b.toDouble() + root) / denominator, (-b.toDouble() - root) / denominator)
        return out.sorted()
    }

    /** The greatest common divisor of two polynomials over the rationals. */
    fun gcd(a: Poly, b: Poly): Poly {
        var x = a
        var y = b
        while (!y.isZero()) {
            val remainder = x.divmod(y).second
            x = y
            y = remainder
        }
        return if (x.isZero()) Poly.ONE else x.primitive()
    }

    /** The part of [p] whose roots are all simple; it has the same distinct roots. */
    fun squareFreePart(p: Poly): Poly {
        if (p.degree < 2) return p
        val g = gcd(p, p.derivative())
        if (g.degree < 1) return p
        val (quotient, remainder) = p.divmod(g)
        return if (remainder.isZero()) quotient else p
    }

    /** The Sturm sequence of [p]. */
    fun sturmSequence(p: Poly): List<Poly> {
        val sequence = mutableListOf(p)
        if (p.degree < 1) return sequence
        sequence.add(p.derivative())
        while (true) {
            val a = sequence[sequence.size - 2]
            val b = sequence[sequence.size - 1]
            if (b.isZero()) break
            val remainder = a.divmod(b).second
            if (remainder.isZero()) break
            sequence.add(remainder * Rat.of(-1L))
        }
        return sequence
    }

    /** The number of sign changes in the Sturm sequence at [x]. */
    fun variationsAt(sequence: List<Poly>, x: Rat): Int {
        var changes = 0
        var previous = 0
        for (poly in sequence) {
            val sign = poly.evaluate(x).signum()
            if (sign == 0) continue
            if (previous != 0 && sign != previous) changes++
            previous = sign
        }
        return changes
    }

    /** How many distinct real roots lie in `(a, b]`. */
    fun countRootsIn(sequence: List<Poly>, a: Double, b: Double): Int {
        val ra = Rat.approximate(a)
        val rb = Rat.approximate(b)
        return (variationsAt(sequence, ra) - variationsAt(sequence, rb)).coerceAtLeast(0)
    }

    private fun isolate(
        sequence: List<Poly>,
        lo: Double,
        hi: Double,
        tolerance: Double,
        out: MutableList<Double>,
    ) {
        val count = countRootsIn(sequence, lo, hi)
        if (count <= 0) return
        if (hi - lo <= tolerance || count == 1 && hi - lo < 1e-6) {
            out.add(bisect(sequence, lo, hi))
            return
        }
        val mid = (lo + hi) / 2
        isolate(sequence, lo, mid, tolerance, out)
        isolate(sequence, mid, hi, tolerance, out)
    }

    /** Finds the root inside `(lo, hi]` by bisection on the square-free part. */
    private fun bisect(sequence: List<Poly>, lo: Double, hi: Double): Double {
        val poly = sequence.first()
        var a = lo
        var b = hi
        repeat(200) {
            val mid = (a + b) / 2
            if (b - a < 1e-15) return mid
            // The sign is evaluated in double precision: rounding the point to
            // a nearby rational would throw away most of the accuracy.
            val fa = poly.evaluateDouble(a)
            val fm = poly.evaluateDouble(mid)
            if (fm == 0.0) return mid
            if (fa * fm < 0) {
                b = mid
            } else {
                a = mid
            }
        }
        return (a + b) / 2
    }

    /** A bound beyond which the polynomial has no real roots (Cauchy's bound). */
    private fun cauchyBound(p: Poly): Double {
        if (p.isConstant()) return 1.0
        val leading = p.coefficient(p.degree).toDouble()
        if (leading == 0.0) return 1.0
        var max = 0.0
        for (i in 0 until p.degree) {
            val ratio = kotlin.math.abs(p.coefficient(i).toDouble() / leading)
            if (ratio > max) max = ratio
        }
        val bound = 1.0 + max
        return if (bound.isFinite() && bound > 1.0) bound else 1.0
    }
}
