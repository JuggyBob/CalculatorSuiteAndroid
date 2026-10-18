package com.calcsuite.expr

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class ExpressionEngineTest {

    private fun evalValue(expr: String, mode: AngleMode = AngleMode.RAD): BigDecimal {
        val result = ExpressionEngine.evaluate(expr, mode)
        assertTrue("Expected success for \"$expr\" but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value.value
    }

    private fun evalError(expr: String, mode: AngleMode = AngleMode.RAD): ErrorKind {
        val result = ExpressionEngine.evaluate(expr, mode)
        assertTrue("Expected failure for \"$expr\" but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    /** Compares to 15 significant digits (display-level precision). */
    private fun assertNumber(expected: String, expr: String, mode: AngleMode = AngleMode.RAD) {
        val actual = evalValue(expr, mode)
        val cmp = actual.round(MathContext(15, RoundingMode.HALF_UP))
            .compareTo(BigDecimal(expected).round(MathContext(15, RoundingMode.HALF_UP)))
        assertEquals("$expr → expected $expected, was $actual", 0, cmp)
    }

    private fun assertExact(expected: String, expr: String, mode: AngleMode = AngleMode.RAD) {
        val actual = evalValue(expr, mode)
        assertEquals("$expr → expected exactly $expected, was $actual", 0, actual.compareTo(BigDecimal(expected)))
    }

    // ---- arithmetic & precedence -------------------------------------------

    @Test fun basicArithmetic() {
        assertNumber("14", "2+3*4")
        assertNumber("20", "(2+3)*4")
        assertNumber("7", "10-3")
        assertNumber("2.5", "5/2")
        assertNumber("1", "1+2-2")
    }

    @Test fun exactDecimalArithmetic() {
        // The engine is decimal-based: 0.1 + 0.2 is exactly 0.3 (unlike binary floats).
        assertExact("0.3", "0.1+0.2")
    }

    @Test fun powerPrecedenceAndDirection() {
        assertExact("512", "2^3^2")       // right associative: 2^(3^2)
        assertExact("-4", "-2^2")          // unary minus binds looser than ^
        assertExact("0.125", "2^-3")
        assertExact("4", "(-2)^2")
    }

    @Test fun unaryAndSigns() {
        assertExact("-5", "-5")
        assertExact("5", "--5")
        assertExact("-1", "2 * -0.5")
        assertExact("1", "-2 + 3")
    }

    @Test fun modulo() {
        assertExact("1", "10 mod 3")
        assertExact("2", "17 mod 5")
        assertEquals(ErrorKind.DivisionByZero, evalError("5 mod 0"))
    }

    @Test fun percentConventions() {
        assertExact("0.15", "15%")
        assertExact("220", "200 + 10%")
        assertExact("180", "200 - 10%")
        assertExact("20", "200 * 10%")
        assertExact("0.005", "50% / 100")
        assertExact("30", "0.15*200")
    }

    @Test fun factorial() {
        assertExact("120", "5!")
        assertExact("1", "0!")
        assertExact("3628800", "10!")
        assertEquals(ErrorKind.Domain, evalError("(-3)!"))
        assertEquals(ErrorKind.Domain, evalError("2.5!"))
    }

    // ---- implicit multiplication -------------------------------------------

    @Test fun implicitMultiplication() {
        assertNumber("6.28318530717959", "2pi")
        assertExact("27", "3(4+5)")
        assertExact("21", "(1+2)(3+4)")
        assertExact("1", "2sin(30)", AngleMode.DEG)
    }

    @Test fun constants() {
        assertNumber("3.14159265358979", "pi")
        assertNumber("2.71828182845905", "e")
        assertNumber("6.28318530717959", "tau")
        assertNumber("1.61803398874989", "phi")
    }

    // ---- functions ---------------------------------------------------------

    @Test fun trigonometryDegrees() {
        assertExact("0.5", "sin(30)", AngleMode.DEG)
        assertExact("1", "cos(0)", AngleMode.DEG)
        assertExact("1", "tan(45)", AngleMode.DEG)
        assertExact("90", "asin(1)", AngleMode.DEG)
        assertExact("0.5", "sin 30", AngleMode.DEG)
    }

    @Test fun trigonometryRadians() {
        assertNumber("1", "sin(pi/2)")
        assertNumber("0", "cos(pi/2)")
        assertNumber("0.785398163397448", "atan(1)")
    }

    @Test fun tanUndefinedAt90Degrees() {
        assertEquals(ErrorKind.Domain, evalError("tan(90)", AngleMode.DEG))
        assertEquals(ErrorKind.Domain, evalError("tan(270)", AngleMode.DEG))
    }

    @Test fun logarithms() {
        assertNumber("2", "log(100)")
        assertNumber("3", "log(8, 2)")
        assertNumber("1", "ln(e)")
        assertNumber("10", "log2(1024)")
        assertEquals(ErrorKind.Domain, evalError("ln(0)"))
        assertEquals(ErrorKind.Domain, evalError("log(-5)"))
        assertEquals(ErrorKind.Domain, evalError("log(10, 1)"))
    }

    @Test fun roots() {
        assertExact("4", "sqrt(16)")
        assertExact("3", "cbrt(27)")
        assertExact("2", "root(4, 2)")
        assertEquals(ErrorKind.Domain, evalError("sqrt(-1)"))
    }

    @Test fun hyperbolicAndInverse() {
        assertExact("0", "sinh(0)")
        assertExact("1", "cosh(0)")
        assertExact("0", "tanh(0)")
        assertExact("0", "asinh(0)")
        assertExact("0", "acosh(1)")
        assertEquals(ErrorKind.Domain, evalError("acosh(0)"))
        assertEquals(ErrorKind.Domain, evalError("atanh(1)"))
    }

    @Test fun roundingFunctions() {
        assertExact("3", "floor(3.7)")
        assertExact("4", "ceil(3.2)")
        assertExact("3.14", "round(3.14159, 2)")
        assertExact("-4", "floor(-3.2)")
        assertExact("7", "abs(-7)")
        assertExact("-1", "sign(-12)")
        assertExact("5", "hypot(3, 4)")
    }

    @Test fun combinatoricsExact() {
        assertExact("10", "ncr(5, 2)")
        assertExact("120", "npr(5, 5)")
        assertExact("2598960", "ncr(52, 5)")
        assertExact("265252859812191058636308480000000", "30!")
        assertEquals(ErrorKind.Domain, evalError("ncr(5, 6)"))
        assertEquals(ErrorKind.Domain, evalError("ncr(-5, 2)"))
    }

    @Test fun bigIntegerPowerExact() {
        assertExact("1267650600228229401496703205376", "2^100")
    }

    @Test fun gcdLcm() {
        assertExact("6", "gcd(18, 24)")
        assertExact("36", "lcm(12, 18)")
    }

    // ---- errors ------------------------------------------------------------

    @Test fun syntaxErrors() {
        assertEquals(ErrorKind.Syntax, evalError("2+"))
        assertEquals(ErrorKind.Syntax, evalError("(2+3"))
        assertEquals(ErrorKind.Syntax, evalError("2+*3"))
        assertEquals(ErrorKind.Syntax, evalError("foo(2)"))
        assertEquals(ErrorKind.Syntax, evalError(""))
        assertEquals(ErrorKind.Syntax, evalError("2 3 +"))
    }

    @Test fun divisionByZero() {
        assertEquals(ErrorKind.DivisionByZero, evalError("1/0"))
        assertEquals(ErrorKind.DivisionByZero, evalError("5/(3-3)"))
    }

    @Test fun domainErrors() {
        assertEquals(ErrorKind.Domain, evalError("asin(2)"))
        assertEquals(ErrorKind.Domain, evalError("sqrt(-4)"))
        assertEquals(ErrorKind.Domain, evalError("0^0"))
        assertEquals(ErrorKind.Domain, evalError("(-2)^0.5"))
    }

    @Test fun overflowErrors() {
        assertEquals(ErrorKind.Overflow, evalError("100001!"))
    }

    @Test fun scientificNotationInput() {
        assertExact("1500", "1.5e3")
        assertExact("0.0015", "1.5e-3")
        assertExact("200000", "2e5")
    }

    @Test fun exactnessFlag() {
        val exact = (ExpressionEngine.evaluate("0.1+0.2") as CalcResult.Ok).value
        assertTrue(exact.exact)
        val inexact = (ExpressionEngine.evaluate("sin(1)") as CalcResult.Ok).value
        assertTrue(!inexact.exact)
        val oneThird = (ExpressionEngine.evaluate("1/3") as CalcResult.Ok).value
        assertTrue(!oneThird.exact)
    }

    @Test fun gradingMode() {
        assertExact("1", "sin(100)", AngleMode.GRAD)
        assertExact("100", "asin(1)", AngleMode.GRAD)
    }
}
