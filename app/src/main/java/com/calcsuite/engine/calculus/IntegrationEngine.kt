package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import kotlin.math.abs

/**
 * Integration: exact antiderivatives where the pattern is recognised, and
 * high-accuracy adaptive Simpson otherwise. Every result states which path was
 * used, so nothing is presented as exact when it is numerical.
 */
object IntegrationEngine {

    fun definite(expr: String, varName: String, aText: String, bText: String): CalcResult<IntegrationResult> {
        val node = when (val p = CalculusParser.parse(expr)) {
            is CalcResult.Err -> return err(p.error.kind, p.error.message)
            is CalcResult.Ok -> p.value
        }
        val a = aText.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "Lower limit must be a number")
        val b = bText.trim().toDoubleOrNull() ?: return err(ErrorKind.InvalidInput, "Upper limit must be a number")
        if (a == b) return ok(IntegrationResult(NodePrinter.print(node), "0", "Definite", true,
            listOf("The limits are equal, so the area under the curve is zero.")))

        val sign = if (b > a) 1.0 else -1.0
        val (lo, hi) = if (b > a) a to b else b to a
        val value = adaptiveSimpson(node, varName.lowercase(), lo, hi)
        if (value == null) {
            return err(ErrorKind.Domain, "The integrand has a singularity inside the interval, or is not defined there")
        }
        val signed = sign * value
        return ok(
            IntegrationResult(
                expression = NodePrinter.print(node),
                value = NodePrinter.format(signed),
                method = "Definite (adaptive Simpson)",
                exact = false,
                steps = listOf(
                    "∫ from ${NodePrinter.format(a)} to ${NodePrinter.format(b)} of ${NodePrinter.print(node)} d$varName",
                    "The interval is integrated numerically with adaptive Simpson refinement.",
                    "Result ≈ ${NodePrinter.format(signed)}",
                ),
            ),
        )
    }

    fun indefinite(expr: String, varName: String): CalcResult<IntegrationResult> {
        val node = when (val p = CalculusParser.parse(expr)) {
            is CalcResult.Err -> return err(p.error.kind, p.error.message)
            is CalcResult.Ok -> p.value
        }
        val v = varName.lowercase()
        when (val anti = antiderivative(node, v)) {
            null -> Unit
            else -> {
                val body = anti
                return ok(
                    IntegrationResult(
                        expression = NodePrinter.print(node),
                        value = "${NodePrinter.print(Simplifier.simplify(body))} + C",
                        method = "Indefinite (exact)",
                        exact = true,
                        steps = listOf(
                            "An antiderivative F satisfies F'($v) = ${NodePrinter.print(node)}.",
                            "Differentiate the result to check it.",
                            "∫ ${NodePrinter.print(node)} d$v = ${NodePrinter.print(Simplifier.simplify(body))} + C",
                        ),
                    ),
                )
            }
        }
        return ok(
            IntegrationResult(
                expression = NodePrinter.print(node),
                value = "No elementary antiderivative — use the definite integral",
                method = "Indefinite (not elementary)",
                exact = false,
                steps = listOf(
                    "${NodePrinter.print(node)} has no elementary antiderivative.",
                    "Use Definite integral for a numerical value over an interval.",
                ),
            ),
        )
    }

    private const val vNamePlaceholder = "(variable)"

    /** Exact antiderivatives for the common patterns; null when not recognised. */
    fun antiderivative(n: Node, v: String): Node? {
        val simplified = Simplifier.simplify(n)
        return when {
            simplified is Node.Num -> Node.Mul(Node.Num(simplified.value), Node.Var(v))
            simplified is Node.Var -> Node.Div(Node.Pow(Node.Var(v), Node.Num(2.0)), Node.Num(2.0))
            // -f(x) -> -(∫f). Needed for negative coefficients, which the
            // parser keeps as an explicit Neg rather than folding into the Mul.
            simplified is Node.Neg -> antiderivative(simplified.a, v)?.let { Node.Neg(it) }
            simplified is Node.Mul && isOne(simplified.b) -> antiderivative(simplified.a, v)
            // 1 * f(x)
            simplified is Node.Mul && isOne(simplified.a) -> antiderivative(simplified.b, v)
            // c * x^n -> x^(n+1) / ((n+1) / c). Written this way rather than as
            // c * (x^(n+1) / (n+1)) so the constant folds away and 3x^2
            // integrates to x^3 instead of "3(x^3 ÷ 3)".
            simplified is Node.Mul && simplified.a is Node.Num && simplified.b is Node.Pow &&
                simplified.b.a is Node.Var && simplified.b.b is Node.Num &&
                simplified.b.b.value != -1.0 && simplified.a.value != 0.0 ->
                Node.Div(
                    Node.Pow(Node.Var(v), Node.Num(simplified.b.b.value + 1.0)),
                    Node.Num((simplified.b.b.value + 1.0) / simplified.a.value),
                )
            simplified is Node.Mul && simplified.b is Node.Num && simplified.a is Node.Pow &&
                simplified.a.a is Node.Var && simplified.a.b is Node.Num &&
                simplified.a.b.value != -1.0 && simplified.b.value != 0.0 ->
                Node.Div(
                    Node.Pow(Node.Var(v), Node.Num(simplified.a.b.value + 1.0)),
                    Node.Num((simplified.a.b.value + 1.0) / simplified.b.value),
                )
            // c * f(x) -> c * ∫f, for any other constant c
            simplified is Node.Mul && isConstant(simplified.a, v) -> {
                val inner = antiderivative(simplified.b, v)
                if (inner != null) Node.Mul(simplified.a, inner) else null
            }
            simplified is Node.Mul && isConstant(simplified.b, v) -> {
                val inner = antiderivative(simplified.a, v)
                if (inner != null) Node.Mul(simplified.b, inner) else null
            }
            // x^n
            simplified is Node.Pow && simplified.a is Node.Var && simplified.b is Node.Num &&
                simplified.b.value != -1.0 ->
                Node.Div(
                    Node.Pow(simplified.a, Node.Add(simplified.b, Node.Num(1.0))),
                    Node.Num(simplified.b.value + 1.0),
                )
            // 1/x
            simplified is Node.Div && isOne(simplified.a) -> Node.Fun("ln", listOf(simplified.b))
            // u^n -> u^(n+1)/(n+1) (chain rule of the power rule)
            simplified is Node.Pow && simplified.b is Node.Num && simplified.b.value != -1.0 ->
                Node.Div(
                    Node.Pow(simplified.a, Node.Add(simplified.b, Node.Num(1.0))),
                    Node.Num(simplified.b.value + 1.0),
                )
            // sin(x), cos(x)
            simplified is Node.Fun && simplified.name == "sin" && simplified.args.firstOrNull() is Node.Var -> Node.Mul(Node.Num(-1.0), Node.Fun("cos", listOf(Node.Var(v))))
            simplified is Node.Fun && simplified.name == "cos" && simplified.args.firstOrNull() is Node.Var -> Node.Fun("sin", listOf(Node.Var(v)))
            // e^x
            simplified is Node.Fun && simplified.name == "exp" && simplified.args.firstOrNull() is Node.Var -> Node.Fun("exp", listOf(Node.Var(v)))
            // sum and difference of terms we know individually. A difference
            // must be handled explicitly: x^3 - 0.25 is a perfectly good
            // polynomial, and without this branch it was reported as having no
            // elementary antiderivative.
            simplified is Node.Add -> {
                val left = antiderivative(simplified.a, v)
                val right = antiderivative(simplified.b, v)
                if (left != null && right != null) Node.Add(left, right) else null
            }
            simplified is Node.Sub -> {
                val left = antiderivative(simplified.a, v)
                val right = antiderivative(simplified.b, v)
                if (left != null && right != null) Node.Sub(left, right) else null
            }
            else -> null
        }
    }

    private fun isOne(n: Node) = n is Node.Num && n.value == 1.0

    /** True when [n] does not depend on [v], i.e. its derivative is exactly zero. */
    private fun isConstant(n: Node, v: String): Boolean =
        DifferentiationEngine.isZero(
            (DifferentiationEngine.differentiate(n, v) as? CalcResult.Ok)?.value ?: Node.Num(1.0)
        )

    /** Adaptive Simpson integration; returns null if the integrand is undefined. */
    fun adaptiveSimpson(node: Node, v: String, lo: Double, hi: Double, maxDepth: Int = 24): Double? {
        val f = { x: Double -> Evaluator.eval(node, mapOf(v to x)).valueOrNull() }
        val fLo = f(lo) ?: return null
        val fHi = f(hi) ?: return null
        val fMid = f((lo + hi) / 2) ?: return null
        val whole = simpson(fLo, fMid, fHi, hi - lo)
        return refine(f, lo, hi, whole, 0, maxDepth)
    }

    private fun refine(f: (Double) -> Double?, lo: Double, hi: Double, whole: Double, depth: Int, maxDepth: Int): Double? {
        if (depth >= maxDepth) return whole
        val mid = (lo + hi) / 2
        val fLo = f(lo) ?: return null
        val fHi = f(hi) ?: return null
        val lm = f((lo + mid) / 2); val mm = f(mid); val rm = f((mid + hi) / 2)
        if (lm == null || mm == null || rm == null) return whole
        val left = simpson(fLo, lm, mm, mid - lo)
        val right = simpson(mm, rm, fHi, hi - mid)
        val sum = left + right
        if (abs(sum - whole) < 1e-13 * maxOf(1.0, abs(sum))) return sum
        val l = refine(f, lo, mid, left, depth + 1, maxDepth)
        val r = refine(f, mid, hi, right, depth + 1, maxDepth)
        return (l ?: sum) + (r ?: sum)
    }

    private fun simpson(a: Double, m: Double, b: Double, h: Double): Double = (h / 6) * (a + 4 * m + b)
}

data class IntegrationResult(
    val expression: String,
    val value: String,
    val method: String,
    val exact: Boolean,
    val steps: List<String>,
)