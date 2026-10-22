package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * Solves a system of two equations in two unknowns.
 *
 * The equations are written as they are in the tool: `x^2 + y = 9` and
 * `x + y = 5`. A system of two linear equations is solved with the chosen
 * method; a system with a quadratic term is solved by substitution, which
 * reduces it to one equation in one unknown.
 */
object SystemSolver {

    /**
     * Solves the system.
     *
     * @param equation1 the first equation.
     * @param equation2 the second equation.
     * @param method the method to use, or [SystemMethod.Automatic] to have a
     *   suitable one chosen.
     * @param variableX the first unknown.
     * @param variableY the second unknown.
     */
    fun solve(
        equation1: String,
        equation2: String,
        method: SystemMethod = SystemMethod.Automatic,
        variableX: String = "x",
        variableY: String = "y",
    ): CalcResult<SystemAnswer> {
        val first = parseEquation(equation1, variableX, variableY)
            ?: return err(
                ErrorKind.Syntax,
                "The first equation could not be read" +
                    (lastParseFailure?.let { ": $it" } ?: "."),
            )
        val second = parseEquation(equation2, variableX, variableY)
            ?: return err(
                ErrorKind.Syntax,
                "The second equation could not be read" +
                    (lastParseFailure?.let { ": $it" } ?: "."),
            )


        val available = availableMethods(first, second)
        val chosen = when (method) {
            SystemMethod.Automatic -> if (first.isLinear && second.isLinear) {
                SystemMethod.Cramer
            } else {
                SystemMethod.Substitution
            }
            else -> method
        }
        if (chosen != SystemMethod.Automatic && chosen !in available) {
            return err(
                ErrorKind.Domain,
                "${chosen.label} does not apply to this system. " +
                    "The methods that do are: " +
                    available.joinToString(", ") { it.label },
            )
        }

        return when (chosen) {
            SystemMethod.Automatic -> err(ErrorKind.Domain, "No method was chosen.")
            SystemMethod.Cramer -> cramer(
                first, second, equation1, equation2, available, variableX, variableY,
            )
            SystemMethod.Substitution -> substitution(
                first, second, equation1, equation2, available, variableX, variableY,
            )
            SystemMethod.Elimination -> elimination(
                first, second, equation1, equation2, available, variableX, variableY,
            )
            SystemMethod.Comparison -> comparison(
                first, second, equation1, equation2, available, variableX, variableY,
            )
        }
    }

    /**
     * The methods that apply to a system.
     *
     * Cramer's rule and the comparison method are for linear systems only:
     * they have no meaning when an equation has a square in it.
     */
    fun availableMethods(
        first: BivariatePolynomial,
        second: BivariatePolynomial,
    ): List<SystemMethod> {
        val linear = first.isLinear && second.isLinear
        val methods = mutableListOf(SystemMethod.Automatic, SystemMethod.Substitution)
        if (linear) {
            methods.add(SystemMethod.Cramer)
            methods.add(SystemMethod.Elimination)
            methods.add(SystemMethod.Comparison)
        } else if (canEliminate(first, second)) {
            methods.add(SystemMethod.Elimination)
        }
        return methods
    }

    /** True when subtracting one equation from the other leaves one unknown. */
    private fun canEliminate(
        first: BivariatePolynomial,
        second: BivariatePolynomial,
    ): Boolean {
        val difference = first - second
        return difference.y.signum() == 0 && difference.y2.signum() == 0 &&
            difference.xy.signum() == 0
    }

    // ------------------------------------------------------------- Cramer

