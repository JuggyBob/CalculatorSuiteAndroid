package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TaxEngineTest {

    private fun okBreakdown(result: CalcResult<TaxEngine.Breakdown>): TaxEngine.Breakdown {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun okMulti(result: CalcResult<TaxEngine.MultiBreakdown>): TaxEngine.MultiBreakdown {
        assertTrue("Expected success but was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun errorKind(result: CalcResult<*>): ErrorKind {
        assertTrue("Expected failure but was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.kind
    }

    private fun assertMoney(expected: String, actual: BigDecimal, message: String = "") {
        assertEquals(
            "$message expected $expected but was $actual",
            0,
            actual.compareTo(BigDecimal(expected)),
        )
    }

    // --------------------------------------------------------------- add tax

    @Test
    fun addTenPercent() {
        val result = okBreakdown(TaxEngine.addTax(BigDecimal("100"), BigDecimal("10")))
        assertMoney("100", result.net, "net")
        assertMoney("10", result.tax, "tax")
        assertMoney("110", result.gross, "gross")
    }

    @Test
    fun addSevenPointTwoFivePercent() {
        val result = okBreakdown(TaxEngine.addTax(BigDecimal("100"), BigDecimal("7.25")))
        assertMoney("107.25", result.gross)
        assertMoney("7.25", result.tax)
    }

    @Test
    fun zeroRateLeavesAmountUnchanged() {
        val result = okBreakdown(TaxEngine.addTax(BigDecimal("49.99"), BigDecimal("0")))
        assertMoney("0", result.tax)
        assertMoney("49.99", result.gross)
    }

    @Test
    fun addTaxRejectsNegativeAmount() {
        val result = TaxEngine.addTax(BigDecimal("-1"), BigDecimal("10"))
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
        assertEquals("Amount must not be negative", (result as CalcResult.Err).error.message)
    }

    @Test
    fun addTaxRejectsNegativeRate() {
        assertEquals(ErrorKind.InvalidInput, errorKind(TaxEngine.addTax(BigDecimal("10"), BigDecimal("-5"))))
    }

    // ------------------------------------------------------------ extract tax

    @Test
    fun extractTenPercentFrom110() {
        val result = okBreakdown(TaxEngine.extractTax(BigDecimal("110"), BigDecimal("10")))
        assertMoney("100", result.net, "net")
        assertMoney("10", result.tax, "tax")
        assertMoney("110", result.gross, "gross")
    }

    @Test
    fun extractZeroRateIsIdentity() {
        val result = okBreakdown(TaxEngine.extractTax(BigDecimal("59.95"), BigDecimal("0")))
        assertMoney("0", result.tax)
        assertMoney("59.95", result.net)
    }

    @Test
    fun extractRejectsNegativeAmount() {
        val result = TaxEngine.extractTax(BigDecimal("-0.01"), BigDecimal("10"))
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun containedTaxEqualsExtractedTax() {
        val contained = TaxEngine.containedTax(BigDecimal("110"), BigDecimal("10"))
        assertTrue(contained is CalcResult.Ok)
        assertMoney("10", (contained as CalcResult.Ok).value)
    }

    // ------------------------------------------------------- multiple components

    @Test
    fun twoFivePercentComponentsOnOneHundred() {
        val result = okMulti(
            TaxEngine.addComponents(
                BigDecimal("100"),
                listOf("State" to BigDecimal("5"), "City" to BigDecimal("5")),
            ),
        )
        assertMoney("100", result.net)
        assertMoney("10", result.totalTax)
        assertMoney("110", result.gross)
        assertEquals(2, result.components.size)
        assertMoney("5", result.components[0].tax, "state tax")
        assertMoney("5", result.components[1].tax, "city tax")
        assertMoney("10", result.combinedRatePct)
    }

    @Test
    fun componentsAreAddedNotCompounded() {
        // 5% + 5% additively: 100 -> 110, never 110.25.
        val result = okMulti(
            TaxEngine.addComponents(
                BigDecimal("100"),
                listOf("A" to BigDecimal("5"), "B" to BigDecimal("5")),
            ),
        )
        assertMoney("110", result.gross)
    }

    @Test
    fun componentsWithZeroRateStillBreakDown() {
        val result = okMulti(
            TaxEngine.addComponents(
                BigDecimal("20"),
                listOf("VAT" to BigDecimal("15"), "City" to BigDecimal("0")),
            ),
        )
        assertMoney("3", result.totalTax)
        assertMoney("0", result.components[1].tax)
        assertMoney("23", result.gross)
    }

    @Test
    fun componentsRejectEmptyList() {
        assertEquals(
            ErrorKind.Insufficient,
            errorKind(TaxEngine.addComponents(BigDecimal("10"), emptyList())),
        )
    }

    @Test
    fun componentsRejectNegativeRate() {
        assertEquals(
            ErrorKind.InvalidInput,
            errorKind(
                TaxEngine.addComponents(
                    BigDecimal("10"),
                    listOf("A" to BigDecimal("5"), "B" to BigDecimal("-1")),
                ),
            ),
        )
    }

    @Test
    fun extractComponentsInvertsCombinedRate() {
        val result = okMulti(
            TaxEngine.extractComponents(
                BigDecimal("110"),
                listOf("State" to BigDecimal("5"), "City" to BigDecimal("5")),
            ),
        )
        assertMoney("100", result.net)
        assertMoney("10", result.totalTax)
        assertMoney("5", result.components[0].tax)
        assertMoney("5", result.components[1].tax)
    }

    @Test
    fun settlementRoundingIsHalfUp() {
        assertMoney("1.01", TaxEngine.roundToCents(BigDecimal("1.005")))
        assertMoney("1.00", TaxEngine.roundToCents(BigDecimal("0.995")))
    }
}
