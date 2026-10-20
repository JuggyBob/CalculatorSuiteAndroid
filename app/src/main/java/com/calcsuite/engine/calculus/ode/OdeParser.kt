package com.calcsuite.engine.calculus.ode

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter

/** Raised while reading a differential equation. */
class OdeParseException(message: String) : Exception(message)

/**
 * Reads a differential equation and puts it into the standard form
 *
 *     a_n·y⁽ⁿ⁾ + … + a_1·y′ + a_0·y = F(x, y, y′, …)
 *
 * The notation the tool accepts:
 *
 * - `dy/dx` and `y′` for the first derivative, `y″` and `d2y/dx2` for the
 *   second, and so on.
 * - An equation such as `y′ + y = x`, where the left-hand side is a linear
 *   combination of the derivatives of y.
 * - The right-hand side may mention x and y, and for a second-order equation
 *   y′ as well.
 *
 * The derivative notations are rewritten to short placeholder names before the
 * expression parser sees them, because the expression language has no notion
 * of a derivative.
 */
object OdeParser {

    /** The prefix used for the derivative placeholders inside the parsed tree. */
    const val DERIVATIVE_PREFIX = "yd"

    /** The placeholder for the n-th derivative of y. */
    fun derivativeName(order: Int): String = DERIVATIVE_PREFIX + order

    // The whitespace after the denominator is kept, so that `dy/dx = 2y` does
    // not lose the space in front of the equals sign.
    private val FRACTION_DERIVATIVE = Regex("""d\s*(\d*)\s*y\s*/\s*d\s*x(\s*)(\d*)""")
    private val PRIME_DERIVATIVE = Regex("""y(\s*')+""")

