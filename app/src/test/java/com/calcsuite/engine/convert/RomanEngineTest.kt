package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun expectError(kind: ErrorKind, result: CalcResult<*>, messagePart: String? = null) {
    assertTrue("expected Err but was $result", result is CalcResult.Err)
    val error = (result as CalcResult.Err).error
    assertEquals(kind, error.kind)
    if (messagePart != null) {
        assertTrue("message '${error.message}' should contain '$messagePart'", error.message.contains(messagePart))
    }
}

class RomanEngineTest {

    @Test
    fun nineteenNinetyFourIsMcmxciv() {
        assertEquals("MCMXCIV", RomanEngine.toRoman(1994).valueOrNull()!!)
    }

    @Test
    fun fourIsIvAndNineIsIx() {
        assertEquals("IV", RomanEngine.toRoman(4).valueOrNull()!!)
        assertEquals("IX", RomanEngine.toRoman(9).valueOrNull()!!)
    }

    @Test
    fun threeThousandNineHundredAndNinetyNineIsMmmcmxcix() {
        assertEquals("MMMCMXCIX", RomanEngine.toRoman(3999).valueOrNull()!!)
    }

    @Test
    fun subtractiveFormsAreUsedForAllSixSteps() {
        assertEquals("XL", RomanEngine.toRoman(40).valueOrNull()!!)
        assertEquals("XC", RomanEngine.toRoman(90).valueOrNull()!!)
        assertEquals("CD", RomanEngine.toRoman(400).valueOrNull()!!)
        assertEquals("CM", RomanEngine.toRoman(900).valueOrNull()!!)
    }

    @Test
    fun classicalExamples() {
        assertEquals("XLII", RomanEngine.toRoman(42).valueOrNull()!!)
        assertEquals("MMXXIV", RomanEngine.toRoman(2024).valueOrNull()!!)
        assertEquals("MMMDCCCLXXXVIII", RomanEngine.toRoman(3888).valueOrNull()!!)
        assertEquals("CDXLIV", RomanEngine.toRoman(444).valueOrNull()!!)
    }

    @Test
    fun zeroIsRejected() {
        expectError(ErrorKind.Domain, RomanEngine.toRoman(0), "no symbol for zero")
    }

    @Test
    fun negativeNumbersAreRejected() {
        expectError(ErrorKind.Domain, RomanEngine.toRoman(-1), "negative")
    }

    @Test
    fun fourThousandIsRejected() {
        expectError(ErrorKind.Domain, RomanEngine.toRoman(4000), "limited to 3999")
    }

    @Test
    fun everyValueInRangeRoundTrips() {
        for (value in 1..RomanEngine.MAX_VALUE) {
            val roman = RomanEngine.toRoman(value).valueOrNull()!!
            val back = RomanEngine.parse(roman).valueOrNull()
            assertEquals("round trip failed at $value ($roman)", value, back)
        }
    }

    @Test
    fun parsingIsCaseInsensitive() {
        assertEquals(1994, RomanEngine.parse("mcmxciv").valueOrNull())
        assertEquals(1994, RomanEngine.parse("  MCMXCIV  ").valueOrNull())
    }

    @Test
    fun parsingTheExtremes() {
        assertEquals(1, RomanEngine.parse("I").valueOrNull())
        assertEquals(3999, RomanEngine.parse("MMMCMXCIX").valueOrNull())
    }

    @Test
    fun ilIsRejectedWithTheCanonicalFormNamed() {
        val result = RomanEngine.parse("IL")
        expectError(ErrorKind.InvalidInput, result, "XLIX")
        assertTrue((result as CalcResult.Err).error.message.contains("49"))
    }

    @Test
    fun iiiiIsRejectedWithTheCanonicalFormNamed() {
        val result = RomanEngine.parse("IIII")
        expectError(ErrorKind.InvalidInput, result, "4 is written IV")
    }

    @Test
    fun vxIsRejectedAsNonStandard() {
        val result = RomanEngine.parse("VX")
        expectError(ErrorKind.InvalidInput, result, "15")
    }

    @Test
    fun nonRomanCharactersAreNamed() {
        val result = RomanEngine.parse("ABC")
        expectError(ErrorKind.InvalidInput, result, "'A' is not a Roman numeral symbol")
    }

    @Test
    fun emptyInputIsRejected() {
        expectError(ErrorKind.InvalidInput, RomanEngine.parse("   "), "Enter a Roman numeral")
    }

    @Test
    fun aboveFourThousandInNonStandardFormIsRejected() {
        expectError(ErrorKind.Domain, RomanEngine.parse("MMMM"), "above the maximum 3999")
    }

    @Test
    fun isValidRomanAcceptsCanonicalAndRejectsTheRest() {
        assertTrue(RomanEngine.isValidRoman("MCMXCIV"))
        assertTrue(RomanEngine.isValidRoman("ix"))
        assertTrue(RomanEngine.isValidRoman("MMMCMXCIX"))
        assertFalse(RomanEngine.isValidRoman("IIII"))
        assertFalse(RomanEngine.isValidRoman("IC"))
        assertFalse(RomanEngine.isValidRoman("VX"))
        assertFalse(RomanEngine.isValidRoman(""))
        assertFalse(RomanEngine.isValidRoman("ABC"))
    }

    @Test
    fun canonicalReturnsTheStandardSpellingOrNullOutsideTheRange() {
        assertEquals("XLIX", RomanEngine.canonical(49))
        assertEquals("MMMCMXCIX", RomanEngine.canonical(3999))
        assertEquals(null, RomanEngine.canonical(0))
        assertEquals(null, RomanEngine.canonical(4000))
    }

    @Test
    fun noSymbolIsRepeatedFourTimesInCanonicalOutput() {
        for (value in 1..RomanEngine.MAX_VALUE) {
            val roman = RomanEngine.canonical(value)!!
            assertFalse("IIII in $value", roman.contains("IIII"))
            assertFalse("XXXX in $value", roman.contains("XXXX"))
            assertFalse("CCCC in $value", roman.contains("CCCC"))
            assertFalse("VV in $value", roman.contains("VV"))
            assertFalse("LL in $value", roman.contains("LL"))
            assertFalse("DD in $value", roman.contains("DD"))
        }
    }

    @Test
    fun theLegendListsAllSevenSymbols() {
        assertEquals(7, RomanEngine.symbolLegend.size)
        assertEquals("I", RomanEngine.symbolLegend.first().first)
        assertEquals(1000, RomanEngine.symbolLegend.last().second)
    }
}
