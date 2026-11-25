package com.calcsuite.data

import java.io.Reader

/** Pure bounds and validation helpers for exchange-rate data. */
object RateLimits {
    /** Upper bound for a provider response body (about 1 MB of text). */
    const val MAX_RESPONSE_CHARS = 1_000_000

    /** Upper bound for the number of rates kept from one response. */
    const val MAX_RATES = 300

    /** Codes in the provider's rates object must be three capital letters. */
    private val CURRENCY_CODE = Regex("^[A-Z]{3}$")

    /** 2001-09-09: no real provider timestamp predates this. */
    const val MIN_UNIX_SECONDS = 1_000_000_000L

    /** 2100-01-01: nothing plausible is later than this. */
    const val MAX_UNIX_SECONDS = 4_102_444_800L

    /** Small clock skew allowed in a provider's "last update" value. */
    const val MAX_FUTURE_SKEW_SECONDS = 86_400L

    const val INVALID_CODE_MESSAGE = "Choose a three-letter currency code"

    fun isValidCurrencyCode(code: String): Boolean = CURRENCY_CODE.matches(code)

    /**
     * Reads at most [maxChars] characters from [reader], looping because a
     * stream may return fewer characters than requested on each call.
     */
    fun readTextCapped(reader: Reader, maxChars: Int): String {
        if (maxChars <= 0) return ""
        val buffer = CharArray(minOf(maxChars, 8_192))
        val out = StringBuilder()
        while (out.length < maxChars) {
            val n = reader.read(buffer, 0, minOf(buffer.size, maxChars - out.length))
            if (n == -1) break
            out.append(buffer, 0, n)
        }
        return out.toString()
    }

    /** A stored fetch time is used only when it is a plausible unix time. */
    fun saneFetchedSeconds(millis: Long): Long {
        val seconds = millis / 1000L
        return if (seconds in MIN_UNIX_SECONDS..MAX_UNIX_SECONDS) seconds else MIN_UNIX_SECONDS
    }

    /**
     * The provider's own timestamp is used only when it is plausible and not
     * far in the future relative to the fetch; otherwise the fetch time wins.
     */
    fun saneProviderSeconds(rawSeconds: Long, fetchedAtMillis: Long): Long {
        val fallback = saneFetchedSeconds(fetchedAtMillis)
        val upper = minOf(MAX_UNIX_SECONDS, fallback + MAX_FUTURE_SKEW_SECONDS)
        return if (rawSeconds in MIN_UNIX_SECONDS..upper) rawSeconds else fallback
    }
}
