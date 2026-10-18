package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * A polynomial `a₂·x² + a₁·x + a₀` with [BigDecimal] coefficients.
 *
 * Expressions are parsed into this form by [EquationEngine]; every operation
 * grows at most to degree 2, higher products are rejected with a message.
 */
data class Polynomial(
    val a2: BigDecimal = BigDecimal.ZERO,
    val a1: BigDecimal = BigDecimal.ZERO,
    val a0: BigDecimal = BigDecimal.ZERO,
) {
    val degree: Int
        get() = when {
            a2.signum() != 0 -> 2
            a1.signum() != 0 -> 1
            else -> 0
        }

    val isConstant: Boolean get() = a2.signum() == 0 && a1.signum() == 0

    operator fun plus(other: Polynomial): Polynomial = Polynomial(
        a2.add(other.a2, ALG_CTX),
        a1.add(other.a1, ALG_CTX),
        a0.add(other.a0, ALG_CTX),
    )

    operator fun minus(other: Polynomial): Polynomial = Polynomial(
        a2.subtract(other.a2, ALG_CTX),
        a1.subtract(other.a1, ALG_CTX),
        a0.subtract(other.a0, ALG_CTX),
    )

    fun negate(): Polynomial = Polynomial(a2.negate(), a1.negate(), a0.negate())

    /** Exact multiplication, rejecting products above degree 2. */
    fun multiply(other: Polynomial): CalcResult<Polynomial> {
        val c4 = a2.multiply(other.a2, ALG_CTX)
        val c3 = a2.multiply(other.a1, ALG_CTX).add(a1.multiply(other.a2, ALG_CTX), ALG_CTX)
        if (c4.signum() != 0 || c3.signum() != 0) {
            return err(ErrorKind.Domain, "The product is above degree 2, which this tool does not solve")
        }
        val c2 = a2.multiply(other.a0, ALG_CTX)
            .add(a1.multiply(other.a1, ALG_CTX), ALG_CTX)
            .add(a0.multiply(other.a2, ALG_CTX), ALG_CTX)
        val c1 = a1.multiply(other.a0, ALG_CTX).add(a0.multiply(other.a1, ALG_CTX), ALG_CTX)
        val c0 = a0.multiply(other.a0, ALG_CTX)
        return ok(Polynomial(c2, c1, c0))
    }

    fun divideByConstant(divisor: BigDecimal): CalcResult<Polynomial> {
        if (divisor.signum() == 0) return err(ErrorKind.DivisionByZero, "Division by zero")
        return ok(
            Polynomial(
                a2.divide(divisor, ALG_CTX),
                a1.divide(divisor, ALG_CTX),
                a0.divide(divisor, ALG_CTX),
            ),
        )
    }

    fun power(exponent: Int): CalcResult<Polynomial> = when (exponent) {
        0 -> ok(Polynomial(a0 = BigDecimal.ONE))
        1 -> ok(this)
        2 -> multiply(this)
        else -> err(ErrorKind.Domain, "Only exponents 0, 1 and 2 are supported")
    }

    fun evaluate(x: BigDecimal): BigDecimal =
        a2.multiply(x, ALG_CTX).multiply(x, ALG_CTX)
            .add(a1.multiply(x, ALG_CTX), ALG_CTX)
            .add(a0, ALG_CTX)

    /** Human-readable form, e.g. `2x^2 − 3x + 2`. Zero terms are dropped. */
    fun format(variable: String): String {
        val pieces = ArrayList<String>()
        val terms = listOf(
            Triple(a2, "$variable\u00B2", true),
            Triple(a1, variable, false),
            Triple(a0, "", false),
        )
        for ((coefficient, suffix, _) in terms) {
            if (coefficient.signum() == 0) continue
            val negative = coefficient.signum() < 0
            val magnitude = coefficient.abs()
            val body = when {
                suffix.isEmpty() -> plain(magnitude)
                magnitude.compareTo(BigDecimal.ONE) == 0 -> suffix
                else -> "${plain(magnitude)}$suffix"
            }
            pieces.add(
                when {
                    pieces.isEmpty() -> if (negative) "\u2212$body" else body
                    negative -> " \u2212 $body"
                    else -> " + $body"
                },
            )
        }
        return if (pieces.isEmpty()) "0" else pieces.joinToString("")
    }
}

