package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

class CombinatoricsEngineTest {

    private fun bd(text: String) = BigDecimal(text)

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun <T> okValue(result: CalcResult<T>): T {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return result.valueOrNull()!!
    }

    private fun factorialReference(n: Int): BigInteger {
        var result = BigInteger.ONE
        for (value in 2..n) result = result.multiply(BigInteger.valueOf(value.toLong()))
        return result
    }

    // ---- nCr / nPr ----------------------------------------------------------

    @Test
    fun combinationExample() {
        // The brief's example: C(5, 2) = 10.
        assertEquals(BigInteger.TEN, okValue(CombinatoricsEngine.nCr(BigInteger.valueOf(5), BigInteger.valueOf(2))))
    }

    @Test
    fun permutationExample() {
        // The brief's example: P(5, 2) = 20.
        assertEquals(
            BigInteger.valueOf(20),
            okValue(CombinatoricsEngine.nPr(BigInteger.valueOf(5), BigInteger.valueOf(2))),
        )
    }

    @Test
    fun knownValues() {
        assertEquals(
            BigInteger.valueOf(2_598_960L),
            okValue(CombinatoricsEngine.nCr(BigInteger.valueOf(52), BigInteger.valueOf(5))),
        )
        assertEquals(
            BigInteger.valueOf(13_983_816L),
            okValue(CombinatoricsEngine.nCr(BigInteger.valueOf(49), BigInteger.valueOf(6))),
        )
    }

    @Test
    fun boundaryCases() {
        assertEquals(BigInteger.ONE, okValue(CombinatoricsEngine.nCr(BigInteger.valueOf(7), BigInteger.ZERO)))
        assertEquals(BigInteger.ONE, okValue(CombinatoricsEngine.nCr(BigInteger.valueOf(7), BigInteger.valueOf(7))))
        assertEquals(BigInteger.ONE, okValue(CombinatoricsEngine.nPr(BigInteger.valueOf(7), BigInteger.ZERO)))
    }

    @Test
    fun invalidArguments() {
        assertEquals(
            ErrorKind.InvalidInput,
            kind(CombinatoricsEngine.nCr(BigInteger.valueOf(4), BigInteger.valueOf(6))),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            kind(CombinatoricsEngine.nCr(BigInteger.valueOf(-1), BigInteger.ZERO)),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            kind(CombinatoricsEngine.nPr(BigInteger.valueOf(3), BigInteger.valueOf(5))),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            kind(CombinatoricsEngine.nCr(BigInteger.valueOf(5), BigInteger.valueOf(-2))),
        )
    }

    @Test
    fun largeCombinationIsExactAndFast() {
        val n = BigInteger.valueOf(1000)
        val r = BigInteger.valueOf(500)
        val expected = factorialReference(1000)
            .divide(factorialReference(500).multiply(factorialReference(500)))
        assertEquals(expected, okValue(CombinatoricsEngine.nCr(n, r)))
    }

    @Test
    fun factorialValues() {
        assertEquals(BigInteger.valueOf(120), okValue(CombinatoricsEngine.factorial(BigInteger.valueOf(5))))
        assertEquals(BigInteger.ONE, okValue(CombinatoricsEngine.factorial(BigInteger.ZERO)))
        assertEquals(
            ErrorKind.InvalidInput,
            kind(CombinatoricsEngine.factorial(BigInteger.valueOf(-1))),
        )
    }

    // ---- explained selections -----------------------------------------------

    @Test
    fun combinationsExplainTheFormula() {
        val result = okValue(
            CombinatoricsEngine.combinations(BigInteger.valueOf(5), BigInteger.valueOf(2)),
        )
        assertEquals(BigInteger.TEN, result.value)
        assertTrue(result.formula.contains("C(5, 2)"))
        assertTrue(result.steps.size >= 3)
        assertTrue(result.note.contains("order does not matter"))
    }

    @Test
    fun permutationsExplainTheOrder() {
        val result = okValue(
            CombinatoricsEngine.permutations(BigInteger.valueOf(5), BigInteger.valueOf(2)),
        )
        assertEquals(BigInteger.valueOf(20), result.value)
        assertTrue(result.formula.contains("P(5, 2)"))
        assertTrue(result.note.contains("order does matter"))
    }

    // ---- probability --------------------------------------------------------

    @Test
    fun lotteryJackpotProbability() {
        val result = okValue(
            CombinatoricsEngine.selectionProbability(
                BigInteger.valueOf(49),
                BigInteger.valueOf(6),
                BigInteger.valueOf(6),
                BigInteger.valueOf(6),
            ),
        )
        val expected = BigDecimal.ONE.divide(BigDecimal(13_983_816L), MathContext(34, RoundingMode.HALF_UP))
        assertEquals(0, result.probability.compareTo(expected))
        val oneIn = result.oneIn!!
        assertEquals(
            0,
            oneIn.round(MathContext(6, RoundingMode.HALF_UP))
                .compareTo(bd("13983816").round(MathContext(6, RoundingMode.HALF_UP))),
        )
    }

    @Test
    fun lotteryPartialMatchIsBetweenZeroAndOne() {
        val result = okValue(
            CombinatoricsEngine.selectionProbability(
                BigInteger.valueOf(49),
                BigInteger.valueOf(6),
                BigInteger.valueOf(6),
                BigInteger.valueOf(3),
            ),
        )
        assertTrue(result.probability.signum() > 0)
        assertTrue(result.probability < BigDecimal.ONE)
        assertTrue(result.steps.isNotEmpty())
        assertTrue(result.note.isNotEmpty())
    }

    @Test
    fun selectionProbabilityValidation() {
        assertEquals(
            ErrorKind.InvalidInput,
            kind(
                CombinatoricsEngine.selectionProbability(
                    BigInteger.valueOf(10),
                    BigInteger.valueOf(5),
                    BigInteger.valueOf(3),
                    BigInteger.valueOf(4),
                ),
            ),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            kind(
                CombinatoricsEngine.selectionProbability(
                    BigInteger.valueOf(10),
                    BigInteger.valueOf(11),
                    BigInteger.valueOf(3),
                    BigInteger.valueOf(1),
                ),
            ),
        )
    }

    @Test
    fun binomialProbabilityExamples() {
        val coin = okValue(
            CombinatoricsEngine.probabilityOfExactlySuccesses(
                BigInteger.valueOf(2),
                BigInteger.ONE,
                bd("0.5"),
            ),
        )
        assertEquals(0, coin.probability.compareTo(bd("0.5")))
        val single = okValue(
            CombinatoricsEngine.probabilityOfExactlySuccesses(
                BigInteger.ONE,
                BigInteger.ONE,
                bd("0.3"),
            ),
        )
        assertEquals(0, single.probability.compareTo(bd("0.3")))
    }

    @Test
    fun binomialProbabilityValidation() {
        assertEquals(
            ErrorKind.Domain,
            kind(
                CombinatoricsEngine.probabilityOfExactlySuccesses(
                    BigInteger.valueOf(3),
                    BigInteger.ONE,
                    bd("1.5"),
                ),
            ),
        )
        assertEquals(
            ErrorKind.InvalidInput,
            kind(
                CombinatoricsEngine.probabilityOfExactlySuccesses(
                    BigInteger.valueOf(3),
                    BigInteger.valueOf(4),
                    bd("0.5"),
                ),
            ),
        )
    }
}
