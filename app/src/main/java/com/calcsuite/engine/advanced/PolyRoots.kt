package com.calcsuite.engine.advanced

import com.calcsuite.core.Poly
import com.calcsuite.core.Rat
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The roots of a polynomial over the complex numbers.
 *
 * The rational roots are found exactly and divided out, which also gives
 * their multiplicity. Whatever is left is a quadratic, which the formula
 * settles exactly, or a higher-degree remainder, whose roots are found
 * numerically with the Durand–Kerner iteration — that one finds the complex
 * roots as well as the real ones.
 */
object PolyRoots {

    /**
     * Every root of the polynomial, counting multiplicity.
     *
     * The roots are returned in the order they were found: the exact ones
     * first, then the numerical ones.
     */
    fun complexRoots(polynomial: Poly): List<ComplexNum> {
        if (polynomial.isZero() || polynomial.isConstant()) return emptyList()

        val roots = mutableListOf<ComplexNum>()
        var remaining = polynomial

        // The rational roots, divided out one at a time.
        val counts = mutableMapOf<Double, Int>()
        while (remaining.degree >= 1) {
            val rational = rationalRootOf(remaining) ?: break
            val key = rational.toDouble()
            counts[key] = (counts[key] ?: 0) + 1
            remaining = remaining.divmod(Poly.of(listOf(-rational, Rat.ONE))).first
            if (counts.size > 64) break
        }
        for ((root, count) in counts) {
            repeat(count) { roots.add(ComplexNum(root, 0.0)) }
        }

        if (remaining.degree == 1) {
            val c = remaining.coeffs[0].toDouble()
            val b = remaining.coeffs[1].toDouble()
            if (b != 0.0) roots.add(ComplexNum(-c / b, 0.0))
            return roots
        }
        if (remaining.degree == 2) {
            roots.addAll(quadraticRoots(remaining))
            return roots
        }
        if (remaining.degree >= 3) {
            roots.addAll(numericalRoots(remaining))
        }
        return roots
    }

    /** Only the roots that are real, to working precision. */
    fun realRoots(polynomial: Poly): List<ComplexNum> =
        complexRoots(polynomial).filter { abs(it.im) < 1e-8 }.sortedBy { it.re }

    // -------------------------------------------------------------- helpers

    /** A rational root of a polynomial, or null. */
    private fun rationalRootOf(polynomial: Poly): Rat? {
        if (polynomial.isConstant()) return null
        val constant = polynomial.coeffs[0]
        if (constant.isZero()) return Rat.ZERO
        val leading = polynomial.coeffs.last()
        val numerators = divisorsOf(constant.num.abs())
        val denominators = divisorsOf(leading.num.abs())
        for (n in numerators) {
            for (d in denominators) {
                for (sign in intArrayOf(1, -1)) {
                    val candidate = Rat.of(
                        BigInteger.valueOf(sign.toLong()) * BigInteger.valueOf(n),
                        BigInteger.valueOf(d),
                    )
                    if (polynomial.evaluate(candidate).isZero()) return candidate
                }
            }
        }
        return null
    }

    private fun divisorsOf(value: BigInteger): List<Long> {
        if (value.signum() == 0) return listOf(1L)
        if (value > BigInteger.valueOf(1_000_000L)) return listOf(1L, value.toLong())
        val out = sortedSetOf<Long>()
        var d = 1L
        val n = value.toLong()
        while (d * d <= n) {
            if (n % d == 0L) {
                out.add(d)
                out.add(n / d)
            }
            d++
        }
        return out.toList()
    }

    /** The two roots of a quadratic, real or complex. */
    private fun quadraticRoots(quadratic: Poly): List<ComplexNum> {
        val a = quadratic.coeffs[2].toDouble()
        val b = quadratic.coeffs[1].toDouble()
        val c = quadratic.coeffs[0].toDouble()
        val discriminant = b * b - 4 * a * c
        if (discriminant >= 0) {
            val root = sqrt(discriminant)
            return listOf(
                ComplexNum((-b - root) / (2 * a), 0.0),
                ComplexNum((-b + root) / (2 * a), 0.0),
            )
        }
        val imaginary = sqrt(-discriminant) / (2 * a)
        val real = -b / (2 * a)
        return listOf(ComplexNum(real, imaginary), ComplexNum(real, -imaginary))
    }

