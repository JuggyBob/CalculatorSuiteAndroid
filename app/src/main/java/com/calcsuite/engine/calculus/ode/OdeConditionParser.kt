package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.formatNumber

/**
 * Reads an initial or boundary condition.
 *
 * The notation the tool accepts is the one people write:
 *
 * - `y(0) = 3` — the value of y at a point.
 * - `y'(0) = 1` — the value of the first derivative at a point.
 * - `y''(0) = 0` — and so on for higher orders.
 *
 * The point and the value may be any number, including a negative one and a
 * decimal one.
 */
object OdeConditionParser {

    /**
     * Parses a condition, or returns null when the text is not one.
     *
     * @param text the condition as it was typed.
     * @param dependent the name of the dependent variable, for the error text.
     */
    fun parse(text: String, dependent: String = "y"): OdeCondition? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val equals = trimmed.indexOf('=')
        if (equals <= 0 || equals == trimmed.length - 1) return null

        val left = trimmed.substring(0, equals).trim()
        val right = trimmed.substring(equals + 1).trim()

        val value = right.toDoubleOrNull() ?: return null

        // The left-hand side is y, y′, y″ … followed by a point in brackets.
        val open = left.indexOf('(')
        val close = left.lastIndexOf(')')
        if (open < 0 || close < open) return null

        val name = left.substring(0, open).trim()
        if (!name.startsWith(dependent, ignoreCase = true)) return null

        // The marks after the name give the order of the derivative.
        val marks = name.substring(dependent.length).trim()
        val order = when {
            marks.isEmpty() -> 0
            marks.all { it == '\'' || it == '′' } -> marks.length
            marks.all { it == '"' || it == '″' } -> marks.length
            else -> return null
        }

        val pointText = left.substring(open + 1, close).trim()
        val at = pointText.toDoubleOrNull() ?: return null

        return OdeCondition(
            order = order,
            at = at,
            value = value,
            display = "$name($pointText) = ${formatNumber(value)}",
        )
    }

    /**
     * Checks that the conditions are enough for the order of the equation.
     *
     * An n-th order equation needs n conditions, one for each derivative from
     * y up to y⁽ⁿ⁻¹⁾, and the same point is usually used for all of them.
     */
    fun checkSufficiency(
        conditions: List<OdeCondition>,
        order: Int,
    ): String? {
        if (order < 1) return null
        if (conditions.isEmpty()) {
            return "An initial value problem for a ${orderText(order)} equation needs " +
                "$order condition(s); none were given."
        }
        val orders = conditions.map { it.order }.toSet()
        for (needed in 0 until order) {
            if (needed !in orders) {
                return "An initial value problem for a ${orderText(order)} equation needs a " +
                    "condition for ${derivativeName(needed)}, and none was given."
            }
        }
        // Two conditions at the same point for the same derivative are
        // contradictory unless they agree.
        val grouped = conditions.groupBy { it.order to it.at }
        for ((key, group) in grouped) {
            val values = group.map { it.value }.distinct()
            if (values.size > 1) {
                return "The conditions for ${derivativeName(key.first)} at " +
                    "${formatNumber(key.second)} disagree: " +
                    group.joinToString(" and ") { formatNumber(it.value) } +
                    ". No solution can satisfy both."
            }
        }
        return null
    }

    /** "first-order", "second-order" and so on. */
    fun orderText(order: Int): String = when (order) {
        1 -> "first-order"
        2 -> "second-order"
        3 -> "third-order"
        else -> "$order-th order"
    }

    /** "y", "y′", "y″" and so on. */
    fun derivativeName(order: Int): String = when (order) {
        0 -> "y"
        1 -> "y′"
        2 -> "y″"
        3 -> "y‴"
        else -> "y" + "′".repeat(order)
    }
}
