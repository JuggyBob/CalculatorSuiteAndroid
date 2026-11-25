package com.calcsuite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class RateLimitsTest {

    @Test fun onlyThreeCapitalLettersAreValidCodes() {
        assertTrue(RateLimits.isValidCurrencyCode("USD"))
        assertTrue(RateLimits.isValidCurrencyCode("EUR"))
        assertFalse(RateLimits.isValidCurrencyCode("usd"))
        assertFalse(RateLimits.isValidCurrencyCode("US"))
        assertFalse(RateLimits.isValidCurrencyCode("USDD"))
        assertFalse(RateLimits.isValidCurrencyCode("U5D"))
        assertFalse(RateLimits.isValidCurrencyCode("US D"))
        assertFalse(RateLimits.isValidCurrencyCode(""))
    }

    @Test fun responseReadStopsAtTheLimit() {
        val body = "0123456789".repeat(200)
        assertEquals(body.take(25), RateLimits.readTextCapped(StringReader(body), 25))
        assertEquals(body, RateLimits.readTextCapped(StringReader(body), body.length + 10))
        assertEquals("", RateLimits.readTextCapped(StringReader(body), 0))
        assertEquals("", RateLimits.readTextCapped(StringReader(""), 10))
    }

    /** A stream is allowed to return fewer characters than asked for. */
    private class OneCharReader(private val source: String) : Reader() {
        private var position = 0
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (position >= source.length) return -1
            cbuf[off] = source[position++]
            return 1
        }
        override fun close() = Unit
    }

    @Test fun responseReadLoopsUntilTheLimitIsReached() {
        assertEquals("abc", RateLimits.readTextCapped(OneCharReader("abcdef"), 3))
        assertEquals("abcdef", RateLimits.readTextCapped(OneCharReader("abcdef"), 10))
    }

    @Test fun implausibleFetchTimesFallBackToTheUnixEpochFloor() {
        assertEquals(1_700_000_000L, RateLimits.saneFetchedSeconds(1_700_000_000_000L))
        for (bad in listOf(0L, -1L, 999_999_999_000L, 5_000_000_000_000L)) {
            assertEquals(RateLimits.MIN_UNIX_SECONDS, RateLimits.saneFetchedSeconds(bad))
        }
    }

    @Test fun providerTimestampsKeepRecentValuesAndRejectImplausibleOnes() {
        val fetched = 1_700_000_000_000L
        val fetchSeconds = fetched / 1000L
        // A provider reporting a recent time, before or slightly after the
        // fetch, is trusted as-is: it must not be rewritten to "now".
        val recentPast = fetchSeconds - 3600L
        assertEquals(recentPast, RateLimits.saneProviderSeconds(recentPast, fetched))
        val recentFuture = fetchSeconds + 3600L
        assertEquals(recentFuture, RateLimits.saneProviderSeconds(recentFuture, fetched))
        val atSkewLimit = fetchSeconds + RateLimits.MAX_FUTURE_SKEW_SECONDS
        assertEquals(atSkewLimit, RateLimits.saneProviderSeconds(atSkewLimit, fetched))
        // Only implausible values fall back to the device's fetch time.
        assertEquals(fetchSeconds, RateLimits.saneProviderSeconds(fetchSeconds + RateLimits.MAX_FUTURE_SKEW_SECONDS + 1L, fetched))
        assertEquals(fetchSeconds, RateLimits.saneProviderSeconds(1L, fetched))
        assertEquals(fetchSeconds, RateLimits.saneProviderSeconds(RateLimits.MAX_UNIX_SECONDS, fetched))
    }
}
