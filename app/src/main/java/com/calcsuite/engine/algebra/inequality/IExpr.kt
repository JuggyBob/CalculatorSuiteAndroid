package com.calcsuite.engine.algebra.inequality

import com.calcsuite.core.Rat

/**
 * The expression tree the inequality parser produces.
 *
 * This is deliberately its own small type rather than a reuse of the calculus
 * engine's `Node`: the calculus tree stores `Double` literals, while an
 * inequality needs exact rational coefficients so that a critical point comes
 * out as `2` and not `2.0000000000000004`.
 */
sealed interface IExpr {

    data class Num(val value: Rat) : IExpr

    /** A variable: `x`, `y` or `z`. */
    data class Var(val name: String) : IExpr

    data class Neg(val a: IExpr) : IExpr

    data class Add(val a: IExpr, val b: IExpr) : IExpr

    data class Sub(val a: IExpr, val b: IExpr) : IExpr

    data class Mul(val a: IExpr, val b: IExpr) : IExpr

    data class Div(val a: IExpr, val b: IExpr) : IExpr

    /** A power. The solver only understands a non-negative integer exponent. */
    data class Pow(val a: IExpr, val b: IExpr) : IExpr

    /** A named function such as `sqrt`, `log` or `abs`. */
    data class Call(val name: String, val args: List<IExpr>) : IExpr
}

/** The comparison operators an inequality may use. */
enum class CompareOp(val symbol: String, val isStrict: Boolean) {
    LT("<", true),
    GT(">", true),
    LE("≤", false),
    GE("≥", false),
    /** Equality. The solution is the set of points where the expression is zero. */
    EQ("=", false);

    /** True when the relation holds for a value that makes both sides equal. */
    val acceptsEquality: Boolean get() = !isStrict

    companion object {
        fun fromSymbol(s: String): CompareOp? = when (s) {
            "<" -> LT
            ">" -> GT
            "<=" , "≤" -> LE
            ">=", "≥" -> GE
            "=" -> EQ
            else -> null
        }
    }
}

/**
 * Renders an expression tree as readable text.
 *
 * The style follows the rest of the app: a true minus sign `−`, `÷` for
 * division, `·` between adjacent factors, and no spaces around `^`.
 */
object IExprPrinter {

    /** Binding strength; a larger number binds more tightly. */
    private fun precedence(e: IExpr): Int = when (e) {
        is IExpr.Add, is IExpr.Sub -> 1
        is IExpr.Div -> 2
        is IExpr.Mul -> 3
        is IExpr.Neg -> 4
        is IExpr.Pow -> 5
        else -> 6
    }

    private fun operand(e: IExpr, minPrecedence: Int): String {
        val text = print(e)
        return if (precedence(e) < minPrecedence) "($text)" else text
    }

    fun print(e: IExpr): String = when (e) {
        is IExpr.Num -> e.value.toString()
        is IExpr.Var -> e.name
        is IExpr.Neg -> "−" + operand(e.a, 5)
        is IExpr.Add -> operand(e.a, 1) + " + " + operand(e.b, 1)
        is IExpr.Sub -> operand(e.a, 2) + " − " + operand(e.b, 2)
        is IExpr.Mul -> joinFactors(operand(e.a, 3), operand(e.b, 3))
        is IExpr.Div -> operand(e.a, 3) + " ÷ " + operand(e.b, 3)
        is IExpr.Pow -> operand(e.a, 6) + "^" + operand(e.b, 5)
        is IExpr.Call -> e.name + "(" + e.args.joinToString(", ") { print(it) } + ")"
    }

    /**
     * Joins two factors of a product, inserting `·` where juxtaposition would
     * read as one name (`x` next to `2y` would scan as `x2y`).
     */
    private fun joinFactors(left: String, right: String): String {
        val l = left.lastOrNull() ?: return "$left$right"
        val r = right.firstOrNull() ?: return "$left$right"
        val digitRun = l.isDigit() && (r.isDigit() || r == '.')
        val identifierRun = (l.isLetter() && r.isLetterOrDigit()) ||
            (l == ')' && r.isLetterOrDigit())
        return if (digitRun || identifierRun) "$left·$right" else "$left$right"
    }
}
