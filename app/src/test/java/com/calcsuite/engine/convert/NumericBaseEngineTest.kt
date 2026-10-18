package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

private fun expectError(kind: ErrorKind, result: CalcResult<*>, messagePart: String? = null) {
    assertTrue("expected Err but was $result", result is CalcResult.Err)
    val error = (result as CalcResult.Err).error
    assertEquals(kind, error.kind)
    if (messagePart != null) {
        assertTrue("message '${error.message}' should contain '$messagePart'", error.message.contains(messagePart))
    }
}

private fun ns(n: Long): BigInteger = BigInteger.valueOf(n)

class NumericBaseEngineTest {

    @Test
    fun twoFiveFiveIsAllOnesInBinary() {
        assertEquals("11111111", NumericBaseEngine.render(ns(255), 2))
    }

    @Test
    fun twoFiveFiveIsFfInHexadecimal() {
        assertEquals("ff", NumericBaseEngine.render(ns(255), 16))
        assertEquals("FF", NumericBaseEngine.render(ns(255), 16, uppercase = true))
    }

    @Test
    fun parseBinaryAllOnesIsTwoFiveFive() {
        assertEquals(ns(255), NumericBaseEngine.parse("11111111", 2).valueOrNull()!!)
    }

    @Test
    fun base36ZzIsOneTwoNineFive() {
        assertEquals(ns(1295), NumericBaseEngine.parse("zz", 36).valueOrNull()!!)
        assertEquals("zz", NumericBaseEngine.render(ns(1295), 36))
    }

    @Test
    fun hexAndBinaryPrefixesAreAccepted() {
        assertEquals(ns(255), NumericBaseEngine.parse("0xFF", 16).valueOrNull()!!)
        assertEquals(ns(255), NumericBaseEngine.parse("0xff", 16).valueOrNull()!!)
        assertEquals(ns(10), NumericBaseEngine.parse("0b1010", 2).valueOrNull()!!)
        assertEquals(ns(15), NumericBaseEngine.parse("0o17", 8).valueOrNull()!!)
    }