    /**
     * Cramer's rule: the determinant of the coefficients gives each variable.
     */
    private fun cramer(
        first: BivariatePolynomial,
        second: BivariatePolynomial,
        equation1: String,
        equation2: String,
        available: List<SystemMethod>,
        variableX: String,
        variableY: String,
    ): CalcResult<SystemAnswer> {
        val steps = mutableListOf<String>()
        steps.add("The system: $equation1  and  $equation2")
        val a1 = first.x
        val b1 = first.y
        val c1 = first.constant.negate()
        val a2 = second.x
        val b2 = second.y
        val c2 = second.constant.negate()
        steps.add(
            "Written as a₁$variableX + b₁$variableY = c₁ and a₂$variableX + b₂$variableY = c₂: " +
                "a₁ = ${plain(a1)}, b₁ = ${plain(b1)}, c₁ = ${plain(c1)}; " +
                "a₂ = ${plain(a2)}, b₂ = ${plain(b2)}, c₂ = ${plain(c2)}",
        )
        val determinant = a1.multiply(b2, CTX).subtract(a2.multiply(b1, CTX), CTX)
        steps.add(
            "The determinant D = a₁b₂ − a₂b₁ = ${plain(a1)} × ${plain(b2)} − " +
                "${plain(a2)} × ${plain(b1)} = ${plain(determinant)}",
        )
        if (determinant.signum() == 0) {
            steps.add("D = 0, so the equations are dependent or contradictory")
            val consistent = a1.multiply(c2, CTX).compareTo(a2.multiply(c1, CTX)) == 0 &&
                b1.multiply(c2, CTX).compareTo(b2.multiply(c1, CTX)) == 0
            steps.add(
                if (consistent) {
                    "The two rows are proportional including the constants, so the " +
                        "equations describe the same line: every point of it is a solution"
                } else {
                    "The two rows are proportional but the constants are not, so the " +
                        "lines are parallel and never meet: there is no solution"
                },
            )
            return ok(
                SystemAnswer(
                    method = SystemMethod.Cramer,
                    available = available,
                    outcome = if (consistent) {
                        SystemOutcome.Infinite
                    } else {
                        SystemOutcome.NoRealSolution
                    },
                    solutions = emptyList(),
                    steps = steps,
                    verification = emptyList(),
                    notes = emptyList(),
                    message = if (consistent) {
                        "Infinitely many solutions"
                    } else {
                        "No solution (the equations contradict each other)"
                    },
                    equation1 = equation1,
                    equation2 = equation2,
                ),
            )
        }
        steps.add("D ≠ 0, so the two lines cross once: there is exactly one solution")
        val x = c1.multiply(b2, CTX).subtract(c2.multiply(b1, CTX), CTX)
            .divide(determinant, CTX)
        val y = a1.multiply(c2, CTX).subtract(a2.multiply(c1, CTX), CTX)
            .divide(determinant, CTX)
        steps.add(
            "$variableX = (c₁b₂ − c₂b₁) ÷ D = (${plain(c1)} × ${plain(b2)} − " +
                "${plain(c2)} × ${plain(b1)}) ÷ ${plain(determinant)} = ${plain(round(x))}",
        )
        steps.add(
            "$variableY = (a₁c₂ − a₂c₁) ÷ D = (${plain(a1)} × ${plain(c2)} − " +
                "${plain(a2)} × ${plain(c1)}) ÷ ${plain(determinant)} = ${plain(round(y))}",
        )
        val solution = x to y
        val verification = verify(first, second, listOf(solution), variableX, variableY)
        steps.addAll(verification)
        return ok(
            SystemAnswer(
                method = SystemMethod.Cramer,
                available = available,
                outcome = SystemOutcome.Unique,
                solutions = listOf(solution),
                steps = steps,
                verification = verification,
                notes = emptyList(),
                message = "One unique solution: $variableX = ${plain(round(x))}, " +
                    "$variableY = ${plain(round(y))}",
                equation1 = equation1,
                equation2 = equation2,
            ),
        )
    }

    // -------------------------------------------------------- substitution