/** Kind of solution the engine could determine. */
enum class EquationKind {
    /** Exactly one solution (or a double root). */
    Unique,

    /** Every value satisfies the equation (0 = 0). */
    Identity,

    /** No value satisfies the equation. */
    Contradiction,

    /** A quadratic with a negative discriminant: no real root. */
    NoRealRoots,
}

data class LinearSolution(
    val variable: String,
    val standardForm: String,
    val coefficient: BigDecimal,
    val constant: BigDecimal,
    val root: BigDecimal?,
    val kind: EquationKind,
    val steps: List<String>,
    val verification: String,
)

data class ComplexRoot(val real: BigDecimal, val imaginary: BigDecimal) {
    val text: String
        get() = "${plain(roundForText(real))} ${if (imaginary.signum() < 0) "\u2212" else "+"} " +
            "${plain(roundForText(imaginary.abs()))}i"
}

data class QuadraticSolution(
    val variable: String,
    val standardForm: String,
    val a: BigDecimal,
    val b: BigDecimal,
    val c: BigDecimal,
    /** null on the linear fallback route. */
    val discriminant: BigDecimal?,
    val route: QuadraticRoute,
    val realRoots: List<BigDecimal>,
    /** False when a real root needed an irrational square root. */
    val rootsExact: Boolean,
    val complexRoots: List<ComplexRoot>,
    val kind: EquationKind,
    val steps: List<String>,
    val verification: String,
    val notes: List<String>,
)

enum class QuadraticRoute { Quadratic, Linear }

enum class SystemKind { Unique, Infinite, Inconsistent }

data class SystemSolution(
    val kind: SystemKind,
    val x: BigDecimal?,
    val y: BigDecimal?,
    val determinant: BigDecimal,
    val message: String,
    val steps: List<String>,
    val verification: String?,
)

/**
 * Equation solving for expressions written with one variable.
 *
 * The parser is a recursive-descent parser over coefficient triples: it
 * understands decimals, the variable, `+ − × * ÷ /`, `^` with integer
 * exponents 0..2, parentheses, fractions such as `1/2` and implicit
 * multiplication (`2x`, `3(x+1)`, `x(x−1)`). Both sides of `=` are parsed
 * and brought to the standard form before the root formulas are applied.
 */
object EquationEngine {

    // --------------------------------------------------------------- parsing

    fun parseExpression(text: String, variable: String = "x"): CalcResult<Polynomial> {
        if (text.isBlank()) return err(ErrorKind.InvalidInput, "Enter an expression")
        return try {
            ok(EqParser(EqLexer.tokenize(text), variable).parse())
        } catch (failure: EqParseException) {
            err(ErrorKind.Syntax, failure.message ?: "The expression could not be parsed")
        }
    }

    /** Splits `left = right` into the two sides. */
    fun splitEquation(text: String): CalcResult<Pair<String, String>> {
        val parts = text.split('=')
        return when {
            parts.size == 1 -> err(ErrorKind.Syntax, "Include one \"=\" sign, for example 2x + 4 = 10")
            parts.size > 2 -> err(ErrorKind.Syntax, "Include only one \"=\" sign")
            else -> ok(Pair(parts[0].trim(), parts[1].trim()))
        }
    }

    // ---------------------------------------------------------------- linear

