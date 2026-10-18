package com.calcsuite.expr

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok

/**
 * Facade for the expression engine used by the standard and scientific
 * calculators.
 *
 * ## Language
 * - Numbers: `12`, `3.5`, `1.5e-9` (a `.` is always the decimal separator
 *   inside expressions; the UI inserts the locale separator and converts).
 * - Operators: `+  −  ×  ÷  ^  mod`, unary `±`, postfix `!` (factorial) and
 *   `%` (percent). `^` is right-associative and binds tighter than unary minus
 *   (`−2^2 = −4`).
 * - Implicit multiplication: `2π`, `3(4+5)`, `(1+2)(3+4)`, `2sin(30)`.
 * - Percent convention: in `a + b%` and `a − b%` the percentage refers to the
 *   left operand (`200 + 10% = 220`); elsewhere `b%` is simply `b/100`.
 * - Functions require parentheses unless they take exactly one argument, in
 *   which case a bare argument is accepted (`sin 30`); see [Functions.ALL].
 * - Constants: π, τ, φ, pi, tau, phi, e.
 */
object ExpressionEngine {

    fun evaluate(input: String, angleMode: AngleMode = AngleMode.DEG): CalcResult<EvalValue> {
        val expr = try {
            ExpressionParser(input).parse()
        } catch (e: ExprParseException) {
            return err(ErrorKind.Syntax, e.message ?: "Invalid expression")
        }
        return try {
            ok(Evaluator(angleMode).eval(expr))
        } catch (e: EvalException) {
            err(e.kind, e.message ?: "Cannot evaluate expression")
        } catch (_: ArithmeticException) {
            err(ErrorKind.Overflow, "The result is too large to compute")
        }
    }

    /** Expression source for display of a stored history entry. */
    fun prettify(input: String): String =
        input.replace("*", "×").replace("/", "÷").replace("-", "−")
}
