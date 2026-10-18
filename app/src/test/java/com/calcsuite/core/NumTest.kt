package com.calcsuite.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

class NumTest {

    private fun parse(raw: String?): BigDecimal? = Num.parseOrNull(raw)

    private fun assertParse(expected: String, raw: String) {
        val actual = parse(raw)
        assertTrue("Expected \"$raw\" to parse, but was rejected", actual != null)
        assertEquals("\"$raw\"", 0, actual!!.compareTo(BigDecimal(expected)))
    }

    // ------------------------------------------------------------------ parse

    @Test fun plainNumbers() {
        assertParse("0", "0")
        assertParse("42", "42")
        assertParse("-42", "-42")
        assertParse("42", "+42")
        assertParse("3.14159", "3.14159")
        assertParse("-0.001", "-0.001")
        assertParse("42", "  42  ")
    }

    @Test fun groupingSeparators() {
        assertParse("1234567", "1,234,567")
        assertParse("1234567", "1.234.567")
        assertParse("1234567.89", "1,234,567.89")
        assertParse("1234567.89", "1.234.567,89")
        assertParse("1000000", "1 000 000")
        assertParse("1000000", "1\u00A0000\u202F000")
        assertParse("1234", "1'234")
    }

    @Test fun singleSeparatorIsDecimal() {
        assertParse("1.5", "1.5")
        assertParse("1.5", "1,5")
        assertParse("0.5", ",5")
        assertParse("0.5", ".5")
        // Documented convention: with a single separator of either kind the
        // last (here: only) separator is a decimal separator.
        assertParse("1.234", "1,234")
        assertParse("12.34", "12,34")
    }

    @Test fun scientificNotation() {
        assertParse("1500", "1.5e3")
        assertParse("0.0000000015", "1.5e-9")
        assertParse("250", "2.5E2")
        assertParse("1000", "1e3")
        assertParse("123450", "1,234.5e2")
    }

    @Test fun invalidInputs() {
        val bad = listOf(
            "", "   ", "abc", "1,23,456", "1..5", "1,5,", "12,345,67",
            "e5", "1e", "1e+", "--5", "1.2.3", ".",
        )
        for (raw in bad) {
            assertNull("Expected \"$raw\" to be rejected", parse(raw))
        }
        assertNull(parse(null))
    }

    @Test fun integerParsing() {
        assertEquals(BigInteger("42"), Num.parseIntOrNull("42"))
        assertEquals(BigInteger("42"), Num.parseIntOrNull("+42"))
        assertEquals(BigInteger("4000"), Num.parseIntOrNull("4.0e3"))
        assertEquals(BigInteger("1234567"), Num.parseIntOrNull("1,234,567"))
        assertEquals(BigInteger("0"), Num.parseIntOrNull("-0"))
        assertNull(Num.parseIntOrNull("4.5"))
        assertNull(Num.parseIntOrNull("x"))
        assertTrue(Num.isValidInteger("1000"))
        assertFalse(Num.isValidInteger("1.5"))
    }

    // ----------------------------------------------------------------- format

    @Test fun plainFormatting() {
        assertEquals("1,234,567.891", Num.format(BigDecimal("1234567.891")))
        assertEquals("1,000,000,000,000", Num.format(BigDecimal("1e12")))
        assertEquals("0", Num.format(BigDecimal.ZERO))
        assertEquals("0", Num.format(BigDecimal("0.000")))
        assertEquals("-1,234.5", Num.format(BigDecimal("-1234.50")))
        assertEquals("1234567.891", Num.format(BigDecimal("1234567.891"), 12, Num.Sep('.', null)))
    }

    @Test fun scientificFormatting() {
        assertEquals("1.25e-7", Num.format(BigDecimal("0.000000125")))
        assertEquals("-1.25e-7", Num.format(BigDecimal("-0.000000125")))
        assertEquals("1e13", Num.format(BigDecimal("1e13")))
        assertEquals("1.23456789012e17", Num.format(BigDecimal("123456789012345678"), 12))
    }

    @Test fun precisionRoundingAndClamping() {
        assertEquals("1.2346", Num.format(BigDecimal("1.23456789012345"), 5))
        // Precision is clamped to the supported 3..15 window.
        assertEquals("0.125", Num.format(BigDecimal("0.125"), 2))
        assertEquals("1,230", Num.format(BigDecimal("1234.5678"), 3))
        assertEquals(3, Num.clampPrecision(1))
        assertEquals(15, Num.clampPrecision(99))
        assertEquals(7, Num.clampPrecision(7))
    }

    @Test fun localeSeparators() {
        assertEquals("1 234 567,891", Num.format(BigDecimal("1234567.891"), 12, Locale.GERMANY, true))
        assertEquals("1234567,891", Num.format(BigDecimal("1234567.891"), 12, Locale.GERMANY, false))
        assertEquals("1,234,567.891", Num.format(BigDecimal("1234567.891"), 12, Locale.US, true))
    }

    // ------------------------------------------------- thousands separator setting

