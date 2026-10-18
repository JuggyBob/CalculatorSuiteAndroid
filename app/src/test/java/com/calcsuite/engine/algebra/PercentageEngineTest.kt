package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class PercentageEngineTest {

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

    private fun assertClose(expected: String, actual: BigDecimal, digits: Int = 12) {
        val cmp = actual.round(MathContext(digits, RoundingMode.HALF_UP))
            .compareTo(BigDecimal(expected).round(MathContext(digits, RoundingMode.HALF_UP)))
        assertEquals("expected ≈ $expected but was $actual", 0, cmp)
    }

    private fun assertExact(expected: String, actual: BigDecimal) {
        assertEquals("expected exactly $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
    }

    // ---- percent of ---------------------------------------------------------

    @Test
    fun percentOfExample() {
        // The brief's example: 15% of 200 = 30.
        assertExact("30", value(PercentageEngine.percentOf(BigDecimal("15"), BigDecimal("200"))))
    }

    @Test
    fun percentOfDecimalsAndZero() {
        assertExact("18.75", value(PercentageEngine.percentOf(BigDecimal("7.5"), BigDecimal("250"))))
        assertExact("0", value(PercentageEngine.percentOf(BigDecimal("0"), BigDecimal("100"))))
        assertExact("100", value(PercentageEngine.percentOf(BigDecimal("100"), BigDecimal("100"))))
    }

    // ---- what percent -------------------------------------------------------

    @Test
    fun whatPercentExample() {
        assertExact("25", value(PercentageEngine.whatPercent(BigDecimal("50"), BigDecimal("200"))))
    }

    @Test
    fun whatPercentNonTerminating() {
        val result = value(PercentageEngine.whatPercent(BigDecimal.ONE, BigDecimal("3")))
        assertClose("33.3333333333", result)
    }

    @Test
    fun whatPercentRejectsZeroReference() {
        val result = PercentageEngine.whatPercent(BigDecimal("5"), BigDecimal.ZERO)
        assertEquals(ErrorKind.DivisionByZero, kind(result))
    }

    // ---- change -------------------------------------------------------------

    @Test
    fun percentIncreaseAndFields() {
        val result = PercentageEngine.percentIncrease(BigDecimal("120"), BigDecimal("150"))
        val change = (result as CalcResult.Ok).value
        assertExact("25", change.percent)
        assertExact("30", change.change)
        assertExact("150", change.finalValue)
        assertExact("120", change.original)
    }

    @Test
    fun percentIncreaseCanBeNegative() {
        val result = PercentageEngine.percentIncrease(BigDecimal("200"), BigDecimal("150"))
        val change = (result as CalcResult.Ok).value
        assertExact("-25", change.percent)
        assertExact("-50", change.change)
    }

    @Test
    fun percentChangeFromZeroIsUndefined() {
        val result = PercentageEngine.percentIncrease(BigDecimal.ZERO, BigDecimal("5"))
        assertEquals(ErrorKind.Domain, kind(result))
        assertEquals(
            "The starting value is zero, so a percentage change is undefined",
            message(result),
        )
    }

    @Test
    fun percentDecreaseExample() {
        val result = PercentageEngine.percentDecrease(BigDecimal("200"), BigDecimal("150"))
        val change = (result as CalcResult.Ok).value
        assertExact("25", change.percent)
        assertExact("50", change.change)
    }

    // ---- recover the original ----------------------------------------------

    @Test
    fun recoverOriginalAfterIncrease() {
        val value = value(PercentageEngine.recoverOriginal(BigDecimal("132"), BigDecimal("10"), increase = true))
        assertExact("120", value)
    }

    @Test
    fun recoverOriginalAfterDecrease() {
        val value = value(PercentageEngine.recoverOriginal(BigDecimal("90"), BigDecimal("10"), increase = false))
        assertExact("100", value)
    }

    @Test
    fun recoverOriginalRejectsTotalDecrease() {
        val result = PercentageEngine.recoverOriginal(BigDecimal("0"), BigDecimal("100"), increase = false)
        assertEquals(ErrorKind.DivisionByZero, kind(result))
    }

    // ---- successive changes -------------------------------------------------

    @Test
    fun successiveChangeCompounds() {
        val result = PercentageEngine.successiveChange(
            BigDecimal("100"),
            listOf(BigDecimal("10"), BigDecimal("-10")),
        )
        val change = (result as CalcResult.Ok).value
        assertExact("99", change.finalValue)
        assertExact("-1", change.change)
        assertExact("-1", change.percent)
    }

    @Test
    fun successiveChangeNeedsAtLeastOneChange() {
        val result = PercentageEngine.successiveChange(BigDecimal("100"), emptyList())
        assertEquals(ErrorKind.Insufficient, kind(result))
    }

    @Test
    fun successiveChangeFromZeroIsUndefined() {
        val result = PercentageEngine.successiveChange(BigDecimal.ZERO, listOf(BigDecimal("10")))
        assertEquals(ErrorKind.Domain, kind(result))
    }

    // ---- points and relative difference ------------------------------------

    @Test
    fun percentagePointsExample() {
        val points = PercentageEngine.percentagePoints(BigDecimal("7"), BigDecimal("5"))
        assertExact("2", points)
        assertExact("-2", PercentageEngine.percentagePoints(BigDecimal("5"), BigDecimal("7")))
    }

    @Test
    fun relativeDifferenceUsesTheMean() {
        val result = value(PercentageEngine.relativePercentDifference(BigDecimal("10"), BigDecimal("12")))
        assertClose("18.1818181818", result)
    }

    @Test
    fun relativeDifferenceIsSymmetric() {
        val first = value(PercentageEngine.relativePercentDifference(BigDecimal("10"), BigDecimal("12")))
        val second = value(PercentageEngine.relativePercentDifference(BigDecimal("12"), BigDecimal("10")))
        assertEquals(0, first.compareTo(second))
    }

    @Test
    fun relativeDifferenceOfEqualValuesIsZero() {
        assertExact("0", value(PercentageEngine.relativePercentDifference(BigDecimal("7"), BigDecimal("7"))))
    }

    @Test
    fun relativeDifferenceUndefinedWhenValuesCancel() {
        val result = PercentageEngine.relativePercentDifference(BigDecimal("5"), BigDecimal("-5"))
        assertEquals(ErrorKind.Undefined, kind(result))
    }

    @Test
    fun relativeDifferenceUndefinedForTwoZeros() {
        val result = PercentageEngine.relativePercentDifference(BigDecimal.ZERO, BigDecimal.ZERO)
        assertEquals(ErrorKind.Undefined, kind(result))
    }
}