    fun solveLinear(left: String, right: String, variable: String = "x"): CalcResult<LinearSolution> {
        val leftPoly = when (val leftResult = parseExpression(left, variable)) {
            is CalcResult.Ok -> leftResult.value
            is CalcResult.Err -> return leftResult
        }
        val rightPoly = when (val rightResult = parseExpression(right, variable)) {
            is CalcResult.Ok -> rightResult.value
            is CalcResult.Err -> return rightResult
        }
        val difference = leftPoly - rightPoly
        if (difference.degree > 1) {
            return err(ErrorKind.Domain, "This equation is quadratic; switch to the Quadratic mode")
        }
        val a = difference.a1
        val b = difference.a0
        val standardForm = "${difference.format(variable)} = 0"
        val steps = ArrayList<String>()
        steps.add("Move every term to the left side: $standardForm")
        val check = if (a.signum() == 0 && b.signum() == 0) {
            steps.add("Both sides simplify to the same expression, so the equation is always true")
            steps.add("Every value of $variable is a solution (infinitely many)")
            "No single value to substitute — any number works"
        } else if (a.signum() == 0) {
            steps.add("The $variable terms cancel, leaving ${plain(b)} = 0")
            steps.add("That statement is false, so no value of $variable can satisfy the equation")
            "The contradiction ${plain(b)} = 0 has no solution"
        } else {
            val root = b.negate().divide(a, ALG_CTX)
            steps.add("Isolate the $variable term: ${plain(a)}$variable = ${plain(b.negate())}")
            steps.add("Divide both sides by ${plain(a)}: $variable = ${plain(roundForText(root))}")
            val leftValue = leftPoly.evaluate(root)
            val rightValue = rightPoly.evaluate(root)
            if (leftValue.subtract(rightValue).abs() <= BigDecimal.ONE.scaleByPowerOfTen(-25).max(leftValue.abs().movePointLeft(25))) {
                "Substitute back: left side ${plain(roundForText(leftValue))}, right side ${plain(roundForText(rightValue))} \u2014 the root satisfies the equation"
            } else {
                "Substitution gives ${plain(roundForText(leftValue))} vs ${plain(roundForText(rightValue))}; the root is rounded"
            }
        }
        val kind = when {
            a.signum() == 0 && b.signum() == 0 -> EquationKind.Identity
            a.signum() == 0 -> EquationKind.Contradiction
            else -> EquationKind.Unique
        }
        val root = if (a.signum() == 0) null else b.negate().divide(a, ALG_CTX)
        return ok(LinearSolution(variable, standardForm, a, b, root, kind, steps, check))
    }

    fun solveLinearEquation(equation: String, variable: String = "x"): CalcResult<LinearSolution> {
        val parts = splitEquation(equation)
        return when (parts) {
            is CalcResult.Ok -> solveLinear(parts.value.first, parts.value.second, variable)
            is CalcResult.Err -> parts
        }
    }

    // -------------------------------------------------------------- quadratic