    /**
     * Substitution: one equation is rearranged and put into the other.
     *
     * This is the method that reaches a system with a square in it: the
     * equation that is linear in one unknown is solved for that unknown and
     * put into the other one, which leaves one equation in one unknown.
     */
    private fun substitution(
        first: BivariatePolynomial,
        second: BivariatePolynomial,
        equation1: String,
        equation2: String,
        available: List<SystemMethod>,
        variableX: String,
        variableY: String,
    ): CalcResult<SystemAnswer> {
        val steps = mutableListOf<String>()
        steps.add("The system: $equation1  and  $equation2")

        val solveFirstForY = first.degreeInY == 1 && first.y.signum() != 0
        val solveSecondForY = second.degreeInY == 1 && second.y.signum() != 0
        if (!solveFirstForY && !solveSecondForY) {
            steps.add(
                "Neither equation can be solved for $variableY on its own, so this " +
                    "method does not reach the system",
            )
            return ok(
                SystemAnswer(
                    method = SystemMethod.Substitution,
                    available = available,
                    outcome = SystemOutcome.Unsupported,
                    solutions = emptyList(),
                    steps = steps,
                    verification = emptyList(),
                    notes = listOf(
                        "This solver reaches a system when one of the equations can be " +
                            "written as one unknown in terms of the other.",
                    ),
                    message = "This system is not one the solver reaches",
                    equation1 = equation1,
                    equation2 = equation2,
                ),
            )
        }
        val solvedFirst = solveFirstForY
        val solved = if (solvedFirst) first else second
        val other = if (solvedFirst) second else first
        val solvedName = if (solvedFirst) "first" else "second"
        val otherName = if (solvedFirst) "second" else "first"

        // y = (the rest) ÷ (the coefficient of y), with the sign moved over.
        val rest = solved.minus(BivariatePolynomial(y = solved.y)).negate()
        val yInX = rest.divide(solved.y).valueOrNull()
            ?: return err(ErrorKind.Domain, "That equation cannot be solved for $variableY")
        steps.add(
            "Take the $solvedName equation and solve it for $variableY: " +
                "$variableY = ${yInX.format(variableX, variableY)}",
        )
        steps.add(
            "Put that into the $otherName equation, so that only $variableX is left",
        )

        val substituted = other.substituteY(yInX)
        val inX = substituted.valueOrNull()
        if (inX == null) {
            steps.add(
                "Putting it in gives a polynomial of a degree above 2, which this " +
                    "solver does not reach",
            )
            return ok(
                SystemAnswer(
                    method = SystemMethod.Substitution,
                    available = available,
                    outcome = SystemOutcome.Unsupported,
                    solutions = emptyList(),
                    steps = steps,
                    verification = emptyList(),
                    notes = listOf("The polynomial that comes out is of too high a degree."),
                    message = "This system is not one the solver reaches",
                    equation1 = equation1,
                    equation2 = equation2,
                ),
            )
        }
        val polynomial = inX.withoutY()
        steps.add("That gives: ${polynomial.format(variableX, variableY)} = 0")
        return finishPolynomial(
            polynomial, steps, first, second, equation1, equation2, available,
            SystemMethod.Substitution, variableX, variableY,
        )
    }

    // ---------------------------------------------------------- elimination

    /**
     * Elimination: the equations are combined so that a variable cancels.
     */
    private fun elimination(
        first: BivariatePolynomial,
        second: BivariatePolynomial,
        equation1: String,
        equation2: String,
        available: List<SystemMethod>,
        variableX: String,
        variableY: String,
    ): CalcResult<SystemAnswer> {
        val steps = mutableListOf<String>()
        steps.add("The system: $equation1  and  $equation2")
        if (first.isLinear && second.isLinear) {
            val a1 = first.x
            val b1 = first.y
            val a2 = second.x
            val b2 = second.y
            steps.add(
                "Written as a₁$variableX + b₁$variableY = c₁ and a₂$variableX + b₂$variableY = c₂",
            )
            val useY = b1.signum() != 0 && b2.signum() != 0
            val factor = if (useY) {
                b2.divide(b1, CTX)
            } else {
                a2.divide(a1, CTX)
            }
            if (factor.signum() == 0) {
                steps.add("Neither unknown can be cancelled out")
                return ok(
                    SystemAnswer(
                        method = SystemMethod.Elimination,
                        available = available,
                        outcome = SystemOutcome.Unsupported,
                        solutions = emptyList(),
                        steps = steps,
                        verification = emptyList(),
                        notes = emptyList(),
                        message = "This system is not one the solver reaches",
                        equation1 = equation1,
                        equation2 = equation2,
                    ),
                )
            }
            steps.add(
                "Multiply the first equation by ${plain(factor)} so that the " +
                    "${if (useY) variableY else variableX} terms are the same",
            )
            val scaled = first.multiply(BivariatePolynomial(constant = factor)).valueOrNull()
                ?: return err(ErrorKind.Domain, "That system is not one the solver reaches")
            val combined = scaled - second
            val polynomial = combined.withoutY()
            steps.add(
                "Subtract the second equation: the ${if (useY) variableY else variableX} " +
                    "terms cancel, leaving ${polynomial.format(variableX, variableY)} = 0",
            )
            return finishPolynomial(
                polynomial, steps, first, second, equation1, equation2, available,
                SystemMethod.Elimination, variableX, variableY,
            )
        }

        // A system with a square in it: subtracting the equations may leave
        // one unknown.
        val difference = first - second
        if (difference.y.signum() != 0 || difference.y2.signum() != 0 ||
            difference.xy.signum() != 0
        ) {
            steps.add("Subtracting the equations does not leave one unknown")
            return ok(
                SystemAnswer(
                    method = SystemMethod.Elimination,
                    available = available,
                    outcome = SystemOutcome.Unsupported,
                    solutions = emptyList(),
                    steps = steps,
                    verification = emptyList(),
                    notes = listOf(
                        "Use substitution: it reaches this system when one equation can " +
                            "be written as one unknown in terms of the other.",
                    ),
                    message = "This system is not one the solver reaches",
                    equation1 = equation1,
                    equation2 = equation2,
                ),
            )
        }
        steps.add("Subtract the second equation from the first")
        val polynomial = difference.withoutY()
        steps.add("That leaves: ${polynomial.format(variableX, variableY)} = 0")
        return finishPolynomial(
            polynomial, steps, first, second, equation1, equation2, available,
            SystemMethod.Elimination, variableX, variableY,
        )
    }

