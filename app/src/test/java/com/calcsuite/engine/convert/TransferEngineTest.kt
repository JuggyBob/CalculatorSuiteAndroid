package com.calcsuite.engine.convert

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

private fun assertClose(expected: String, actual: BigDecimal, tol: String) {
    assertTrue("expected $expected ± $tol but was $actual", (actual - BigDecimal(expected)).abs() < BigDecimal(tol))
}

private fun expectError(kind: ErrorKind, result: CalcResult<*>, messagePart: String? = null) {
    assertTrue("expected Err but was $result", result is CalcResult.Err)
    val error = (result as CalcResult.Err).error
    assertEquals(kind, error.kind)
    if (messagePart != null) {
        assertTrue("message '${error.message}' should contain '$messagePart'", error.message.contains(messagePart))
    }
}

class TransferEngineTest {

    @Test
    fun oneMegabytePerSecondIsEightMegabitsPerSecond() {
        assertExact("8", TransferEngine.convert(BigDecimal.ONE, "MB_s", "Mbit_s").valueOrNull()!!)
    }

    @Test
    fun oneBytePerSecondIsEightBitsPerSecond() {
        assertExact("8", TransferEngine.convert(BigDecimal.ONE, "B_s", "bit_s").valueOrNull()!!)
    }

    @Test
    fun oneGigabitPerSecondIsOneThousandMegabits() {
        assertExact("1000", TransferEngine.convert(BigDecimal.ONE, "Gbit_s", "Mbit_s").valueOrNull()!!)
    }

    @Test
    fun oneMebibytePerSecondInMegabytesPerSecond() {
        assertExact("1.048576", TransferEngine.convert(BigDecimal.ONE, "MiB_s", "MB_s").valueOrNull()!!)
    }

    @Test
    fun negativeRateIsRejected() {
        expectError(ErrorKind.Domain, TransferEngine.convert(BigDecimal("-1"), "MB_s", "Mbit_s"), "negative")
    }

    @Test
    fun unknownRateUnitIsRejected() {
        expectError(ErrorKind.InvalidInput, TransferEngine.convert(BigDecimal.ONE, "MB_s", "MiB_s_x"), "Unknown")
    }

    @Test
    fun oneGigabyteOverOneHundredMegabitsIsEightySeconds() {
        val result = TransferEngine.transferTime(BigDecimal.ONE, "GB", BigDecimal("100"), "Mbit_s", false)
            .valueOrNull()!!
        assertExact("80", result)
    }

    @Test
    fun oneGibibyteOverOneHundredMegabitsIsAboutEightySixSeconds() {
        val result = TransferEngine.transferTime(BigDecimal.ONE, "GiB", BigDecimal("100"), "Mbit_s")
            .valueOrNull()!!
        assertClose("85.89934592", result, "1e-6")
    }

    @Test
    fun theBinaryFlagChangesHowGbIsRead() {
        val decimalGb = TransferEngine.transferTime(BigDecimal.ONE, "GB", BigDecimal("100"), "Mbit_s", false)
            .valueOrNull()!!
        val binaryGb = TransferEngine.transferTime(BigDecimal.ONE, "GB", BigDecimal("100"), "Mbit_s", true)
            .valueOrNull()!!
        assertExact("80", decimalGb)
        assertClose("85.89934592", binaryGb, "1e-6")
        assertTrue(binaryGb.compareTo(decimalGb) > 0)
    }

    @Test
    fun binaryContainersAreAlwaysBinary() {
        // 1 MiB = 1 048 576 B = 8 388 608 bits → 8.388608 s at 1 Mbit/s.
        val result = TransferEngine.transferTime(BigDecimal.ONE, "MiB", BigDecimal.ONE, "Mbit_s").valueOrNull()!!
        assertExact("8.388608", result)
    }

    @Test
    fun oneHundredAndTwentyFiveKilobytesOverOneMegabitIsOneSecond() {
        val result = TransferEngine.transferTime(BigDecimal("125000"), "B", BigDecimal.ONE, "Mbit_s")
            .valueOrNull()!!
        assertExact("1", result)
    }

