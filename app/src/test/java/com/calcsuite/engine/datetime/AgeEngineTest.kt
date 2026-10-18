package com.calcsuite.engine.datetime

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class AgeEngineTest {

    private fun value(result: CalcResult<AgeEngine.Result>): AgeEngine.Result {
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<AgeEngine.Result>): String {
        assertTrue("Expected failure, was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    @Test
    fun briefExampleYearsMonthsDays() {
        val result = value(AgeEngine.compute(LocalDate.of(1990, 5, 15), LocalDate.of(2024, 5, 14)))
        assertEquals(33, result.years)
        assertEquals(11, result.months)
        assertEquals(29, result.days)
        assertEquals(12418L, result.totalDays)
        assertEquals(1774L, result.totalWeeks)
        assertEquals(407L, result.totalMonths)
        assertEquals(LocalDate.of(2024, 5, 15), result.nextBirthday)
        assertEquals(1L, result.daysToNextBirthday)
        assertEquals(DayOfWeek.TUESDAY, result.weekdayOfBirth)
    }

    @Test
    fun birthdayOnTheReferenceDateCountsZeroDays() {
        val result = value(AgeEngine.compute(LocalDate.of(1990, 5, 15), LocalDate.of(2024, 5, 15)))
        assertEquals(34, result.years)
        assertEquals(0, result.months)
        assertEquals(0, result.days)
        assertEquals(0L, result.daysToNextBirthday)
    }

    @Test
    fun leapYearTotalsAreExact() {
        assertEquals(366L, value(AgeEngine.compute(LocalDate.of(2000, 1, 1), LocalDate.of(2001, 1, 1))).totalDays)
        assertEquals(365L, value(AgeEngine.compute(LocalDate.of(1999, 1, 1), LocalDate.of(2000, 1, 1))).totalDays)
        // 29 February is counted in the 2024 span.
        assertEquals(2L, value(AgeEngine.compute(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1))).totalDays)
        assertEquals(1L, value(AgeEngine.compute(LocalDate.of(2023, 2, 28), LocalDate.of(2023, 3, 1))).totalDays)
    }

    @Test
    fun leapDayBirthdayUsesMarchFirstInCommonYears() {
        val result = value(AgeEngine.compute(LocalDate.of(2000, 2, 29), LocalDate.of(2023, 1, 1)))
        assertEquals(LocalDate.of(2023, 3, 1), result.nextBirthday)
        assertEquals(59L, result.daysToNextBirthday)
        assertTrue(result.notes.any { it.contains("1 March") })
    }

    @Test
    fun birthdayInYearHandlesLeapDay() {
        val birth = LocalDate.of(2000, 2, 29)
        assertEquals(LocalDate.of(2023, 3, 1), AgeEngine.birthdayInYear(birth, 2023))
        assertEquals(LocalDate.of(2024, 2, 29), AgeEngine.birthdayInYear(birth, 2024))
        // The next birthday search rolls over to the following year.
        assertEquals(LocalDate.of(2024, 2, 29), AgeEngine.nextBirthday(birth, LocalDate.of(2023, 3, 2)))
    }

    @Test
    fun birthAfterReferenceIsRejected() {
        val message = error(AgeEngine.compute(LocalDate.of(2024, 5, 15), LocalDate.of(2024, 5, 14)))
        assertEquals("Birth date must be on or before the reference date", message)
    }

    @Test
    fun invalidCalendarDatesAreReported() {
        val feb30 = AgeEngine.dateOf(2023, 2, 30)
        assertTrue(feb30 is CalcResult.Err)
        assertEquals(ErrorKind.InvalidInput, (feb30 as CalcResult.Err).error.kind)
        assertTrue(feb30.error.message.contains("2023-02-30"))

        val month13 = AgeEngine.dateOf(2023, 13, 1)
        assertTrue(month13 is CalcResult.Err)

        val feb29Common = AgeEngine.dateOf(2023, 2, 29)
        assertTrue(feb29Common is CalcResult.Err)

        val feb29Leap = AgeEngine.dateOf(2024, 2, 29)
        assertTrue(feb29Leap is CalcResult.Ok)
        assertEquals(LocalDate.of(2024, 2, 29), (feb29Leap as CalcResult.Ok).value)
    }

    @Test
    fun dateTimeAgeRespectsTheTimeOfDay() {
        val beforeTime = value(
            AgeEngine.compute(
                LocalDateTime.of(1990, 5, 15, 12, 0),
                LocalDateTime.of(2024, 5, 14, 9, 0),
            ),
        )
        assertEquals(33, beforeTime.years)
        assertEquals(11, beforeTime.months)
        assertEquals(28, beforeTime.days)

        val afterTime = value(
            AgeEngine.compute(
                LocalDateTime.of(1990, 5, 15, 9, 0),
                LocalDateTime.of(2024, 5, 14, 12, 0),
            ),
        )
        assertEquals(29, afterTime.days)
    }

    @Test
    fun invalidTimeIsReported() {
        val bad = AgeEngine.timeOf(24, 0)
        assertTrue(bad is CalcResult.Err)
        assertTrue((bad as CalcResult.Err).error.message.contains("24:00"))
        assertTrue(AgeEngine.timeOf(23, 59) is CalcResult.Ok)
    }
}