    // ---------------------------------------------------------- comparison

    /**
     * Comparison: both equations are written with the same unknown on its
     * own, and the two expressions are equated.
     */
    private fun comparison(
        first: BivariatePolynomial,
        second: BivariatePolynomial,
        equation1: String,
        equation2: String,
        available: List<SystemMethod>,
        variableX: String,
        variableY: String,
    ): CalcResult<SystemAnswer> {
        val steps = mutableListOf<String>()
        steps.add("The system: $equation1  and  $equation2")
        if (first.y.signum() == 0 || second.y.signum() == 0) {
            steps.add("One of the equations cannot be written with $variableY on its own")
            return ok(
                SystemAnswer(
                    method = SystemMethod.Comparison,
                    available = available,
                    outcome = SystemOutcome.Unsupported,
                    solutions = emptyList(),
                    steps = steps,
                    verification = emptyList(),
                    notes = emptyList(),
                    message = "This system is not one the solver reaches",
                    equation1 = equation1,
                    equation2 = equation2,
                ),
            )
        }
        val firstY = first.minus(BivariatePolynomial(y = first.y)).negate()
            .divide(first.y).valueOrNull()
            ?: return err(ErrorKind.Domain, "That equation cannot be solved for $variableY")
        val secondY = second.minus(BivariatePolynomial(y = second.y)).negate()
            .divide(second.y).valueOrNull()
            ?: return err(ErrorKind.Domain, "That equation cannot be solved for $variableY")
        steps.add(
            "The first equation gives $variableY = ${firstY.format(variableX, variableY)}",
        )
        steps.add(
            "The second equation gives $variableY = ${secondY.format(variableX, variableY)}",
        )
        steps.add("Equate the two, because they are the same $variableY")
        val difference = firstY - secondY
        val polynomial = difference.withoutY()
        steps.add("That gives: ${polynomial.format(variableX, variableY)} = 0")
        return finishPolynomial(
            polynomial, steps, first, second, equation1, equation2, available,
            SystemMethod.Comparison, variableX, variableY,
        )
    }

