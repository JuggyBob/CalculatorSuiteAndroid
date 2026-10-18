package com.calcsuite.expr

import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.atanh
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

internal class EvalException(val kind: ErrorKind, message: String) : Exception(message)

/** Result of evaluating an expression: value plus whether it is mathematically exact. */
data class EvalValue(val value: BigDecimal, val exact: Boolean)

/** Exact values for angles that are multiples of 15°, shared by the trig functions. */
private object TrigTable {
    /** sin(k·15°) for k = 0..23 at 30 digits. */
    private val SINS: List<BigDecimal> = listOf(
        "0", "0.258819045102520762348898837624", "0.5", "0.707106781186547524400844362105",
        "0.866025403784438646763723170753", "0.965925826289068286749743199729", "1",
        "0.965925826289068286749743199729", "0.866025403784438646763723170753",
        "0.707106781186547524400844362105", "0.5", "0.258819045102520762348898837624", "0",
        "-0.258819045102520762348898837624", "-0.5", "-0.707106781186547524400844362105",
        "-0.866025403784438646763723170753", "-0.965925826289068286749743199729", "-1",
        "-0.965925826289068286749743199729", "-0.866025403784438646763723170753",
        "-0.707106781186547524400844362105", "-0.5", "-0.258819045102520762348898837624",
    ).map { BigDecimal(it) }

    /**
     * Returns `k` in 0..23 when the angle is a multiple of 15° (within a tight
     * relative tolerance), or null when the value must be computed numerically.
     */
    fun niceIndex(x: BigDecimal, mode: AngleMode): Int? {
        val kExact: BigDecimal = when (mode) {
            AngleMode.DEG -> x.divide(BigDecimal(15), MathContext(40))
            AngleMode.GRAD -> x.multiply(BigDecimal(3), MathContext(40))
                .divide(BigDecimal(50), MathContext(40))
            AngleMode.RAD -> x.divide(
                Consts.PI.divide(BigDecimal(12), MathContext(40)),
                MathContext(40),
            )
        }
        val nearest = kExact.setScale(0, RoundingMode.HALF_UP)
        val diff = kExact.subtract(nearest).abs()
        val tolerance = BigDecimal("1e-12").multiply(maxOf(BigDecimal.ONE, kExact.abs()))
        if (diff > tolerance) return null
        val k = nearest.toInt()
        return ((k % 24) + 24) % 24
    }

    fun sin(k: Int): BigDecimal = SINS[k]

    fun cos(k: Int): BigDecimal = SINS[(k + 6) % 24]

    fun isRational(k: Int): Boolean = k % 2 == 0 // 0°, ±30°, ±90°, ±150° family

    /**
     * Evaluates sin/cos/tan at a multiple of 15°; returns (value, exact) or
     * null when the function is undefined there (tan at 90° + k·180°).
     */
    fun eval(name: String, k: Int): Pair<BigDecimal, Boolean>? = when (name) {
        "sin" -> sin(k).let { it to isRational(k) }
        "cos" -> cos(k).let { it to isRational((k + 6) % 24) }
        else -> { // tan
            val s = sin(k)
            val c = cos(k)
            when {
                c.signum() == 0 -> null
                s.signum() == 0 -> BigDecimal.ZERO to true
                s.abs().compareTo(c.abs()) == 0 -> (if (s.signum() == c.signum()) BigDecimal.ONE else BigDecimal("-1")) to true
                else -> s.divide(c, MathContext(34)) to false
            }
        }
    }
}

internal class Evaluator(private val angleMode: AngleMode) {

    private val ctx = Num.EVAL_CONTEXT

    fun eval(node: Expr): EvalValue = when (node) {
        is Expr.Num -> EvalValue(node.value, true)
        is Expr.Const -> EvalValue(node.value, true)
        is Expr.Unary -> when (node.op) {
            UnOp.Pos -> eval(node.operand)
            UnOp.Neg -> eval(node.operand).let { EvalValue(it.value.negate(ctx), it.exact) }
        }
        is Expr.Postfix -> evalPostfix(node)
        is Expr.Binary -> evalBinary(node)
        is Expr.Call -> evalCall(node)
    }

    // ------------------------------------------------------------- operators

    private fun evalPostfix(node: Expr.Postfix): EvalValue = when (node.op) {
        PostOp.Percent -> {
            val v = eval(node.operand)
            EvalValue(v.value.divide(BigDecimal(100), ctx), v.exact)
        }
        PostOp.Factorial -> factorial(eval(node.operand))
    }