    /**
     * Parses [text] into a [ParsedOde].
     */
    fun parse(text: String): CalcResult<ParsedOde> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return err(ErrorKind.InvalidInput, "Enter a differential equation.")
        }
        return try {
            ok(parseChecked(trimmed))
        } catch (e: OdeParseException) {
            err(ErrorKind.Syntax, e.message ?: "The equation could not be read.")
        }
    }

    private fun parseChecked(text: String): ParsedOde {
        val (leftText, rightText) = splitAtEquals(text)
        if (leftText.isBlank() || rightText.isBlank()) {
            throw OdeParseException("Write the equation with an equals sign, for example dy/dx = 2y.")
        }

        val (leftRewritten, rightRewritten) = splitAtEquals(rewriteDerivatives(text))
        val leftNode = parseExpression(leftRewritten, "the left-hand side")
        val rightNode = parseExpression(rightRewritten, "the right-hand side")

        val dependent = findDependentName(leftNode) ?: "y"
        val independent = findIndependentVariable(leftNode, rightNode, dependent)

        val terms = flattenSum(leftNode)
        val coefficients = mutableMapOf<Int, Node>()
        var remainder: Node? = null

        for (term in terms) {
            val derivative = derivativeTerm(term)
            if (derivative != null) {
                val (order, coefficient) = derivative
                if (containsVariable(coefficient, dependent) || containsDerivative(coefficient)) {
                    throw OdeParseException("The coefficient of a derivative must not depend on y.")
                }
                val existing = coefficients[order]
                coefficients[order] = if (existing == null) coefficient else addTerms(existing, coefficient)
                continue
            }
            // A term of the form c·y is the coefficient of y itself.
            val linearInY = linearTermInY(term, dependent)
            if (linearInY != null) {
                val existing = coefficients[0]
                coefficients[0] = if (existing == null) linearInY else addTerms(existing, linearInY)
                continue
            }
            // Anything else belongs on the right-hand side.
            remainder = addTerms(remainder, term)
        }

        if (coefficients.isEmpty()) {
            throw OdeParseException("The left-hand side has to contain y or one of its derivatives.")
        }
        val order = coefficients.keys.filter { it >= 1 }.maxOrNull()
            ?: throw OdeParseException("The left-hand side has to contain a derivative of y.")

        // Move the extra terms across.
        val rhs = if (remainder == null) rightNode else subtractTerms(rightNode, remainder)

        val leading = coefficients[order]!!
        val ordered = (0..order).map { coefficients[it] }

        // y⁽ⁿ⁾ = (F − Σ aₖ·y⁽ᵏ⁾) / aₙ
        var solvedFor = rhs
        for (k in 0 until order) {
            val coefficient = ordered[k] ?: continue
            // The zeroth derivative is y itself.
            val term = if (k == 0) {
                Node.Mul(coefficient, Node.Var(dependent))
            } else {
                Node.Mul(coefficient, Node.Var(derivativeName(k)))
            }
            solvedFor = subtractTerms(solvedFor, term)
        }
        val normalised = if (isConstantOne(leading)) solvedFor else Node.Div(solvedFor, leading)

        return ParsedOde(
            original = text,
            independent = independent,
            dependent = dependent,
            order = order,
            coefficients = ordered,
            rhs = rhs,
            normalisedRhs = normalised,
            display = renderEquation(text),
        )
    }

    // ------------------------------------------------------------- rewriting

    /**
     * Rewrites the derivative notations into placeholder names.
     *
     * `dy/dx` becomes `yd1`, `y″` becomes `yd2`, and so on. The expression
     * parser then sees ordinary identifiers.
     */
    fun rewriteDerivatives(text: String): String {
        var out = FRACTION_DERIVATIVE.replace(text) { match ->
            val first = match.groupValues[1].toIntOrNull() ?: 0
            val second = match.groupValues[3].toIntOrNull() ?: 0
            // The whitespace after the denominator is kept, so the space in
            // front of the equals sign survives.
            derivativeName(maxOf(first, second, 1)) + match.groupValues[2]
        }
        out = PRIME_DERIVATIVE.replace(out) { match ->
            derivativeName(match.value.count { it == '\'' })
        }
        return out
    }

    /** Splits an equation at its equals sign. */
    private fun splitAtEquals(text: String): Pair<String, String> {
        val index = text.indexOf('=')
        if (index < 0) {
            throw OdeParseException("Write the equation with an equals sign, for example dy/dx = 2y.")
        }
        if (text.indexOf('=', index + 1) >= 0) {
            throw OdeParseException("The equation has more than one equals sign.")
        }
        return text.substring(0, index) to text.substring(index + 1)
    }

    private fun parseExpression(text: String, what: String): Node {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw OdeParseException("The $what is empty.")
        }
        return when (val parsed = CalculusParser.parse(trimmed)) {
            is CalcResult.Ok -> parsed.value
            is CalcResult.Err -> throw OdeParseException(
                "The $what could not be read: ${parsed.error.message}",
            )
        }
    }

    // ----------------------------------------------------------- tree helpers

    /** Splits an addition into its terms, keeping the sign of each. */
    fun flattenSum(node: Node): List<Node> = when (node) {
        is Node.Add -> flattenSum(node.a) + flattenSum(node.b)
        is Node.Sub -> flattenSum(node.a) + flattenSum(node.b).map { Node.Neg(it) }
        else -> listOf(node)
    }

    /** Adds two trees, folding a zero term away. */
    fun addTerms(a: Node?, b: Node): Node = when {
        a == null -> b
        isZero(a) -> b
        isZero(b) -> a
        else -> Node.Add(a, b)
    }

    /** Subtracts two trees. */
    fun subtractTerms(a: Node, b: Node): Node = when {
        isZero(b) -> a
        else -> Node.Sub(a, b)
    }

    /** True when the tree is exactly zero. */
    fun isZero(node: Node): Boolean = when (node) {
        is Node.Num -> node.value == 0.0
        is Node.Neg -> isZero(node.a)
        is Node.Add -> isZero(node.a) && isZero(node.b)
        is Node.Sub -> isZero(node.a) && isZero(node.b)
        is Node.Mul -> isZero(node.a) || isZero(node.b)
        else -> false
    }

    /** True when the tree is exactly one. */
    fun isConstantOne(node: Node): Boolean = node is Node.Num && node.value == 1.0

    /** True when the tree is a plain number. */
    fun isNumber(node: Node): Boolean = node is Node.Num

    /** The value of a plain number, or null. */
    fun numberValue(node: Node?): Double? = (node as? Node.Num)?.value

    /**
     * The value of a tree that is a constant number, or null.
     *
     * A constant can arrive as `3`, as `−3` or as `2·3`, and all three mean
     * the same thing, so the sign and the product are taken into account.
     */
    fun numericValue(node: Node?): Double? = when (node) {
        null -> null
        is Node.Num -> node.value
        is Node.Neg -> numericValue(node.a)?.let { -it }
        is Node.Mul -> {
            val a = numericValue(node.a)
            val b = numericValue(node.b)
            if (a == null || b == null) null else a * b
        }
        is Node.Div -> {
            val a = numericValue(node.a)
            val b = numericValue(node.b)
            if (a == null || b == null || b == 0.0) null else a / b
        }
        else -> null
    }

    /** The variables the tree mentions. */
    fun variables(node: Node, into: MutableSet<String> = mutableSetOf()): Set<String> {
        when (node) {
            is Node.Num -> Unit
            is Node.Var -> into.add(node.name.lowercase())
            is Node.Const -> Unit
            is Node.Neg -> variables(node.a, into)
            is Node.Add -> { variables(node.a, into); variables(node.b, into) }
            is Node.Sub -> { variables(node.a, into); variables(node.b, into) }
            is Node.Mul -> { variables(node.a, into); variables(node.b, into) }
            is Node.Div -> { variables(node.a, into); variables(node.b, into) }
            is Node.Pow -> { variables(node.a, into); variables(node.b, into) }
            is Node.Fun -> node.args.forEach { variables(it, into) }
        }
        return into
    }

    /** True when the tree mentions [name]. */
    fun containsVariable(node: Node, name: String): Boolean =
        variables(node).any { it.equals(name, ignoreCase = true) }

    /** True when the tree mentions one of the derivative placeholders. */
    fun containsDerivative(node: Node): Boolean =
        variables(node).any { it.startsWith(DERIVATIVE_PREFIX) }

    /** The multiplicative factors of a tree. */
    fun flattenProduct(node: Node): List<Node> = when (node) {
        is Node.Mul -> flattenProduct(node.a) + flattenProduct(node.b)
        else -> listOf(node)
    }

    /**
     * Splits a term into the derivative it holds and the coefficient in front
     * of it, or returns null when the term holds no derivative.
     */
    private fun derivativeTerm(term: Node): Pair<Int, Node>? {
        // A term may carry a leading negation, as in "- 3y′", so the sign is
        // taken off the front before the factors are looked at.
        val negated = term is Node.Neg
        val inner = if (negated) term.a else term
        val factors = flattenProduct(inner)
        val derivative = factors.filter { it is Node.Var && it.name.startsWith(DERIVATIVE_PREFIX) }
        if (derivative.isEmpty()) return null
        if (derivative.size > 1) {
            throw OdeParseException("A term cannot contain more than one derivative.")
        }
        val name = (derivative.first() as Node.Var).name
        val order = name.removePrefix(DERIVATIVE_PREFIX).toIntOrNull()
            ?: throw OdeParseException("\"$name\" does not name a derivative of y.")
        if (order < 1) {
            throw OdeParseException("\"$name\" does not name a derivative of y.")
        }
        val rest = factors.filter { it !== derivative.first() }
        val coefficient = when (rest.size) {
            0 -> Node.Num(1.0)
            1 -> rest.first()
            else -> {
                var acc: Node = rest.first()
                for (factor in rest.drop(1)) acc = Node.Mul(acc, factor)
                acc
            }
        }
        return order to if (negated) Node.Neg(coefficient) else coefficient
    }

    /**
     * Splits a term that is a multiple of y itself into its coefficient, or
     * returns null when the term is not of that shape.
     */
    private fun linearTermInY(term: Node, dependent: String): Node? {
        val negated = term is Node.Neg
        val inner = if (negated) term.a else term
        val factors = flattenProduct(inner)
        val ys = factors.filter { it is Node.Var && it.name.equals(dependent, ignoreCase = true) }
        if (ys.size != 1) return null
        val rest = factors.filter { it !== ys.first() }
        val coefficient = when (rest.size) {
            0 -> Node.Num(1.0)
            1 -> rest.first()
            else -> rest.drop(1).fold(rest.first()) { acc, factor -> Node.Mul(acc, factor) }
        }
        if (containsVariable(coefficient, dependent) || containsDerivative(coefficient)) return null
        return if (negated) Node.Neg(coefficient) else coefficient
    }

    /** Finds the name of the variable the derivatives are taken of. */
    private fun findDependentName(node: Node): String? = when (node) {
        is Node.Var -> if (node.name.startsWith(DERIVATIVE_PREFIX)) "y" else null
        is Node.Neg -> findDependentName(node.a)
        is Node.Add -> findDependentName(node.a) ?: findDependentName(node.b)
        is Node.Sub -> findDependentName(node.a) ?: findDependentName(node.b)
        is Node.Mul -> findDependentName(node.a) ?: findDependentName(node.b)
        is Node.Div -> findDependentName(node.a) ?: findDependentName(node.b)
        is Node.Pow -> findDependentName(node.a) ?: findDependentName(node.b)
        is Node.Fun -> node.args.firstNotNullOfOrNull { findDependentName(it) }
        else -> null
    }

    /** Finds the independent variable: the one that is not the dependent one. */
    private fun findIndependentVariable(left: Node, right: Node, dependent: String): String {
        val all = variables(left) + variables(right)
        val plain = all.filter {
            !it.startsWith(DERIVATIVE_PREFIX) && !it.equals(dependent, ignoreCase = true)
        }
        return plain.firstOrNull() ?: "x"
    }

    /** The marks used for the first, second and third derivatives. */
    private val DERIVATIVE_MARKS = listOf("", "′", "″", "‴", "⁗")

    /** Renders the equation for display, with the placeholders turned back into y. */
    private fun renderEquation(text: String): String {
        val rewritten = rewriteDerivatives(text)
        val (left, right) = splitAtEquals(rewritten)
        val leftText = restoreMarks(NodePrinter.print(parseExpression(left, "the left-hand side")))
        val rightText = restoreMarks(NodePrinter.print(parseExpression(right, "the right-hand side")))
        return "$leftText = $rightText"
    }

    /** Turns `yd2` back into `y″`. */
    private fun restoreMarks(text: String): String {
        var out = text
        for (order in 4 downTo 1) {
            out = out.replace(derivativeName(order), "y" + DERIVATIVE_MARKS[order])
        }
        return out
    }
}