    @Test
    fun aPrefixThatDoesNotMatchTheBaseIsTreatedAsDigits() {
        // 0x in base 10: 'x' is not a digit, so this is an error, not a prefix.
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.parse("0x10", 10), "'x'")
    }

    @Test
    fun signAndPrefixCombine() {
        assertEquals(ns(-16), NumericBaseEngine.parse("-0x10", 16).valueOrNull()!!)
        assertEquals(ns(-255), NumericBaseEngine.parse("-ff", 16).valueOrNull()!!)
        assertEquals("-ff", NumericBaseEngine.render(ns(-255), 16))
    }

    @Test
    fun groupingSeparatorsAreTolerated() {
        assertEquals(ns(240), NumericBaseEngine.parse("1111 0000", 2).valueOrNull()!!)
        assertEquals(ns(240), NumericBaseEngine.parse("1111_0000", 2).valueOrNull()!!)
    }

    @Test
    fun binaryGroupingRendersNibbles() {
        assertEquals("1111 0000", NumericBaseEngine.render(ns(240), 2, groupBinary = true))
        assertEquals("1111 1111 1111 1111", NumericBaseEngine.render(ns(65535), 2, groupBinary = true))
    }

    @Test
    fun prefixesAreRenderedOnlyForTheirBases() {
        assertEquals("0xff", NumericBaseEngine.render(ns(255), 16, prefix = true))
        assertEquals("0xFF", NumericBaseEngine.render(ns(255), 16, uppercase = true, prefix = true))
        assertEquals("0b11111111", NumericBaseEngine.render(ns(255), 2, prefix = true))
        assertEquals("0o377", NumericBaseEngine.render(ns(255), 8, prefix = true))
        assertEquals("10", NumericBaseEngine.render(ns(10), 10, prefix = true))
    }

    @Test
    fun zeroRendersAsZero() {
        assertEquals("0", NumericBaseEngine.render(BigInteger.ZERO, 2))
        assertEquals(ns(0), NumericBaseEngine.parse("0", 10).valueOrNull()!!)
    }

    @Test
    fun twoIsNotAValidBinaryDigit() {
        val result = NumericBaseEngine.parse("2", 2)
        expectError(ErrorKind.InvalidInput, result, "'2' is not a valid digit in base 2")
    }

    @Test
    fun gIsNotAValidHexDigit() {
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.parse("g", 16), "'g'")
    }

    @Test
    fun emptyInputIsRejected() {
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.parse("   ", 10), "Enter a value")
    }

    @Test
    fun basesOutsideTwoToThirtySixAreRejected() {
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.parse("10", 1), "between 2 and 36")
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.parse("10", 37), "between 2 and 36")
    }

    @Test
    fun minBaseAndMaxBaseConstants() {
        assertEquals(2, NumericBaseEngine.MIN_BASE)
        assertEquals(36, NumericBaseEngine.MAX_BASE)
        assertEquals(listOf(8, 16, 32, 64), NumericBaseEngine.WIDTHS)
    }

    @Test
    fun digitRangeDescribesTheBase() {
        assertEquals("0–1", NumericBaseEngine.digitRange(2))
        assertEquals("0–9", NumericBaseEngine.digitRange(10))
        assertEquals("0–9 and a–f", NumericBaseEngine.digitRange(16))
        assertEquals("0–9 and a–z", NumericBaseEngine.digitRange(36))
    }

    @Test
    fun roundTripAcrossManyBases() {
        val value = ns(123456789)
        for (base in 2..36) {
            val text = NumericBaseEngine.render(value, base)
            assertEquals("round trip in base $base", value, NumericBaseEngine.parse(text, base).valueOrNull()!!)
        }
    }

    // ---- Two's complement ---------------------------------------------------

    @Test
    fun minusOneInEightBitTwosComplementIsAllOnes() {
        assertEquals("11111111", NumericBaseEngine.toFixedWidth(ns(-1), 8, signed = true).valueOrNull()!!)
    }

    @Test
    fun allOnesReadBackAsMinusOne() {
        assertEquals(ns(-1), NumericBaseEngine.fromTwosComplement("11111111", 2, 8).valueOrNull()!!)
    }

    @Test
    fun minusOneCannotBeUnsignedEightBit() {
        expectError(ErrorKind.Overflow, NumericBaseEngine.toFixedWidth(ns(-1), 8, signed = false), "unsigned")
    }

    @Test
    fun oneTwentyEightOverflowsSignedEightBit() {
        val result = NumericBaseEngine.toFixedWidth(ns(128), 8, signed = true)
        expectError(ErrorKind.Overflow, result, "does not fit in a signed 8-bit value")
        assertTrue((result as CalcResult.Err).error.message.contains("-128 to 127"))
    }

    @Test
    fun minusOneTwentyEightFitsSignedEightBit() {
        assertEquals("10000000", NumericBaseEngine.toFixedWidth(ns(-128), 8, signed = true).valueOrNull()!!)
        assertEquals(ns(-128), NumericBaseEngine.fromTwosComplement("10000000", 2, 8).valueOrNull()!!)
    }

    @Test
    fun twoFiveFiveIsTheLargestUnsignedByte() {
        assertEquals("11111111", NumericBaseEngine.toFixedWidth(ns(255), 8, signed = false).valueOrNull()!!)
        expectError(ErrorKind.Overflow, NumericBaseEngine.toFixedWidth(ns(256), 8, signed = false), "unsigned")
    }

    @Test
    fun sixteenBitPadding() {
        assertEquals("0000000000000001", NumericBaseEngine.toFixedWidth(ns(1), 16, signed = true).valueOrNull()!!)
        assertEquals("1111111111111111", NumericBaseEngine.toFixedWidth(ns(-1), 16, signed = true).valueOrNull()!!)
    }

    @Test
    fun thirtyTwoBitMinusOneIsThirtyTwoOnes() {
        val pattern = NumericBaseEngine.toFixedWidth(ns(-1), 32, signed = true).valueOrNull()!!
        assertEquals(32, pattern.length)
        assertTrue(pattern.all { it == '1' })
    }

    @Test
    fun sixtyFourBitExtremes() {
        val min = BigInteger("-9223372036854775808")
        val pattern = NumericBaseEngine.toFixedWidth(min, 64, signed = true).valueOrNull()!!
        assertEquals(64, pattern.length)
        assertEquals(min, NumericBaseEngine.fromTwosComplement(pattern, 2, 64).valueOrNull()!!)
        // The largest unsigned 64-bit value does not fit a signed 64-bit value.
        val unsignedMax = BigInteger("18446744073709551615")
        assertEquals("1".repeat(64), NumericBaseEngine.toFixedWidth(unsignedMax, 64, signed = false).valueOrNull()!!)
        expectError(ErrorKind.Overflow, NumericBaseEngine.toFixedWidth(unsignedMax, 64, signed = true), "signed")
        expectError(
            ErrorKind.Overflow,
            NumericBaseEngine.toFixedWidth(BigInteger("18446744073709551616"), 64, signed = false),
            "unsigned",
        )
    }

    @Test
    fun widthsOutsideTheOfferedSetAreRejected() {
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.toFixedWidth(ns(1), 12), "8, 16, 32 or 64")
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.fromTwosComplement("1", 2, 7), "8, 16, 32 or 64")
    }

    @Test
    fun aPatternTooLargeForTheWidthIsRejected() {
        expectError(ErrorKind.Overflow, NumericBaseEngine.fromTwosComplement("111111111", 2, 8), "does not fit in 8 bits")
    }

    @Test
    fun negativeTwosComplementInputIsRejected() {
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.fromTwosComplement("-1", 2, 8), "minus sign")
    }

    @Test
    fun invalidDigitsPropagateFromFromTwosComplement() {
        expectError(ErrorKind.InvalidInput, NumericBaseEngine.fromTwosComplement("12", 2, 8), "'2'")
    }

    @Test
    fun rangeTextDescribesSignedAndUnsignedRanges() {
        assertEquals("-128 to 127", NumericBaseEngine.rangeText(8, true))
        assertEquals("0 to 255", NumericBaseEngine.rangeText(8, false))
        assertEquals("-32768 to 32767", NumericBaseEngine.rangeText(16, true))
        assertEquals("0 to 65535", NumericBaseEngine.rangeText(16, false))
        assertEquals("0 to 4294967295", NumericBaseEngine.rangeText(32, false))
    }

    @Test
    fun signedAndUnsignedAgreeForSmallPositiveValues() {
        assertEquals(
            NumericBaseEngine.toFixedWidth(ns(42), 8, signed = true).valueOrNull()!!,
            NumericBaseEngine.toFixedWidth(ns(42), 8, signed = false).valueOrNull()!!,
        )
    }
}
