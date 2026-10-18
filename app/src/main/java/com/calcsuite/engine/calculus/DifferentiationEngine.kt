package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.map
import com.calcsuite.core.ok
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Symbolic differentiation (chain rule applied to the tree) plus numerical
 * differentiation for partials and for expressions we do not differentiate
 * symbolically.
 */
object DifferentiationEngine {

    fun derivative(expr: String, varName: String = "x"): CalcResult<DerivativeResult> {
        val parsed = CalculusParser.parse(expr)
        if (parsed is CalcResult.Err) return err(parsed.error.kind, parsed.error.message)
        val node = (parsed as CalcResult.Ok).value
        val d = when (val r = differentiate(node, varName.lowercase())) {
            is CalcResult.Err -> return err(r.error.kind, r.error.message)
            is CalcResult.Ok -> r.value
        }
        val simplified = Simplifier.simplify(d)
        return ok(
            DerivativeResult(
                expression = NodePrinter.print(node),
                derivative = NodePrinter.print(simplified),
                steps = derivativeSteps(node, varName.lowercase()),
                numericAt = null,
            ),
        )
    }

    /**
     * Symbolic derivative of [n] with respect to [v].
     *
     * The result is simplified. That matters for more than tidiness: applying
     * the quotient rule to a constant such as `(5/2^1)/10` produces a
     * denominator of `(2^1)^2`, and repeating that leaves a removable
     * singularity that evaluates as undefined even though the derivative is
     * exactly zero. Simplifying collapses those to `0` before they can be
     * mistaken for a genuinely undefined derivative.
     */
    fun differentiate(n: Node, v: String): CalcResult<Node> =
        differentiateRaw(n, v).map { Simplifier.simplify(it) }

    private fun differentiateRaw(n: Node, v: String): CalcResult<Node> {
        return when (n) {
            is Node.Num -> ok(Node.Num(0.0))
            is Node.Const -> ok(Node.Num(0.0))
            is Node.Var -> ok(if (n.name.lowercase() == v) Node.Num(1.0) else Node.Num(0.0))
            is Node.Neg -> {
                val d = differentiateRaw(n.a, v)
                if (d is CalcResult.Err) d else ok(Node.Neg((d as CalcResult.Ok).value))
            }
            is Node.Add -> combine(n.a, n.b, v) { x, y -> Node.Add(x, y) }
            is Node.Sub -> combine(n.a, n.b, v) { x, y -> Node.Sub(x, y) }
            is Node.Mul -> {
                val da = differentiateRaw(n.a, v)
                val db = differentiateRaw(n.b, v)
                if (da is CalcResult.Err) da
                else if (db is CalcResult.Err) db
                else {
                    val (xa, xb) = (da as CalcResult.Ok).value to (db as CalcResult.Ok).value
                    ok(Node.Add(Node.Mul(xa, n.b), Node.Mul(n.a, xb)))
                }
            }
            is Node.Div -> {
                val da = differentiateRaw(n.a, v)
                val db = differentiateRaw(n.b, v)
                if (da is CalcResult.Err) da
                else if (db is CalcResult.Err) db
                else {
                    val (xa, xb) = (da as CalcResult.Ok).value to (db as CalcResult.Ok).value
                    val num = Node.Sub(Node.Mul(xa, n.b), Node.Mul(n.a, xb))
                    ok(Node.Div(num, Node.Pow(n.b, Node.Num(2.0))))
                }
            }
            is Node.Pow -> powerDerivative(n, v)
            is Node.Fun -> functionDerivative(n, v)
        }
    }

    private inline fun combine(a: Node, b: Node, v: String, f: (Node, Node) -> Node): CalcResult<Node> {
        val da = differentiateRaw(a, v)
        if (da is CalcResult.Err) return da
        val db = differentiateRaw(b, v)
        if (db is CalcResult.Err) return db
        return ok(f((da as CalcResult.Ok).value, (db as CalcResult.Ok).value))
    }

    private fun powerDerivative(n: Node.Pow, v: String): CalcResult<Node> {
        val a = n.a
        val b = n.b
        val da = differentiateRaw(a, v)
        if (da is CalcResult.Err) return da
        val db = differentiateRaw(b, v)
        if (db is CalcResult.Err) return db
        val daV = (da as CalcResult.Ok).value
        val dbV = (db as CalcResult.Ok).value
        if (isZero(dbV)) {
            val powerTerm = Node.Mul(b, Node.Pow(a, Node.Sub(b, Node.Num(1.0))))
            return ok(Node.Mul(powerTerm, daV))
        }
        if (isZero(daV)) {
            val logTerm = Node.Mul(n, Node.Fun("ln", listOf(a)))
            return ok(Node.Mul(logTerm, dbV))
        }
        val term1 = Node.Mul(dbV, Node.Fun("ln", listOf(a)))
        val term2 = Node.Mul(Node.Mul(b, daV), Node.Div(Node.Num(1.0), a))
        return ok(Node.Mul(n, Node.Add(term1, term2)))
    }

