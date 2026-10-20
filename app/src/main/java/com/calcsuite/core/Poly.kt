package com.calcsuite.core

import java.math.BigInteger

/**
 * A polynomial in one variable with exact rational coefficients.
 *
 * Coefficients are stored lowest-degree first: `coeffs[0]` is the constant
 * term and `coeffs[n]` the coefficient of `x^n`. The zero polynomial is the
 * empty list, so `degree` of the zero polynomial is -1.
 */
class Poly private constructor(val coeffs: List<Rat>) {

    companion object {
        val ZERO = Poly(emptyList())
        val ONE = Poly(listOf(Rat.ONE))

        /** Builds a polynomial, dropping trailing zero coefficients. */
        fun of(coeffs: List<Rat>): Poly {
            var end = coeffs.size
            while (end > 0 && coeffs[end - 1].isZero()) end--
            return if (end == 0) ZERO else Poly(coeffs.subList(0, end))
        }

        fun constant(value: Rat): Poly = of(listOf(value))

        /** The polynomial `x^degree`. */
        fun monomial(degree: Int): Poly {
            require(degree >= 0)
            val c = ArrayList<Rat>(degree + 1)
            repeat(degree) { c.add(Rat.ZERO) }
            c.add(Rat.ONE)
            return of(c)
        }
    }

    val degree: Int get() = coeffs.size - 1

    fun isZero(): Boolean = coeffs.isEmpty()

    fun isConstant(): Boolean = coeffs.size <= 1

    fun coefficient(i: Int): Rat = coeffs.getOrElse(i) { Rat.ZERO }

    operator fun plus(other: Poly): Poly {
        val n = maxOf(coeffs.size, other.coeffs.size)
        val out = ArrayList<Rat>(n)
        for (i in 0 until n) out.add(coefficient(i) + other.coefficient(i))
        return of(out)
    }

    operator fun minus(other: Poly): Poly = this + (other * Rat.of(-1L))

    operator fun times(scalar: Rat): Poly = of(coeffs.map { it * scalar })

    operator fun times(other: Poly): Poly {
        if (isZero() || other.isZero()) return ZERO
        val out = MutableList<Rat>(coeffs.size + other.coeffs.size - 1) { Rat.ZERO }
        for (i in coeffs.indices) {
            for (j in other.coeffs.indices) {
                out[i + j] = out[i + j] + coeffs[i] * other.coeffs[j]
            }
        }
        return of(out)
    }

    /** Exact polynomial division; the remainder is returned alongside. */
    fun divmod(other: Poly): Pair<Poly, Poly> {
        require(!other.isZero()) { "Division by the zero polynomial" }
        if (degree < other.degree) return ZERO to this
        val remainder = ArrayList<Rat>(coeffs)
        val quotient = List<Rat>(degree - other.degree + 1) { Rat.ZERO }.toMutableList()
        val divisor = other.coeffs
        val top = divisor.size - 1
        // Long division works from the leading term downwards, so the loop runs
        // from the highest quotient coefficient to the lowest.
        for (k in (degree - other.degree) downTo 0) {
            val factor = remainder[k + top] / divisor[top]
            quotient[k] = factor
            if (factor.isZero()) continue
            for (j in divisor.indices) {
                remainder[k + j] = remainder[k + j] - factor * divisor[j]
            }
        }
        return of(quotient) to of(remainder)
    }

    /** The constant term. */
    fun constantTerm(): Rat = coefficient(0)

    /** The formal derivative. */
    fun derivative(): Poly {
        if (degree < 1) return ZERO
        val out = ArrayList<Rat>(degree)
        for (i in 1..degree) out.add(coefficient(i) * Rat.of(i.toLong()))
        return of(out)
    }

    /** Evaluates the polynomial at [x] in double precision. */
    fun evaluateDouble(x: Double): Double {
        var acc = 0.0
        for (i in coeffs.indices.reversed()) acc = acc * x + coeffs[i].toDouble()
        return acc
    }

    /** Evaluates the polynomial at [x]. */
    fun evaluate(x: Rat): Rat {
        var acc = Rat.ZERO
        for (i in coeffs.indices.reversed()) acc = acc * x + coeffs[i]
        return acc
    }

    /** The greatest common divisor of the coefficients, as a positive rational. */
    fun content(): Rat {
        if (isZero()) return Rat.ONE
        var num = BigInteger.ZERO
        var den = BigInteger.ONE
        for (c in coeffs) {
            num = num.gcd(c.num)
            den = den.gcd(c.den)
        }
        return Rat.of(num, den)
    }

    /** Divides through by the content so the polynomial is primitive. */
    fun primitive(): Poly {
        val c = content()
        return if (c == Rat.ONE) this else this * (Rat.ONE / c)
    }

    /** Removes repeated factors of `x`, i.e. factors the root zero. */
    fun withoutZeroRoot(): Poly {
        if (isZero() || constantTerm().isZero()) return this
        return divmod(monomial(1)).first
    }

    override fun toString(): String = buildString {
        var first = true
        for (i in coeffs.indices.reversed()) {
            val c = coeffs[i]
            if (c.isZero()) continue
            val negative = c.signum() < 0
            // The separator carries the sign, so only the magnitude follows it.
            if (!first) append(if (negative) " − " else " + ")
            val magnitude = if (negative) c.abs() else c
            if (i == 0) {
                // The constant term carries its own sign when it comes first.
                append(if (first && negative) c.display() else magnitude.display())
            } else if (magnitude != Rat.ONE) {
                append(magnitude.display())
                append("·")
            } else if (negative) {
                append("−")
            }
            if (i > 0) {
                append("x")
                if (i > 1) append("^$i")
            }
            first = false
        }
        if (isEmpty()) append("0")
    }
}
