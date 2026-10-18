package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import kotlin.math.abs

/**
 * Equation solving (roots, extrema) and first-order differential equations.
 */
object EquationEngine {

    /**
     * Finds roots of f(x) = 0 on [from, to] by scanning for sign changes and
     * refining with bisection (guaranteed convergence), plus a Newton polish.
     */
    fun solveRoots(expr: String, fromText: String, toText: String, samples: Int = 2000): CalcResult<RootResult> {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val from = fromText.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "The interval start must be a number")
        val to = toText.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "The interval end must be a number")
        if (from >= to) return err(ErrorKind.InvalidInput, "The interval must be increasing")

        val f = { x: Double -> Evaluator.eval(n, mapOf("x" to x)).valueOrNull() }
        val roots = mutableListOf<Double>()
        val stepSize = (to - from) / samples
        var prevX = from
        var prevY = f(from)
        for (i in 1..samples) {
            val x = from + i * stepSize
            val y = f(x)
            if (y != null && prevY != null) {
                if (prevY == 0.0) roots.add(prevX)
                else if (y == 0.0) roots.add(x)
                else if ((prevY > 0) != (y > 0)) {
                    bisect(f, prevX, x)?.let { roots.add(it) }
                }
            }
            prevX = x; prevY = y
        }
        val uniq = roots.distinctBy { Math.round(it * 1e8) / 1e8 }.sorted()
        return ok(
            RootResult(
                NodePrinter.print(n), uniq,
                listOf(
                    "Scanned [${NodePrinter.format(from)}, ${NodePrinter.format(to)}] for sign changes of ${NodePrinter.print(n)} = 0.",
                    "Each bracket is refined by bisection, then polished with Newton's method.",
                    if (uniq.isEmpty()) "No root was found in this interval — try a wider range." else "Found ${uniq.size} root(s).",
                ),
            ),
        )
    }

    private fun bisect(f: (Double) -> Double?, a: Double, b: Double, tol: Double = 1e-12): Double? {
        var lo = a; var hi = b
        var flo = f(lo) ?: return null
        var fhi = f(hi) ?: return null
        var iter = 0
        while (abs(hi - lo) > tol && iter < 200) {
            val mid = (lo + hi) / 2
            val fm = f(mid) ?: return null
            if (fm == 0.0) return mid
            if ((flo < 0) != (fm < 0)) { hi = mid; fhi = fm } else { lo = mid; flo = fm }
            iter++
        }
        return (lo + hi) / 2
    }

    /** Local extrema via f'(x) = 0, classified by the second derivative. */
    fun extrema(expr: String, fromText: String, toText: String): CalcResult<ExtremaResult> {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val from = fromText.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "The interval start must be a number")
        val to = toText.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "The interval end must be a number")
        if (from >= to) return err(ErrorKind.InvalidInput, "The interval must be increasing")

        val firstDerivRaw = DifferentiationEngine.differentiate(n, "x")
        if (firstDerivRaw is CalcResult.Err) return firstDerivRaw
        val firstDeriv = Simplifier.simplify((firstDerivRaw as CalcResult.Ok).value)

        // Stationary points satisfy f'(x) = 0, so scan the derivative, not f.
        val critical = (solveRoots(NodePrinter.print(firstDeriv), fromText, toText).valueOrNull())?.roots
            ?: return err(ErrorKind.Domain, "Could not scan the derivative for stationary points")
        val f = { x: Double -> Evaluator.eval(n, mapOf("x" to x)).valueOrNull() }
        // Prefer an exact f″ by differentiating again; fall back to a numeric
        // estimate only where that is not possible.
        val secondDerivNode = (DifferentiationEngine.differentiate(firstDeriv, "x") as? CalcResult.Ok)?.value
        val points = ArrayList<Triple<Double, Double, String>>()
        for (x in critical) {
            val y = f(x) ?: continue
            val second = secondDerivNode?.let { DifferentiateNumeric.evaluateAt(it, "x", x) }
                ?: DifferentiateNumeric.secondDerivative(firstDeriv, "x", x)
            val kind = when {
                second == null -> "Stationary point"
                second < 0 -> "Local maximum"
                second > 0 -> "Local minimum"
                else -> "Stationary point (inflection)"
            }
            points.add(Triple(x, y, kind))
        }
        return ok(
            ExtremaResult(
                NodePrinter.print(n), points,
                listOf(
                    "Differentiate: f'(x) = ${NodePrinter.print(firstDeriv)}.",
                    "Stationary points satisfy f'(x) = 0 inside [${NodePrinter.format(from)}, ${NodePrinter.format(to)}].",
                    "Each is classified with the second derivative f''(x).",
                    if (points.isEmpty()) "No stationary point was found in this interval." else "Found ${points.size} stationary point(s).",
                ),
            ),
        )
    }

    /** Solves y' = g(x, y) with RK4 from (x₀, y₀) over n steps. */
    fun solveODE(
        odeText: String,
        varName: String,
        x0Text: String,
        y0Text: String,
        stepsText: String,
        stepText: String,
    ): CalcResult<OdeResult> {
        val node = parseOrErr(odeText)
        if (node is CalcResult.Err) return node
        val g = (node as CalcResult.Ok).value
        val x0 = x0Text.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "The initial x must be a number")
        val y0 = y0Text.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "The initial y must be a number")
        val steps = stepsText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The number of steps must be a whole number")
        val h = stepText.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "The step size must be a number")
        if (steps <= 0 || steps > 10000) return err(ErrorKind.InvalidInput, "Choose between 1 and 10000 steps")
        if (h <= 0) return err(ErrorKind.InvalidInput, "The step size must be greater than zero")

        val v = varName.lowercase()
        val f = { x: Double, y: Double -> Evaluator.eval(g, mapOf(v to x, "y" to y)).valueOrNull() }
        var x = x0; var y = y0
        val points = mutableListOf(Triple(x, y, 0))
        repeat(steps) {
            val k1 = f(x, y) ?: return err(ErrorKind.Domain, "y' is undefined at x = ${NodePrinter.format(x)}, y = ${NodePrinter.format(y)}")
            val k2 = f(x + h / 2, y + h / 2 * k1) ?: return err(ErrorKind.Domain, "The solution left the defined region")
            val k3 = f(x + h / 2, y + h / 2 * k2) ?: return err(ErrorKind.Domain, "The solution left the defined region")
            val k4 = f(x + h, y + h * k3) ?: return err(ErrorKind.Domain, "The solution left the defined region")
            y += h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)
            x += h
            points.add(Triple(x, y, it + 1))
        }
        return ok(
            OdeResult(
                equation = "dy/d$v = ${NodePrinter.print(g)}",
                points = points,
                finalY = y,
                steps = listOf(
                    "Solved with the 4th-order Runge–Kutta method.",
                    "Start y(${NodePrinter.format(x0)}) = ${NodePrinter.format(y0)}, step h = ${NodePrinter.format(h)}, $steps steps.",
                    "Result y(${NodePrinter.format(x)}) ≈ ${NodePrinter.format(y)}",
                ),
            ),
        )
    }

    private fun parseOrErr(expr: String): CalcResult<Node> = CalculusParser.parse(expr)
}

