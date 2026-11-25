package com.calcsuite.expr

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Regression tests for the robustness fixes in the expression engine:
 * bounded parse/eval depth, bounded exponents and integer magnitudes,
 * `round` digit validation and the cosecant table check.
 */
class ExpressionRobustnessTest {

    private fun result(expr: String, mode: AngleMode = AngleMode.DEG): CalcResult<EvalValue> =
        ExpressionEngine.evaluate(expr, mode)

    private fun errorKind(expr: String, mode: AngleMode = AngleMode.DEG): ErrorKind {
        val r = result(expr, mode)
        assertTrue("Expected \"$expr\" to fail, was $r", r is CalcResult.Err)
        return (r as CalcResult.Err).error.kind
    }

    private fun assertNumber(expected: String, expr: String, mode: AngleMode = AngleMode.DEG) {
        val r = result(expr, mode)
        assertTrue("Expected \"$expr\" to succeed, was $r", r is CalcResult.Ok)
        val actual = (r as CalcResult.Ok).value.value
        assertEquals(
            "$expr → expected $expected, was $actual",
            0,
            actual.round(MathContext(15, RoundingMode.HALF_UP))
                .compareTo(BigDecimal(expected).round(MathContext(15, RoundingMode.HALF_UP))),
        )
    }

    // ---- 1. recursion depth -------------------------------------------------

    @Test fun deeplyNestedParenthesesReturnAnError() {
        val expr = "(".repeat(5_000) + "1" + ")".repeat(5_000)
        assertEquals(ErrorKind.Syntax, errorKind(expr))
    }

    @Test fun longUnaryChainReturnsAnError() {
        assertEquals(ErrorKind.Syntax, errorKind("-".repeat(5_000) + "1"))
    }

    @Test fun deepButLegalExpressionReturnsOverflowInsteadOfCrashing() {
        // Addition is parsed in a loop, so the parser accepts this input; the
        // resulting tree is left-deep and must be rejected while evaluating.
        val expr = List(5_000) { "1" }.joinToString("+")
        assertEquals(ErrorKind.Overflow, errorKind(expr))
    }

    @Test fun stackOverflowErrorIsCaughtByTheEngine() {
        // Build a tree far deeper than any stack can hold and bypass the
        // normal depth guard to exercise the engine's safety net directly.
        var deep: Expr = Expr.Num(BigDecimal.ONE)
        repeat(500_000) { deep = Expr.Unary(UnOp.Pos, deep) }
        val r = ExpressionEngine.evaluate(deep, maxDepth = Int.MAX_VALUE)
        assertTrue(r is CalcResult.Err)
        assertEquals(ErrorKind.Overflow, (r as CalcResult.Err).error.kind)
    }

    // ---- 2. exponent / magnitude bounds -----------------------------------

    @Test fun scientificExponentsAreBounded() {
        assertEquals(ErrorKind.Syntax, errorKind("1e999999"))
        assertEquals(ErrorKind.Syntax, errorKind("1e-999999"))
        assertTrue(result("1e10000") is CalcResult.Ok)
    }

    @Test fun hugeFactorialIsRejectedBeforeAllocating() {
        val hugeInteger = "9".repeat(10_001)
        assertEquals(ErrorKind.Overflow, errorKind("fact($hugeInteger)"))
        assertTrue(result("fact(1e999999)") is CalcResult.Err)
        assertNumber("120", "5!")
    }

    @Test fun hugeIntegerArgumentsToGcdAreRejected() {
        val hugeInteger = "9".repeat(10_001)
        assertEquals(ErrorKind.Overflow, errorKind("gcd($hugeInteger, 2)"))
        assertTrue(result("gcd(1e999999, 2)") is CalcResult.Err)
        assertNumber("6", "gcd(18, 24)")
    }

    @Test fun hugeValuesPassedToRoundingAreErrors() {
        assertTrue(result("floor(1e999999)") is CalcResult.Err)
        assertTrue(result("round(1e999999, 2)") is CalcResult.Err)
        assertNumber("3", "floor(3.7)")
    }

    // ---- 3. combinations ----------------------------------------------------

    @Test fun combinationSelectionsAreBounded() {
        assertEquals(ErrorKind.Overflow, errorKind("ncr(1000000, 500000)"))
        assertEquals(ErrorKind.Overflow, errorKind("npr(1000000, 500000)"))
        assertNumber("10", "ncr(5, 2)")
    }

    @Test fun hugeCombinationArgumentsAreRejected() {
        assertTrue(result("ncr(1e999999, 2)") is CalcResult.Err)
    }

    // ---- 5. round digits ----------------------------------------------------

    @Test fun roundDigitsAreValidatedBeforeTruncation() {
        assertEquals(ErrorKind.Overflow, errorKind("round(1, 1000000000)"))
        assertEquals(ErrorKind.Overflow, errorKind("round(1, 1001)"))
        assertEquals(ErrorKind.Overflow, errorKind("round(1, -1001)"))
        assertEquals(ErrorKind.Domain, errorKind("round(1, 1.5)"))
        assertNumber("3.14", "round(3.14159, 2)")
        assertNumber("1000", "round(1234, -3)")
    }

    // ---- 6. cosecant --------------------------------------------------------

    @Test fun cosecantWorksAtNonZeroNiceAngles() {
        assertNumber("2", "csc(30)", AngleMode.DEG)
        assertNumber("1", "csc(90)", AngleMode.DEG)
        assertEquals(ErrorKind.Domain, errorKind("csc(180)", AngleMode.DEG))
        assertEquals(ErrorKind.Domain, errorKind("csc(0)", AngleMode.DEG))
    }
}
