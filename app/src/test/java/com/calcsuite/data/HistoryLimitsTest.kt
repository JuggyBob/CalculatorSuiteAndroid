package com.calcsuite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryLimitsTest {

    @Test fun ordinaryTextIsLeftAlone() {
        val text = "0 °C → 32 °F, line 1\nline 2"
        assertSame(text, HistoryLimits.limitText(text))
        assertTrue(HistoryLimits.jsonWithinLimit("[]"))
    }

    @Test fun oversizedTextIsTruncatedToTwentyThousand() {
        val text = "x".repeat(HistoryLimits.MAX_TEXT_LENGTH + 123)
        val limited = HistoryLimits.limitText(text)
        assertEquals(HistoryLimits.MAX_TEXT_LENGTH, limited.length)
        assertEquals(text.take(HistoryLimits.MAX_TEXT_LENGTH), limited)
    }

    @Test fun ordinaryValuesAreLeftAlone() {
        val values = mapOf("a" to "1", "b" to "2")
        assertSame(values, HistoryLimits.limitValues(values))
    }

    @Test fun valuesAreCappedByCountAndByLength() {
        val long = "v".repeat(HistoryLimits.MAX_VALUE_LENGTH + 10)
        val values = (0 until HistoryLimits.MAX_VALUES_PER_ENTRY + 5)
            .associate { "k$it" to long }
        val limited = HistoryLimits.limitValues(values)
        assertEquals(HistoryLimits.MAX_VALUES_PER_ENTRY, limited.size)
        assertTrue(limited.values.all { it.length == HistoryLimits.MAX_VALUE_LENGTH })
    }

    @Test fun entriesAreCappedToFiveHundred() {
        val entries = (0 until 600).toList()
        val limited = HistoryLimits.limitEntries(entries)
        assertEquals(HistoryLimits.MAX_ENTRIES, limited.size)
        assertEquals(0, limited.first())
        assertEquals(499, limited.last())
    }

    @Test fun jsonLengthLimitIsInclusive() {
        assertTrue(HistoryLimits.jsonWithinLimit("x".repeat(HistoryLimits.MAX_STORED_JSON_LENGTH)))
        assertTrue(!HistoryLimits.jsonWithinLimit("x".repeat(HistoryLimits.MAX_STORED_JSON_LENGTH + 1)))
    }
}
