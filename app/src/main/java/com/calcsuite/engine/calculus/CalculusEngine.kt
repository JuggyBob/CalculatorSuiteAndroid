package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import kotlin.math.abs

/**
 * Limits, infinite series and convergence, Taylor/Maclaurin expansions,
 * summation/product notation and differential equations.
 */
object CalculusEngine {

    // ---------------------------------------------------------------- limits

    fun limit(
        expr: String,
        varName: String,
        pointText: String,
        fromRight: Boolean,
        fromLeft: Boolean,
    ): CalcResult<LimitResult> {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val point = pointText.trim().toDoubleOrNull()
            ?: return err(ErrorKind.InvalidInput, "The limit point must be a number")
        val n = (node as CalcResult.Ok).value
        val v = varName.lowercase()

        // Try direct substitution first.
        val direct = Evaluator.eval(n, mapOf(v to point)).valueOrNull()
        if (direct != null && !direct.isInfinite() && !direct.isNaN()) {
            return ok(
                LimitResult(
                    NodePrinter.print(n), NodePrinter.format(direct), "Direct substitution",
                    listOf("The function is continuous at the point, so lim = f(${NodePrinter.format(point)}) = ${NodePrinter.format(direct)}"),
                ),
            )
        }
        if (point.isInfinite()) {
            return ok(infiniteLimit(n, v, point))
        }
        val side = when {
            fromRight && fromLeft -> "both sides"
            fromRight -> "from the right"
            else -> "from the left"
        }
        val value = numericLimit(n, v, point, fromRight, fromLeft)
            ?: return err(ErrorKind.Domain, "The limit could not be determined (discontinuous or undefined near the point)")
        return ok(
            LimitResult(
                NodePrinter.print(n),
                if (value.isNaN()) "does not exist" else NodePrinter.format(value),
                "Numerical ($side)",
                listOf(
                    "Direct substitution gives 0/0 or an undefined value, so the limit is taken numerically from the $side.",
                    "lim($v → ${NodePrinter.format(point)}) = " + if (value.isNaN()) "does not exist" else NodePrinter.format(value),
                ),
            ),
        )
    }

    private fun infiniteLimit(node: Node, v: String, point: Double): LimitResult {
        // x → +∞  and  x → −∞ : evaluate at a large magnitude and report the trend.
        val step = 1e6
        var x = if (point > 0) step else -step
        val values = mutableListOf<Double>()
        repeat(4) {
            values.add(Evaluator.eval(node, mapOf(v to x)).valueOrNull() ?: Double.NaN)
            x *= 10
        }
        val last = values.lastOrNull() ?: Double.NaN
        val verdict = when {
            last.isNaN() -> "does not exist"
            abs(last) < 1e-8 -> "0"
            abs(last) > 1e12 -> if (last > 0) "∞" else "−∞"
            else -> NodePrinter.format(last)
        }
        return LimitResult(
            NodePrinter.print(node), verdict, "Numerical (large x)",
            listOf(
                "Evaluated at x = ±10⁶, ±10⁷, … to observe the behaviour at infinity.",
                "lim($v → ${if (point > 0) "+∞" else "−∞"}) = $verdict",
            ),
        )
    }

    private fun numericLimit(node: Node, v: String, point: Double, right: Boolean, left: Boolean): Double? {
        val f = { x: Double -> Evaluator.eval(node, mapOf(v to x)).valueOrNull() }
        val h = 1e-4
        val rightVal = if (right) f(point + h) else null
        val leftVal = if (left) f(point - h) else null
        // Both sides requested: the two limits must agree, otherwise no limit.
        if (right && left) {
            if (rightVal == null || leftVal == null) return null
            if (abs(rightVal - leftVal) > 1e-2 * maxOf(1.0, abs(rightVal))) return Double.NaN
            return (rightVal + leftVal) / 2
        }
        return rightVal ?: leftVal
    }

    // -------------------------------------------------------- infinite series

