package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun expectError(kind: ErrorKind, result: CalcResult<*>, messagePart: String? = null) {
    assertTrue("expected Err but was $result", result is CalcResult.Err)
    val error = (result as CalcResult.Err).error
    assertEquals(kind, error.kind)
    if (messagePart != null) {
        assertTrue("message '${error.message}' should contain '$messagePart'", error.message.contains(messagePart))
    }
}

class ShoeSizeEngineTest {

    @Test
    fun mensEuFortyTwoIsUsEightAndAHalf() {
        val match = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "eu", "42").valueOrNull()!!
        assertEquals("8.5", match.row.us)
        assertEquals("8", match.row.uk)
        assertEquals("26.5", match.row.jp)
        assertEquals(265, match.row.footLengthMm)
    }

    @Test
    fun theReverseDirectionAlsoWorks() {
        val match = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "us", "8.5").valueOrNull()!!
        assertEquals("42", match.row.eu)
    }

    @Test
    fun mensUsSixIsEuThirtyEightPointFive() {
        val match = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "us", "6").valueOrNull()!!
        assertEquals("38.5", match.row.eu)
    }

    @Test
    fun mensUsThirteenIsEuFortyEight() {
        val match = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "us", "13").valueOrNull()!!
        assertEquals("48", match.row.eu)
        assertEquals(310, match.row.footLengthMm)
    }

    @Test
    fun womensUkFiveIsUsSeven() {
        val match = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.WOMEN, "uk", "5").valueOrNull()!!
        assertEquals("7", match.row.us)
        assertEquals("38", match.row.eu)
    }

    @Test
    fun womensUsFourIsEuThirtyFive() {
        val match = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.WOMEN, "us", "4").valueOrNull()!!
        assertEquals("35", match.row.eu)
        assertEquals(205, match.row.footLengthMm)
    }

    @Test
    fun childrenUseTheirOwnTable() {
        val match = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.CHILDREN, "eu", "20").valueOrNull()!!
        assertEquals("4", match.row.us)
        assertEquals("3.5", match.row.uk)
        // The same EU 20 does not exist in the men's table (which starts at 38.5).
        val mens = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "eu", "20")
        assertTrue(mens is CalcResult.Err)
    }

    @Test
    fun childrenYouthRowsWrapAsPublished() {
        val thirteen = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.CHILDREN, "eu", "31").valueOrNull()!!
        assertEquals("13", thirteen.row.us)
        val youthOne = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.CHILDREN, "eu", "32").valueOrNull()!!
        assertEquals("1", youthOne.row.us)
        assertEquals("13", youthOne.row.uk)
        val youthTwo = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.CHILDREN, "eu", "34").valueOrNull()!!
        assertEquals("2", youthTwo.row.us)
        assertEquals("1.5", youthTwo.row.uk)
    }

    @Test
    fun anUnknownSizeReportsTheNearestSizes() {
        val result = ShoeSizeEngine.convert(ShoeSizeEngine.Audience.WOMEN, "us", "3")
        expectError(ErrorKind.InvalidInput, result, "nearest sizes")
    }

    @Test
    fun anUnknownSystemIsRejected() {
        expectError(ErrorKind.InvalidInput, ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "moon", "42"), "Unknown")
    }

    @Test
    fun negativeSizesAreRejected() {
        expectError(ErrorKind.Domain, ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "eu", "-5"), "negative")
    }

    @Test
    fun nonNumericSizesAreRejected() {
        expectError(ErrorKind.InvalidInput, ShoeSizeEngine.convert(ShoeSizeEngine.Audience.MEN, "eu", "big"), "shoe size")
    }

    @Test
    fun byFootLengthFindsTheEuFortyTwoRow() {
        val match = ShoeSizeEngine.byFootLength(ShoeSizeEngine.Audience.MEN, BigDecimal("265")).valueOrNull()!!
        assertEquals("42", match.row.eu)
        assertEquals("8.5", match.row.us)
        assertTrue(match.note!!.contains("matches the 265 mm row"))
    }

    @Test
    fun byFootLengthReportsHowFarTheMeasurementIsFromARow() {
        val match = ShoeSizeEngine.byFootLength(ShoeSizeEngine.Audience.MEN, BigDecimal("266")).valueOrNull()!!
        assertEquals(265, match.row.footLengthMm)
        assertTrue(match.note!!.contains("1 mm above"))
    }

    @Test
    fun byFootLengthRejectsValuesFarOutsideTheRange() {
        expectError(
            ErrorKind.Domain,
            ShoeSizeEngine.byFootLength(ShoeSizeEngine.Audience.MEN, BigDecimal("400")),
            "far outside",
        )
    }

    @Test
    fun byFootLengthRejectsNonPositiveLengths() {
        expectError(ErrorKind.Domain, ShoeSizeEngine.byFootLength(ShoeSizeEngine.Audience.MEN, BigDecimal.ZERO), "greater than zero")
    }

    @Test
    fun tableSizes() {
        assertEquals(15, ShoeSizeEngine.rowsFor(ShoeSizeEngine.Audience.MEN).size)
        assertEquals(17, ShoeSizeEngine.rowsFor(ShoeSizeEngine.Audience.WOMEN).size)
        assertEquals(15, ShoeSizeEngine.rowsFor(ShoeSizeEngine.Audience.CHILDREN).size)
        assertEquals(15 + 17 + 15, ShoeSizeEngine.allRows().size)
        assertEquals(4, ShoeSizeEngine.systems.size)
    }
}