    private fun functionDerivative(n: Node.Fun, v: String): CalcResult<Node> {
        val arg = n.args.firstOrNull() ?: return err(ErrorKind.InvalidInput, "${n.name} needs an argument")
        val inner = differentiateRaw(arg, v)
        if (inner is CalcResult.Err) return inner
        val dInner = (inner as CalcResult.Ok).value

        // Outer derivative * inner derivative
        val outer: Node? = when (n.name) {
            "sin" -> Node.Fun("cos", listOf(arg))
            "cos" -> Node.Mul(Node.Num(-1.0), Node.Fun("sin", listOf(arg)))
            "tan" -> Node.Add(Node.Num(1.0), Node.Pow(Node.Fun("tan", listOf(arg)), Node.Num(2.0)))
            "asin" -> Node.Div(Node.Num(1.0), Node.Fun("sqrt", listOf(Node.Sub(Node.Num(1.0), Node.Pow(arg, Node.Num(2.0))))))
            "acos" -> Node.Mul(Node.Num(-1.0), Node.Div(Node.Num(1.0), Node.Fun("sqrt", listOf(Node.Sub(Node.Num(1.0), Node.Pow(arg, Node.Num(2.0)))))))
            "atan" -> Node.Div(Node.Num(1.0), Node.Add(Node.Num(1.0), Node.Pow(arg, Node.Num(2.0))))
            "sinh" -> Node.Fun("cosh", listOf(arg))
            "cosh" -> Node.Fun("sinh", listOf(arg))
            "tanh" -> Node.Sub(Node.Num(1.0), Node.Pow(Node.Fun("tanh", listOf(arg)), Node.Num(2.0)))
            // ln(u) -> u'/u, but ln of a known constant simplifies to a number.
            "ln" -> if (arg is Node.Const && Constants.value(arg.name) != null) {
                Node.Num(0.0)
            } else {
                Node.Div(Node.Num(1.0), arg)
            }
            "log" -> if (n.args.size == 2) Node.Div(Node.Num(1.0), Node.Mul(arg, Node.Fun("ln", listOf(n.args[1])))) else Node.Div(Node.Num(1.0), Node.Mul(arg, Node.Fun("ln", listOf(Node.Num(10.0)))))
            "log2" -> Node.Div(Node.Num(1.0), Node.Mul(arg, Node.Fun("ln", listOf(Node.Num(2.0)))))
            "exp" -> Node.Fun("exp", listOf(arg))
            "sqrt" -> Node.Div(Node.Num(0.5), Node.Fun("sqrt", listOf(arg)))
            "cbrt" -> Node.Div(Node.Num(1.0 / 3.0), Node.Pow(Node.Fun("cbrt", listOf(arg)), Node.Num(2.0)))
            "abs" -> Node.Fun("sign", listOf(arg))
            else -> null
        }
        if (outer == null) {
            return ok(Node.Num(Double.NaN)) // signal: not symbolically differentiable
        }
        return ok(Node.Mul(outer, dInner))
    }

    fun isZero(n: Node): Boolean = when (n) {
        is Node.Num -> n.value == 0.0
        is Node.Add -> isZero(n.a) && isZero(n.b)
        is Node.Sub -> isZero(n.a) && isZero(n.b)
        is Node.Mul -> isZero(n.a) || isZero(n.b)
        is Node.Div -> isZero(n.a)
        else -> false
    }

    /** Human-readable differentiation steps (product/quotient/chain rule). */
    private fun derivativeSteps(n: Node, v: String): List<String> {
        val resultText = (DifferentiationEngine.differentiate(n, v) as? CalcResult.Ok)
            ?.let { NodePrinter.print(Simplifier.simplify(it.value)) } ?: "—"
        return when (n) {
            is Node.Add, is Node.Sub -> listOf(
                "Sum/difference rule: differentiate each term separately.",
                "d/d$v ${NodePrinter.print(n)} = $resultText",
            )
            is Node.Mul -> listOf(
                "Product rule: (uv)' = u'v + uv'.",
                "d/d$v ${NodePrinter.print(n)} = $resultText",
            )
            is Node.Div -> listOf(
                "Quotient rule: (u/v)' = (u'v − uv') / v².",
                "d/d$v ${NodePrinter.print(n)} = $resultText",
            )
            is Node.Pow -> listOf(
                "Power/chain rule: d/d$v a^b = a^b · (b'·ln a + b·a'/a).",
                "Result: $resultText",
            )
            is Node.Fun -> listOf(
                "Chain rule: d/d$v f(g) = f'(g)·g'.",
                "Result: $resultText",
            )
            else -> listOf("Derivative of a constant is 0.")
        }
    }