    fun series(expr: String, varName: String, firstTerm: Long, lastTerm: Long): CalcResult<SeriesResult> {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val v = indexVariable(n, varName)
        val kMax = 5000

        // Geometric: r^k  ->  converges when |r| < 1. The exponent must be the
        // index being summed over, otherwise x^2 is not a geometric series.
        if (n is Node.Pow && n.b is Node.Var && n.a is Node.Num &&
            n.b.name.lowercase() == v
        ) {
            val r = n.a.value
            val sum = geometricSeries(v, firstTerm, lastTerm, r)
            if (sum != null) return ok(sum)
        }
        // Divergent helper: n^k or k^n grows.
        if (n is Node.Pow) {
            val term = Evaluator.eval(n, mapOf(v to firstTerm.toDouble())).valueOrNull() ?: 0.0
            val large = Evaluator.eval(n, mapOf(v to (firstTerm + 50).toDouble())).valueOrNull() ?: term
            val verdict = if (abs(large) > abs(term)) "Diverges (terms do not approach zero)" else "Converges numerically"
            return ok(
                SeriesResult(
                    NodePrinter.print(n), verdict,
                    listOf(
                        "Terms: ${NodePrinter.format(term)}, … , ${NodePrinter.format(large)}",
                        "A necessary condition for convergence is that the terms approach 0.",
                    ),
                ),
            )
        }
        val terms = finiteSumTerms(n, v, firstTerm, lastTerm.coerceAtMost(firstTerm + kMax))
        val verdict = when {
            abs(firstTerm.toDouble()) > 1e15 || lastTerm > firstTerm + kMax -> "Series too long to evaluate"
            else -> "Partial sum over the given (finite) range"
        }
        return ok(
            SeriesResult(
                NodePrinter.print(n), verdict,
                listOf(
                    "∑ from ${firstTerm} to ${lastTerm} of ${NodePrinter.print(n)}",
                    if (terms != null) "Partial sum = ${NodePrinter.format(terms)}" else "The terms could not be evaluated",
                ),
            ),
        )
    }

    private fun geometricSeries(index: String, first: Long, last: Long, r: Double): SeriesResult {
        val converges = abs(r) < 1.0
        val steps = mutableListOf<String>(
            "∑ from $first to $last of r^$index with r = ${NodePrinter.format(r)}",
            "Geometric series converges when |r| < 1; here |r| = ${NodePrinter.format(abs(r))}.",
        )
        if (!converges) {
            steps.add("The ratio test shows the terms do not shrink, so the sum has no finite value.")
            return SeriesResult("r^$index", "Diverges (|r| ≥ 1)", steps)
        }
        // ∑ from k=a to b of r^k = (r^a − r^(b+1)) ÷ (1 − r)
        val num = Math.pow(r, first.toDouble()) - Math.pow(r, (last + 1).toDouble())
        val sum = num / (1 - r)
        val value = NodePrinter.format(sum)
        steps.add("Exact sum = (r^$first − r^${last + 1}) ÷ (1 − r) = $value")
        return SeriesResult("r^$index", "Converges", steps)
    }

    private fun finiteSumTerms(node: Node, v: String, from: Long, to: Long): Double? {
        var sum = 0.0
        for (k in from..to) {
            val term = Evaluator.eval(node, mapOf(v to k.toDouble())).valueOrNull() ?: return null
            sum += term
        }
        return sum
    }

    // ------------------------------------------------------------ Taylor

    fun taylor(expr: String, varName: String, centreText: String, orderText: String): CalcResult<SeriesResult> {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val a = centreText.trim().toDoubleOrNull()
            ?: return err(ErrorKind.InvalidInput, "The centre must be a number")
        val order = orderText.trim().toIntOrNull()
            ?: return err(ErrorKind.InvalidInput, "The order must be a whole number")
        if (order < 0 || order > 12) return err(ErrorKind.InvalidInput, "Choose an order between 0 and 12")
        val v = indexVariable(n, varName)

        val steps = mutableListOf("Maclaurin/Taylor expansion of ${NodePrinter.print(n)} about $v = ${NodePrinter.format(a)}")
        var current = n
        var factorial = 1.0
        val terms = mutableListOf<String>()
        val f0 = Evaluator.eval(current, mapOf(v to a)).valueOrNull()
            ?: return err(ErrorKind.Domain, "The function is not defined at $v = ${NodePrinter.format(a)}")
        terms.add("f(a) = ${NodePrinter.format(f0)}")
        for (k in 1..order) {
            val d = DifferentiationEngine.differentiate(current, v).valueOrNull()
                ?: return err(ErrorKind.Domain, "The function cannot be differentiated that many times symbolically")
            // Each derivative is simplified before the next one is taken. Without
            // this the tree grows without bound — differentiating a constant
            // such as (5/2^1)/10 through the quotient rule leaves a removable
            // singularity that evaluates to "undefined" even though the true
            // derivative is exactly 0.
            current = Simplifier.simplify(d)
            factorial *= k
            val fk = Evaluator.eval(current, mapOf(v to a)).valueOrNull()
            if (fk == null) { steps.add("The $k-th derivative is undefined at the centre."); break }
            val coeff = fk / factorial
            val pow = if (k == 1) "$v" else if (k == 2) "$v²" else "$v^$k"
            terms.add("(f^$k / $k!) = ${NodePrinter.format(coeff)}  →  ${NodePrinter.format(coeff)}·${pow.replace(v, "($v − ${NodePrinter.format(a)})")}")
        }
        return ok(SeriesResult(NodePrinter.print(n), "Taylor series (order $order)", steps + terms))
    }