    fun solveQuadratic(
        a: BigDecimal,
        b: BigDecimal,
        c: BigDecimal,
        variable: String = "x",
        includeComplex: Boolean = true,
    ): CalcResult<QuadraticSolution> {
        val polynomial = Polynomial(a, b, c)
        if (a.signum() == 0) return linearFallback(variable, polynomial, includeComplex)
        val disc = b.multiply(b, ALG_CTX)
            .subtract(BigDecimal.valueOf(4).multiply(a, ALG_CTX).multiply(c, ALG_CTX), ALG_CTX)
        val twoA = a.multiply(BigDecimal.valueOf(2), ALG_CTX)
        val negB = b.negate()
        val standardForm = "${polynomial.format(variable)} = 0"
        val steps = ArrayList<String>()
        val notes = ArrayList<String>()
        steps.add("Standard form: $standardForm")
        steps.add("Identify a = ${plain(a)}, b = ${plain(b)}, c = ${plain(c)}")
        steps.add("Discriminant \u0394 = b\u00B2 \u2212 4ac = ${plain(b)}\u00B2 \u2212 4 \u00D7 ${plain(a)} \u00D7 ${plain(c)} = ${plain(roundForText(disc))}")

        val roots = ArrayList<BigDecimal>()
        val complex = ArrayList<ComplexRoot>()
        var exact = true
        var kind = EquationKind.Unique

        when {
            disc.signum() > 0 -> {
                val sqrt = bigSqrt(disc)
                    ?: return err(ErrorKind.Domain, "The discriminant could not be evaluated")
                exact = sqrt.multiply(sqrt).compareTo(disc) == 0
                val first = negB.add(sqrt, ALG_CTX).divide(twoA, ALG_CTX)
                val second = negB.subtract(sqrt, ALG_CTX).divide(twoA, ALG_CTX)
                roots.addAll(listOf(first, second).sorted())
                steps.add("\u0394 > 0 \u2192 two distinct real roots")
                steps.add(
                    "$variable = (\u2212b \u00B1 \u221A\u0394) \u00F7 2a = (${plain(negB)} \u00B1 " +
                        "${plain(roundForText(sqrt))}) \u00F7 ${plain(roundForText(twoA))}",
                )
                steps.add("$variable\u2081 = ${plain(roundForText(roots[0]))}, $variable\u2082 = ${plain(roundForText(roots[1]))}")
            }

            disc.signum() == 0 -> {
                val root = negB.divide(twoA, ALG_CTX)
                roots.add(root)
                steps.add("\u0394 = 0 \u2192 one double root")
                steps.add("$variable = \u2212b \u00F7 2a = ${plain(roundForText(root))}")
                notes.add("The two roots coincide: the parabola touches the $variable axis at one point")
            }

            else -> {
                kind = EquationKind.NoRealRoots
                steps.add("\u0394 < 0 \u2192 there is no real root")
                if (includeComplex) {
                    val sqrt = bigSqrt(disc.negate())
                        ?: return err(ErrorKind.Domain, "The discriminant could not be evaluated")
                    exact = sqrt.multiply(sqrt).compareTo(disc.negate()) == 0
                    val realPart = negB.divide(twoA, ALG_CTX)
                    val imaginary = sqrt.divide(twoA.abs(), ALG_CTX)
                    complex.add(ComplexRoot(realPart, imaginary))
                    complex.add(ComplexRoot(realPart, imaginary.negate()))
                    steps.add(
                        "Complex roots: $variable = \u2212b \u00F7 2a \u00B1 (\u221A(\u2212\u0394) \u00F7 |2a|)i = " +
                            "${plain(roundForText(realPart))} \u00B1 ${plain(roundForText(imaginary))}i",
                    )
                    if (!exact) {
                        notes.add("Complex roots are approximate: the square root is irrational (34 significant digits)")
                    }
                }
            }
        }
        if (!exact && roots.isNotEmpty()) {
            notes.add("The real roots are approximate (34 significant digits); the displayed values are rounded")
        }

        val verification = if (roots.isNotEmpty()) {
            val root = roots[0]
            val value = polynomial.evaluate(root)
            "Substituting $variable = ${plain(roundForText(root))} gives ${plain(roundForText(value))} \u2248 0"
        } else {
            "No real $variable satisfies the equation"
        }
        return ok(
            QuadraticSolution(
                variable = variable,
                standardForm = standardForm,
                a = a,
                b = b,
                c = c,
                discriminant = disc,
                route = QuadraticRoute.Quadratic,
                realRoots = roots,
                rootsExact = exact,
                complexRoots = complex,
                kind = kind,
                steps = steps,
                verification = verification,
                notes = notes,
            ),
        )
    }

    fun solveQuadratic(
        left: String,
        right: String,
        variable: String = "x",
        includeComplex: Boolean = true,
    ): CalcResult<QuadraticSolution> {
        val leftPoly = when (val leftResult = parseExpression(left, variable)) {
            is CalcResult.Ok -> leftResult.value
            is CalcResult.Err -> return leftResult
        }
        val rightPoly = when (val rightResult = parseExpression(right, variable)) {
            is CalcResult.Ok -> rightResult.value
            is CalcResult.Err -> return rightResult
        }
        val difference = leftPoly - rightPoly
        return solveQuadratic(difference.a2, difference.a1, difference.a0, variable, includeComplex)
    }

    fun solveQuadraticEquation(
        equation: String,
        variable: String = "x",
        includeComplex: Boolean = true,
    ): CalcResult<QuadraticSolution> {
        val parts = when (val split = splitEquation(equation)) {
            is CalcResult.Ok -> split.value
            is CalcResult.Err -> return split
        }
        return solveQuadratic(parts.first, parts.second, variable, includeComplex)
    }

