package com.calcsuite.core

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * An exact rational number, `num / den`, always kept with a positive
 * denominator and in lowest terms.
 *
 * Inequality solving needs exactness: a critical point of `x^2 - 5x + 6` is
 * `2` and `3`, not `2.0000000000001` and `2.9999999999999`. Fractions also let
 * the rational-root theorem work, which is how a cubic like `x^3 - 6x^2 + 11x
 * - 6` is solved exactly rather than numerically.
 *
 * Doubles are used only where the result is genuinely numeric — a root of a
 * cubic with no rational root, for instance — and those are labelled as
 * approximations by the caller.
 */
class Rat private constructor(val num: BigInteger, val den: BigInteger) : Comparable<Rat> {

    companion object {
        val ZERO = Rat(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Rat(BigInteger.ONE, BigInteger.ONE)
        val TWO = Rat(BigInteger.valueOf(2), BigInteger.ONE)

        /** Largest decimal scale accepted by [ofDecimal], matching `Num`. */
        const val MAX_DECIMAL_SCALE = 10_000

        /**
         * Builds a reduced fraction. A zero denominator is a programming error:
         * callers handling user input should use [ofOrNull], which reports it
         * as a null result instead of throwing.
         */
        fun of(num: BigInteger, den: BigInteger): Rat {
            return ofOrNull(num, den) ?: throw IllegalArgumentException("A rational number cannot have a zero denominator")
        }

        /** Null-safe form of [of]: returns null for a zero denominator. */
        fun ofOrNull(num: BigInteger, den: BigInteger): Rat? {
            if (den.signum() == 0) return null
            if (num.signum() == 0) return ZERO
            var n = num
            var d = den
            if (d.signum() < 0) {
                n = n.negate()
                d = d.negate()
            }
            val g = n.gcd(d)
            if (g != BigInteger.ONE) {
                n = n.divide(g)
                d = d.divide(g)
            }
            return Rat(n, d)
        }

        fun of(num: BigInteger): Rat = of(num, BigInteger.ONE)

        fun of(num: Long, den: Long = 1L): Rat =
            of(BigInteger.valueOf(num), BigInteger.valueOf(den))

        /**
         * A rational built from a decimal literal, which is exact by
         * construction. Malformed or out-of-range text maps to [ZERO] so this
         * primitive never throws; use [ofDecimalOrNull] when the caller needs
         * to distinguish an invalid literal from a real zero.
         */
        fun ofDecimal(text: String): Rat = ofDecimalOrNull(text) ?: ZERO

        /**
         * Null-safe form of [ofDecimal]. Returns null for malformed text and
         * for a scale outside ±[MAX_DECIMAL_SCALE], which would otherwise try
         * to allocate an unbounded power of ten.
         */
        fun ofDecimalOrNull(text: String): Rat? {
            val d = try {
                BigDecimal(text)
            } catch (_: NumberFormatException) {
                return null
            }
            if (d.scale() > MAX_DECIMAL_SCALE || d.scale() < -MAX_DECIMAL_SCALE) return null
            // A literal in scientific notation has a negative scale, which means
            // the value is a whole number times a power of ten.
            return if (d.scale() >= 0) {
                ofOrNull(d.unscaledValue(), BigInteger.TEN.pow(d.scale()))
            } else {
                ofOrNull(d.unscaledValue().multiply(BigInteger.TEN.pow(-d.scale())), BigInteger.ONE)
            }
        }

        /** The closest rational to [value] with a denominator of at most [limit]. */
        fun approximate(value: Double, limit: Long = 1_000_000L): Rat {
            if (!value.isFinite()) return ZERO
            val scaled = BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(limit))
            val n = scaled.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact()
            return of(n, BigInteger.valueOf(limit))
        }
    }

    operator fun plus(other: Rat): Rat =
        of(num.multiply(other.den).add(other.num.multiply(den)), den.multiply(other.den))

    operator fun minus(other: Rat): Rat =
        of(num.multiply(other.den).subtract(other.num.multiply(den)), den.multiply(other.den))

    operator fun times(other: Rat): Rat =
        of(num.multiply(other.num), den.multiply(other.den))

    /**
     * Exact division. A zero divisor is a programming error; callers handling
     * user input should use [divOrNull].
     */
    operator fun div(other: Rat): Rat = divOrNull(other) ?: throw IllegalArgumentException("Division by zero")

    /** Null-safe form of [div]: returns null when [other] is zero. */
    fun divOrNull(other: Rat): Rat? =
        if (other.num.signum() == 0) null
        else of(num.multiply(other.den), den.multiply(other.num))

    operator fun unaryMinus(): Rat = Rat(num.negate(), den)

    fun signum(): Int = num.signum()

    /** The magnitude, always non-negative. */
    fun abs(): Rat = if (signum() < 0) -this else this

    /** How the value is written in an explanation, with a true minus sign. */
    fun display(): String = toString().replace('-', '\u2212')

    fun isZero(): Boolean = num.signum() == 0

    fun isOne(): Boolean = num == BigInteger.ONE && den == BigInteger.ONE

    fun isInteger(): Boolean = den == BigInteger.ONE

    /** This value as a [BigInteger], or null when it is not a whole number. */
    fun toBigIntegerOrNull(): BigInteger? = if (isInteger()) num else null

    fun toBigDecimal(context: MathContext): BigDecimal =
        BigDecimal(num).divide(BigDecimal(den), context)

    fun toDouble(): Double = num.toDouble() / den.toDouble()

    override fun compareTo(other: Rat): Int =
        num.multiply(other.den).compareTo(other.num.multiply(den))

    override fun equals(other: Any?): Boolean = other is Rat && num == other.num && den == other.den

    override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()

    /**
     * The exact square root when this value is a perfect square, otherwise null.
     *
     * Both parts of the fraction must be squares, since `sqrt(a/b) = sqrt(a)/sqrt(b)`.
     */
    fun perfectSquareRoot(): Rat? {
        if (num.signum() < 0) return null
        val n = integerSquareRoot(num)
        val d = integerSquareRoot(den)
        return if (n != null && d != null) Rat.of(n, d) else null
    }

    /** The square root of this value as a double. */
    fun squareRootAsDouble(): Double =
        if (num.signum() < 0) Double.NaN else Math.sqrt(num.toDouble() / den.toDouble())

    /**
     * The integer square root of a non-negative integer, or null when it is not
     * a perfect square.
     *
     * Newton's method rather than `BigInteger.sqrt`, which needs API 33 while
     * the app supports API 26.
     */
    private fun integerSquareRoot(value: BigInteger): BigInteger? {
        if (value.signum() < 0) return null
        if (value.signum() == 0) return BigInteger.ZERO
        if (value == BigInteger.ONE) return BigInteger.ONE
        // Start above the root, then halve the gap each step.
        var x = BigInteger.ONE.shiftLeft((value.bitLength() + 1) / 2)
        while (true) {
            val next = x.add(value.divide(x)).shiftRight(1)
            if (next >= x) break
            x = next
        }
        return if (x.multiply(x) == value) x else null
    }

    /**
     * Plain text form. A whole number prints without a fraction; a negative
     * fraction keeps the sign on the numerator.
     */
    override fun toString(): String =
        if (isInteger()) num.toString() else "$num/$den"

    /** A friendlier form for display, e.g. `-1/2` as `-½` is left to the UI. */
    fun toDisplayString(): String = toString()
}
