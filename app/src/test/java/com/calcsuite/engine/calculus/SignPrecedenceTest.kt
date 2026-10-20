package com.calcsuite.engine.calculus

import com.calcsuite.core.CalcResult
import com.calcsuite.expr.AngleMode
import com.calcsuite.expr.ExpressionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign and the power.
 *
 * `-2^2` and `(-2)^2` are different expressions, and the app has to read
 * them the way the mathematics does: the power binds tighter than the
 * sign, so the first is `-(2^2)` and the second is `(-2)^2`.
 *
 * A negative number raised to an even power is positive **when the sign is
 * part of the base** — `(-2)^2 = 4`. But `-2^2` is not that expression: it
 * is the negative of `2^2`, and the sign is in front of the power, so it
 * is `-4`.
 */
class SignPrecedenceTest {

    /** The value the standard calculator gives. */
    private fun standard(text: String): String {
        val result = ExpressionEngine.evaluate(text, AngleMode.DEG)
        assertTrue("the expression $text should be readable", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value.value.toPlainString()
    }

    /** The value the calculus engine gives, read through the evaluator. */
    private fun calculus(text: String, variable: String = "x"): Double {
        val parsed = CalculusParser.parse(text)
        assertTrue("the expression $text should be readable", parsed is CalcResult.Ok)
        val value = Evaluator.eval((parsed as CalcResult.Ok).value, mapOf(variable to 1.0))
        assertTrue("the expression $text should be readable", value is CalcResult.Ok)
        return (value as CalcResult.Ok).value
    }

    /** The value of a printed expression at a point. */
    private fun at(text: String, point: Double, variable: String = "x"): Double {
        val parsed = CalculusParser.parse(text)
        assertTrue("the expression $text should be readable", parsed is CalcResult.Ok)
        val value = Evaluator.eval((parsed as CalcResult.Ok).value, mapOf(variable to point))
        assertTrue("the expression $text should be readable", value is CalcResult.Ok)
        return (value as CalcResult.Ok).value
    }

    @Test fun thePowerBindsTighterThanTheSign() {
        // -2^2 is the negative of two squared, which is minus four.
        assertEquals("-2^2 is the negative of two squared", "-4", standard("-2^2"))
        assertEquals("-2^2 is the negative of two squared", -4.0, calculus("-2^2"), 1e-12)
    }

    @Test fun theBracketsMakeTheSignPartOfTheBase() {
        // (-2)^2 is the square of minus two, which is four.
        assertEquals("(-2)^2 is the square of minus two", "4", standard("(-2)^2"))
        assertEquals("(-2)^2 is the square of minus two", 4.0, calculus("(-2)^2"), 1e-12)
    }

    @Test fun theSameHoldsForALetter() {
        // -x^2 is the negative of x squared, not the square of minus x.
        assertEquals("-x^2 is the negative of x squared", -1.0, calculus("-x^2"), 1e-12)
        assertEquals("(-x)^2 is the square of minus x", 1.0, calculus("(-x)^2"), 1e-12)
    }

    @Test fun theTwoFormsAreNeverConfused() {
        // The one place where they differ in value is where the confusion
        // would show, so both are pinned here.
        assertEquals(-4.0, calculus("-2^2"), 1e-12)
        assertEquals(4.0, calculus("(-2)^2"), 1e-12)
    }

    @Test fun theDerivativeTellsThemApartToo() {
        // The derivative of -x^2 is -2x, and the derivative of (-x)^2 is
        // 2x, because (-x)^2 is x^2. The two expressions are different
        // things, and the engine keeps them apart.
        val first = DifferentiationEngine.derivative("-x^2", "x")
        val second = DifferentiationEngine.derivative("(-x)^2", "x")
        assertTrue("the derivative should be found", first is CalcResult.Ok)
        assertTrue("the derivative should be found", second is CalcResult.Ok)
        assertEquals(
            "the derivative of -x^2 at 1 is -2",
            -2.0,
            at((first as CalcResult.Ok).value.derivative, 1.0),
            1e-12,
        )
        assertEquals(
            "the derivative of (-x)^2 at 1 is 2",
            2.0,
            at((second as CalcResult.Ok).value.derivative, 1.0),
            1e-12,
        )
    }
}