    private fun linearFallback(
        variable: String,
        polynomial: Polynomial,
        includeComplex: Boolean,
    ): CalcResult<QuadraticSolution> {
        val b = polynomial.a1
        val c = polynomial.a0
        val standardForm = "${polynomial.format(variable)} = 0"
        val steps = ArrayList<String>()
        steps.add("The \u00B2 coefficient is zero, so the equation is linear: $standardForm")
        val roots: List<BigDecimal>
        val kind: EquationKind
        val verification: String
        when {
            b.signum() == 0 && c.signum() == 0 -> {
                kind = EquationKind.Identity
                roots = emptyList()
                steps.add("Both sides are identical: every value of $variable is a solution")
                verification = "Any value satisfies the equation"
            }
            b.signum() == 0 -> {
                kind = EquationKind.Contradiction
                roots = emptyList()
                steps.add("The $variable terms cancel leaving ${plain(c)} = 0, which is false")
                verification = "No value satisfies the equation"
            }
            else -> {
                kind = EquationKind.Unique
                val root = c.negate().divide(b, ALG_CTX)
                roots = listOf(root)
                steps.add("$variable = \u2212(${plain(c)}) \u00F7 ${plain(b)} = ${plain(roundForText(root))}")
                verification = "Substituting $variable = ${plain(roundForText(root))} gives 0"
            }
        }
        return ok(
            QuadraticSolution(
                variable = variable,
                standardForm = standardForm,
                a = BigDecimal.ZERO,
                b = b,
                c = c,
                discriminant = null,
                route = QuadraticRoute.Linear,
                realRoots = roots,
                rootsExact = true,
                complexRoots = emptyList(),
                kind = kind,
                steps = steps,
                verification = verification,
                notes = listOf("The quadratic formula needs a \u2260 0; this equation was solved as a linear one"),
            ),
        )
    }

    // ------------------------------------------------------------- system 2×2

    fun solveSystem2(
        a1: BigDecimal, b1: BigDecimal, c1: BigDecimal,
        a2: BigDecimal, b2: BigDecimal, c2: BigDecimal,
        variableX: String = "x", variableY: String = "y",
    ): CalcResult<SystemSolution> {
        val determinant = a1.multiply(b2, ALG_CTX).subtract(a2.multiply(b1, ALG_CTX), ALG_CTX)
        val equations = "${plain(a1)}$variableX + ${plain(b1)}$variableY = ${plain(c1)} ; " +
            "${plain(a2)}$variableX + ${plain(b2)}$variableY = ${plain(c2)}"
        val steps = ArrayList<String>()
        steps.add("Equations: $equations")
        steps.add(
            "Determinant D = a\u2081b\u2082 \u2212 a\u2082b\u2081 = ${plain(a1)} \u00D7 ${plain(b2)} \u2212 " +
                "${plain(a2)} \u00D7 ${plain(b1)} = ${plain(determinant)}",
        )
        if (determinant.signum() != 0) {
            val x = c1.multiply(b2, ALG_CTX).subtract(c2.multiply(b1, ALG_CTX), ALG_CTX)
                .divide(determinant, ALG_CTX)
            val y = a1.multiply(c2, ALG_CTX).subtract(a2.multiply(c1, ALG_CTX), ALG_CTX)
                .divide(determinant, ALG_CTX)
            steps.add("D \u2260 0 \u2192 the lines cross once: exactly one solution")
            steps.add(
                "$variableX = (c\u2081b\u2082 \u2212 c\u2082b\u2081) \u00F7 D = " +
                    "(${plain(roundForText(x))} after substituting the coefficients)",
            )
            steps.add("$variableY = (a\u2081c\u2082 \u2212 a\u2082c\u2081) \u00F7 D = ${plain(roundForText(y))}")
            val firstLeft = a1.multiply(x, ALG_CTX).add(b1.multiply(y, ALG_CTX), ALG_CTX)
            val secondLeft = a2.multiply(x, ALG_CTX).add(b2.multiply(y, ALG_CTX), ALG_CTX)
            val verification =
                "Check: ${plain(roundForText(firstLeft))} = ${plain(c1)} and ${plain(roundForText(secondLeft))} = ${plain(c2)}"
            steps.add(verification)
            return ok(
                SystemSolution(
                    kind = SystemKind.Unique,
                    x = x,
                    y = y,
                    determinant = determinant,
                    message = "One unique solution: $variableX = ${plain(roundForText(x))}, $variableY = ${plain(roundForText(y))}",
                    steps = steps,
                    verification = verification,
                ),
            )
        }

        // Determinant zero: parallel / identical lines or a degenerate row.
        val row1Zero = a1.signum() == 0 && b1.signum() == 0
        val row2Zero = a2.signum() == 0 && b2.signum() == 0
        val consistent: Boolean
        val explanation: String
        when {
            row1Zero && row2Zero -> {
                consistent = c1.signum() == 0 && c2.signum() == 0
                explanation = if (consistent) {
                    "Both equations are 0 = 0, so every pair ($variableX, $variableY) is a solution"
                } else {
                    "One equation reads 0 = ${plain(if (c1.signum() != 0) c1 else c2)}, which is false"
                }
            }
            row1Zero -> {
                consistent = c1.signum() == 0
                explanation = if (consistent) {
                    "The first equation is 0 = 0; the second describes a whole line of solutions"
                } else {
                    "The first equation reads 0 = ${plain(c1)}, which is false"
                }
            }
            row2Zero -> {
                consistent = c2.signum() == 0
                explanation = if (consistent) {
                    "The second equation is 0 = 0; the first describes a whole line of solutions"
                } else {
                    "The second equation reads 0 = ${plain(c2)}, which is false"
                }
            }
            else -> {
                val consistentConstants =
                    a1.multiply(c2, ALG_CTX).compareTo(a2.multiply(c1, ALG_CTX)) == 0 &&
                        b1.multiply(c2, ALG_CTX).compareTo(b2.multiply(c1, ALG_CTX)) == 0
                consistent = consistentConstants
                explanation = if (consistent) {
                    "The two rows are proportional including the constants, so the equations describe the same line"
                } else {
                    "The two rows are proportional but the constants are not, so the lines are parallel and never meet"
                }
            }
        }
        steps.add("D = 0 \u2192 the equations are dependent or contradictory")
        steps.add(explanation)
        return ok(
            SystemSolution(
                kind = if (consistent) SystemKind.Infinite else SystemKind.Inconsistent,
                x = null,
                y = null,
                determinant = determinant,
                message = if (consistent) "Infinitely many solutions" else "No solution (inconsistent system)",
                steps = steps,
                verification = null,
            ),
        )
    }
}