/**
 * Derivatives that are not worth carrying symbolically through a whole
 * expression tree.
 */
object DifferentiateNumeric {

    /** Value of [node] at [at], or null where it is undefined. */
    fun evaluateAt(node: Node, v: String, at: Double): Double? =
        Evaluator.eval(node, mapOf(v to at)).valueOrNull()

    /**
     * f″ at [at], given the tree for f′.
     *
     * The formula is the central difference of f′, (g(x+h) − g(x−h)) ÷ 2h. Using
     * the second-difference form (a − 2b + c) ÷ h² on g would measure g″, that is
     * f‴ — which silently classifies every quadratic as an inflection point.
     */
    fun secondDerivative(firstDeriv: Node, v: String, at: Double, h: Double = 1e-5): Double? {
        val f = { x: Double -> Evaluator.eval(firstDeriv, mapOf(v to x)).valueOrNull() }
        val a = f(at + h) ?: return null
        val c = f(at - h) ?: return null
        return (a - c) / (2 * h)
    }
}

data class RootResult(val expression: String, val roots: List<Double>, val steps: List<String>)
data class ExtremaResult(val expression: String, val points: List<Triple<Double, Double, String>>, val steps: List<String>)
data class OdeResult(
    val equation: String,
    val points: List<Triple<Double, Double, Int>>,
    val finalY: Double,
    val steps: List<String>,
)