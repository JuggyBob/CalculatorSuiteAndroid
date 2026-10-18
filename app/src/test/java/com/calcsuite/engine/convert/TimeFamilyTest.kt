package com.calcsuite.engine.convert

import com.calcsuite.engine.convert.families.TimeFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

private fun assertExact(expected: String, actual: BigDecimal) {
    assertEquals("expected $expected but was $actual", 0, actual.compareTo(BigDecimal(expected)))
}

class TimeFamilyTest {

    @Test
    fun oneDayIsTwentyFourHours() {
        assertExact("24", TimeFamily.convert(BigDecimal.ONE, "d", "h").valueOrNull()!!)
    }

    @Test
    fun oneDayIsOneThousandFourHundredAndFortyMinutes() {
        assertExact("1440", TimeFamily.convert(BigDecimal.ONE, "d", "min").valueOrNull()!!)
    }

    @Test
    fun oneDayIsEightySixThousandFourHundredSeconds() {
        assertExact("86400", TimeFamily.convert(BigDecimal.ONE, "d", "s").valueOrNull()!!)
    }

    @Test
    fun oneWeekIsSevenDays() {
        assertExact("7", TimeFamily.convert(BigDecimal.ONE, "wk", "d").valueOrNull()!!)
    }

    @Test
    fun oneWeekIsOneHundredAndSixtyEightHours() {
        assertExact("168", TimeFamily.convert(BigDecimal.ONE, "wk", "h").valueOrNull()!!)
    }

    @Test
    fun oneHourIsThreeThousandSixHundredSeconds() {
        assertExact("3600", TimeFamily.convert(BigDecimal.ONE, "h", "s").valueOrNull()!!)
    }

    @Test
    fun oneMinuteIsSixtySeconds() {
        assertExact("60", TimeFamily.convert(BigDecimal.ONE, "min", "s").valueOrNull()!!)
    }

    @Test
    fun oneMillisecondIsOneThousandMicroseconds() {
        assertExact("1000", TimeFamily.convert(BigDecimal.ONE, "ms", "us").valueOrNull()!!)
    }

    @Test
    fun oneMicrosecondIsOneThousandNanoseconds() {
        assertExact("1000", TimeFamily.convert(BigDecimal.ONE, "us", "ns").valueOrNull()!!)
    }

    @Test
    fun oneSecondIsOneBillionNanoseconds() {
        assertExact("1000000000", TimeFamily.convert(BigDecimal.ONE, "s", "ns").valueOrNull()!!)
    }

    @Test
    fun oneNanosecondIsOneBillionthOfASecond() {
        assertExact("0.000000001", TimeFamily.convert(BigDecimal.ONE, "ns", "s").valueOrNull()!!)
    }

    @Test
    fun oneMonthIsTheMeanGregorianMonth() {
        assertExact("30.436875", TimeFamily.convert(BigDecimal.ONE, "mo", "d").valueOrNull()!!)
    }

    @Test
    fun oneYearIsTheMeanGregorianYear() {
        assertExact("365.2425", TimeFamily.convert(BigDecimal.ONE, "yr", "d").valueOrNull()!!)
    }

    @Test
    fun oneYearIsTwelveAverageMonths() {
        assertExact("12", TimeFamily.convert(BigDecimal.ONE, "yr", "mo").valueOrNull()!!)
    }

    @Test
    fun calendarUnitsAreGroupedSeparately() {
        val month = TimeFamily.unit("mo")!!
        val year = TimeFamily.unit("yr")!!
        assertEquals("Calendar (average, convention-based)", month.group)
        assertEquals("Calendar (average, convention-based)", year.group)
    }

    @Test
    fun monthsAreNotExactCalendarMonths() {
        val result = TimeFamily.convert(BigDecimal.ONE, "mo", "d").valueOrNull()!!
        assertTrue("a month is an average, not 28-31 days exactly", (result - BigDecimal("30")).abs() > BigDecimal("0.1"))
    }

    @Test
    fun ninetyMinutesInHours() {
        assertExact("1.5", TimeFamily.convert(BigDecimal("90"), "min", "h").valueOrNull()!!)
    }

    @Test
    fun zeroAndNegativeAreLinear() {
        assertEquals(0, TimeFamily.convert(BigDecimal.ZERO, "d", "ns").valueOrNull()!!.signum())
        assertExact("-3600", TimeFamily.convert(BigDecimal("-1"), "h", "s").valueOrNull()!!)
    }

    @Test
    fun unknownUnitIsRejected() {
        assertTrue(TimeFamily.convert(BigDecimal.ONE, "s", "fortnight") is com.calcsuite.core.CalcResult.Err)
    }
}