    /**
     * The roots of a polynomial, found numerically.
     *
     * The Durand–Kerner iteration works on the complex plane, so the complex
     * roots come out with it; the conjugates are then paired so that each
     * pair is reported once.
     */
    fun numericalRoots(polynomial: Poly, iterations: Int = 400): List<ComplexNum> {
        val degree = polynomial.degree
        if (degree < 1) return emptyList()
        val coefficients = DoubleArray(degree + 1) { polynomial.coeffs[it].toDouble() }
        val leading = coefficients[degree]
        if (leading == 0.0) return emptyList()
        for (i in coefficients.indices) coefficients[i] /= leading

        fun evaluate(z: ComplexNum): ComplexNum {
            var acc = ComplexNum(0.0, 0.0)
            for (i in coefficients.indices.reversed()) {
                acc = acc * z + ComplexNum(coefficients[i], 0.0)
            }
            return acc
        }

        val radius = 1.0 + coefficients.dropLast(1).maxOf { abs(it) }
        val guesses = ArrayList<ComplexNum>(degree)
        for (i in 0 until degree) {
            val angle = 2 * Math.PI * i / degree
            guesses.add(ComplexNum(radius * cos(angle), radius * sin(angle)))
        }

        // The iteration is synchronous: every step is worked out from the
        // values of the previous round, which is the form that converges.
        val next = ArrayList<ComplexNum>(degree)
        for (round in 0 until iterations) {
            next.clear()
            var moved = 0.0
            for (i in guesses.indices) {
                var denominator = ComplexNum(1.0, 0.0)
                for (j in guesses.indices) {
                    if (i != j) denominator = denominator * (guesses[i] - guesses[j])
                }
                if (denominator.modulus < 1e-300) {
                    next.add(guesses[i])
                    continue
                }
                val step = evaluate(guesses[i]) / denominator
                next.add(guesses[i] - step)
                if (step.modulus > moved) moved = step.modulus
            }
            guesses.clear()
            guesses.addAll(next)
            if (moved < 1e-15) break
        }

        // The iteration can stop short, so each root is polished with Newton's
        // method, which is very effective once the guess is close.
        val polished = guesses.map { guess -> newtonRefine(guess, coefficients) }

        // Every root is returned, so the list has one entry for each root of
        // the polynomial. A conjugate pair is two entries; a caller that wants
        // the pair as one thing can pair them up.
        return polished.sortedBy { it.re }
    }

    /**
     * One root, polished with Newton's method.
     *
     * The step is `p(z) / p′(z)`, and the iteration stops when the step is
     * small enough or when the value stops improving.
     */
    private fun newtonRefine(
        guess: ComplexNum,
        coefficients: DoubleArray,
    ): ComplexNum {
        fun evaluate(z: ComplexNum): ComplexNum {
            var acc = ComplexNum(0.0, 0.0)
            for (i in coefficients.indices.reversed()) {
                acc = acc * z + ComplexNum(coefficients[i], 0.0)
            }
            return acc
        }

        fun derivative(z: ComplexNum): ComplexNum {
            var acc = ComplexNum(0.0, 0.0)
            for (i in coefficients.indices.reversed()) {
                acc = acc * z + ComplexNum(coefficients[i], 0.0)
            }
            return acc
        }
        var current = guess
        var best = guess
        var bestSize = evaluate(guess).modulus
        repeat(40) {
            val value = evaluate(current)
            val slope = derivative(current)
            if (slope.modulus < 1e-14) return@repeat
            val step = value / slope
            val next = current - step
            val size = evaluate(next).modulus
            if (size < bestSize) {
                bestSize = size
                best = next
            }
            if (step.modulus < 1e-15) return@repeat
            current = next
        }
        return best
    }
}
