package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.data.RatesSnapshot
import com.calcsuite.data.RateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

class CurrencyEngineTest {

    private val fixtureJson = """
        {
          "result": "success",
          "time_last_update_unix": 1700000000,
          "base_code": "USD",
          "rates": { "EUR": 0.92, "GBP": 0.79, "JPY": 149.5, "KWD": 0.307, "CHF": 0.885 }
        }
    """.trimIndent()

    private val fetchedAt = 1_700_000_000_000L

    private fun snapshotFromFixture(): RatesSnapshot {
        val parsed = RateParser.parse(fixtureJson, fetchedAt)
        assertTrue("Expected the fixture to parse but was $parsed", parsed is CalcResult.Ok)
        return (parsed as CalcResult.Ok).value
    }

    private fun handBuiltSnapshot(
        fetchedAtMillis: Long = System.currentTimeMillis(),
    ): RatesSnapshot = RatesSnapshot(
        base = "USD",
        rates = mapOf(
            "EUR" to BigDecimal("0.92"),
            "GBP" to BigDecimal("0.79"),
            "JPY" to BigDecimal("149.5"),
            "KWD" to BigDecimal("0.307"),
        ),
        fetchedAtMillis = fetchedAtMillis,
        providerUpdatedAtMillis = fetchedAtMillis,
        sourceLabel = RateParser.SOURCE_LABEL,
    )

