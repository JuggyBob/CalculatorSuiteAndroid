package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
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
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/** Numeric evaluation of a [Node] with a variable environment. */
object Evaluator {

    fun eval(n: Node, env: Map<String, Double>): CalcResult<Double> = try {
        ok(evalOrThrow(n, env))
    } catch (e: EvalError) {
        err(e.kind, e.message ?: "Could not evaluate the expression")
    }

    class EvalError(val kind: ErrorKind, override val message: String) : Exception(message)

    private fun fail(message: String): Nothing = throw EvalError(ErrorKind.Domain, message)

    fun evalOrThrow(n: Node, env: Map<String, Double>): Double = when (n) {
        is Node.Num -> n.value
        is Node.Var -> env[n.name.lowercase()]
            ?: throw EvalError(ErrorKind.InvalidInput, "Unknown variable “${n.name}”")
        is Node.Const -> Constants.value(n.name) ?: fail("Unknown constant “${n.name}”")
        is Node.Neg -> -evalOrThrow(n.a, env)
        is Node.Add -> evalOrThrow(n.a, env) + evalOrThrow(n.b, env)
        is Node.Sub -> evalOrThrow(n.a, env) - evalOrThrow(n.b, env)
        is Node.Mul -> evalOrThrow(n.a, env) * evalOrThrow(n.b, env)
        is Node.Div -> {
            val d = evalOrThrow(n.b, env)
            if (d == 0.0) fail("Division by zero")
            evalOrThrow(n.a, env) / d
        }
        is Node.Pow -> powOf(evalOrThrow(n.a, env), evalOrThrow(n.b, env))
        is Node.Fun -> callFunction(n, env)
    }

    private fun powOf(base: Double, exp: Double): Double {
        if (base < 0 && exp != floor(exp)) {
            // Complex results are outside the numeric scope; report clearly.
            fail("Negative base with a fractional exponent has no real value")
        }
        if (base == 0.0 && exp < 0) fail("Division by zero")
        return base.pow(exp)
    }

    private fun callFunction(n: Node.Fun, env: Map<String, Double>): Double {
        val args = n.args.map { evalOrThrow(it, env) }
        fun arg(i: Int): Double = args.getOrNull(i) ?: fail("${n.name} needs ${n.args.size} argument(s)")
        return when (n.name) {
            "sin" -> sin(arg(0))
            "cos" -> cos(arg(0))
            "tan" -> tan(arg(0))
            "csc" -> { val s = sin(arg(0)); if (s == 0.0) fail("cosecant is undefined here"); 1 / s }
            "sec" -> { val c = cos(arg(0)); if (c == 0.0) fail("secant is undefined here"); 1 / c }
            "cot" -> { val t = tan(arg(0)); if (t == 0.0) fail("cotangent is undefined here"); 1 / t }
            "asin" -> asin(arg(0))
            "acos" -> acos(arg(0))
            "atan" -> if (args.size == 2) atan2(arg(0), arg(1)) else atan(arg(0))
            "atan2" -> atan2(arg(0), arg(1))
            "sinh" -> sinh(arg(0))
            "cosh" -> cosh(arg(0))
            "tanh" -> tanh(arg(0))
            "asinh" -> asinh(arg(0))
            "acosh" -> acosh(arg(0))
            "atanh" -> atanh(arg(0))
            "ln" -> ln(arg(0))
            "log" -> if (args.size == 2) ln(arg(0)) / ln(arg(1)) else log10(arg(0))
            "log2" -> log2(arg(0))
            "exp" -> exp(arg(0))
            "sqrt" -> sqrt(arg(0))
            "cbrt" -> cbrt(arg(0))
            "root" -> {
                val v = arg(0); val k = arg(1)
                if (k == 0.0) fail("Root degree must not be zero")
                if (v < 0 && k != floor(k)) fail("Negative value with a fractional root has no real result")
                v.pow(1.0 / k)
            }
            "abs" -> abs(arg(0))
            "sign" -> sign(arg(0))
            "floor" -> floor(arg(0))
            "ceil" -> ceil(arg(0))
            "round" -> {
                val factor = if (args.size == 2) 10.0.pow(arg(1)) else 1.0
                roundHalfUp(arg(0) * factor) / factor
            }
            "min" -> args.minOrNull() ?: fail("min needs at least one argument")
            "max" -> args.maxOrNull() ?: fail("max needs at least one argument")
            "fact" -> factorial(arg(0))
            else -> fail("Unknown function “${n.name}”")
        }
    }

    fun roundHalfUp(x: Double): Double {
        val f = floor(x)
        val diff = x - f
        return when {
            diff >= 0.5 -> f + 1
            diff <= -0.5 -> f
            else -> f
        }
    }

    fun factorial(x: Double): Double {
        if (x < 0) fail("Factorial needs a non-negative integer")
        if (x != floor(x)) fail("Factorial needs a whole number")
        if (x > 170) fail("Factorial is limited to 170! for double precision")
        var r = 1.0
        for (i in 2..x.toInt()) r *= i
        return r
    }
}