    /**
     * Finishes a system whose second equation has been reduced to one
     * polynomial in one unknown.
     */
    private fun finishPolynomial(
        polynomial: BivariatePolynomial,
        steps: MutableList<String>,
        first: BivariatePolynomial,
        second: BivariatePolynomial,
        equation1: String,
        equation2: String,
        available: List<SystemMethod>,
        method: SystemMethod,
        variableX: String,
        variableY: String,
    ): CalcResult<SystemAnswer> {
        val roots: List<BigDecimal>
        when {
            polynomial.x2.signum() != 0 -> {
                val solvedQuadratic = EquationEngine.solveQuadratic(
                    polynomial.x2, polynomial.x, polynomial.constant,
                )
                val value = solvedQuadratic.valueOrNull()
                    ?: return err(ErrorKind.Domain, "That polynomial could not be solved")
                steps.add(
                    "It is a quadratic: the discriminant is " +
                        "${plain(value.discriminant ?: BigDecimal.ZERO)}, so " +
                        "${value.realRoots.size} real root" +
                        (if (value.realRoots.size == 1) "" else "s"),
                )
                roots = value.realRoots
            }
            polynomial.x.signum() != 0 -> {
                steps.add("It is a linear equation in $variableX")
                roots = listOf(polynomial.constant.negate().divide(polynomial.x, CTX))
            }
            else -> {
                steps.add("Everything cancels, so the equations say the same thing")
                return ok(
                    SystemAnswer(
                        method = method,
                        available = available,
                        outcome = SystemOutcome.Infinite,
                        solutions = emptyList(),
                        steps = steps,
                        verification = emptyList(),
                        notes = emptyList(),
                        message = "Infinitely many solutions",
                        equation1 = equation1,
                        equation2 = equation2,
                    ),
                )
            }
        }
        if (roots.isEmpty()) {
            steps.add("It has no real root, so the system has no real solution")
            return ok(
                SystemAnswer(
                    method = method,
                    available = available,
                    outcome = SystemOutcome.NoRealSolution,
                    solutions = emptyList(),
                    steps = steps,
                    verification = emptyList(),
                    notes = listOf("The polynomial that comes out has no real root."),
                    message = "No real solution",
                    equation1 = equation1,
                    equation2 = equation2,
                ),
            )
        }
        // The second unknown comes from the equation that is linear in it.
        val linear = if (first.y.signum() != 0) first else second
        val numerator = linear.minus(BivariatePolynomial(y = linear.y)).negate()
        val solutions = mutableListOf<Pair<BigDecimal, BigDecimal>>()
        for (root in roots) {
            val y = numerator.evaluate(root, BigDecimal.ZERO)
                .divide(linear.y, CTX)
            solutions.add(root to y)
            steps.add("$variableX = ${plain(round(root))} gives $variableY = ${plain(round(y))}")
        }
        val verification = verify(first, second, solutions, variableX, variableY)
        steps.addAll(verification)
        val distinct = solutions.distinct()
        return ok(
            SystemAnswer(
                method = method,
                available = available,
                outcome = when (distinct.size) {
                    0 -> SystemOutcome.NoRealSolution
                    1 -> SystemOutcome.Unique
                    else -> SystemOutcome.Multiple
                },
                solutions = distinct,
                steps = steps,
                verification = verification,
                notes = emptyList(),
                message = if (distinct.size == 1) {
                    "One solution: $variableX = ${plain(round(distinct[0].first))}, " +
                        "$variableY = ${plain(round(distinct[0].second))}"
                } else {
                    "${distinct.size} real solutions"
                },
                equation1 = equation1,
                equation2 = equation2,
            ),
        )
    }

    // ------------------------------------------------------------ helpers

    /**
     * Puts each solution back into both equations.
     */
    private fun verify(
        first: BivariatePolynomial,
        second: BivariatePolynomial,
        solutions: List<Pair<BigDecimal, BigDecimal>>,
        variableX: String,
        variableY: String,
    ): List<String> = solutions.map { (x, y) ->
        val left = first.evaluate(x, y)
        val right = second.evaluate(x, y)
        "Check $variableX = ${plain(round(x))}, $variableY = ${plain(round(y))}: " +
            "the first equation reads ${plain(round(left))} and the second " +
            "${plain(round(right))}, so " +
            if (left.abs().max(right.abs()).compareTo(BigDecimal("0.0000001")) < 0) {
                "both sides agree"
            } else {
                "the values differ, so the root is rounded"
            }
    }

    /**
     * The polynomial with the second unknown taken out.
     */
    private fun BivariatePolynomial.withoutY(): BivariatePolynomial =
        BivariatePolynomial(x2 = x2, x = x, constant = constant)

    /**
     * Reads an equation into a polynomial: the left side minus the right.
     */
    private fun parseEquation(
        text: String,
        variableX: String,
        variableY: String,
    ): BivariatePolynomial? {
        val parts = text.split('=')
        if (parts.size != 2) return null
        return try {
            val left = BivariateParser(
                EqLexer.tokenize(parts[0].trim()),
                variableX,
                variableY,
            ).parse()
            val right = BivariateParser(
                EqLexer.tokenize(parts[1].trim()),
                variableX,
                variableY,
            ).parse()
            left - right
        } catch (failure: EqParseException) {
            lastParseFailure = failure.message
            null
        }
    }

    /** Why the last equation could not be read, for the message. */
    private var lastParseFailure: String? = null