    // ------------------------------------------------- sums & products

    fun summation(expr: String, varName: String, fromText: String, toText: String): CalcResult<SeriesResult> {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val index = indexVariable(n, varName)
        val a = fromText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The lower limit must be a whole number")
        val b = toText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The upper limit must be a whole number")
        if (b < a) return err(ErrorKind.InvalidInput, "The upper limit must not be below the lower limit")
        if (b - a > 100000) return err(ErrorKind.InvalidInput, "The range is too large (max 100000 terms)")
        var sum = 0.0
        for (k in a..b) {
            sum += Evaluator.eval(n, mapOf(index to k.toDouble())).valueOrNull()
                ?: return err(ErrorKind.Domain, "The term at $index = $k is undefined")
        }
        val exact = n is Node.Num
        return ok(
            SeriesResult(
                NodePrinter.print(n), NodePrinter.format(sum),
                listOf(
                    "∑ from $a to $b of ${NodePrinter.print(n)}",
                    "Evaluated ${b - a + 1} terms → ${NodePrinter.format(sum)}",
                    if (exact) "The result is exact (constant term)." else "The result is a numerical accumulation.",
                ),
            ),
        )
    }

    fun product(expr: String, varName: String, fromText: String, toText: String): CalcResult<SeriesResult> {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val index = indexVariable(n, varName)
        val a = fromText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The lower limit must be a whole number")
        val b = toText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The upper limit must be a whole number")
        if (b < a) return err(ErrorKind.InvalidInput, "The upper limit must not be below the lower limit")
        if (b - a > 5000) return err(ErrorKind.InvalidInput, "The range is too large (max 5000 terms)")
        var p = 1.0
        for (k in a..b) {
            p *= Evaluator.eval(n, mapOf(index to k.toDouble())).valueOrNull()
                ?: return err(ErrorKind.Domain, "The factor at $index = $k is undefined")
        }
        return ok(
            SeriesResult(
                NodePrinter.print(n), NodePrinter.format(p),
                listOf("∏ from $a to $b of ${NodePrinter.print(n)}", "Evaluated ${b - a + 1} factors → ${NodePrinter.format(p)}"),
            ),
        )
    }

    private fun parseOrErr(expr: String): CalcResult<Node> = CalculusParser.parse(expr)

    /**
     * The variable an iterated operation should run over.
     *
     * A summation of `0.5^k` has nothing to do with the `x` the user happens to
     * be differentiating in, so rather than making every caller add an index
     * field the engine picks the caller's choice when it appears in the
     * expression and otherwise the first variable that does.
     */
    fun indexVariable(node: Node, preferred: String): String {
        val vars = LinkedHashSet<String>()
        collectVariables(node, vars)
        val choice = preferred.trim().lowercase()
        return when {
            vars.isEmpty() -> choice
            choice in vars -> choice
            else -> vars.first()
        }
    }

    private fun collectVariables(n: Node, into: MutableSet<String>) {
        when (n) {
            is Node.Var -> into.add(n.name.lowercase())
            is Node.Neg -> collectVariables(n.a, into)
            is Node.Add -> { collectVariables(n.a, into); collectVariables(n.b, into) }
            is Node.Sub -> { collectVariables(n.a, into); collectVariables(n.b, into) }
            is Node.Mul -> { collectVariables(n.a, into); collectVariables(n.b, into) }
            is Node.Div -> { collectVariables(n.a, into); collectVariables(n.b, into) }
            is Node.Pow -> { collectVariables(n.a, into); collectVariables(n.b, into) }
            is Node.Fun -> n.args.forEach { collectVariables(it, into) }
            is Node.Num, is Node.Const -> Unit
        }
    }
}

data class LimitResult(val expression: String, val value: String, val method: String, val steps: List<String>)
data class SeriesResult(val expression: String, val verdict: String, val steps: List<String>)