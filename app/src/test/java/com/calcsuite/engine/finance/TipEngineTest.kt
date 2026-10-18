package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TipEngineTest {

    private fun ok(result: CalcResult<TipEngine.TipResult>): TipEngine.TipResult {
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

    private fun sum(values: List<BigDecimal>): BigDecimal =
        values.fold(BigDecimal.ZERO) { acc, value -> acc.add(value) }

    @Test
    fun twentyPercentOnFifty() {
        val result = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("50"),
                    tipPct = BigDecimal("20"),
                    people = 1,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NONE,
                ),
            ),
        )
        assertMoney("10", result.tip)
        assertMoney("60", result.total)
        assertMoney("50", result.tipBase)
    }

    @Test
    fun zeroPercentIsValid() {
        val result = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("80"),
                    tipPct = BigDecimal("0"),
                    people = 1,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NONE,
                ),
            ),
        )
        assertMoney("0", result.tip)
        assertMoney("80", result.total)
    }

    @Test
    fun threeWaySplitOf115AllocatesEveryCent() {
        val result = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("100"),
                    tipPct = BigDecimal("15"),
                    people = 3,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NONE,
                ),
            ),
        )
        assertMoney("15", result.tip)
        assertMoney("115", result.total)
        assertEquals(3, result.shares.size)
        assertMoney("38.34", result.shares[0], "first share carries the odd cent")
        assertMoney("38.33", result.shares[1], "second share")
        assertMoney("38.33", result.shares[2], "third share")
        assertMoney("115.00", sum(result.shares), "shares sum exactly to the total")
        assertEquals(false, result.evenSplit)
    }

    @Test
    fun evenSplitIsDetected() {
        val result = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("90"),
                    tipPct = BigDecimal("10"),
                    people = 3,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NONE,
                ),
            ),
        )
        assertMoney("99", result.total)
        assertTrue(result.evenSplit)
        assertEquals(3, result.shares.size)
        result.shares.forEach { assertMoney("33", it) }
    }

    @Test
    fun tipOnPreTaxVersusPostTax() {
        val preTax = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("100"),
                    tax = BigDecimal("10"),
                    tipPct = BigDecimal("20"),
                    people = 1,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NONE,
                ),
            ),
        )
        assertMoney("100", preTax.tipBase)
        assertMoney("20", preTax.tip)
        assertMoney("130", preTax.total)

        val postTax = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("100"),
                    tax = BigDecimal("10"),
                    tipPct = BigDecimal("20"),
                    people = 1,
                    tipOnPreTax = false,
                    rounding = TipEngine.RoundingOption.NONE,
                ),
            ),
        )
        assertMoney("110", postTax.tipBase)
        assertMoney("22", postTax.tip)
        assertMoney("132", postTax.total)
    }

    @Test
    fun roundingToFiveCents() {
        val result = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("43.21"),
                    tipPct = BigDecimal("18"),
                    people = 1,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NEAREST_005,
                ),
            ),
        )
        // 43.21 × 1.18 = 50.9878 → nearest 0.05 = 51.00, tip absorbs the cent.
        assertMoney("51.00", result.total)
        assertMoney("7.79", result.tip)
        assertEquals(0, result.total.remainder(BigDecimal("0.05")).compareTo(BigDecimal.ZERO))
    }

    @Test
    fun roundingToWholeUnit() {
        val result = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("40"),
                    tipPct = BigDecimal("10"),
                    people = 2,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NEAREST_100,
                ),
            ),
        )
        assertMoney("44.00", result.total)
        assertMoney("4.00", result.tip)
        assertMoney("22.00", result.shares[0])
        assertMoney("22.00", result.shares[1])
    }

    @Test
    fun zeroPeopleRejected() {
        val result = TipEngine.calculate(
            TipEngine.TipInput(
                bill = BigDecimal("10"),
                tipPct = BigDecimal("10"),
                people = 0,
                tipOnPreTax = true,
                rounding = TipEngine.RoundingOption.NONE,
            ),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
        assertEquals("At least one person is required", (result as CalcResult.Err).error.message)
    }

    @Test
    fun negativeBillRejected() {
        val result = TipEngine.calculate(
            TipEngine.TipInput(
                bill = BigDecimal("-5"),
                tipPct = BigDecimal("10"),
                people = 1,
                tipOnPreTax = true,
                rounding = TipEngine.RoundingOption.NONE,
            ),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun negativePercentageRejected() {
        val result = TipEngine.calculate(
            TipEngine.TipInput(
                bill = BigDecimal("5"),
                tipPct = BigDecimal("-1"),
                people = 1,
                tipOnPreTax = true,
                rounding = TipEngine.RoundingOption.NONE,
            ),
        )
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun splitHelperAllocatesCents() {
        val shares = TipEngine.split(BigDecimal("10.00"), 3)
        assertMoney("3.34", shares[0])
        assertMoney("3.33", shares[1])
        assertMoney("3.33", shares[2])
        assertMoney("10.00", sum(shares))
    }

    @Test
    fun tipSharesAlsoSumExactly() {
        val result = ok(
            TipEngine.calculate(
                TipEngine.TipInput(
                    bill = BigDecimal("99.99"),
                    tipPct = BigDecimal("17.5"),
                    people = 4,
                    tipOnPreTax = true,
                    rounding = TipEngine.RoundingOption.NONE,
                ),
            ),
        )
        assertMoney("17.50", result.tip)
        assertMoney(result.tip.toPlainString(), sum(result.tipShares), "tip shares sum to the tip")
        assertMoney(result.total.toPlainString(), sum(result.shares), "total shares sum to the total")
    }
}
