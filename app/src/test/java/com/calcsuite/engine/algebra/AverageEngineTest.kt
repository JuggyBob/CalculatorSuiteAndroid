package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class AverageEngineTest {

    private fun value(result: CalcResult<BigDecimal>): BigDecimal {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun kind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun message(result: CalcResult<*>): String {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    private fun assertExact(expected: String, actual: BigDecimal) {
        assertEquals("expected exactly $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
    }

    private fun bd(text: String) = BigDecimal(text)

    // ---- parsing ------------------------------------------------------------

    @Test
    fun datasetSeparators() {
        val values = (AverageEngine.parseDataset("1, 2; 3\n4") as CalcResult.Ok).value
        assertEquals(4, values.size)
        assertEquals(0, values[3].compareTo(BigDecimal("4")))
    }

    @Test
    fun datasetKeepsGroupingSpacesInsideANumber() {
        val values = (AverageEngine.parseDataset("1 234, 5") as CalcResult.Ok).value
        assertEquals(2, values.size)
        assertEquals(0, values[0].compareTo(BigDecimal("1234")))
        assertEquals(0, values[1].compareTo(BigDecimal("5")))
    }

    @Test
    fun datasetRejectsGarbage() {
        assertEquals(ErrorKind.InvalidInput, kind(AverageEngine.parseDataset("3, x, 5")))
        assertEquals(ErrorKind.Insufficient, kind(AverageEngine.parseDataset("   ")))
    }

    // ---- mean ---------------------------------------------------------------

    @Test
    fun arithmeticMeanExample() {
        assertExact("4", value(AverageEngine.arithmeticMean(listOf(bd("2"), bd("4"), bd("6")))))
        assertExact("1.5", value(AverageEngine.arithmeticMean(listOf(bd("1"), bd("2")))))
    }

    @Test
    fun meanNeedsValues() {
        assertEquals(ErrorKind.Insufficient, kind(AverageEngine.arithmeticMean(emptyList())))
    }

    // ---- summary ------------------------------------------------------------

    @Test
    fun summarizeExposesFacts() {
        val summary = (AverageEngine.summarize(listOf(bd("2"), bd("4"), bd("6"))) as CalcResult.Ok).value
        assertEquals(3, summary.count)
        assertExact("12", summary.sum)
        assertExact("2", summary.min)
        assertExact("6", summary.max)
        assertEquals(listOf(bd("2"), bd("4"), bd("6")), summary.sorted)
    }

    // ---- weighted mean ------------------------------------------------------

    @Test
    fun weightedMeanExample() {
        val result = AverageEngine.weightedMean(
            listOf(bd("2"), bd("4"), bd("6")),
            listOf(bd("1"), bd("2"), bd("3")),
        )
        val weighted = (result as CalcResult.Ok).value
        assertEquals(0, weighted.mean.round(MathContext(10, RoundingMode.HALF_UP)).compareTo(bd("4.6666666667").round(MathContext(10, RoundingMode.HALF_UP))))
        assertExact("6", weighted.weightSum)
        assertExact("28", weighted.weightedSum)
    }

    @Test
    fun weightedMeanChecksWeightCount() {
        val result = AverageEngine.weightedMean(listOf(bd("2"), bd("4")), listOf(bd("1")))
        assertEquals(ErrorKind.InvalidInput, kind(result))
        assertTrue(message(result).contains("Provide one weight per value"))
    }

    @Test
    fun weightedMeanRejectsNegativeWeights() {
        val result = AverageEngine.weightedMean(listOf(bd("2"), bd("4")), listOf(bd("1"), bd("-1")))
        assertEquals(ErrorKind.Domain, kind(result))
    }

    @Test
    fun weightedMeanRejectsZeroWeightSum() {
        val result = AverageEngine.weightedMean(listOf(bd("2"), bd("4")), listOf(bd("0"), bd("0")))
        assertEquals(ErrorKind.Domain, kind(result))
    }

    // ---- median -------------------------------------------------------------

    @Test
    fun medianOddCount() {
        assertExact("2", value(AverageEngine.median(listOf(bd("3"), bd("1"), bd("2")))))
    }

    @Test
    fun medianEvenCount() {
        assertExact("2.5", value(AverageEngine.median(listOf(bd("4"), bd("1"), bd("3"), bd("2")))))
    }

    // ---- mode ---------------------------------------------------------------

    @Test
    fun modeSingleValue() {
        assertEquals(listOf(bd("2")), (AverageEngine.mode(listOf(bd("1"), bd("2"), bd("2"), bd("3"))) as CalcResult.Ok).value)
    }

    @Test
    fun modeCanBeMultiModal() {
        val modes = (AverageEngine.mode(listOf(bd("2"), bd("1"), bd("1"), bd("2"))) as CalcResult.Ok).value
        assertEquals(listOf(bd("1"), bd("2")), modes)
    }

    @Test
    fun modeEmptyWhenAllValuesAreUnique() {
        val modes = (AverageEngine.mode(listOf(bd("1"), bd("2"), bd("3"))) as CalcResult.Ok).value
        assertTrue("expected no mode, was $modes", modes.isEmpty())
    }

    // ---- geometric mean -----------------------------------------------------

    @Test
    fun geometricMeanExamples() {
        assertExact("2", value(AverageEngine.geometricMean(listOf(bd("1"), bd("4")))))
        assertExact("4", value(AverageEngine.geometricMean(listOf(bd("2"), bd("8")))))
    }

    @Test
    fun geometricMeanOfOneValueIsThatValue() {
        assertExact("4", value(AverageEngine.geometricMean(listOf(bd("4")))))
    }

    @Test
    fun geometricMeanRejectsNonPositiveValues() {
        val result = AverageEngine.geometricMean(listOf(bd("1"), bd("-4")))
        assertEquals(ErrorKind.Domain, kind(result))
        assertEquals(ErrorKind.Domain, kind(AverageEngine.geometricMean(listOf(bd("0"), bd("4")))))
    }

    @Test
    fun geometricMeanOfIrrationalRootIsClose() {
        val result = value(AverageEngine.geometricMean(listOf(bd("2"), bd("8"), bd("4"))))
        // (2 × 8 × 4)^(1/3) = 64^(1/3) = 4
        assertExact("4", result)
    }
}