    @Test
    fun zeroRateIsRejected() {
        val result = TransferEngine.transferTime(BigDecimal.ONE, "GB", BigDecimal.ZERO, "Mbit_s")
        expectError(ErrorKind.DivisionByZero, result, "greater than zero")
    }

    @Test
    fun negativeRateIsRejectedByTransferTime() {
        val result = TransferEngine.transferTime(BigDecimal.ONE, "GB", BigDecimal("-100"), "Mbit_s")
        expectError(ErrorKind.DivisionByZero, result, "greater than zero")
    }

    @Test
    fun negativeFileSizeIsRejected() {
        val result = TransferEngine.transferTime(BigDecimal("-1"), "GB", BigDecimal("100"), "Mbit_s")
        expectError(ErrorKind.Domain, result, "File size")
    }

    @Test
    fun unknownFileSizeUnitIsRejected() {
        val result = TransferEngine.transferTime(BigDecimal.ONE, "gibble", BigDecimal("100"), "Mbit_s")
        expectError(ErrorKind.InvalidInput, result, "Unknown")
    }

    @Test
    fun sizeInBytesHonoursTheBinaryFlag() {
        assertExact("1000000", TransferEngine.sizeInBytes(BigDecimal.ONE, "MB", false).valueOrNull()!!)
        assertExact("1048576", TransferEngine.sizeInBytes(BigDecimal.ONE, "MB", true).valueOrNull()!!)
        assertExact("1048576", TransferEngine.sizeInBytes(BigDecimal.ONE, "MiB", false).valueOrNull()!!)
        // GiB is an unambiguous binary container, so the flag does not change it.
        assertExact("1073741824", TransferEngine.sizeInBytes(BigDecimal.ONE, "GiB", false).valueOrNull()!!)
        assertExact("1073741824", TransferEngine.sizeInBytes(BigDecimal.ONE, "GiB", true).valueOrNull()!!)
    }

    @Test
    fun sizeInBytesRejectsNegativeAndUnknown() {
        expectError(ErrorKind.Domain, TransferEngine.sizeInBytes(BigDecimal("-1"), "MB"), "File size")
        expectError(ErrorKind.InvalidInput, TransferEngine.sizeInBytes(BigDecimal.ONE, "wobble"), "Unknown")
    }

    @Test
    fun requiredRateForAGigabyteInTwoMinutes() {
        val result = TransferEngine.requiredRate(BigDecimal.ONE, "GB", BigDecimal("120"), false).valueOrNull()!!
        assertClose("66666666.667", result, "0.01")
    }

    @Test
    fun requiredRateRejectsZeroDuration() {
        val result = TransferEngine.requiredRate(BigDecimal.ONE, "GB", BigDecimal.ZERO, false)
        expectError(ErrorKind.DivisionByZero, result, "Duration")
    }

    @Test
    fun sizeForTenSecondsAtOneGigabytePerSecond() {
        val decimal = TransferEngine.sizeFor(BigDecimal.ONE, "GB_s", BigDecimal("10"), "GB", false)
            .valueOrNull()!!
        assertExact("10", decimal)
        val binary = TransferEngine.sizeFor(BigDecimal.ONE, "GB_s", BigDecimal("10"), "GB", true)
            .valueOrNull()!!
        assertExact("9.31322574615478515625", binary)
    }

    @Test
    fun familyFacadeUsesBitsPerSecondAsBase() {
        assertEquals("bit_s", TransferEngine.family.baseUnitId)
        assertExact("8", TransferEngine.family.convert(BigDecimal.ONE, "B_s", "bit_s").valueOrNull()!!)
    }

    @Test
    fun theRateListSeparatesDecimalAndBinaryUnits() {
        assertEquals(11, TransferEngine.allUnits.size)
        assertEquals("Bytes (decimal)", TransferEngine.unit("MB_s")!!.group)
        assertEquals("Bits", TransferEngine.unit("Mbit_s")!!.group)
        assertEquals("Bytes (binary)", TransferEngine.unit("MiB_s")!!.group)
        assertEquals(null, TransferEngine.unit("nope"))
    }

    @Test
    fun notesMentionTheoreticalTiming() {
        val notes = TransferEngine.notes.joinToString(" ")
        assertTrue(notes.contains("theoretical"))
        assertTrue(notes.contains("60–90%"))
    }
}