    @Test fun defaultSeparatorFollowsTheLocale() {
        // ThousandsSeparator.Locale must keep the previous behaviour exactly.
        assertEquals(
            "1,234,567.89",
            Num.format(BigDecimal("1234567.89"), 12, Locale.US, true, ThousandsSeparator.Locale),
        )
        assertEquals(
            "1 234 567,89",
            Num.format(BigDecimal("1234567.89"), 12, Locale.GERMANY, true, ThousandsSeparator.Locale),
        )
    }

    @Test fun eachSeparatorStyleIsAppliedToLargeIntegers() {
        val value = BigDecimal("1234567")
        fun fmt(sep: ThousandsSeparator) = Num.format(value, 12, Locale.US, true, sep)
        assertEquals("1,234,567", fmt(ThousandsSeparator.Comma))
        assertEquals("1.234.567", fmt(ThousandsSeparator.Period))
        assertEquals("1'234'567", fmt(ThousandsSeparator.Apostrophe))
        assertEquals("1\u00A0234\u00A0567", fmt(ThousandsSeparator.Space))
    }

    @Test fun separatorStyleDoesNotAffectTheDecimalMark() {
        // The decimal mark always follows the locale and is never configurable.
        // Values below 1 000 are never grouped at all, whatever the choice.
        for (sep in ThousandsSeparator.entries) {
            assertEquals("7.25", Num.format(BigDecimal("7.25"), 12, Locale.US, true, sep))
            assertEquals("123.5", Num.format(BigDecimal("123.5"), 12, Locale.US, true, sep))
            assertEquals("123,5", Num.format(BigDecimal("123.5"), 12, Locale.GERMANY, true, sep))
            assertEquals("0.5", Num.format(BigDecimal("0.5"), 12, Locale.US, true, sep))
        }
        // And it is never grouped as if it were an integer part either.
        for (sep in ThousandsSeparator.entries) {
            assertEquals(
                "-7.25",
                Num.format(BigDecimal("-7.25"), 12, Locale.US, true, sep),
            )
        }
    }

    @Test fun negativesAndGroupingOffBehaveSensibly() {
        // The sign sits outside the grouping, whichever separator is chosen.
        assertEquals(
            "-1.234.567,89",
            Num.format(BigDecimal("-1234567.89"), 12, Locale.GERMANY, true, ThousandsSeparator.Period),
        )
        assertEquals(
            "-1\u00A0234\u00A0567,89",
            Num.format(BigDecimal("-1234567.89"), 12, Locale.GERMANY, true, ThousandsSeparator.Space),
        )
        assertEquals(
            "-1'234'567.89",
            Num.format(BigDecimal("-1234567.89"), 12, Locale.US, true, ThousandsSeparator.Apostrophe),
        )
        // Grouping off ignores the chosen style entirely.
        assertEquals(
            "1234567.89",
            Num.format(BigDecimal("1234567.89"), 12, Locale.US, false, ThousandsSeparator.Apostrophe),
        )
    }

    @Test fun scientificNotationIsUnaffectedByTheSeparatorChoice() {
        val big = BigDecimal("1.23456789e17")
        assertEquals("1.23456789e17", Num.format(big, 12, Locale.US, true, ThousandsSeparator.Period))
        assertEquals("1.23456789e17", Num.format(big, 12, Locale.US, true, ThousandsSeparator.Comma))
    }

    @Test fun theSeparatorChoiceNeverChangesHowInputIsParsed() {
        // The important guarantee: parsing is independent of the display
        // preference, so no selection can reinterpret a typed number. The one
        // place the two meet is a separator that doubles as the decimal mark,
        // and there the app rejects rather than guesses.
        for (raw in listOf("1,234.56", "1.234,56", "1234.56", "1234,56", "1 234,56", "1'234.56")) {
            assertNotNull("'$raw' must still parse", Num.parseOrNull(raw))
        }
        assertEquals(0, Num.parseOrNull("1,234,567")!!.compareTo(BigDecimal("1234567")))
        assertEquals(0, Num.parseOrNull("1.234.567")!!.compareTo(BigDecimal("1234567")))
        assertEquals(0, Num.parseOrNull("1.5")!!.compareTo(BigDecimal("1.5")))
        assertEquals(0, Num.parseOrNull("1,5")!!.compareTo(BigDecimal("1.5")))
        // Malformed grouping stays rejected rather than being read as a decimal.
        assertNull(Num.parseOrNull("12,345,67"))
        assertNull(Num.parseOrNull("1.234.56"))
    }

    @Test fun copyFormatting() {
        assertEquals("1234567.89", Num.formatForCopy(BigDecimal("1234567.890")))
        assertEquals("100E-9", Num.formatForCopy(BigDecimal("0.0000001")))
    }

    @Test fun helpers() {
        assertEquals(0, Num.clean(BigDecimal("1.500")).compareTo(BigDecimal("1.5")))
        assertEquals(1, Num.clean(BigDecimal("1.500")).scale())
        assertEquals("1.01", Num.roundTo(BigDecimal("1.005"), 2).toPlainString())
        assertEquals("12,345.679", Num.format(Num.roundTo(BigDecimal("12345.6789"), 3)))
    }
}
