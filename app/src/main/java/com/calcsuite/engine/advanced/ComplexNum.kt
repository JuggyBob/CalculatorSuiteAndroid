package com.calcsuite.engine.advanced

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A complex number, `re + im·i`.
 *
 * The operations follow the standard conventions, and the branch choices are
 * the principal ones:
 *
 * - The **argument** is in `(−π, π]`, so `arg(−1) = π`.
 * - The **principal logarithm** has an imaginary part in `(−π, π]`, which is
 *   the usual branch of the complex logarithm.
 * - The **principal square root** and the `n`-th roots come from the
 *   principal logarithm, so the principal root of a negative real number is
 *   the one with a non-negative imaginary part.
 *
 * Where a function has more than one value, the tool offers all of them
 * rather than hiding the choice.
 */
data class ComplexNum(val re: Double, val im: Double = 0.0) {

    // ------------------------------------------------------------ arithmetic

    operator fun plus(o: ComplexNum) = ComplexNum(re + o.re, im + o.im)
    operator fun minus(o: ComplexNum) = ComplexNum(re - o.re, im - o.im)
    operator fun times(o: ComplexNum) = ComplexNum(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun unaryMinus() = ComplexNum(-re, -im)

    operator fun div(o: ComplexNum): ComplexNum {
        if (o.re == 0.0 && o.im == 0.0) return ComplexNum(Double.NaN, Double.NaN)
        val d = o.re * o.re + o.im * o.im
        return ComplexNum((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }

    /** The modulus, `|z|`. */
    val modulus: Double get() = hypot(re, im)

    /** The conjugate, `z̄`. */
    val conjugate: ComplexNum get() = ComplexNum(re, -im)

    /**
     * The principal argument, in `(−π, π]`.
     */
    val argument: Double get() = atan2(im, re).let { if (it <= -Math.PI) Math.PI else it }

    /** True when the imaginary part is zero to working precision. */
    val isReal: Boolean get() = abs(im) < 1e-12

    /** True when the number is exactly zero. */
    val isZero: Boolean get() = re == 0.0 && im == 0.0

    // --------------------------------------------------------- transcendental

    /** The complex exponential, `e^z`. */
    fun exp(): ComplexNum {
        val factor = exp(re)
        return ComplexNum(factor * cos(im), factor * sin(im))
    }

    /**
     * The principal logarithm, `ln|z| + i·arg(z)`.
     *
     * The imaginary part is in `(−π, π]`, so the logarithm is single-valued
     * only on the slit plane.
     */
    fun ln(): ComplexNum {
        if (isZero) return ComplexNum(Double.NEGATIVE_INFINITY, 0.0)
        return ComplexNum(ln(modulus), argument)
    }

    /** The principal logarithm base ten. */
    fun log10(): ComplexNum = ln() / ComplexNum(ln(10.0), 0.0)

    /**
     * The principal power, `z^w = e^(w·ln z)`.
     *
     * For a whole-number power the exact repeated multiplication is used, so
     * the answer does not drift the way the exponential form does.
     */
    fun pow(w: ComplexNum): ComplexNum {
        if (w.im == 0.0 && w.re == Math.rint(w.re) && abs(w.re) <= 1024) {
            return pow(w.re.toInt())
        }
        if (isZero) return ComplexNum(0.0, 0.0)
        return (ln() * w).exp()
    }

    /** An integer power, by repeated multiplication. */
    fun pow(n: Int): ComplexNum {
        if (n == 0) return ComplexNum(1.0, 0.0)
        if (n < 0) return ComplexNum(1.0, 0.0) / pow(-n)
        var acc = ComplexNum(1.0, 0.0)
        var base = this
        var k = n
        while (k > 0) {
            if (k and 1 == 1) acc = acc * base
            base = base * base
            k = k shr 1
        }
        return acc
    }

    /**
     * The principal square root, the one with a non-negative imaginary part.
     */
    fun sqrt(): ComplexNum {
        if (isZero) return ComplexNum(0.0, 0.0)
        val r = modulus
        val a = sqrt((r + re) / 2)
        val b = sqrt((r - re) / 2)
        return ComplexNum(a, if (im < 0) -b else b)
    }

    /**
     * All the `n`-th roots, evenly spaced on the circle of radius
     * `|z|^(1/n)`.
     */
    fun roots(n: Int): List<ComplexNum> {
        if (n < 1) return emptyList()
        val radius = modulus.pow(1.0 / n)
        val start = argument / n
        return (0 until n).map { k ->
            val angle = start + 2 * Math.PI * k / n
            ComplexNum(radius * cos(angle), radius * sin(angle))
        }
    }

    // --------------------------------------------------------- trigonometry

    fun sin(): ComplexNum {
        // sin(a+bi) = sin a cosh b + i cos a sinh b
        val s = sin(re) * cosh(im)
        val c = cos(re) * sinh(im)
        return ComplexNum(s, c)
    }

    fun cos(): ComplexNum {
        // cos(a+bi) = cos a cosh b - i sin a sinh b
        val c = cos(re) * cosh(im)
        val s = sin(re) * sinh(im)
        return ComplexNum(c, -s)
    }

    fun tan(): ComplexNum = sin() / cos()

    fun sinh(): ComplexNum {
        val s = sinh(re) * cos(im)
        val c = cosh(re) * sin(im)
        return ComplexNum(s, c)
    }

    fun cosh(): ComplexNum {
        val c = cosh(re) * cos(im)
        val s = sinh(re) * sin(im)
        return ComplexNum(c, s)
    }

    fun tanh(): ComplexNum = sinh() / cosh()

    // -------------------------------------------------------------- display

    /** The Cartesian form, `a + bi`. */
    fun cartesian(): String {
        if (isZero) return "0"
        if (isReal) return formatReal(re)
        val imaginary = "${formatReal(abs(im))}i"
        if (re == 0.0) return if (im < 0) "−$imaginary" else imaginary
        return "${formatReal(re)} ${if (im < 0) "−" else "+"} $imaginary"
    }

    /** The polar form, `r·e^(iθ)` with the angle in degrees or radians. */
    fun polar(degrees: Boolean = true): String {
        val r = modulus
        val theta = argument
        val angle = if (degrees) theta * 180 / Math.PI else theta
        val unit = if (degrees) "°" else " rad"
        return "${formatReal(r)}·e^(i·${formatReal(angle)}$unit)"
    }

    /** The rectangular form with the angle in radians. */
    fun polarRectangular(): String {
        val r = modulus
        val theta = argument
        return "${formatReal(r)}·(cos ${formatReal(theta)} + i·sin ${formatReal(theta)})"
    }

    override fun toString(): String = cartesian()

    companion object {
        /** The number zero. */
        val ZERO = ComplexNum(0.0, 0.0)
        val ONE = ComplexNum(1.0, 0.0)
        val I = ComplexNum(0.0, 1.0)

        /** Builds a number from a real part and an imaginary part. */
        fun of(re: Double, im: Double = 0.0) = ComplexNum(re, im)

        /** Euler's formula, `e^(iθ) = cos θ + i·sin θ`. */
        fun euler(theta: Double) = ComplexNum(cos(theta), sin(theta))
    }
}

/** The hyperbolic cosine. */
private fun cosh(x: Double): Double = (exp(x) + exp(-x)) / 2

/** The hyperbolic sine. */
private fun sinh(x: Double): Double = (exp(x) - exp(-x)) / 2

/** A real number written the way the rest of the app writes them. */
fun formatReal(value: Double): String {
    if (value.isNaN()) return "undefined"
    if (value.isInfinite()) return if (value > 0) "∞" else "−∞"
    if (value == Math.floor(value) && abs(value) < 1e15) return value.toLong().toString()
    var text = String.format(java.util.Locale.ROOT, "%.6f", value)
    text = text.trimEnd('0').trimEnd('.')
    return if (text.isEmpty() || text == "-") "0" else text
}