// ----------------------------------------------------------------- tokenizer

internal sealed interface EqToken {
    data class Number(val text: String) : EqToken
    data class Name(val text: String) : EqToken
    data object Plus : EqToken
    data object Minus : EqToken
    data object Star : EqToken
    data object Slash : EqToken
    data object Caret : EqToken
    data object LParen : EqToken
    data object RParen : EqToken
    data object End : EqToken
}

internal class EqParseException(message: String) : Exception(message)

internal object EqLexer {
    fun tokenize(input: String): List<EqToken> {
        val tokens = ArrayList<EqToken>()
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char.isWhitespace() -> index++

                char.isDigit() || (char == '.' && index + 1 < input.length && input[index + 1].isDigit()) -> {
                    val start = index
                    while (index < input.length &&
                        (input[index].isDigit() || input[index] == '.' || input[index] == ',')
                    ) index++
                    tokens.add(EqToken.Number(input.substring(start, index)))
                }

                char.isLetter() || char == '_' -> {
                    val start = index
                    while (index < input.length && (input[index].isLetterOrDigit() || input[index] == '_')) index++
                    tokens.add(EqToken.Name(input.substring(start, index)))
                }

                char == '+' -> { tokens.add(EqToken.Plus); index++ }
                char == '-' || char == '\u2212' || char == '\u2013' -> { tokens.add(EqToken.Minus); index++ }
                char == '*' || char == '\u00D7' || char == '\u2217' || char == '\u22C5' -> { tokens.add(EqToken.Star); index++ }
                char == '/' || char == '\u00F7' -> { tokens.add(EqToken.Slash); index++ }
                char == '^' -> { tokens.add(EqToken.Caret); index++ }
                char == '(' || char == '[' -> { tokens.add(EqToken.LParen); index++ }
                char == ')' || char == ']' -> { tokens.add(EqToken.RParen); index++ }
                else -> throw EqParseException("Unexpected character '$char'")
            }
        }
        tokens.add(EqToken.End)
        return tokens
    }
}

// ------------------------------------------------------------------- parser

