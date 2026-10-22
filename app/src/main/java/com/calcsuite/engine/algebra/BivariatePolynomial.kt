package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * A polynomial in two variables, of total degree at most two.
 *
 * The terms are `x²`, `xy`, `y²`, `x`, `y` and the constant; a product that
 * would be of a higher degree is refused rather than quietly dropped.
 */
data class BivariatePolynomial(
    val x2: BigDecimal = BigDecimal.ZERO,
    val xy: BigDecimal = BigDecimal.ZERO,
    val y2: BigDecimal = BigDecimal.ZERO,
    val x: BigDecimal = BigDecimal.ZERO,
    val y: BigDecimal = BigDecimal.ZERO,
    val constant: BigDecimal = BigDecimal.ZERO,
) {
    /** True when every term is of degree one or less. */
    val isLinear: Boolean
        get() = x2.signum() == 0 && xy.signum() == 0 && y2.signum() == 0

    /** True when the polynomial does not depend on either variable. */
    val isConstant: Boolean
        get() = isLinear && x.signum() == 0 && y.signum() == 0

    /** The largest exponent of x in the polynomial. */
    val degreeInX: Int
        get() = when {
            x2.signum() != 0 -> 2
            xy.signum() != 0 -> 1
            x.signum() != 0 -> 1
            else -> 0
        }

    /** The largest exponent of y in the polynomial. */
    val degreeInY: Int
        get() = when {
            y2.signum() != 0 -> 2
            xy.signum() != 0 -> 1
            y.signum() != 0 -> 1
            else -> 0
        }

    operator fun plus(other: BivariatePolynomial): BivariatePolynomial = BivariatePolynomial(
        x2.add(other.x2, CTX),
        xy.add(other.xy, CTX),
        y2.add(other.y2, CTX),
        x.add(other.x, CTX),
        y.add(other.y, CTX),
        constant.add(other.constant, CTX),
    )

    operator fun minus(other: BivariatePolynomial): BivariatePolynomial = BivariatePolynomial(
        x2.subtract(other.x2, CTX),
        xy.subtract(other.xy, CTX),
        y2.subtract(other.y2, CTX),
        x.subtract(other.x, CTX),
        y.subtract(other.y, CTX),
        constant.subtract(other.constant, CTX),
    )

    fun negate(): BivariatePolynomial = BivariatePolynomial(
        x2.negate(), xy.negate(), y2.negate(), x.negate(), y.negate(), constant.negate(),
    )

    /**
     * Multiplies two polynomials, refusing a product of a degree above two.
     */
    fun multiply(other: BivariatePolynomial): CalcResult<BivariatePolynomial> {
        val x4 = x2.multiply(other.x2, CTX)
        val x3y = x2.multiply(other.xy, CTX).add(xy.multiply(other.x2, CTX), CTX)
        val xy3 = xy.multiply(other.y2, CTX).add(y2.multiply(other.xy, CTX), CTX)
        val y4 = y2.multiply(other.y2, CTX)
        val x3 = x2.multiply(other.x, CTX).add(x.multiply(other.x2, CTX), CTX)
        val x2y2 = x2.multiply(other.y2, CTX)
            .add(xy.multiply(other.xy, CTX), CTX)
            .add(y2.multiply(other.x2, CTX), CTX)
        // The terms of degree three: x²·y, x·y², and their other pairings.
        val x2y = x2.multiply(other.y, CTX)
            .add(xy.multiply(other.x, CTX), CTX)
            .add(y.multiply(other.x2, CTX), CTX)
        val xy2 = x.multiply(other.y2, CTX)
            .add(y.multiply(other.xy, CTX), CTX)
            .add(xy.multiply(other.y, CTX), CTX)
        val y3 = y2.multiply(other.y, CTX).add(y.multiply(other.y2, CTX), CTX)
        if (x4.signum() != 0 || x3y.signum() != 0 || xy3.signum() != 0 || y4.signum() != 0 ||
            x3.signum() != 0 || x2y2.signum() != 0 || x2y.signum() != 0 ||
            xy2.signum() != 0 || y3.signum() != 0
        ) {
            return err(
                ErrorKind.Domain,
                "That expression is of a degree above 2, which this solver does not reach",
            )
        }
        return ok(
            BivariatePolynomial(
                x2 = x2.multiply(other.constant, CTX)
                    .add(x.multiply(other.x, CTX), CTX)
                    .add(constant.multiply(other.x2, CTX), CTX),
                xy = xy.multiply(other.constant, CTX)
                    .add(x.multiply(other.y, CTX), CTX)
                    .add(y.multiply(other.x, CTX), CTX)
                    .add(constant.multiply(other.xy, CTX), CTX),
                y2 = y2.multiply(other.constant, CTX)
                    .add(y.multiply(other.y, CTX), CTX)
                    .add(constant.multiply(other.y2, CTX), CTX),
                x = x.multiply(other.constant, CTX).add(constant.multiply(other.x, CTX), CTX),
                y = y.multiply(other.constant, CTX).add(constant.multiply(other.y, CTX), CTX),
                constant = constant.multiply(other.constant, CTX),
            ),
        )
    }

    /** Divides the polynomial by a number. */
    fun divide(divisor: BigDecimal): CalcResult<BivariatePolynomial> {
        if (divisor.signum() == 0) return err(ErrorKind.DivisionByZero, "Division by zero")
        return ok(
            BivariatePolynomial(
                x2.divide(divisor, CTX),
                xy.divide(divisor, CTX),
                y2.divide(divisor, CTX),
                x.divide(divisor, CTX),
                y.divide(divisor, CTX),
                constant.divide(divisor, CTX),
            ),
        )
    }

    /** The value of the polynomial at a point. */
    fun evaluate(xValue: BigDecimal, yValue: BigDecimal): BigDecimal =
        x2.multiply(xValue, CTX).multiply(xValue, CTX)
            .add(xy.multiply(xValue, CTX).multiply(yValue, CTX), CTX)
            .add(y2.multiply(yValue, CTX).multiply(yValue, CTX), CTX)
            .add(x.multiply(xValue, CTX), CTX)
            .add(y.multiply(yValue, CTX), CTX)
            .add(constant, CTX)

    /** The polynomial with y replaced by another polynomial. */
    fun substituteY(replacement: BivariatePolynomial): CalcResult<BivariatePolynomial> {
        // The terms that do not have y in them stay as they are.
        var result = BivariatePolynomial(x2 = x2, x = x, constant = constant)
        if (xy.signum() != 0) {
            // The term x·y becomes x times the replacement.
            val xTimesReplacement = replacement
                .multiply(BivariatePolynomial(x = BigDecimal.ONE)).valueOrNull()
                ?: return replacement.multiply(BivariatePolynomial(x = BigDecimal.ONE))
            result = result.plus(xTimesReplacement.scaledBy(xy))
        }
        if (y2.signum() != 0) {
            // The term y² becomes the square of the replacement.
            val square = replacement.multiply(replacement).valueOrNull()
                ?: return replacement.multiply(replacement)
            result = result.plus(square.scaledBy(y2))
        }
        if (y.signum() != 0) {
            result = result.plus(replacement.scaledBy(y))
        }
        return ok(result)
    }

    /** The polynomial multiplied by a number. */
    fun scaledBy(factor: BigDecimal): BivariatePolynomial = BivariatePolynomial(
        x2 = x2.multiply(factor, CTX),
        xy = xy.multiply(factor, CTX),
        y2 = y2.multiply(factor, CTX),
        x = x.multiply(factor, CTX),
        y = y.multiply(factor, CTX),
        constant = constant.multiply(factor, CTX),
    )

    /** The polynomial as it is written, for the working. */
    fun format(variableX: String, variableY: String): String {
        val parts = mutableListOf<String>()
        fun add(coefficient: BigDecimal, term: String) {
            if (coefficient.signum() == 0) return
            val magnitude = coefficient.abs().stripTrailingZeros().toPlainString()
            val sign = if (coefficient.signum() < 0) " − " else ""
            val first = parts.isEmpty()
            val body = if (term.isEmpty()) {
                magnitude
            } else if (magnitude == "1") {
                term
            } else {
                "$magnitude$term"
            }
            parts.add(if (first) (if (coefficient.signum() < 0) "− $body" else body) else "$sign$body")
        }
        add(x2, "$variableX\u00B2")
        add(xy, "$variableX$variableY")
        add(y2, "$variableY\u00B2")
        add(x, variableX)
        add(y, variableY)
        add(constant, "")
        return if (parts.isEmpty()) "0" else parts.joinToString(" ")
    }

    companion object {
        /** The context the arithmetic is done in. */
        val CTX: MathContext = MathContext(34, RoundingMode.HALF_UP)
    }
}