    /** The context the arithmetic is done in. */
    private val CTX = BivariatePolynomial.CTX

    /** A number as it is written in the working. */
    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    /** A number rounded for the working. */
    private fun round(value: BigDecimal): BigDecimal =
        value.round(BivariatePolynomial.CTX).stripTrailingZeros()
}

/**
 * Reads an expression in two variables.
 *
 * It understands the same notation as the equation tool: decimals, the two
 * unknowns, `+ − × * ÷ /`, `^` with integer exponents 0 to 2, parentheses and
 * implicit multiplication such as `2x` or `x(y + 1)`.
 */
internal class BivariateParser(
    private val tokens: List<EqToken>,
    private val variableX: String,
    private val variableY: String,
) {
    private var position = 0

    private fun peek(): EqToken = tokens[position]

    private fun advance(): EqToken = tokens[position++]

    fun parse(): BivariatePolynomial {
        if (peek() == EqToken.End) throw EqParseException("The expression is empty")
        val result = parseSum()
        if (peek() != EqToken.End) throw EqParseException("There is more in the expression")
        return result
    }

    private fun parseSum(): BivariatePolynomial {
        var result = parseProduct()
        while (true) {
            when (peek()) {
                is EqToken.Plus -> {
                    advance()
                    result = result + parseProduct()
                }
                is EqToken.Minus -> {
                    advance()
                    result = result - parseProduct()
                }
                else -> return result
            }
        }
    }

    private fun parseProduct(): BivariatePolynomial {
        var result = parsePower()
        while (true) {
            when (peek()) {
                is EqToken.Star -> {
                    advance()
                    result = multiplyOrFail(result, parsePower())
                }
                is EqToken.Slash -> {
                    advance()
                    val right = parsePower()
                    if (!right.isConstant) {
                        throw EqParseException("Only a number can be divided by")
                    }
                    result = divideOrFail(result, right.constant)
                }
                else -> {
                    // An implicit multiplication, such as 2x or x(y + 1).
                    if (startsFactor(peek())) {
                        result = multiplyOrFail(result, parsePower())
                    } else {
                        return result
                    }
                }
            }
        }
    }

    private fun startsFactor(token: EqToken): Boolean = when (token) {
        is EqToken.Number, is EqToken.Name, is EqToken.LParen -> true
        else -> false
    }

    private fun parsePower(): BivariatePolynomial {
        val base = parseFactor()
        if (peek() is EqToken.Caret) {
            advance()
            val exponent = when (val token = advance()) {
                is EqToken.Number -> token.text.toIntOrNull()
                    ?: throw EqParseException("The exponent is not a whole number")
                else -> throw EqParseException("The exponent is not a number")
            }
            return when (exponent) {
                0 -> BivariatePolynomial(constant = BigDecimal.ONE)
                1 -> base
                2 -> multiplyOrFail(base, base)
                else -> throw EqParseException("Only exponents 0, 1 and 2 are supported")
            }
        }
        return base
    }

    private fun parseFactor(): BivariatePolynomial {
        return when (val token = advance()) {
            is EqToken.Number -> {
                val value = token.text.replace(",", "").toBigDecimalOrNull()
                    ?: throw EqParseException("\"${token.text}\" is not a number")
                BivariatePolynomial(constant = value)
            }
            is EqToken.Name -> when (token.text) {
                variableX -> BivariatePolynomial(x = BigDecimal.ONE)
                variableY -> BivariatePolynomial(y = BigDecimal.ONE)
                else -> throw EqParseException("\"${token.text}\" is not an unknown")
            }
            is EqToken.Plus -> parseFactor()
            is EqToken.Minus -> parseFactor().negate()
            is EqToken.LParen -> {
                val result = parseSum()
                if (peek() !is EqToken.RParen) throw EqParseException("A bracket is not closed")
                advance()
                result
            }
            else -> throw EqParseException("The expression is not complete")
        }
    }

    private fun multiplyOrFail(
        left: BivariatePolynomial,
        right: BivariatePolynomial,
    ): BivariatePolynomial = left.multiply(right).valueOrNull()
        ?: throw EqParseException("That expression is of too high a degree")

    private fun divideOrFail(
        left: BivariatePolynomial,
        divisor: BigDecimal,
    ): BivariatePolynomial = left.divide(divisor).valueOrNull()
        ?: throw EqParseException("Division by zero")
}
