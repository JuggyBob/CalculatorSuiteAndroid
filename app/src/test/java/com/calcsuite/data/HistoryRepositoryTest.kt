package com.calcsuite.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryTest {

    private val sample = listOf(
        HistoryEntry(1L, "algebra.percentage", "Percentage", "algebra", "15% of 200", "30", 1_700_000_000_000L),
        HistoryEntry(2L, "convert.temperature", "Temperature", "convert", "0 °C", "32 °F, 273.15 K", 1_700_000_000_001L),
        HistoryEntry(3L, "finance.tip", "Tip", "finance", "bill 42.50, 18%", "7.65", 1_700_000_000_002L),
    )

    @Test fun roundTripPreservesEntriesAndOrder() {
        val decoded = HistoryRepository.decode(HistoryRepository.encode(sample))
        assertEquals(sample, decoded)
    }

    @Test fun specialCharactersSurvive() {
        val tricky = listOf(
            HistoryEntry(9L, "convert.roman", "Roman numerals", "convert", "1994", "MCMXCIV", 5L),
            HistoryEntry(
                10L, "geometry.shapes", "2D Shapes", "geometry",
                "5'6\" / 2πr ≈ x", "line1\nline2", Long.MAX_VALUE,
            ),
            HistoryEntry(11L, "health.bmi", "Body mass index", "health", "70 kg / 1.75 m", "≈ 22.86", -1L),
        )
        assertEquals(tricky, HistoryRepository.decode(HistoryRepository.encode(tricky)))
    }

    @Test fun blankOrInvalidJsonDecodesToEmpty() {
        assertTrue(HistoryRepository.decode("").isEmpty())
        assertTrue(HistoryRepository.decode("   ").isEmpty())
        assertTrue(HistoryRepository.decode("not json at all").isEmpty())
        assertTrue(HistoryRepository.decode("{\"a\":1}").isEmpty())
        assertTrue(HistoryRepository.decode("[{\"id\":5}").isEmpty())
    }

    @Test fun missingFieldsGetDefaults() {
        val decoded = HistoryRepository.decode("[{\"id\":7,\"toolId\":\"x\"}]")
        assertEquals(1, decoded.size)
        assertEquals(7L, decoded[0].id)
        assertEquals("x", decoded[0].toolId)
        assertEquals("", decoded[0].toolTitle)
        assertEquals("", decoded[0].category)
        assertEquals("", decoded[0].input)
        assertEquals("", decoded[0].result)
        assertEquals(0L, decoded[0].timestamp)
    }

    @Test fun nonObjectItemsAreSkipped() {
        val decoded = HistoryRepository.decode("[1,\"two\",{\"id\":3}]")
        assertEquals(1, decoded.size)
        assertEquals(3L, decoded[0].id)
    }

    @Test fun historyIsCappedAtFiveHundred() {
        assertEquals(500, HistoryRepository.MAX_ENTRIES)
    }
}