/**
 * The ways a system of two equations can be solved.
 */
enum class SystemMethod(val label: String, val description: String) {
    Automatic(
        "Automatic",
        "The method that suits the equations, chosen for you.",
    ),
    Cramer(
        "Cramer's rule",
        "The determinant of the coefficients gives each variable directly.",
    ),
    Substitution(
        "Substitution",
        "One equation is rearranged and put into the other.",
    ),
    Elimination(
        "Elimination",
        "The equations are combined so that a variable cancels out.",
    ),
    Comparison(
        "Comparison",
        "Both equations are written with the same variable on its own, and equated.",
    ),
}

/**
 * What came of solving a system.
 */
enum class SystemOutcome {
    /** Exactly one solution. */
    Unique,

    /** More than one solution. */
    Multiple,

    /** One solution, but it is a repeated root. */
    Repeated,

    /** No real solution. */
    NoRealSolution,

    /** Every point of a line is a solution. */
    Infinite,

    /** The system does not say enough. */
    Underdetermined,

    /** The shape of the equations is not one this solver reaches. */
    Unsupported,
}

/**
 * The answer of a system of two equations.
 *
 * @param method the method that was used.
 * @param available the methods that apply to this system.
 * @param outcome what kind of answer it is.
 * @param solutions the real solutions, as pairs.
 * @param steps the working of the method that was used.
 * @param verification each solution put back into both equations.
 * @param notes anything worth saying about the answer.
 * @param message the answer in one line.
 * @param equation1 the first equation, as it was written.
 * @param equation2 the second equation, as it was written.
 */
data class SystemAnswer(
    val method: SystemMethod,
    val available: List<SystemMethod>,
    val outcome: SystemOutcome,
    val solutions: List<Pair<BigDecimal, BigDecimal>>,
    val steps: List<String>,
    val verification: List<String>,
    val notes: List<String>,
    val message: String,
    val equation1: String,
    val equation2: String,
) {
    /** True when the answer is one or more real solutions. */
    val hasSolutions: Boolean
        get() = solutions.isNotEmpty() && outcome != SystemOutcome.NoRealSolution
}
