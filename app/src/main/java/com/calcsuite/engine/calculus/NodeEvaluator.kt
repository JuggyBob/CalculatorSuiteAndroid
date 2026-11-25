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
    } catch (e: StackOverflowError) {
        err(ErrorKind.Overflow, TOO_DEEP_MESSAGE)
    }

    class EvalError(val kind: ErrorKind, override val message: String) : Exception(message)

    private fun fail(message: String): Nothing = throw EvalError(ErrorKind.Domain, message)

    fun evalOrThrow(n: Node, env: Map<String, Double>): Double = when (n) {
        is Node.Num -> finiteResult(n.value, "Number")
        is Node.Var -> finiteResult(
            env[n.name.lowercase()]
                ?: throw EvalError(ErrorKind.InvalidInput, "Unknown variable “${n.name}”"),
            "Value of ${n.name}",
        )
        is Node.Const -> Constants.value(n.name) ?: fail("Unknown constant “${n.name}”")
        is Node.Neg -> finiteResult(-evalOrThrow(n.a, env), "Negation")
        is Node.Add -> {
            val a = evalOrThrow(n.a, env)
            val b = evalOrThrow(n.b, env)
            finiteResult(a + b, "Addition")
        }
        is Node.Sub -> {
            val a = evalOrThrow(n.a, env)
            val b = evalOrThrow(n.b, env)
            finiteResult(a - b, "Subtraction")
        }
        is Node.Mul -> {
            val a = evalOrThrow(n.a, env)
            val b = evalOrThrow(n.b, env)
            finiteResult(a * b, "Multiplication")
        }
        is Node.Div -> {
            val d = evalOrThrow(n.b, env)
            if (d == 0.0) fail("Division by zero")
            finiteResult(evalOrThrow(n.a, env) / d, "Division")
        }
        is Node.Pow -> powOf(evalOrThrow(n.a, env), evalOrThrow(n.b, env))
        is Node.Fun -> callFunction(n, env)
    }

    private fun requireFinite(x: Double, what: String): Double {
        if (!x.isFinite()) fail("$what requires a finite value")
        return x
    }

    private fun finiteResult(x: Double, what: String, kind: ErrorKind = ErrorKind.Overflow): Double {
        if (!x.isFinite()) throw EvalError(kind, "$what result is not representable")
        return x
    }

    private fun powOf(base: Double, exp: Double): Double {
        requireFinite(base, "Power base")
        requireFinite(exp, "Power exponent")
        if (base < 0 && exp != floor(exp)) {
            // Complex results are outside the numeric scope; report clearly.
            fail("Negative base with a fractional exponent has no real value")
        }
        if (base == 0.0 && exp < 0) fail("Division by zero")
        return finiteResult(base.pow(exp), "Power")
    }

    private fun callFunction(n: Node.Fun, env: Map<String, Double>): Double {
        val args = n.args.map { evalOrThrow(it, env) }
        fun arg(i: Int): Double = args.getOrNull(i) ?: fail("${n.name} needs ${n.args.size} argument(s)")
        return when (n.name) {
            "sin" -> finiteResult(sin(requireFinite(arg(0), "sin")), "sin")
            "cos" -> finiteResult(cos(requireFinite(arg(0), "cos")), "cos")
            "tan" -> {
                val x = requireFinite(arg(0), "tan")
                // Math.tan returns a huge finite number at a pole, which would
                // otherwise be shown as an ordinary answer.
                if (abs(cos(x)) < 1e-15) fail("tan is undefined at this angle")
                finiteResult(tan(x), "tan")
            }
            "csc" -> {
                val x = requireFinite(arg(0), "csc"); val s = sin(x)
                if (abs(s) < 1e-15) fail("cosecant is undefined here")
                finiteResult(1 / s, "csc")
            }
            "sec" -> {
                val x = requireFinite(arg(0), "sec"); val c = cos(x)
                if (abs(c) < 1e-15) fail("secant is undefined here")
                finiteResult(1 / c, "sec")
            }
            "cot" -> {
                val x = requireFinite(arg(0), "cot"); val s = sin(x)
                if (abs(s) < 1e-15) fail("cotangent is undefined here")
                finiteResult(cos(x) / s, "cot")
            }
            "asin" -> {
                val x = requireFinite(arg(0), "asin")
                if (x < -1.0 || x > 1.0) fail("asin requires a value between −1 and 1")
                finiteResult(asin(x), "asin", ErrorKind.Domain)
            }
            "acos" -> {
                val x = requireFinite(arg(0), "acos")
                if (x < -1.0 || x > 1.0) fail("acos requires a value between −1 and 1")
                finiteResult(acos(x), "acos", ErrorKind.Domain)
            }
            "atan" -> if (args.size == 2) {
                finiteResult(atan2(requireFinite(arg(0), "atan"), requireFinite(arg(1), "atan")), "atan")
            } else {
                finiteResult(atan(requireFinite(arg(0), "atan")), "atan")
            }
            "atan2" -> finiteResult(
                atan2(requireFinite(arg(0), "atan2"), requireFinite(arg(1), "atan2")), "atan2",
            )
            "sinh" -> finiteResult(sinh(requireFinite(arg(0), "sinh")), "sinh")
            "cosh" -> finiteResult(cosh(requireFinite(arg(0), "cosh")), "cosh")
            "tanh" -> finiteResult(tanh(requireFinite(arg(0), "tanh")), "tanh")
            "asinh" -> finiteResult(asinh(requireFinite(arg(0), "asinh")), "asinh")
            "acosh" -> {
                val x = requireFinite(arg(0), "acosh")
                if (x < 1.0) fail("acosh requires a value ≥ 1")
                finiteResult(acosh(x), "acosh", ErrorKind.Domain)
            }
            "atanh" -> {
                val x = requireFinite(arg(0), "atanh")
                if (x <= -1.0 || x >= 1.0) fail("atanh requires a value between −1 and 1")
                finiteResult(atanh(x), "atanh", ErrorKind.Domain)
            }
            "ln" -> {
                val x = requireFinite(arg(0), "ln")
                if (x <= 0.0) fail("ln requires a positive value")
                finiteResult(ln(x), "ln", ErrorKind.Domain)
            }
            "log" -> {
                val x = requireFinite(arg(0), "log")
                if (x <= 0.0) fail("log requires a positive value")
                val value = if (args.size == 2) {
                    val base = requireFinite(arg(1), "log")
                    if (base <= 0.0 || base == 1.0) fail("log base must be positive and not equal to 1")
                    ln(x) / ln(base)
                } else {
                    log10(x)
                }
                finiteResult(value, "log", ErrorKind.Domain)
            }
            "log2" -> {
                val x = requireFinite(arg(0), "log2")
                if (x <= 0.0) fail("log2 requires a positive value")
                finiteResult(log2(x), "log2", ErrorKind.Domain)
            }
            "exp" -> finiteResult(exp(requireFinite(arg(0), "exp")), "exp")
            "sqrt" -> {
                val x = requireFinite(arg(0), "sqrt")
                if (x < 0.0) fail("sqrt requires a non-negative value")
                finiteResult(sqrt(x), "sqrt", ErrorKind.Domain)
            }
            "cbrt" -> finiteResult(cbrt(requireFinite(arg(0), "cbrt")), "cbrt")
            "root" -> {
                val v = requireFinite(arg(0), "root"); val k = requireFinite(arg(1), "root")
                if (k == 0.0) fail("Root degree must not be zero")
                if (v < 0) {
                    if (k != floor(k)) fail("Negative value with a fractional root has no real result")
                    if (floor(k) % 2.0 == 0.0) fail("Even root of a negative value has no real result")
                    finiteResult(-(-v).pow(1.0 / k), "root", ErrorKind.Domain)
                } else {
                    finiteResult(v.pow(1.0 / k), "root", ErrorKind.Domain)
                }
            }
            "abs" -> finiteResult(abs(requireFinite(arg(0), "abs")), "abs")
            "sign" -> finiteResult(sign(requireFinite(arg(0), "sign")), "sign")
            "floor" -> requireFinite(floor(requireFinite(arg(0), "floor")), "floor")
            "ceil" -> requireFinite(ceil(requireFinite(arg(0), "ceil")), "ceil")
            "round" -> {
                val x = requireFinite(arg(0), "round")
                val factor = if (args.size == 2) 10.0.pow(requireFinite(arg(1), "round")) else 1.0
                if (!factor.isFinite() || factor == 0.0) fail("round supports between -300 and 300 digits")
                finiteResult(roundHalfUp(x * factor) / factor, "round")
            }
            "min" -> finiteResult(requireFinite(args.minOrNull() ?: fail("min needs at least one argument"), "min"), "min", ErrorKind.Domain)
            "max" -> finiteResult(requireFinite(args.maxOrNull() ?: fail("max needs at least one argument"), "max"), "max", ErrorKind.Domain)
            "fact" -> finiteResult(factorial(arg(0)), "fact")
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