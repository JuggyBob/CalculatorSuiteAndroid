package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.formatNumber

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Evaluates an expression at a point, returning `NaN` when it is undefined.
 *
 * `NaN` marks every way an expression can fail to exist: the logarithm of a
 * non-positive number, the square root of a negative one, a division by zero.
 * The scan uses that to find the domain.
 */
object NumericEval {

    fun eval(expr: IExpr, variables: Map<String, Double>): Double = when (expr) {
        is IExpr.Num -> expr.value.toDouble()
        is IExpr.Var -> variables[expr.name] ?: Double.NaN
        is IExpr.Neg -> -eval(expr.a, variables)
        is IExpr.Add -> eval(expr.a, variables) + eval(expr.b, variables)
        is IExpr.Sub -> eval(expr.a, variables) - eval(expr.b, variables)
        is IExpr.Mul -> eval(expr.a, variables) * eval(expr.b, variables)
        is IExpr.Div -> {
            val denominator = eval(expr.b, variables)
            if (denominator == 0.0) Double.NaN else eval(expr.a, variables) / denominator
        }
        is IExpr.Pow -> {
            val base = eval(expr.a, variables)
            val exponent = eval(expr.b, variables)
            if (base.isNaN() || exponent.isNaN()) Double.NaN
            else if (base < 0 && exponent != Math.floor(exponent)) Double.NaN
            else base.pow(exponent)
        }
        is IExpr.Call -> call(expr, variables)
    }

    private fun call(expr: IExpr.Call, variables: Map<String, Double>): Double {
        val args = expr.args.map { eval(it, variables) }
        if (args.any { it.isNaN() }) return Double.NaN
        return when (expr.name) {
            "abs" -> abs(args[0])
            "sqrt" -> if (args[0] < 0) Double.NaN else sqrt(args[0])
            "cbrt" -> Math.cbrt(args[0])
            "ln" -> if (args[0] <= 0) Double.NaN else ln(args[0])
            "log" -> if (args[0] <= 0) Double.NaN else log10(args[0])
            "log2" -> if (args[0] <= 0) Double.NaN else log10(args[0]) / log10(2.0)
            "exp" -> Math.exp(args[0])
            "sin" -> Math.sin(args[0])
            "cos" -> Math.cos(args[0])
            "tan" -> Math.tan(args[0])
            "asin" -> if (args[0] < -1 || args[0] > 1) Double.NaN else Math.asin(args[0])
            "acos" -> if (args[0] < -1 || args[0] > 1) Double.NaN else Math.acos(args[0])
            "atan" -> Math.atan(args[0])
            "sign" -> args[0].let { if (it > 0) 1.0 else if (it < 0) -1.0 else 0.0 }
            "floor" -> Math.floor(args[0])
            "ceil" -> Math.ceil(args[0])
            "round" -> Math.round(args[0]).toDouble()
            "fact" -> {
                val n = args[0]
                if (n < 0 || n != Math.floor(n) || n > 170) Double.NaN
                else (2..n.toInt()).fold(1.0) { acc, i -> acc * i }
            }
            else -> Double.NaN
        }
    }
}

/** A point found by the scan: either a zero of the expression or a pole. */
data class ScanPoint(val value: Double, val display: String, val isPole: Boolean)

/**
 * Scans the number line for the points where an expression is zero or
 * undefined.
 *
 * The scan samples the expression at a fine grid, notes where the sign flips
 * (a zero) and where the expression stops being defined (a pole), and refines
 * each sign change by bisection.
 *
 * This is inherently approximate. Two zeros closer together than the scan
 * step can be missed, which is why every result derived from a scan is
 * labelled as approximate and the limitation is stated in the explanation.
 */
class NumericScan(
    val roots: List<ScanPoint>,
    val poles: List<ScanPoint>,
    private val samples: List<Pair<Double, Double>>,
) {

    /** The sign of the expression somewhere in the open interval, and whether it satisfies the relation. */
    fun signIn(lo: Double?, hi: Double?, op: CompareOp): Triple<Int, Boolean, Double> {
        val sample = sampleBetween(lo, hi) ?: 0.0
        val value = evaluateAt(sample)
        val sign = when {
            value.isNaN() -> 0
            value > 0 -> 1
            value < 0 -> -1
            else -> 0
        }
        val accepted = when (op) {
            CompareOp.LT -> sign < 0
            CompareOp.GT -> sign > 0
            CompareOp.LE -> sign <= 0
            CompareOp.GE -> sign >= 0
            CompareOp.EQ -> false
        }
        return Triple(sign, accepted, sample)
    }

    /** A point strictly inside the open interval, used to test the sign. */
    fun sampleBetween(lo: Double?, hi: Double?): Double? = when {
        lo != null && hi != null -> (lo + hi) / 2
        lo == null && hi != null -> hi - maxOf(1.0, abs(hi))
        lo != null -> lo + maxOf(1.0, abs(lo))
        else -> 0.0
    }

    private fun evaluateAt(x: Double): Double = samples
        .minByOrNull { abs(it.first - x) }
        ?.second ?: Double.NaN

    companion object {
        const val RANGE = 1000.0
        const val STEPS = 4000

        fun scan(expr: IExpr, variable: String): NumericScan {
            val samples = ArrayList<Pair<Double, Double>>(STEPS + 1)
            val roots = mutableListOf<ScanPoint>()
            val poles = mutableListOf<ScanPoint>()
            val step = 2 * RANGE / STEPS

            var previousX = -RANGE
            var previousValue = NumericEval.eval(expr, mapOf(variable to previousX))
            samples.add(previousX to previousValue)
            if (previousValue == 0.0) roots.add(ScanPoint(previousX, formatNumber(previousX), false))

            for (i in 1..STEPS) {
                val x = -RANGE + step * i
                val value = NumericEval.eval(expr, mapOf(variable to x))
                samples.add(x to value)

                if (value.isNaN()) {
                    if (!previousValue.isNaN()) {
                        // The expression just stopped being defined: a pole.
                        poles.add(ScanPoint(x, "≈" + formatNumber(x), true))
                    }
                } else {
                    if (value == 0.0) {
                        roots.add(ScanPoint(x, formatNumber(x), false))
                    } else if (!previousValue.isNaN() && previousValue != 0.0 &&
                        (previousValue > 0) != (value > 0)
                    ) {
                        val root = bisect(expr, variable, previousX, x)
                        roots.add(ScanPoint(root, "≈" + formatNumber(root), false))
                    }
                }
                previousX = x
                previousValue = value
            }

            return NumericScan(
                roots = roots.distinctBy { Math.round(it.value * 1e6) }.sortedBy { it.value },
                poles = poles.distinctBy { Math.round(it.value * 1e6) }.sortedBy { it.value },
                samples = samples,
            )
        }

        /** Refines a sign change to a root. */
        private fun bisect(expr: IExpr, variable: String, lo: Double, hi: Double): Double {
            var a = lo
            var b = hi
            repeat(120) {
                val mid = (a + b) / 2
                if (b - a < 1e-14) return mid
                val fa = NumericEval.eval(expr, mapOf(variable to a))
                val fm = NumericEval.eval(expr, mapOf(variable to mid))
                if (fm == 0.0) return mid
                if (fa.isNaN()) return mid
                if ((fa > 0) != (fm > 0)) {
                    b = mid
                } else {
                    a = mid
                }
            }
            return (a + b) / 2
        }
    }
}
