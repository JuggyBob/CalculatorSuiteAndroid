package com.calcsuite.engine.calculus

/**
 * A small expression tree used by the calculus engine.
 *
 * The tree is intentionally minimal and immutable: every calculus operation
 * (differentiation, integration, limits, series, …) is a rewrite of trees, so
 * keeping values immutable lets results be shared safely.
 */
sealed interface Node {
    data class Num(val value: Double) : Node
    data class Var(val name: String) : Node
    data class Const(val name: String) : Node          // pi, e, tau, phi
    data class Neg(val a: Node) : Node
    data class Add(val a: Node, val b: Node) : Node
    data class Sub(val a: Node, val b: Node) : Node
    data class Mul(val a: Node, val b: Node) : Node
    data class Div(val a: Node, val b: Node) : Node
    data class Pow(val a: Node, val b: Node) : Node
    data class Fun(val name: String, val args: List<Node>) : Node
}

object Constants {
    val PI = Math.PI
    val E = Math.E

    fun value(name: String): Double? = when (name.lowercase()) {
        "pi", "π" -> PI
        "e" -> E
        "tau", "τ" -> 2 * PI
        "phi", "φ" -> (1 + kotlin.math.sqrt(5.0)) / 2
        else -> null
    }
}

/**
 * Renders a tree back to a compact, readable expression string.
 *
 * The output round-trips: printing a tree and parsing the result yields the
 * same tree. Engines such as `extrema`, `series` and `taylor` rely on that, so
 * operands are parenthesised according to precedence rather than relying on the
 * reader to infer it.
 */
object NodePrinter {

    /**
     * Binding strength; a larger number binds more tightly. `Mul` is listed
     * separately from `Div` so a product can sit inside a product without
     * parentheses (multiplication is associative) while a quotient cannot.
     */
    private fun precedence(n: Node): Int = when (n) {
        is Node.Add, is Node.Sub -> 1
        is Node.Div -> 2
        is Node.Mul -> 3
        is Node.Neg -> 4
        is Node.Pow -> 5
        else -> 6 // Num, Var, Const, Fun
    }

    /** Prints [n] as an operand of a parent that binds at least as tightly as [minPrecedence]. */
    private fun operand(n: Node, minPrecedence: Int): String {
        val text = print(n)
        return if (precedence(n) < minPrecedence) "($text)" else text
    }

    fun print(n: Node): String = when (n) {
        is Node.Num -> format(n.value)
        is Node.Var -> n.name
        is Node.Const -> n.name
        // − binds looser than ^, so −x^2 means −(x^2) and needs no parentheses.
        is Node.Neg -> "−" + operand(n.a, 5)
        is Node.Add -> operand(n.a, 1) + " + " + operand(n.b, 1)
        is Node.Sub -> operand(n.a, 2) + " − " + operand(n.b, 2)
        is Node.Mul -> joinFactors(operand(n.a, 3), operand(n.b, 3))
        is Node.Div -> operand(n.a, 3) + " ÷ " + operand(n.b, 3)
        is Node.Pow -> operand(n.a, 6) + "^" + operand(n.b, 5)
        is Node.Fun -> n.name + "(" + n.args.joinToString(", ") { print(it) } + ")"
    }

    /**
     * Joins two factors of a product.
     *
     * Bare juxtaposition is used where it round-trips through the parser
     * (`2x`, `3sin(x)`), and an explicit `·` is inserted where concatenation
     * would re-lex differently — `x` next to `2y` would scan as one name
     * `x2y`, and `x` next to `(y+1)` as a call `x(…)`.
     */
    private fun joinFactors(left: String, right: String): String {
        val l = left.lastOrNull() ?: return "$left$right"
        val r = right.firstOrNull() ?: return "$left$right"
        val digitRun = l.isDigit() && (r.isDigit() || r == '.')
        val identifierRun = (l.isLetter() && r.isLetterOrDigit()) ||
            (l == ')' && r.isLetterOrDigit())
        return if (digitRun || identifierRun) "$left·$right" else "$left$right"
    }

    /**
     * Formats a numeric result for display.
     *
     * Whole numbers are printed without a decimal point; everything else keeps
     * up to ten decimals with trailing zeros removed. The ASCII minus is kept
     * so the output stays parseable by [value] as well as readable.
     */
    fun format(d: Double): String {
        if (d.isNaN()) return "undefined"
        if (d.isInfinite()) return if (d > 0) "∞" else "-∞"
        if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) {
            return d.toLong().toString()
        }
        var s = String.format(java.util.Locale.ROOT, "%.10f", d)
        s = s.trimEnd('0').trimEnd('.')
        return if (s.isEmpty() || s == "-") "0" else s
    }
}