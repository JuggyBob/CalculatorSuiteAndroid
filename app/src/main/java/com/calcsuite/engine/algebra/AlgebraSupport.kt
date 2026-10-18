package com.calcsuite.engine.algebra

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Working precision shared by the algebra engines (34 significant digits). */
internal val ALG_CTX: MathContext = MathContext(34, RoundingMode.HALF_UP)

internal fun bd(text: String): BigDecimal = BigDecimal(text)

/** Plain text without insignificant trailing zeros, used inside steps/messages. */
internal fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

/** Rounds for display inside explanation strings. */
internal fun roundForText(value: BigDecimal, digits: Int = 12): BigDecimal =
    value.round(MathContext(digits, RoundingMode.HALF_UP))

/** Newton square root. Returns null when [value] is negative. */
internal fun bigSqrt(value: BigDecimal, context: MathContext = ALG_CTX): BigDecimal? {
    if (value.signum() < 0) return null
    if (value.signum() == 0) return BigDecimal.ZERO
    if (value.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ONE
    val work = MathContext(context.precision + 12, RoundingMode.HALF_UP)
    val two = BigDecimal.valueOf(2)
    val tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(context.precision + 6))
    var x = sqrtSeed(value)
    repeat(400) {
        val next = x.add(value.divide(x, work), work).divide(two, work)
        if (next.subtract(x).abs() < tolerance) return next.round(context)
        x = next
    }
    return x.round(context)
}

private fun sqrtSeed(value: BigDecimal): BigDecimal {
    val d = value.toDouble()
    if (d.isFinite() && d > 0.0) {
        val s = Math.sqrt(d)
        if (s.isFinite() && s > 0.0) return BigDecimal.valueOf(s)
    }
    val exponent = value.precision() - value.scale() - 1
    return BigDecimal.ONE.scaleByPowerOfTen(Math.floorDiv(exponent, 2))
}

/** Newton n-th root. Returns null for negative input (even roots) or n <= 0. */
internal fun bigNthRoot(value: BigDecimal, n: Int, context: MathContext = ALG_CTX): BigDecimal? {
    if (n <= 0) return null
    if (value.signum() < 0) {
        if (n % 2 == 0) return null
        val positive = bigNthRoot(value.negate(), n, context) ?: return null
        return positive.negate()
    }
    if (value.signum() == 0) return BigDecimal.ZERO
    if (n == 1) return value.round(context)
    if (n == 2) return bigSqrt(value, context)
    val work = MathContext(context.precision + 12, RoundingMode.HALF_UP)
    val nBd = BigDecimal.valueOf(n.toLong())
    val nMinusOne = BigDecimal.valueOf((n - 1).toLong())
    val tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(context.precision + 6))
    var x = nthRootSeed(value, n)
    repeat(400) {
        val power = x.pow(n - 1, work)
        val next = x.multiply(nMinusOne, work)
            .add(value.divide(power, work), work)
            .divide(nBd, work)
        if (next.subtract(x).abs() < tolerance) return next.round(context)
        x = next
    }
    return x.round(context)
}

private fun nthRootSeed(value: BigDecimal, n: Int): BigDecimal {
    val d = value.toDouble()
    if (d.isFinite() && d > 0.0) {
        val s = Math.pow(d, 1.0 / n)
        if (s.isFinite() && s > 0.0) return BigDecimal.valueOf(s)
    }
    val exponent = value.precision() - value.scale() - 1
    return BigDecimal.ONE.scaleByPowerOfTen(Math.floorDiv(exponent, n))
}
