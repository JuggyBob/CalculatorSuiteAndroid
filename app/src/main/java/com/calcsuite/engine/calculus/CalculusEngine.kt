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

    /** Largest number of terms accepted by a sum or a product. */
    private const val MAX_SUM_TERMS = 100_000L
    private const val MAX_PRODUCT_TERMS = 5_000L

    /** Bounds on the index itself, so `k.toDouble()` stays meaningful. */
    private const val MAX_INDEX_MAGNITUDE = 1_000_000_000L

    /** How many terms the convergence heuristics are allowed to sample. */
    private const val MAX_SAMPLE_TERMS = 5_000L

    // ---------------------------------------------------------------- limits

    fun limit(
        expr: String,
        varName: String,
        pointText: String,
        fromRight: Boolean,
        fromLeft: Boolean,
    ): CalcResult<LimitResult> = guarded {
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

    private fun sampleAtInfinity(node: Node, v: String, sign: Double, magnitudes: List<Double>): List<Double> =
        magnitudes.map { x -> Evaluator.eval(node, mapOf(v to sign * x)).valueOrNull() ?: Double.NaN }

    private fun infiniteLimit(node: Node, v: String, point: Double): LimitResult {
        // x → +∞  and  x → −∞ : evaluate at a large magnitude and report the trend.
        val sign = if (point > 0) 1.0 else -1.0
        var values = sampleAtInfinity(node, v, sign, listOf(1e6, 1e7, 1e8, 1e9))
        if (values.all { it.isNaN() }) {
            // Every large sample overflowed (e^x, for instance). Smaller
            // magnitudes still show which way the function is heading.
            values = sampleAtInfinity(node, v, sign, listOf(10.0, 100.0, 1e3, 1e4))
        }
        val finite = values.filter { !it.isNaN() }
        val last = finite.lastOrNull() ?: Double.NaN
        val overflowedAtEnd = finite.size in 1 until values.size &&
            values.takeLast(values.size - finite.size).all { it.isNaN() } && abs(last) > 1e6
        val verdict = when {
            last.isNaN() -> "does not exist"
            abs(last) < 1e-8 -> "0"
            abs(last) > 1e12 || overflowedAtEnd -> if (last > 0) "∞" else "−∞"
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

    fun series(expr: String, varName: String, firstTerm: Long, lastTerm: Long): CalcResult<SeriesResult> = guarded {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val v = indexVariable(n, varName)
        if (lastTerm < firstTerm) {
            return err(ErrorKind.InvalidInput, "The last index must not be below the first index")
        }
        // Work in Long: `lastTerm - firstTerm` in Int/Long can wrap when the
        // caller passes extreme indices.
        val rangeTooLong = firstTerm <= Long.MAX_VALUE - MAX_SAMPLE_TERMS &&
            lastTerm > firstTerm + MAX_SAMPLE_TERMS
        val cappedLast = if (rangeTooLong) firstTerm + MAX_SAMPLE_TERMS else lastTerm

        // Geometric: r^k  ->  converges when |r| < 1. The exponent must be the
        // index being summed over, otherwise x^2 is not a geometric series.
        if (n is Node.Pow && n.b is Node.Var && n.a is Node.Num &&
            n.b.name.lowercase() == v
        ) {
            val r = n.a.value
            // geometricSeries reports divergence, convergence or an overflow
            // in the same result type as every other series.
            return geometricSeries(v, firstTerm, lastTerm, r)
        }
        // k^p is a p-series: it converges only when p < −1. Two sampled terms
        // get smaller for the harmonic series too, so the size heuristic below
        // must not be allowed to call that series convergent.
        if (n is Node.Pow && n.a is Node.Var) {
            val p = numericExponent(n.b)
            if (p != null) {
                val pVerdict = when {
                    p >= 0.0 -> "Diverges (terms do not approach zero)"
                    p >= -1.0 -> "Diverges (p-series with p ≤ 1)"
                    else -> "Converges (p-series with p > 1)"
                }
                return ok(
                    SeriesResult(
                        NodePrinter.print(n), pVerdict,
                        listOf(
                            "∑ from $firstTerm to $lastTerm of ${NodePrinter.print(n)}",
                            "A p-series converges exactly when its exponent is less than −1.",
                        ),
                    ),
                )
            }
        }
        // Divergent helper: n^k or k^n grows.
        if (n is Node.Pow) {
            val term = Evaluator.eval(n, mapOf(v to firstTerm.toDouble())).valueOrNull() ?: 0.0
            val sampleIndex = if (firstTerm > Long.MAX_VALUE - 50) Long.MAX_VALUE else firstTerm + 50
            val large = Evaluator.eval(n, mapOf(v to sampleIndex.toDouble())).valueOrNull() ?: term
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
        val terms = finiteSumTerms(n, v, firstTerm, cappedLast)
        val verdict = when {
            abs(firstTerm.toDouble()) > 1e15 || rangeTooLong -> "Series too long to evaluate"
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

    private fun geometricSeries(index: String, first: Long, last: Long, r: Double): CalcResult<SeriesResult> {
        val converges = abs(r) < 1.0
        val steps = mutableListOf<String>(
            "∑ from $first to $last of r^$index with r = ${NodePrinter.format(r)}",
            "Geometric series converges when |r| < 1; here |r| = ${NodePrinter.format(abs(r))}.",
        )
        if (!converges) {
            steps.add("The ratio test shows the terms do not shrink, so the sum has no finite value.")
            return ok(SeriesResult("r^$index", "Diverges (|r| ≥ 1)", steps))
        }
        // `last + 1` would wrap to Long.MIN_VALUE at Long.MAX_VALUE.
        if (last == Long.MAX_VALUE) {
            return err(ErrorKind.Overflow, "The last index is too large for a geometric sum")
        }
        val lastPlusOne = last + 1
        // ∑ from k=a to b of r^k = (r^a − r^(b+1)) ÷ (1 − r)
        val num = Math.pow(r, first.toDouble()) - Math.pow(r, lastPlusOne.toDouble())
        val sum = num / (1 - r)
        if (!sum.isFinite()) {
            return err(ErrorKind.Overflow, "The geometric sum is not representable")
        }
        val value = NodePrinter.format(sum)
        steps.add("Exact sum = (r^$first − r^$lastPlusOne) ÷ (1 − r) = $value")
        return ok(SeriesResult("r^$index", "Converges", steps))
    }

    /** A numeric exponent, including a leading minus such as `k^-2`. */
    private fun numericExponent(n: Node): Double? = when (n) {
        is Node.Num -> n.value
        is Node.Neg -> numericExponent(n.a)?.let { -it }
        else -> null
    }

    private fun finiteSumTerms(node: Node, v: String, from: Long, to: Long): Double? {
        if (from > to) return null
        var sum = 0.0
        for (k in from..to) {
            val term = Evaluator.eval(node, mapOf(v to k.toDouble())).valueOrNull() ?: return null
            if (!term.isFinite()) return null
            sum += term
            if (!sum.isFinite()) return null
        }
        return sum
    }

    // ------------------------------------------------------------ Taylor

    fun taylor(expr: String, varName: String, centreText: String, orderText: String): CalcResult<SeriesResult> = guarded {
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

    fun summation(expr: String, varName: String, fromText: String, toText: String): CalcResult<SeriesResult> = guarded {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val index = indexVariable(n, varName)
        val a = fromText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The lower limit must be a whole number")
        val b = toText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The upper limit must be a whole number")
        if (b < a) return err(ErrorKind.InvalidInput, "The upper limit must not be below the lower limit")
        // Int subtraction wraps for Int.MIN..Int.MAX, so compare as Long.
        if (b.toLong() - a.toLong() > MAX_SUM_TERMS) {
            return err(ErrorKind.InvalidInput, "The range is too large (max 100000 terms)")
        }
        if (abs(a.toLong()) > MAX_INDEX_MAGNITUDE || abs(b.toLong()) > MAX_INDEX_MAGNITUDE) {
            return err(ErrorKind.InvalidInput, "The summation limits are too large")
        }
        var sum = 0.0
        for (k in a..b) {
            val term = Evaluator.eval(n, mapOf(index to k.toDouble())).valueOrNull()
                ?: return err(ErrorKind.Domain, "The term at $index = $k is undefined")
            if (!term.isFinite()) {
                return err(ErrorKind.Overflow, "The term at $index = $k is not representable")
            }
            sum += term
            if (!sum.isFinite()) return err(ErrorKind.Overflow, "The sum is not representable")
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

    fun product(expr: String, varName: String, fromText: String, toText: String): CalcResult<SeriesResult> = guarded {
        val node = parseOrErr(expr)
        if (node is CalcResult.Err) return node
        val n = (node as CalcResult.Ok).value
        val index = indexVariable(n, varName)
        val a = fromText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The lower limit must be a whole number")
        val b = toText.trim().toIntOrNull() ?: return err(ErrorKind.InvalidInput, "The upper limit must be a whole number")
        if (b < a) return err(ErrorKind.InvalidInput, "The upper limit must not be below the lower limit")
        if (b.toLong() - a.toLong() > MAX_PRODUCT_TERMS) {
            return err(ErrorKind.InvalidInput, "The range is too large (max 5000 terms)")
        }
        if (abs(a.toLong()) > MAX_INDEX_MAGNITUDE || abs(b.toLong()) > MAX_INDEX_MAGNITUDE) {
            return err(ErrorKind.InvalidInput, "The product limits are too large")
        }
        var p = 1.0
        for (k in a..b) {
            val factor = Evaluator.eval(n, mapOf(index to k.toDouble())).valueOrNull()
                ?: return err(ErrorKind.Domain, "The factor at $index = $k is undefined")
            if (!factor.isFinite()) {
                return err(ErrorKind.Overflow, "The factor at $index = $k is not representable")
            }
            p *= factor
            if (!p.isFinite()) return err(ErrorKind.Overflow, "The product is not representable")
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