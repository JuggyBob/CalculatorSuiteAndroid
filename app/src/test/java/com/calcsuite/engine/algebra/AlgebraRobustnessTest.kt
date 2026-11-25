package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.engine.algebra.inequality.InequalityParseException
import com.calcsuite.engine.algebra.inequality.InequalityParser
import com.calcsuite.engine.algebra.inequality.InequalityResult
import com.calcsuite.engine.algebra.inequality.InequalitySolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

// 2 s: generous, but a hang never passes.
private const val TIME_BUDGET_NS = 2_000_000_000L

/**
 * Regression tests for inputs that could previously hang a tool or overflow
 * the JVM stack. Every case is bounded and runs in milliseconds on the JVM.
 */
class AlgebraRobustnessTest {

    private fun assertFast(started: Long, what: String) {
        val elapsed = System.nanoTime() - started
        assertTrue("$what took ${elapsed / 1_000_000} ms", elapsed < TIME_BUDGET_NS)
    }

    private fun <T> okValue(result: CalcResult<T>): T {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    // ---- 1. combinations with a huge r --------------------------------------

    @Test
    fun extremeCombinationWithHugeRReturnsQuickly() {
        val n = BigInteger.TEN.pow(12)
        val r = n.subtract(BigInteger.ONE)
        val started = System.nanoTime()
        val result = CombinatoricsEngine.combinations(n, r)
        assertFast(started, "C(10^12, 10^12 − 1)")

        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        val selection = (result as CalcResult.Ok).value
        // C(n, n − 1) = n; the real nCr path uses min(r, n − r) = 1.
        assertEquals(n, selection.value)
        assertTrue("the displayed product must be abbreviated", selection.steps.any { it.contains("…") })

        // Permutations with an r above the iteration cap are still rejected.
        assertEquals(ErrorKind.Unavailable, kind(CombinatoricsEngine.permutations(n, r)))
    }

    @Test
    fun ordinaryCombinationsKeepTheirExactValueAndTrace() {
        val result = okValue(CombinatoricsEngine.combinations(BigInteger.valueOf(5), BigInteger.valueOf(2)))
        assertEquals(BigInteger.TEN, result.value)
        assertTrue(result.steps.any { it.contains("5 × 4") })
    }

    @Test
    fun ordinaryWorkingStillExpandsTheFactorial() {
        val result = okValue(CombinatoricsEngine.combinations(BigInteger.valueOf(5), BigInteger.valueOf(2)))
        assertEquals(BigInteger.TEN, result.value)
        assertTrue(result.steps.any { it.contains("5 × 4") })
        assertTrue(result.steps.any { it.contains("÷ 2 = 10") })
    }

    @Test
    fun hugeResultsAreRefusedBeforeTheyAreMaterialised() {
        val started = System.nanoTime()
        val combination = okValue(
            CombinatoricsEngine.combinations(BigInteger.valueOf(100_000), BigInteger.valueOf(100_000)),
        )
        val permutation = CombinatoricsEngine.permutations(
            BigInteger.valueOf(1_000_000),
            BigInteger.valueOf(100_000),
        )
        assertFast(started, "C(100000,100000) and P(1000000,100000)")

        // C(n, n) = 1 is cheap and keeps a bounded, symbolic working.
        assertEquals(BigInteger.ONE, combination.value)
        assertTrue(combination.steps.any { it.contains("100000! (not expanded)") })
        assertTrue(
            "a C(100000,100000) working step has ${combination.steps.maxOf { it.length }} characters",
            combination.steps.all { it.length < 500 },
        )

        // P(10^6, 10^5) would have ~598 000 digits: refuse before building it.
        assertEquals(ErrorKind.Unavailable, kind(permutation))
        val refusal = permutation as CalcResult.Err
        assertTrue(
            "the refusal must state the digit cap: ${refusal.error.message}",
            refusal.error.message.contains("${CombinatoricsEngine.MAX_RESULT_DIGITS} digits"),
        )
    }

    @Test
    fun resultsBelowTheDigitCapStayExact() {
        assertEquals(BigInteger.TEN, okValue(CombinatoricsEngine.nCr(BigInteger.valueOf(5), BigInteger.valueOf(2))))
        assertEquals(
            BigInteger.valueOf(720),
            okValue(CombinatoricsEngine.nPr(BigInteger.valueOf(10), BigInteger.valueOf(3))),
        )
        val large = okValue(CombinatoricsEngine.nCr(BigInteger.valueOf(1000), BigInteger.valueOf(500)))
        assertTrue("a valid large result was refused", large.toString().length < CombinatoricsEngine.MAX_RESULT_DIGITS)
        assertTrue(large.bitLength() > 100)
    }

    // ---- 2. recursive parsers ------------------------------------------------

    @Test
    fun fiveThousandNestedParenthesesDoNotOverflowTheInequalityParser() {
        val input = "(".repeat(5000) + "x" + ")".repeat(5000) + " < 1"
        val started = System.nanoTime()
        val rejected = try {
            InequalityParser.parse(input)
            false
        } catch (e: InequalityParseException) {
            e.message?.contains("nested") == true
        }
        assertTrue("the parser must reject runaway nesting", rejected)
        val solved = InequalitySolver.solve(input)
        assertTrue("expected a failure but was $solved", solved is InequalityResult.Failed)
        assertTrue((solved as InequalityResult.Failed).message.contains("nested"))
        assertFast(started, "5000 nested parentheses in the inequality solver")
    }

    @Test
    fun deeplyNestedInequalityUnaryChainDoesNotOverflow() {
        val input = "-".repeat(5000) + "x < 1"
        val started = System.nanoTime()
        val solved = InequalitySolver.solve(input)
        assertTrue("expected a failure but was $solved", solved is InequalityResult.Failed)
        assertTrue((solved as InequalityResult.Failed).message.contains("nested"))
        assertFast(started, "5000 unary minus signs in the inequality solver")
    }

    @Test
    fun fiveThousandNestedParenthesesDoNotOverflowTheEquationEngine() {
        val body = "(".repeat(5000) + "x" + ")".repeat(5000)
        val started = System.nanoTime()
        val parsed = EquationEngine.parseExpression(body)
        assertEquals(ErrorKind.Syntax, kind(parsed))
        assertTrue((parsed as CalcResult.Err).error.message.contains("nested"))
        val solved = EquationEngine.solveLinearEquation("$body = 3")
        assertEquals(ErrorKind.Syntax, kind(solved))
        assertFast(started, "5000 nested parentheses in the equation engine")
    }

    @Test
    fun deeplyNestedSystemDoesNotOverflow() {
        val body = "(".repeat(5000) + "x" + ")".repeat(5000)
        val started = System.nanoTime()
        val result = SystemSolver.solve("$body + y = 5", "x - y = 1")
        assertEquals(ErrorKind.Syntax, kind(result))
        assertTrue((result as CalcResult.Err).error.message.contains("nested"))
        assertFast(started, "5000 nested parentheses in the system solver")
    }

    @Test
    fun moderateNestingStillSolvesNormally() {
        val body = "(".repeat(20) + "x" + ")".repeat(20)
        assertTrue(InequalitySolver.solve("$body < 3") is InequalityResult.Solved)
        assertEquals(
            BigInteger.valueOf(3),
            okValue(EquationEngine.solveLinearEquation("$body = 3")).root?.toBigIntegerExact(),
        )
        assertTrue(SystemSolver.solve("$body + y = 5", "x - y = 1") is CalcResult.Ok)
    }

    // ---- 3. primality digit cap ---------------------------------------------

    @Test
    fun hundredDigitPrimalityInputIsRejectedFast() {
        val huge = BigInteger("9".repeat(100))
        val started = System.nanoTime()
        val direct = PrimeEngine.isPrime(huge)
        val checked = PrimeEngine.isPrimeResult(huge)
        val next = PrimeEngine.nextPrime(huge)
        val previous = PrimeEngine.previousPrimeResult(huge)
        val legacyPrevious = PrimeEngine.previousPrime(huge)
        assertFast(started, "100-digit primality input")

        assertTrue(!direct.isPrime && !direct.proven)
        assertTrue(direct.explanation.contains("digits"))
        assertEquals(ErrorKind.Unavailable, kind(checked))
        assertEquals(ErrorKind.Unavailable, kind(next))
        assertEquals(ErrorKind.Unavailable, kind(previous))
        assertNull(legacyPrevious)
    }

    @Test
    fun normalPrimesStillWorkAfterTheDigitCap() {
        assertTrue(PrimeEngine.isPrime(BigInteger.valueOf(97)).isPrime)
        assertTrue(PrimeEngine.isPrime(BigInteger.valueOf(2305843009213693951L)).proven)
        assertTrue(PrimeEngine.isPrimeResult(BigInteger.valueOf(97)) is CalcResult.Ok)
        assertEquals(BigInteger.valueOf(101), okValue(PrimeEngine.nextPrime(BigInteger.valueOf(100))))
        assertEquals(BigInteger.valueOf(97), PrimeEngine.previousPrime(BigInteger.valueOf(100)))
        assertEquals(BigInteger.valueOf(97), okValue(PrimeEngine.previousPrimeResult(BigInteger.valueOf(100))))
    }
}