    private fun evalBinary(node: Expr.Binary): EvalValue {
        // Percentage convention: in `a + b%` / `a − b%` the percentage refers to
        // the left operand (the behaviour users expect from everyday calculators).
        if ((node.op == BinOp.Add || node.op == BinOp.Sub) &&
            node.right is Expr.Postfix && node.right.op == PostOp.Percent
        ) {
            val left = eval(node.left)
            val pct = eval(node.right.operand)
            val delta = left.value.multiply(pct.value, ctx).divide(BigDecimal(100), ctx)
            val value = if (node.op == BinOp.Add) left.value.add(delta, ctx) else left.value.subtract(delta, ctx)
            return EvalValue(value, left.exact && pct.exact)
        }

        val l = eval(node.left)
        val r = eval(node.right)
        return when (node.op) {
            BinOp.Add -> EvalValue(l.value.add(r.value, ctx), l.exact && r.exact)
            BinOp.Sub -> EvalValue(l.value.subtract(r.value, ctx), l.exact && r.exact)
            BinOp.Mul -> EvalValue(l.value.multiply(r.value, ctx), l.exact && r.exact)
            BinOp.Div -> divide(l, r)
            BinOp.Mod -> {
                if (r.value.signum() == 0) throw EvalException(ErrorKind.DivisionByZero, "Cannot take the remainder with a zero divisor")
                EvalValue(exactRemainder(l.value, r.value), l.exact && r.exact)
            }
            BinOp.Pow -> power(l, r)
        }
    }

    private fun divide(l: EvalValue, r: EvalValue): EvalValue {
        if (r.value.signum() == 0) throw EvalException(ErrorKind.DivisionByZero, "Division by zero")
        val exact = try {
            EvalValue(l.value.divide(r.value), l.exact && r.exact)
        } catch (_: ArithmeticException) {
            EvalValue(l.value.divide(r.value, ctx), false)
        }
        return exact
    }

    private fun exactRemainder(a: BigDecimal, b: BigDecimal): BigDecimal = a.remainder(b, ctx)

    private fun power(base: EvalValue, exponent: EvalValue): EvalValue {
        val b = base.value
        val e = exponent.value
        val isIntegerExponent = e.stripTrailingZeros().scale() <= 0
        if (isIntegerExponent) {
            val ei = e.toBigInteger()
            if (b.signum() == 0) {
                return when {
                    ei.signum() == 0 -> throw EvalException(ErrorKind.Domain, "0^0 is undefined")
                    ei.signum() < 0 -> throw EvalException(ErrorKind.DivisionByZero, "0 raised to a negative power is undefined")
                    else -> EvalValue(BigDecimal.ZERO, true)
                }
            }
            val absExp = ei.abs()
            // Guard for memory/time: estimated result digits.
            val baseDigits = b.precision().coerceAtLeast(1) + 2
            val estimatedDigits = absExp.toDouble() * baseDigits
            if (estimatedDigits <= 20_000 && absExp <= BigInteger.valueOf(50_000)) {
                val expInt = ei.toInt()
                return if (expInt >= 0) {
                    EvalValue(b.pow(expInt), base.exact && exponent.exact)
                } else {
                    val positive = b.pow(-expInt)
                    divide(EvalValue(BigDecimal.ONE, true), EvalValue(positive, base.exact && exponent.exact))
                }
            }
            // Too large to compute exactly: fall through to floating point.
        }
        if (b.signum() < 0) {
            throw EvalException(ErrorKind.Domain, "A negative number cannot be raised to a non-integer power (no real result)")
        }
        val result = b.toDouble().pow(e.toDouble())
        return inexactOrThrow(result, "Power result is not representable")
    }

    private fun factorial(v: EvalValue): EvalValue {
        val n = v.value.stripTrailingZeros()
        if (n.scale() > 0) throw EvalException(ErrorKind.Domain, "Factorial requires an integer")
        val ni = n.toBigInteger()
        if (ni.signum() < 0) throw EvalException(ErrorKind.Domain, "Factorial requires a non-negative integer")
        if (ni > BigInteger.valueOf(10_000)) {
            throw EvalException(ErrorKind.Overflow, "Factorial larger than 10000! is not supported")
        }
        var acc = BigInteger.ONE
        var i = 2
        val limit = ni.toInt()
        while (i <= limit) {
            acc = acc.multiply(BigInteger.valueOf(i.toLong()))
            i++
        }
        return EvalValue(BigDecimal(acc), v.exact)
    }

    // ------------------------------------------------------------- functions

