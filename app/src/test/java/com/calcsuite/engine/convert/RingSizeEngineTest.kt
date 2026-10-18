package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class RingSizeEngineTest {

    @Test
    fun circumferenceFiftyFourPointFourMillimetresIsUsSeven() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_CIRCUMFERENCE, "54.4").valueOrNull()!!
        assertEquals("7", match.row.us)
        assertEquals("N½", match.row.uk)
        assertFalse(match.nearest)
    }

    @Test
    fun usSevenHasDiameterSeventeenPointThreeMillimetres() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_US, "7").valueOrNull()!!
        assertEquals(0, match.row.diameterMm.compareTo(BigDecimal("17.3")))
        assertEquals(0, match.row.circumferenceMm.compareTo(BigDecimal("54.4")))
        assertEquals("54", match.row.eu)
        assertEquals("14", match.row.jp)
    }

    @Test
    fun diameterSeventeenPointThreeMillimetresIsUsSeven() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_DIAMETER, "17.3").valueOrNull()!!
        assertEquals("7", match.row.us)
    }

    @Test
    fun fiftyFiveMillimetresReportsTheNearestRowWithTheDelta() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_CIRCUMFERENCE, "55").valueOrNull()!!
        assertTrue(match.nearest)
        assertEquals("7", match.row.us)
        assertEquals(0, match.deltaMm!!.compareTo(BigDecimal("0.6")))
        assertTrue(match.note!!.contains("0.6 mm above"))
    }

    @Test
    fun aMeasurementJustBelowARowReportsBelow() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_CIRCUMFERENCE, "44").valueOrNull()!!
        assertEquals("3", match.row.us)
        assertTrue(match.nearest)
        assertTrue(match.note!!.contains("below"))
    }

    @Test
    fun aMeasurementWithinTheToleranceIsExact() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_CIRCUMFERENCE, "54.42").valueOrNull()!!
        assertFalse(match.nearest)
        assertTrue(match.note!!.contains("Within 0.05 mm"))
    }

    @Test
    fun euAndJpLookups() {
        assertEquals("7", RingSizeEngine.convert(RingSizeEngine.SYSTEM_EU, "54").valueOrNull()!!.row.us)
        assertEquals("7", RingSizeEngine.convert(RingSizeEngine.SYSTEM_JP, "14").valueOrNull()!!.row.us)
    }

    @Test
    fun ukLookupAcceptsThePublishedSpelling() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_UK, "N½").valueOrNull()!!
        assertEquals("7", match.row.us)
        // "n 1/2" with a space and slash is normalised to the same row.
        assertEquals("7", RingSizeEngine.convert(RingSizeEngine.SYSTEM_UK, "n 1/2").valueOrNull()!!.row.us)
    }

    @Test
    fun aUkLetterThatIsNotInTheChartIsRejected() {
        expectError(ErrorKind.InvalidInput, RingSizeEngine.convert(RingSizeEngine.SYSTEM_UK, "Q"), "not a UK ring size")
    }

    @Test
    fun aUsSizeThatIsNotListedFallsBackToTheNearestRow() {
        val match = RingSizeEngine.convert(RingSizeEngine.SYSTEM_US, "6.7").valueOrNull()!!
        assertTrue(match.nearest)
        assertEquals("6.5", match.row.us)
        assertTrue(match.note!!.contains("not a listed size"))
    }

    @Test
    fun theChartHasTwentyOneRowsFromUsThreeToThirteen() {
        assertEquals(21, RingSizeEngine.rows.size)
        assertEquals("3", RingSizeEngine.rows.first().us)
        assertEquals("13", RingSizeEngine.rows.last().us)
        assertEquals("F", RingSizeEngine.rows.first().uk)
        assertEquals("Z+1", RingSizeEngine.rows.last().uk)
    }

    @Test
    fun nonPositiveMeasurementsAreRejected() {
        expectError(ErrorKind.Domain, RingSizeEngine.convert(RingSizeEngine.SYSTEM_CIRCUMFERENCE, "0"), "greater than zero")
        expectError(ErrorKind.Domain, RingSizeEngine.convert(RingSizeEngine.SYSTEM_DIAMETER, "-1"), "greater than zero")
    }

    @Test
    fun nonNumericMeasurementsAreRejected() {
        expectError(ErrorKind.InvalidInput, RingSizeEngine.convert(RingSizeEngine.SYSTEM_CIRCUMFERENCE, "abc"), "millimetres")
    }

    @Test
    fun unknownSystemIsRejected() {
        expectError(ErrorKind.InvalidInput, RingSizeEngine.convert("banana", "7"), "Unknown ring size system")
    }

    @Test
    fun allSixSystemsAreOffered() {
        assertEquals(6, RingSizeEngine.systems.size)
        assertEquals(RingSizeEngine.SYSTEM_CIRCUMFERENCE, RingSizeEngine.systems.first().first)
    }
}