    /**
     * Numeric partial derivative via central differences (used for functions
     * we do not want to differentiate symbolically, and for mixed variables).
     */
    fun numericalDerivative(
        node: Node,
        varName: String,
        at: Double,
        h: Double = 1e-6,
    ): CalcResult<Double> {
        val plus = Evaluator.eval(node, mapOf(varName.lowercase() to at + h)).valueOrNull()
        val minus = Evaluator.eval(node, mapOf(varName.lowercase() to at - h)).valueOrNull()
        if (plus == null || minus == null) {
            return err(ErrorKind.Domain, "The function is not defined near $varName = ${NodePrinter.format(at)}")
        }
        return ok((plus - minus) / (2 * h))
    }
}

data class DerivativeResult(
    val expression: String,
    val derivative: String,
    val steps: List<String>,
    val numericAt: Double?,
)

/** Light algebraic simplification: constant folding and zero/one elimination. */
object Simplifier {
    fun simplify(n: Node): Node = when (n) {
        is Node.Add -> {
            val a = simplify(n.a); val b = simplify(n.b)
            when {
                a is Node.Num && b is Node.Num -> Node.Num(a.value + b.value)
                isZero(a) -> b
                isZero(b) -> a
                else -> Node.Add(a, b)
            }
        }
        is Node.Sub -> {
            val a = simplify(n.a); val b = simplify(n.b)
            when {
                a is Node.Num && b is Node.Num -> Node.Num(a.value - b.value)
                isZero(b) -> a
                isZero(a) -> Node.Neg(b)
                else -> Node.Sub(a, b)
            }
        }
        is Node.Mul -> {
            val a = simplify(n.a); val b = simplify(n.b)
            when {
                a is Node.Num && b is Node.Num -> Node.Num(a.value * b.value)
                isZero(a) || isZero(b) -> Node.Num(0.0)
                isOne(a) -> b
                isOne(b) -> a
                // Pull a leading negative coefficient out front so results read
                // as −1 ÷ sqrt(1 − x²) rather than -1(1 ÷ sqrt(1 − x²)).
                a is Node.Num && a.value < 0.0 ->
                    if (-a.value == 1.0) Node.Neg(b) else Node.Neg(Node.Mul(Node.Num(-a.value), b))
                else -> Node.Mul(a, b)
            }
        }
        is Node.Div -> {
            val a = simplify(n.a); val b = simplify(n.b)
            when {
                a is Node.Num && b is Node.Num && b.value != 0.0 -> Node.Num(a.value / b.value)
                isZero(a) -> Node.Num(0.0)
                isOne(b) -> a
                else -> Node.Div(a, b)
            }
        }
        is Node.Neg -> {
            val a = simplify(n.a)
            when (a) {
                is Node.Num -> Node.Num(-a.value)
                is Node.Neg -> a.a
                else -> Node.Neg(a)
            }
        }
        is Node.Pow -> {
            val a = simplify(n.a); val b = simplify(n.b)
            when {
                isZero(b) -> Node.Num(1.0)
                isOne(b) -> a
                isOne(a) -> Node.Num(1.0)
                isZero(a) -> Node.Num(0.0)
                // x^1 -> x, x^0 handled above
                b is Node.Num && b.value == 1.0 -> a
                // x^(n-1) / x^(n+1): fold the arithmetic back into a number
                b is Node.Sub && b.b is Node.Num -> {
                    val exp = (b.a as? Node.Num)?.value?.minus((b.b as Node.Num).value)
                    when {
                        exp == null -> Node.Pow(a, b)
                        exp == 0.0 -> Node.Num(1.0)
                        exp == 1.0 -> a
                        else -> Node.Pow(a, Node.Num(exp))
                    }
                }
                b is Node.Add && b.b is Node.Num -> {
                    val exp = (b.a as? Node.Num)?.value?.plus((b.b as Node.Num).value)
                    when {
                        exp == null -> Node.Pow(a, b)
                        exp == 0.0 -> Node.Num(1.0)
                        exp == 1.0 -> a
                        else -> Node.Pow(a, Node.Num(exp))
                    }
                }
                else -> Node.Pow(a, b)
            }
        }
        is Node.Fun -> {
            // Fold log(e) -> 1 so e^x differentiates to e^x, not e^x·ln(e).
            val folded = when {
                // ln(e) = 1, ln(π) = 1.1447… — fold to the numeric value so a
                // derivative such as d/dx e^x comes out as e^x, not e^x·ln(e).
                n.name == "ln" && n.args.size == 1 && n.args[0] is Node.Const ->
                    Constants.value((n.args[0] as Node.Const).name)?.let { Node.Num(kotlin.math.ln(it)) }
                n.name == "exp" && n.args.size == 1 && (n.args[0] as? Node.Num)?.value == 0.0 -> Node.Num(1.0)
                else -> null
            }
            folded ?: Node.Fun(n.name, n.args.map { simplify(it) })
        }
        else -> n
    }

    fun isZero(n: Node): Boolean = n is Node.Num && n.value == 0.0
    fun isOne(n: Node): Boolean = n is Node.Num && n.value == 1.0
}