    private fun evalCall(node: Expr.Call): EvalValue {
        val args = node.args.map { eval(it) }
        val d = args.map { it.value.toDouble() }
        val allExact = args.all { it.exact }
        fun inexact(x: Double, what: String = node.name): EvalValue = inexactOrThrow(x, "\"${what}\" result is not representable")
        fun require(condition: Boolean, message: String) {
            if (!condition) throw EvalException(ErrorKind.Domain, message)
        }

        return when (node.name) {
            "sin", "cos", "tan" -> {
                val k = TrigTable.niceIndex(args[0].value, angleMode)
                val nice = if (k != null) TrigTable.eval(node.name, k) else null
                when {
                    nice != null -> EvalValue(nice.first, nice.second)
                    k != null -> throw EvalException(ErrorKind.Domain, "tan is undefined at 90° + k·180°")
                    else -> when (node.name) {
                        "sin" -> inexact(sin(toRadians(d[0])))
                        "cos" -> inexact(cos(toRadians(d[0])))
                        else -> inexact(tan(toRadians(d[0])))
                    }
                }
            }
            "csc" -> {
                niceOrNull("sin", args[0])?.let { throw EvalException(ErrorKind.Domain, "csc is undefined when sin is zero") }
                require(sin(toRadians(d[0])) != 0.0, "csc is undefined when sin is zero")
                inexact(1.0 / sin(toRadians(d[0])))
            }
            "sec" -> {
                require(cos(toRadians(d[0])) != 0.0, "sec is undefined when cos is zero")
                inexact(1.0 / cos(toRadians(d[0])))
            }
            "cot" -> {
                require(tan(toRadians(d[0])) != 0.0, "cot is undefined when tan is zero")
                inexact(1.0 / tan(toRadians(d[0])))
            }
            "asin" -> {
                require(d[0] in -1.0..1.0, "asin requires a value between −1 and 1")
                angleResult(asin(d[0]))
            }
            "acos" -> {
                require(d[0] in -1.0..1.0, "acos requires a value between −1 and 1")
                angleResult(acos(d[0]))
            }
            "atan" -> angleResult(atan(d[0]))
            "atan2" -> angleResult(atan2(d[0], d[1]))
            "sinh" -> inexact(sinh(d[0]))
            "cosh" -> inexact(cosh(d[0]))
            "tanh" -> inexact(tanh(d[0]))
            "asinh" -> inexact(asinh(d[0]))
            "acosh" -> {
                require(d[0] >= 1.0, "acosh requires a value ≥ 1")
                inexact(acosh(d[0]))
            }
            "atanh" -> {
                require(d[0] > -1.0 && d[0] < 1.0, "atanh requires a value between −1 and 1")
                inexact(atanh(d[0]))
            }
            "ln" -> {
                require(d[0] > 0, "ln requires a positive value")
                inexact(ln(d[0]))
            }
            "log" -> {
                if (args.size == 1) {
                    require(d[0] > 0, "log requires a positive value")
                    inexact(log10(d[0]))
                } else {
                    require(d[0] > 0, "log requires a positive value")
                    require(d[1] > 0 && d[1] != 1.0, "log base must be positive and not equal to 1")
                    inexact(ln(d[0]) / ln(d[1]))
                }
            }
            "log2" -> {
                require(d[0] > 0, "log2 requires a positive value")
                inexact(ln(d[0]) / ln(2.0))
            }
            "exp" -> inexact(exp(d[0]))
            "sqrt" -> {
                require(d[0] >= 0, "sqrt requires a non-negative value")
                rootExactOrInexact(args[0].value, 2)
            }
            "cbrt" -> rootExactOrInexact(args[0].value, 3)
            "root" -> {
                val n = d[1].roundToLong()
                require(n != 0L, "root degree must not be zero")
                if (n % 2 != 0L && args[0].value.signum() < 0) {
                    inexact(-(-d[0]).pow(1.0 / n))
                } else {
                    require(d[0] >= 0, "Even root requires a non-negative value")
                    inexact(d[0].pow(1.0 / n))
                }
            }
            "abs" -> EvalValue(args[0].value.abs(), args[0].exact)
            "sign" -> EvalValue(BigDecimal(args[0].value.signum()), args[0].exact)
            "floor" -> EvalValue(args[0].value.setScale(0, RoundingMode.FLOOR), args[0].exact)
            "ceil" -> EvalValue(args[0].value.setScale(0, RoundingMode.CEILING), args[0].exact)
            "round" -> {
                val digits = if (args.size == 2) d[1].roundToLong().toInt() else 0
                EvalValue(args[0].value.setScale(digits, RoundingMode.HALF_UP), args[0].exact)
            }
            "fact" -> factorial(args[0])
            "ncr" -> combinations(args[0], args[1], ordered = false)
            "npr" -> combinations(args[0], args[1], ordered = true)
            "gcd" -> {
                val a = requireInteger(args[0], "gcd")
                val b = requireInteger(args[1], "gcd")
                EvalValue(BigDecimal(a.gcd(b)), allExact)
            }
            "lcm" -> {
                val a = requireInteger(args[0], "lcm")
                val b = requireInteger(args[1], "lcm")
                if (a.signum() == 0 || b.signum() == 0) EvalValue(BigDecimal.ZERO, allExact)
                else EvalValue(BigDecimal(a.divide(a.gcd(b)).multiply(b).abs()), allExact)
            }
            "min" -> args.minBy { it.value }
            "max" -> args.maxBy { it.value }
            "hypot" -> inexact(hypot(d[0], d[1]))
            "deg" -> EvalValue(args[0].value.multiply(BigDecimal(180), ctx).divide(Consts.PI, ctx), false)
            "rad" -> EvalValue(args[0].value.multiply(Consts.PI, ctx).divide(BigDecimal(180), ctx), false)
            else -> throw EvalException(ErrorKind.Syntax, "Unknown function \"${node.name}\"")
        }
    }

