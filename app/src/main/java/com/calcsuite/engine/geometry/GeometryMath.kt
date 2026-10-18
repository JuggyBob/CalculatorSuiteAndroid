package com.calcsuite.engine.geometry

import com.calcsuite.core.Num
import com.calcsuite.expr.Consts
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Shared numeric helpers for the geometry engines.
 *
 * Everything that can be computed at [Num.EVAL_CONTEXT] precision is: square
 * roots use a Newton iteration and forward trigonometry uses Taylor series with
 * argument reduction, so π-based results keep the 34-digit precision of
 * [Consts.PI]. Inverse trigonometry (asin/acos/atan) falls back to
 * `kotlin.math` doubles — those results are correct to about 15 significant
 * digits and are reported as approximate notes by the engines.
 *
 * All engine results are pure [BigDecimal]s; nothing here touches Android.
 */
internal object GeometryMath {

    val CTX: MathContext = Num.EVAL_CONTEXT

    val PI: BigDecimal = Consts.PI
    val HALF_PI: BigDecimal = PI.divide(BigDecimal("2"), CTX)
    val QUARTER_PI: BigDecimal = PI.divide(BigDecimal("4"), CTX)
    val TWO_PI: BigDecimal = PI.multiply(BigDecimal("2"), CTX)

    private val ONE_HUNDRED_EIGHTY = BigDecimal("180")
    private val TWO = BigDecimal("2")
    private val TINY: BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(-40)

    // ------------------------------------------------------------------- roots

    /** Square root of a non-negative value, correct to the context precision. */
    fun sqrt(value: BigDecimal, ctx: MathContext = CTX): BigDecimal {
        require(value.signum() >= 0) { "Square root of a negative number" }
        if (value.signum() == 0) return BigDecimal.ZERO
        val asDouble = value.toDouble()
        var guess = if (asDouble.isFinite() && asDouble > 0.0) {
            BigDecimal.valueOf(kotlin.math.sqrt(asDouble))
        } else {
            BigDecimal.ONE.scaleByPowerOfTen(value.precision() - value.scale())
        }
        if (guess.signum() <= 0) guess = BigDecimal.ONE
        repeat(100) {
            val next = guess.add(value.divide(guess, ctx), ctx).divide(TWO, ctx)
            val delta = next.subtract(guess).abs()
            if (next.compareTo(guess) == 0 ||
                delta.scaleByPowerOfTen(ctx.precision + 1).compareTo(next.abs()) < 0
            ) {
                return next
            }
            guess = next
        }
        return guess
    }

    /** √(a² + b²). */
    fun hypot(a: BigDecimal, b: BigDecimal): BigDecimal =
        sqrt(a.multiply(a, CTX).add(b.multiply(b, CTX), CTX))

    // ---------------------------------------------------------- forward trig

    fun radians(degrees: BigDecimal): BigDecimal =
        degrees.multiply(PI, CTX).divide(ONE_HUNDRED_EIGHTY, CTX)

    fun degrees(radians: BigDecimal): BigDecimal =
        radians.multiply(ONE_HUNDRED_EIGHTY, CTX).divide(PI, CTX)

    fun sinDegrees(degrees: BigDecimal): BigDecimal = sinCos(radians(degrees)).first

    fun cosDegrees(degrees: BigDecimal): BigDecimal = sinCos(radians(degrees)).second

    fun sinRadians(radians: BigDecimal): BigDecimal = sinCos(radians).first

    fun cosRadians(radians: BigDecimal): BigDecimal = sinCos(radians).second

    /** cot(x) for x in radians; null when sin(x) rounds to zero (x = kπ). */
    fun cotRadians(radians: BigDecimal): BigDecimal? {
        val (sin, cos) = sinCos(radians)
        if (sin.signum() == 0) return null
        return cos.divide(sin, CTX)
    }

    fun cotDegrees(degrees: BigDecimal): BigDecimal? = cotRadians(radians(degrees))

    /** (sin x, cos x) at full context precision; any real x is reduced mod 2π. */
    fun sinCos(x: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val turns = x.divide(TWO_PI, 0, RoundingMode.FLOOR)
        val r0 = x.subtract(turns.multiply(TWO_PI, CTX), CTX) // [0, 2π)
        var r = r0
        if (r.signum() < 0) r = r.add(TWO_PI, CTX)
        var quadrant = r.divide(HALF_PI, 0, RoundingMode.FLOOR).toInt().coerceIn(0, 3)
        val rest = r.subtract(HALF_PI.multiply(BigDecimal(quadrant), CTX), CTX)
        val (sinRest, cosRest) = halfPiSinCos(rest)
        return when (quadrant) {
            0 -> sinRest to cosRest
            1 -> cosRest to sinRest.negate()
            2 -> sinRest.negate() to cosRest.negate()
            else -> cosRest.negate() to sinRest
        }
    }

    /** (sin r, cos r) for r in [0, π/2]. */
    private fun halfPiSinCos(r: BigDecimal): Pair<BigDecimal, BigDecimal> =
        if (r <= QUARTER_PI) {
            series(r)
        } else {
            val (s, c) = series(r.subtract(HALF_PI, CTX))
            c to s.negate()
        }

    /** Taylor series of sin/cos for |x| ≤ π/4 (converges in well under 40 terms). */
    private fun series(x: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val x2 = x.multiply(x, CTX)
        var sinTerm = x
        var cosTerm = BigDecimal.ONE
        var sinSum = x
        var cosSum = BigDecimal.ONE
        var k = 1
        while (k <= 200) {
            cosTerm = cosTerm.multiply(x2, CTX)
                .divide(BigDecimal.valueOf(2L * k * (2L * k - 1)), CTX)
                .negate()
            sinTerm = sinTerm.multiply(x2, CTX)
                .divide(BigDecimal.valueOf(2L * k * (2L * k + 1)), CTX)
                .negate()
            cosSum = cosSum.add(cosTerm, CTX)
            sinSum = sinSum.add(sinTerm, CTX)
            if (cosTerm.abs().compareTo(TINY) < 0 && sinTerm.abs().compareTo(TINY) < 0) break
            k++
        }
        return sinSum to cosSum
    }

    // --------------------------------------------------------- inverse trig

    /** atan(x) in degrees (double precision, ~15 significant digits). */
    fun atanDegrees(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        return BigDecimal.valueOf(Math.toDegrees(kotlin.math.atan(x.toDouble())))
    }

    /** atan2(y, x) in degrees (double precision, ~15 significant digits). */
    fun atan2Degrees(y: BigDecimal, x: BigDecimal): BigDecimal {
        if (y.signum() == 0 && x.signum() == 0) return BigDecimal.ZERO
        return BigDecimal.valueOf(Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())))
    }

    /** acos(v) in degrees for v in [-1, 1]; values outside are clamped. */
    fun acosDegrees(v: BigDecimal): BigDecimal {
        val d = v.toDouble().coerceIn(-1.0, 1.0)
        return BigDecimal.valueOf(Math.toDegrees(kotlin.math.acos(d)))
    }

    /** asin(v) in degrees for v in [-1, 1]; values outside are clamped. */
    fun asinDegrees(v: BigDecimal): BigDecimal {
        val d = v.toDouble().coerceIn(-1.0, 1.0)
        return BigDecimal.valueOf(Math.toDegrees(kotlin.math.asin(d)))
    }

    // ------------------------------------------------------------- formatting

    /** Compact display form used inside step strings (12 significant digits). */
    fun n(value: BigDecimal): String = Num.format(value, 12)
}
