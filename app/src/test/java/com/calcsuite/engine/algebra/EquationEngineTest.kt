package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class EquationEngineTest {

    private fun bd(text: String) = BigDecimal(text)

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun message(result: CalcResult<*>): String {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    private fun <T> okValue(result: CalcResult<T>): T {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return result.valueOrNull()!!
    }

    private fun assertExact(expected: String, actual: BigDecimal) {
        assertEquals("expected exactly $expected but was $actual", 0, actual.compareTo(bd(expected)))
    }

    private fun assertClose(expected: String, actual: BigDecimal, digits: Int = 12) {
        val cmp = actual.round(MathContext(digits, RoundingMode.HALF_UP))
            .compareTo(bd(expected).round(MathContext(digits, RoundingMode.HALF_UP)))
        assertEquals("expected ≈ $expected but was $actual", 0, cmp)
    }

    // ---- linear equations ---------------------------------------------------

    @Test
    fun solvesLinearExample() {
        val solved = okValue(EquationEngine.solveLinearEquation("2x + 4 = 10"))
        assertEquals(EquationKind.Unique, solved.kind)
        assertExact("3", solved.root!!)
        assertTrue(solved.steps.isNotEmpty())
    }

    @Test
    fun solvesLinearWithPlainCoefficient() {
        assertExact("4", okValue(EquationEngine.solveLinearEquation("3x = 12")).root!!)
    }

    @Test
    fun solvesLinearWithFractionCoefficient() {
        assertExact("4", okValue(EquationEngine.solveLinearEquation("x/2 + 1 = 3")).root!!)
        assertExact("2", okValue(EquationEngine.solveLinearEquation("1/2 x + 3 = 4")).root!!)
    }

    @Test
    fun linearVariableOnBothSides() {
        assertExact("3", okValue(EquationEngine.solveLinearEquation("5x - 2 = 2x + 7")).root!!)
    }

    @Test
    fun identityHasInfinitelyManySolutions() {
        val solved = okValue(EquationEngine.solveLinearEquation("2(x + 1) = 2x + 2"))
        assertEquals(EquationKind.Identity, solved.kind)
        assertEquals(null, solved.root)
    }

    @Test
    fun contradictionHasNoSolution() {
        val solved = okValue(EquationEngine.solveLinearEquation("x + 1 = x + 2"))
        assertEquals(EquationKind.Contradiction, solved.kind)
        assertEquals(null, solved.root)
    }

    @Test
    fun quadraticSentToTheQuadraticMode() {
        val result = EquationEngine.solveLinearEquation("x^2 = 4")
        assertEquals(ErrorKind.Domain, kind(result))
        assertTrue(message(result).contains("quadratic"))
    }

    @Test
    fun splitEquationRules() {
        assertEquals("a", okValue(EquationEngine.splitEquation("a = b")).first)
        assertEquals(ErrorKind.Syntax, kind(EquationEngine.splitEquation("a")))
        assertEquals(ErrorKind.Syntax, kind(EquationEngine.splitEquation("a = b = c")))
    }

    // ---- parsing ------------------------------------------------------------

    @Test
    fun parsesImplicitMultiplication() {
        val polynomial = okValue(EquationEngine.parseExpression("2x + 3(x - 1)"))
        assertExact("2", polynomial.evaluate(BigDecimal.ONE))
    }

    @Test
    fun parsesVariableProducts() {
        val polynomial = okValue(EquationEngine.parseExpression("x(x - 1)"))
        assertExact("20", polynomial.evaluate(bd("5")))
    }

    @Test
    fun parseErrors() {
        assertEquals(ErrorKind.InvalidInput, kind(EquationEngine.parseExpression("")))
        assertEquals(ErrorKind.Syntax, kind(EquationEngine.parseExpression("x^3")))
        assertEquals(ErrorKind.Syntax, kind(EquationEngine.parseExpression("1/x")))
        assertEquals(ErrorKind.Syntax, kind(EquationEngine.parseExpression("2y + 1", "x")))
        assertEquals(ErrorKind.Syntax, kind(EquationEngine.parseExpression("x +")))
    }

    // ---- quadratic equations ------------------------------------------------

    @Test
    fun quadraticTwoRealRoots() {
        val solved = okValue(EquationEngine.solveQuadraticEquation("x^2 - 3x + 2 = 0"))
        assertEquals(listOf(bd("1"), bd("2")), solved.realRoots)
        assertEquals(0, solved.discriminant!!.compareTo(BigDecimal.ONE))
        assertTrue(solved.rootsExact)
        assertEquals(QuadraticRoute.Quadratic, solved.route)
    }

    @Test
    fun quadraticDoubleRoot() {
        val solved = okValue(EquationEngine.solveQuadraticEquation("x^2 - 2x + 1 = 0"))
        assertEquals(1, solved.realRoots.size)
        assertExact("1", solved.realRoots[0])
        assertExact("0", solved.discriminant!!)
    }

    @Test
    fun quadraticNoRealRootsGivesComplexConjugates() {
        val solved = okValue(EquationEngine.solveQuadraticEquation("x^2 + 1 = 0"))
        assertEquals(EquationKind.NoRealRoots, solved.kind)
        assertTrue(solved.realRoots.isEmpty())
        assertEquals(2, solved.complexRoots.size)
        assertExact("0", solved.complexRoots[0].real)
        assertExact("1", solved.complexRoots[0].imaginary)
        assertExact("-1", solved.complexRoots[1].imaginary)
        assertTrue(solved.complexRoots[0].text.contains("i"))
    }

    @Test
    fun quadraticIrrationalRootsAreMarkedApproximate() {
        val solved = okValue(EquationEngine.solveQuadraticEquation("x^2 = 2"))
        assertEquals(2, solved.realRoots.size)
        assertClose("-1.41421356237", solved.realRoots[0])
        assertClose("1.41421356237", solved.realRoots[1])
        assertTrue(!solved.rootsExact)
    }

    @Test
    fun quadraticRouteFallsBackToLinear() {
        val solved = okValue(EquationEngine.solveQuadraticEquation("2x + 4 = 10"))
        assertEquals(QuadraticRoute.Linear, solved.route)
        assertEquals(1, solved.realRoots.size)
        assertExact("3", solved.realRoots[0])
        assertEquals(null, solved.discriminant)
    }

    @Test
    fun quadraticFormulaOverload() {
        val solved = okValue(EquationEngine.solveQuadratic(bd("1"), bd("-3"), bd("2")))
        assertEquals(listOf(bd("1"), bd("2")), solved.realRoots)
    }

    // ---- systems ------------------------------------------------------------

    @Test
    fun systemUniqueSolution() {
        val solved = okValue(
            EquationEngine.solveSystem2(bd("2"), bd("3"), bd("12"), bd("1"), bd("-1"), bd("1")),
        )
        assertEquals(SystemKind.Unique, solved.kind)
        assertExact("3", solved.x!!)
        assertExact("2", solved.y!!)
        assertExact("-5", solved.determinant)
        assertTrue(solved.steps.any { it.contains("=") })
    }

    @Test
    fun systemInconsistent() {
        val solved = okValue(
            EquationEngine.solveSystem2(bd("1"), bd("1"), bd("1"), bd("1"), bd("1"), bd("2")),
        )
        assertEquals(SystemKind.Inconsistent, solved.kind)
        assertEquals(null, solved.x)
        assertTrue(solved.message.contains("No solution"))
    }

    @Test
    fun systemInfinitelyManySolutions() {
        val solved = okValue(
            EquationEngine.solveSystem2(bd("1"), bd("1"), bd("1"), bd("2"), bd("2"), bd("2")),
        )
        assertEquals(SystemKind.Infinite, solved.kind)
        assertTrue(solved.message.contains("Infinitely many"))
    }

    @Test
    fun systemWithABlankRow() {
        val solved = okValue(
            EquationEngine.solveSystem2(bd("0"), bd("0"), bd("5"), bd("1"), bd("-1"), bd("1")),
        )
        assertEquals(SystemKind.Inconsistent, solved.kind)
    }
}