internal class EqParser(
    private val tokens: List<EqToken>,
    private val variable: String,
) {
    private var position = 0

    private fun peek(): EqToken = tokens[position]
    private fun advance(): EqToken = tokens[position++]

    fun parse(): Polynomial {
        if (peek() == EqToken.End) throw EqParseException("The expression is empty")
        val result = parseSum()
        if (peek() != EqToken.End) throw EqParseException("Unexpected ${describe(peek())}")
        return result
    }

    private fun describe(token: EqToken): String = when (token) {
        is EqToken.Number -> "\"${token.text}\""
        is EqToken.Name -> "\"${token.text}\""
        EqToken.Plus -> "+"
        EqToken.Minus -> "\u2212"
        EqToken.Star -> "\u00D7"
        EqToken.Slash -> "\u00F7"
        EqToken.Caret -> "^"
        EqToken.LParen -> "("
        EqToken.RParen -> ")"
        EqToken.End -> "the end of the expression"
    }

    private fun unwrap(result: CalcResult<Polynomial>): Polynomial = when (result) {
        is CalcResult.Ok -> result.value
        is CalcResult.Err -> throw EqParseException(result.error.message)
    }

    private fun parseSum(): Polynomial {
        var left = parseProduct()
        while (true) {
            left = when (peek()) {
                EqToken.Plus -> { advance(); left + parseProduct() }
                EqToken.Minus -> { advance(); left - parseProduct() }
                else -> return left
            }
        }
    }

    private fun parseProduct(): Polynomial {
        var left = parseUnary()
        while (true) {
            left = when {
                peek() == EqToken.Star -> {
                    advance()
                    unwrap(left.multiply(parseUnary()))
                }
                peek() == EqToken.Slash -> {
                    advance()
                    val divisor = parseUnary()
                    if (!divisor.isConstant) {
                        throw EqParseException("Dividing by an expression containing $variable is not supported")
                    }
                    unwrap(left.divideByConstant(divisor.a0))
                }
                startsFactor(peek()) -> unwrap(left.multiply(parseUnary()))
                else -> return left
            }
        }
    }

    private fun startsFactor(token: EqToken): Boolean =
        token is EqToken.Number || token is EqToken.Name || token == EqToken.LParen

    private fun parseUnary(): Polynomial = when (peek()) {
        EqToken.Minus -> { advance(); parseUnary().negate() }
        EqToken.Plus -> { advance(); parseUnary() }
        else -> parsePower()
    }

    private fun parsePower(): Polynomial {
        val base = parsePrimary()
        if (peek() != EqToken.Caret) return base
        advance()
        val negative = when (peek()) {
            EqToken.Minus -> { advance(); true }
            EqToken.Plus -> { advance(); false }
            else -> false
        }
        val token = advance()
        if (token !is EqToken.Number) throw EqParseException("The exponent must be a whole number")
        val value = Num.parseOrNull(token.text)
            ?: throw EqParseException("Malformed exponent \"${token.text}\"")
        val exponent = try {
            value.toBigIntegerExact().toInt()
        } catch (_: ArithmeticException) {
            throw EqParseException("The exponent must be a whole number")
        }
        if (negative || exponent < 0 || exponent > 2) {
            throw EqParseException("Only exponents 0, 1 and 2 are supported")
        }
        return unwrap(base.power(exponent))
    }

    private fun parsePrimary(): Polynomial {
        val token = advance()
        return when (token) {
            is EqToken.Number -> {
                val value = Num.parseOrNull(token.text)
                    ?: throw EqParseException("Malformed number \"${token.text}\"")
                Polynomial(a0 = value)
            }
            is EqToken.Name -> when {
                token.text == variable -> Polynomial(a1 = BigDecimal.ONE)
                token.text.equals(variable, ignoreCase = true) -> Polynomial(a1 = BigDecimal.ONE)
                else -> throw EqParseException(
                    "Unknown name \"${token.text}\"; this tool solves expressions in \"$variable\"",
                )
            }
            EqToken.LParen -> {
                val inner = parseSum()
                if (peek() != EqToken.RParen) throw EqParseException("Missing closing bracket")
                advance()
                inner
            }
            else -> throw EqParseException("Unexpected ${describe(token)}")
        }
    }
}
