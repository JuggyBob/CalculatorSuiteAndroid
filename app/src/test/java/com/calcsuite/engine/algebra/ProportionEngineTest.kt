package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ProportionEngineTest {

    private fun bd(text: String) = BigDecimal(text)

    private fun solution(result: CalcResult<ProportionSolution>): ProportionSolution {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun assertExact(expected: String, actual: BigDecimal) {
        assertEquals("expected exactly $expected but was $actual", 0, actual.compareTo(bd(expected)))
    }

    // ---- direct proportions -------------------------------------------------

    @Test
    fun solvesForMissingDenominator() {
        val solved = solution(
            ProportionEngine.solveDirect(
                a = bd("2"),
                b = bd("3"),
                c = bd("8"),
                unknown = ProportionSlot.D,
            ),
        )
        assertExact("12", solved.value)
        assertEquals(ProportionSlot.D, solved.unknown)
        assertEquals("2/3 = 8/12", solved.equation)
    }

    @Test
    fun solvesForEachOfTheFourSlots() {
        assertExact(
            "2",
            solution(ProportionEngine.solveDirect(b = bd("3"), c = bd("8"), d = bd("12"), unknown = ProportionSlot.A)).value,
        )
        assertExact(
            "3",
            solution(ProportionEngine.solveDirect(a = bd("2"), c = bd("8"), d = bd("12"), unknown = ProportionSlot.B)).value,
        )
        assertExact(
            "8",
            solution(ProportionEngine.solveDirect(a = bd("2"), b = bd("3"), d = bd("12"), unknown = ProportionSlot.C)).value,
        )
        assertExact(
            "12",
            solution(ProportionEngine.solveDirect(a = bd("2"), b = bd("3"), c = bd("8"), unknown = ProportionSlot.D)).value,
        )
    }

    @Test
    fun directSolutionHasStepsAndVerification() {
        val solved = solution(
            ProportionEngine.solveDirect(a = bd("2"), b = bd("3"), c = bd("8"), unknown = ProportionSlot.D),
        )
        assertTrue("expected steps", solved.steps.size >= 4)
        assertTrue(
            "unexpected verification: ${solved.verification}",
            solved.verification.contains("proportion holds exactly"),
        )
    }

    @Test
    fun threeKnownValuesAreRequired() {
        val result = ProportionEngine.solveDirect(a = bd("2"), b = bd("3"), unknown = ProportionSlot.D)
        assertEquals(ErrorKind.Insufficient, kind(result))
    }

    @Test
    fun zeroDivisorIsRejected() {
        // Solving D in direct mode divides by a.
        val result = ProportionEngine.solveDirect(a = bd("0"), b = bd("3"), c = bd("8"), unknown = ProportionSlot.D)
        assertEquals(ErrorKind.DivisionByZero, kind(result))
    }

    // ---- inverse proportions ------------------------------------------------

    @Test
    fun inverseProportionWorkerExample() {
        // 4 workers × 9 days = 6 workers × ? days → 6 days.
        val solved = solution(
            ProportionEngine.solveInverse(a = bd("4"), b = bd("9"), c = bd("6"), unknown = ProportionSlot.D),
        )
        assertExact("6", solved.value)
        assertTrue(solved.equation.contains("×"))
    }

    @Test
    fun inverseProportionOtherSlot() {
        val solved = solution(
            ProportionEngine.solveInverse(a = bd("4"), c = bd("6"), d = bd("6"), unknown = ProportionSlot.B),
        )
        assertExact("9", solved.value)
    }

    @Test
    fun solveDispatchesOnMode() {
        val direct = solution(
            ProportionEngine.solve(ProportionMode.Direct, a = bd("2"), b = bd("3"), c = bd("8"), unknown = ProportionSlot.D),
        )
        val inverse = solution(
            ProportionEngine.solve(ProportionMode.Inverse, a = bd("2"), b = bd("6"), c = bd("4"), unknown = ProportionSlot.D),
        )
        assertExact("12", direct.value)
        assertExact("3", inverse.value)
        assertEquals(ProportionMode.Direct, direct.mode)
        assertEquals(ProportionMode.Inverse, inverse.mode)
    }
}