    private fun requireInteger(v: EvalValue, fn: String): BigInteger {
        val stripped = v.value.stripTrailingZeros()
        if (stripped.scale() > 0) throw EvalException(ErrorKind.Domain, "$fn requires integer arguments")
        return stripped.toBigInteger()
    }

    private fun combinations(nVal: EvalValue, rVal: EvalValue, ordered: Boolean): EvalValue {
        val n = requireInteger(nVal, if (ordered) "nPr" else "nCr")
        val r = requireInteger(rVal, if (ordered) "nPr" else "nCr")
        if (n.signum() < 0 || r.signum() < 0) {
            throw EvalException(ErrorKind.Domain, "n and r must be non-negative integers")
        }
        if (r > n) throw EvalException(ErrorKind.Domain, "r must not exceed n")
        if (n > BigInteger.valueOf(1_000_000)) {
            throw EvalException(ErrorKind.Overflow, "n is too large (limit 1,000,000)")
        }
        val k = if (ordered) r else minOf(r, n.subtract(r))
        var result = BigInteger.ONE
        var i = 0
        val kInt = k.toInt()
        while (i < kInt) {
            result = result.multiply(n.subtract(BigInteger.valueOf(i.toLong())))
            i++
            if (!ordered) result = result.divide(BigInteger.valueOf(i.toLong()))
        }
        return EvalValue(BigDecimal(result), nVal.exact && rVal.exact)
    }

    private fun rootExactOrInexact(value: BigDecimal, degree: Int): EvalValue {
        val d = value.toDouble()
        val result = if (degree == 2) sqrt(d) else cbrt(d)
        if (!result.isFinite()) throw EvalException(ErrorKind.Overflow, "Root result is not representable")
        val candidate = BigDecimal.valueOf(result)
        // Check whether the real root is exact by raising the candidate back.
        val back = try {
            candidate.pow(degree).round(MathContext(30, RoundingMode.HALF_UP))
        } catch (_: ArithmeticException) {
            null
        }
        val exact = back != null && back.compareTo(value.round(MathContext(30, RoundingMode.HALF_UP))) == 0
        return EvalValue(if (exact) candidate.stripTrailingZeros() else candidate, exact)
    }

    private fun isTanUndefined(x: BigDecimal): Boolean = false

    /** Convenience for the nice-angle table used by reciprocal functions. */
    private fun niceOrNull(name: String, arg: EvalValue): BigDecimal? {
        val k = TrigTable.niceIndex(arg.value, angleMode) ?: return null
        return TrigTable.eval(name, k)?.first
    }

    /**
     * Inverse trig results are angles. In degree-based modes the value is
     * snapped to the nearest half degree when it is within 1e-9 of it, which
     * turns 30.000000000000004 into exactly 30.
     */
    private fun angleResult(radians: Double): EvalValue = when (angleMode) {
        AngleMode.RAD -> inexactOrThrow(radians, "Angle result is not representable")
        else -> {
            val converted = BigDecimal.valueOf(fromRadians(radians))
            val twice = converted.multiply(BigDecimal(2), MathContext(40)).setScale(0, RoundingMode.HALF_UP)
            val snapped = twice.divide(BigDecimal(2), MathContext(40)).stripTrailingZeros()
            if (converted.subtract(snapped).abs() <= BigDecimal("1e-9")) {
                EvalValue(snapped, true)
            } else {
                EvalValue(converted, false)
            }
        }
    }

    private fun toRadians(x: Double): Double = when (angleMode) {
        AngleMode.DEG -> Math.toRadians(x)
        AngleMode.GRAD -> x * Math.PI / 200.0
        AngleMode.RAD -> x
    }

    private fun fromRadians(x: Double): Double = when (angleMode) {
        AngleMode.DEG -> Math.toDegrees(x)
        AngleMode.GRAD -> x * 200.0 / Math.PI
        AngleMode.RAD -> x
    }

    private fun inexactOrThrow(x: Double, message: String): EvalValue {
        if (x.isNaN()) throw EvalException(ErrorKind.Domain, "The result is not a number")
        if (x.isInfinite()) throw EvalException(ErrorKind.Overflow, message)
        return EvalValue(BigDecimal.valueOf(x), false)
    }
}
