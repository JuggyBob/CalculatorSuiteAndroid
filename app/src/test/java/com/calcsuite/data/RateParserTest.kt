package com.calcsuite.data

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class RateParserTest {

    private val now = 1_700_000_000_000L

    private val validJson = """
        {"result":"success","time_last_update_unix":1699833600,"base_code":"USD",
         "rates":{"EUR":0.92,"GBP":0.79,"JPY":149.5,"CHF":0.89}}
    """.trimIndent()

    private fun snapshot(json: String = validJson): RatesSnapshot {
        val result = RateParser.parse(json, now)
        assertTrue("Expected success, got $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(json: String): com.calcsuite.core.CalcError {
        val result = RateParser.parse(json, now)
        assertTrue("Expected failure, got $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error
    }

    // ------------------------------------------------------------------ parse

    @Test fun parsesValidProviderResponse() {
        val snap = snapshot()
        assertEquals("USD", snap.base)
        assertEquals(now, snap.fetchedAtMillis)
        assertEquals(1699833600L * 1000L, snap.providerUpdatedAtMillis)
        assertEquals(BigDecimal("0.92"), snap.rates["EUR"])
        assertEquals(BigDecimal("149.5"), snap.rates["JPY"])
        assertEquals(4, snap.rates.size)
        assertEquals(RateParser.SOURCE_LABEL, snap.sourceLabel)
    }

    @Test fun baseRateIsAlwaysOneAndUnknownCodesAreNull() {
        val snap = snapshot()
        assertEquals(0, snap.rateFor("USD")!!.compareTo(BigDecimal.ONE))
        assertNull(snap.rateFor("XYZ"))
    }

    @Test fun malformedJsonIsRejected() {
        assertEquals(ErrorKind.Unavailable, error("not json").kind)
        assertEquals(ErrorKind.Unavailable, error("").kind)
    }

    @Test fun providerErrorsCarryTheReason() {
        val e = error("""{"result":"error","error-type":"unsupported-code"}""")
        assertEquals(ErrorKind.Unavailable, e.kind)
        assertTrue(e.message.contains("unsupported-code"))
    }

    @Test fun missingOrEmptyRatesAreRejected() {
        assertEquals(ErrorKind.Unavailable, error("""{"result":"success","base_code":"USD"}""").kind)
        assertEquals(
            ErrorKind.Unavailable,
            error("""{"result":"success","base_code":"USD","rates":{}}""").kind,
        )
    }

    @Test fun unusableRateValuesAreDropped() {
        val snap = snapshot(
            """{"result":"success","base_code":"USD","rates":{"EUR":0.92,"ZERO":0,"NEG":-1,"TXT":"x"}}""",
        )
        assertEquals(setOf("EUR"), snap.rates.keys)
    }

    @Test fun baseFallsBackToUsdWhenAbsent() {
        val snap = snapshot("""{"result":"success","rates":{"EUR":0.92}}""")
        assertEquals("USD", snap.base)
    }

    // ------------------------------------------------------------------ convert

    @Test fun convertsWithBaseCurrency() {
        val snap = snapshot()
        val result = RateParser.convert(BigDecimal("100"), "USD", "EUR", snap)
        assertTrue(result is CalcResult.Ok)
        assertEquals(0, (result as CalcResult.Ok).value.compareTo(BigDecimal("92")))
    }

    @Test fun derivesCrossRates() {
        val snap = snapshot()
        val result = RateParser.convert(BigDecimal.ONE, "GBP", "EUR", snap)
        assertTrue(result is CalcResult.Ok)
        val actual = (result as CalcResult.Ok).value
        val expected = BigDecimal("0.92").divide(BigDecimal("0.79"), MathContext(20, RoundingMode.HALF_UP))
        val cmp = actual.round(MathContext(15, RoundingMode.HALF_UP))
            .compareTo(expected.round(MathContext(15, RoundingMode.HALF_UP)))
        assertEquals("GBP→EUR cross rate", 0, cmp)
    }

    @Test fun unknownCurrencyIsAnError() {
        val snap = snapshot()
        val result = RateParser.convert(BigDecimal.TEN, "USD", "XYZ", snap)
        assertEquals(ErrorKind.InvalidInput, (result as CalcResult.Err).error.kind)
    }

    @Test fun pairRateOfSameCurrencyIsOne() {
        val result = RateParser.pairRate("EUR", "EUR", snapshot())
        assertEquals(0, (result as CalcResult.Ok).value.compareTo(BigDecimal.ONE))
    }

    // ------------------------------------------------------------------ staleness

    @Test fun stalenessUsesTwentyFourHours() {
        val snap = snapshot()
        assertTrue(!snap.isStale(now + RatesSnapshot.STALE_AFTER_MS))
        assertTrue(snap.isStale(now + RatesSnapshot.STALE_AFTER_MS + 1))
        assertTrue(!snap.isStale(now + 60_000))
    }
}