    private fun okConversion(result: CalcResult<CurrencyEngine.Conversion>): CurrencyEngine.Conversion {
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

    // ------------------------------------------------------------- rate parsing

    @Test
    fun parserReadsTheFixture() {
        val snapshot = snapshotFromFixture()
        assertEquals("USD", snapshot.base)
        assertEquals(5, snapshot.rates.size)
        assertEquals(fetchedAt, snapshot.fetchedAtMillis)
        assertEquals(1_700_000_000_000L, snapshot.providerUpdatedAtMillis)
        assertMoney("0.92", snapshot.rates.getValue("EUR"))
        assertMoney("1", snapshot.rateFor("USD")!!, "the base currency always has rate 1")
    }

    // --------------------------------------------------------- cross-rate maths

    @Test
    fun euroToPoundCrossesThroughTheUsdBase() {
        val snapshot = snapshotFromFixture()
        val conversion = okConversion(
            CurrencyEngine.convert(BigDecimal("100"), "EUR", "GBP", snapshot),
        )
        // 100 EUR → USD → GBP: 100 / 0.92 × 0.79 = 85.8695652173…
        val exact = conversion.exactResult.toDouble()
        assertTrue("was $exact", abs(exact - 100.0 / 0.92 * 0.79) < 1e-9)
        assertMoney("85.87", conversion.displayResult, "display rounding to GBP cents")
        assertEquals(2, conversion.displayResult.scale())
        assertEquals("GBP", conversion.to.code)
        assertEquals("£", conversion.to.symbol)
    }

    @Test
    fun pairRateEuroPoundMatchesTheCrossRate() {
        val snapshot = snapshotFromFixture()
        val rate = CurrencyEngine.pairRate("EUR", "GBP", snapshot)
        assertTrue(rate is CalcResult.Ok)
        val value = (rate as CalcResult.Ok<BigDecimal>).value
        val expected = BigDecimal("0.79").divide(BigDecimal("0.92"), MathContext(20, RoundingMode.HALF_UP))
        assertEquals(0, value.compareTo(expected))
        assertEquals("0.858696", CurrencyEngine.formatRate(value))
    }

    @Test
    fun pairRateIsInvertible() {
        val snapshot = snapshotFromFixture()
        val direct = (CurrencyEngine.pairRate("GBP", "EUR", snapshot) as CalcResult.Ok<BigDecimal>).value
        val inverse = (CurrencyEngine.pairRate("EUR", "GBP", snapshot) as CalcResult.Ok<BigDecimal>).value
        val product = direct.multiply(inverse, MathContext(20, RoundingMode.HALF_UP))
        assertTrue(abs(product.toDouble() - 1.0) < 1e-15)
    }

    @Test
    fun zeroDecimalCurrencyRoundsToWholeUnits() {
        val snapshot = snapshotFromFixture()
        val conversion = okConversion(
            CurrencyEngine.convert(BigDecimal("100"), "GBP", "JPY", snapshot),
        )
        // 100 / 0.79 × 149.5 = 18924.05… JPY → no minor units.
        assertEquals(0, conversion.displayResult.scale())
        assertMoney("18924", conversion.displayResult)
    }

    @Test
    fun threeDecimalCurrencyKeepsThreeDigits() {
        val snapshot = snapshotFromFixture()
        val conversion = okConversion(
            CurrencyEngine.convert(BigDecimal("100"), "USD", "KWD", snapshot),
        )
        assertEquals(3, conversion.displayResult.scale())
        assertMoney("30.7", conversion.displayResult)
    }

    @Test
    fun handBuiltSnapshotWorksWithoutParsing() {
        val snapshot = handBuiltSnapshot()
        val conversion = okConversion(
            CurrencyEngine.convert(BigDecimal("10"), "EUR", "GBP", snapshot),
        )
        assertMoney("8.59", conversion.displayResult) // 10 × 0.79 / 0.92 = 8.5869…
    }

    // ------------------------------------------------------------------ errors

    @Test
    fun unknownCurrencyCodeIsRejected() {
        val snapshot = snapshotFromFixture()
        val result = CurrencyEngine.convert(BigDecimal.ONE, "EUR", "XXX", snapshot)
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
        assertTrue(((result as CalcResult.Err).error.message).contains("XXX"))
    }

    @Test
    fun missingRateForKnownCurrencyIsReported() {
        val snapshot = RatesSnapshot(
            base = "USD",
            rates = mapOf("EUR" to BigDecimal("0.92")),
            fetchedAtMillis = System.currentTimeMillis(),
            providerUpdatedAtMillis = System.currentTimeMillis(),
            sourceLabel = "test",
        )
        val result = CurrencyEngine.convert(BigDecimal.ONE, "EUR", "CHF", snapshot)
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
        assertTrue(((result as CalcResult.Err).error.message).contains("CHF"))
    }

    @Test
    fun negativeAmountIsRejected() {
        val result = CurrencyEngine.convert(BigDecimal("-5"), "USD", "EUR", snapshotFromFixture())
        assertEquals(ErrorKind.InvalidInput, errorKind(result))
    }

    @Test
    fun zeroRateIsReportedAsDivisionByZero() {
        val snapshot = RatesSnapshot(
            base = "USD",
            rates = mapOf("EUR" to BigDecimal.ZERO, "GBP" to BigDecimal("0.79")),
            fetchedAtMillis = System.currentTimeMillis(),
            providerUpdatedAtMillis = System.currentTimeMillis(),
            sourceLabel = "test",
        )
        val result = CurrencyEngine.convert(BigDecimal.ONE, "EUR", "GBP", snapshot)
        assertEquals(ErrorKind.DivisionByZero, errorKind(result))
    }

    // --------------------------------------------------------------- staleness

    @Test
    fun snapshotOlderThanADayIsStale() {
        val now = 2_000_000_000_000L
        val old = snapshotFromFixture().copy(fetchedAtMillis = now - 25L * 60 * 60 * 1000)
        assertTrue(old.isStale(now))
        assertTrue(CurrencyEngine.statusOf(old, now).stale)
        assertFalse(CurrencyEngine.statusOf(snapshotFromFixture().copy(fetchedAtMillis = now - 60_000L), now).stale)
    }

    @Test
    fun ageDescriptionIsHumanReadable() {
        assertEquals("just now", CurrencyEngine.describeAge(30_000L))
        assertEquals("5 minutes ago", CurrencyEngine.describeAge(5L * 60_000))
        assertEquals("3 hours ago", CurrencyEngine.describeAge(3L * 3_600_000))
        assertEquals("2 days ago", CurrencyEngine.describeAge(50L * 3_600_000))
    }

    // ------------------------------------------------------------- unit rounding

    @Test
    fun displayRoundingUsesHalfUpAndMinorUnits() {
        val jpy = CurrencyEngine.roundToMinorUnits(BigDecimal("100.5"), "JPY")
        assertMoney("101", (jpy as CalcResult.Ok<BigDecimal>).value)
        val kwd = CurrencyEngine.roundToMinorUnits(BigDecimal("1.2345"), "KWD")
        assertMoney("1.235", (kwd as CalcResult.Ok<BigDecimal>).value)
        val gbp = CurrencyEngine.roundToMinorUnits(BigDecimal("1.235"), "GBP")
        assertMoney("1.24", (gbp as CalcResult.Ok<BigDecimal>).value)
        assertEquals(
            ErrorKind.InvalidInput,
            errorKind(CurrencyEngine.roundToMinorUnits(BigDecimal.ONE, "XXX")),
        )
    }

    @Test
    fun percentChangeHelper() {
        val change = CurrencyEngine.percentChange(BigDecimal("0.80"), BigDecimal("0.88"))
        assertTrue(change is CalcResult.Ok)
        assertMoney("10", (change as CalcResult.Ok<BigDecimal>).value)
    }

    // ------------------------------------------------------------------ catalog

    @Test
    fun catalogHasAboutFiftyCurrencies() {
        assertTrue("was ${CurrencyCatalog.count()}", CurrencyCatalog.count() >= 50)
        assertEquals(CurrencyCatalog.count(), CurrencyCatalog.currencies.size)
        assertEquals(CurrencyCatalog.count(), CurrencyCatalog.codes.size)
    }

    @Test
    fun catalogSearchFindsPoundByNameAndCode() {
        val byName = CurrencyCatalog.search("pound")
        assertTrue("pound should match GBP, was $byName", byName.any { it.code == "GBP" })

        val byCode = CurrencyCatalog.search("GBP")
        assertEquals("GBP", byCode.first().code)

        val byAlias = CurrencyCatalog.search("sterling")
        assertTrue(byAlias.any { it.code == "GBP" })
    }

    @Test
    fun catalogLookupIsCaseInsensitive() {
        assertEquals("USD", CurrencyCatalog.byCode("usd")?.code)
        assertEquals("EUR", CurrencyCatalog.byCode(" eur ")?.code)
        assertEquals(null, CurrencyCatalog.byCode("XXX"))
    }

    @Test
    fun catalogMinorUnitsMatchTheRealCurrencies() {
        assertEquals(0, CurrencyCatalog.byCode("JPY")!!.minorUnits)
        assertEquals(3, CurrencyCatalog.byCode("KWD")!!.minorUnits)
        assertEquals(3, CurrencyCatalog.byCode("BHD")!!.minorUnits)
        assertEquals(2, CurrencyCatalog.byCode("EUR")!!.minorUnits)
        assertEquals("€", CurrencyCatalog.byCode("EUR")!!.symbol)
    }
}
