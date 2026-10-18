package com.calcsuite.engine.datetime

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DateMathEngineTest {

    private fun value(result: CalcResult<DateMathEngine.Result>): DateMathEngine.Result {
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    private fun error(result: CalcResult<DateMathEngine.Result>): String {
        assertTrue("Expected failure, was $result", result is CalcResult.Err)
        return (result as CalcResult.Err).error.message
    }

    @Test
    fun januaryPlusOneMonthClampsToLeapFebruary() {
        val result = value(
            DateMathEngine.apply(LocalDate.of(2024, 1, 31), DateMathEngine.Shift(months = 1)),
        )
        assertEquals(LocalDate.of(2024, 2, 29), result.result.toLocalDate())
        assertTrue(result.clamped)
        assertTrue(result.clampNote != null)
    }

    @Test
    fun januaryPlusOneMonthClampsToCommonFebruary() {
        val result = value(
            DateMathEngine.apply(LocalDate.of(2023, 1, 31), DateMathEngine.Shift(months = 1)),
        )
        assertEquals(LocalDate.of(2023, 2, 28), result.result.toLocalDate())
        assertTrue(result.clamped)
        assertTrue(result.clampNote!!.contains("clamped"))
    }

    @Test
    fun leapDayPlusOneYearClampsToFebruary28() {
        val result = value(
            DateMathEngine.apply(LocalDate.of(2024, 2, 29), DateMathEngine.Shift(years = 1)),
        )
        assertEquals(LocalDate.of(2025, 2, 28), result.result.toLocalDate())
        assertTrue(result.clamped)
    }

    @Test
    fun leapDayPlusFourYearsStaysOnFebruary29() {
        val result = value(
            DateMathEngine.apply(LocalDate.of(2024, 2, 29), DateMathEngine.Shift(years = 4)),
        )
        assertEquals(LocalDate.of(2028, 2, 29), result.result.toLocalDate())
        assertFalse(result.clamped)
        assertNull(result.clampNote)
    }

    @Test
    fun weeksAndDaysArithmetic() {
        val plusWeek = value(
            DateMathEngine.apply(LocalDate.of(2024, 1, 1), DateMathEngine.Shift(weeks = 1)),
        )
        assertEquals(LocalDate.of(2024, 1, 8), plusWeek.result.toLocalDate())

        val minusDays = value(
            DateMathEngine.apply(LocalDate.of(2024, 1, 1), DateMathEngine.Shift(days = 3), subtract = true),
        )
        assertEquals(LocalDate.of(2023, 12, 29), minusDays.result.toLocalDate())
    }

    @Test
    fun combinedFieldsApplyYearsMonthsThenWeeksDays() {
        val result = value(
            DateMathEngine.apply(
                LocalDate.of(2023, 12, 31),
                DateMathEngine.Shift(years = 1, months = 1, weeks = 1, days = 2),
            ),
        )
        assertEquals(LocalDate.of(2025, 2, 9), result.result.toLocalDate())
        assertFalse(result.clamped)
    }

    @Test
    fun minutesOnADateTime() {
        val result = value(
            DateMathEngine.apply(
                LocalDateTime.of(2024, 1, 1, 10, 0),
                DateMathEngine.Shift(minutes = 90),
            ),
        )
        assertEquals(LocalDateTime.of(2024, 1, 1, 11, 30), result.result)
        assertEquals(Duration.ofMinutes(90), result.fixedDuration)
    }

    @Test
    fun dateOnlyModeRejectsTimeUnits() {
        val message = error(
            DateMathEngine.apply(LocalDate.of(2024, 1, 1), DateMathEngine.Shift(hours = 1)),
        )
        assertEquals("Hour, minute and second units need a start time", message)
    }

    @Test
    fun yearMonthShiftsHaveNoFixedDuration() {
        val result = value(
            DateMathEngine.apply(LocalDate.of(2024, 1, 31), DateMathEngine.Shift(months = 1)),
        )
        assertNull(result.fixedDuration)
        assertTrue(result.notes.any { it.contains("no fixed length") })
    }

    @Test
    fun fixedDurationIsDefinedForDayShifts() {
        val result = value(
            DateMathEngine.apply(
                LocalDateTime.of(2024, 1, 1, 8, 0),
                DateMathEngine.Shift(days = 1),
            ),
        )
        assertEquals(Duration.ofDays(1), result.fixedDuration)
        assertTrue(result.notes.any { it.contains("Fixed-duration mode adds exact seconds") })
    }

    @Test
    fun daylightSavingMakesCalendarAndFixedResultsDiffer() {
        val zone = ZoneId.of("Europe/London")
        val result = value(
            DateMathEngine.apply(
                LocalDateTime.of(2024, 3, 30, 12, 0),
                DateMathEngine.Shift(days = 1),
                zone = zone,
            ),
        )
        // Calendar keeps the wall clock; the timeline gains 24 h (DST started 31 March 2024).
        assertEquals(LocalDateTime.of(2024, 3, 31, 12, 0), result.result)
        assertEquals(LocalDateTime.of(2024, 3, 31, 13, 0), result.fixedResult)
        assertEquals(Duration.ofHours(23), result.elapsed)
        assertEquals(Duration.ofDays(1), result.fixedDuration)
        assertTrue(result.notes.any { it.contains("DST") })
    }

    @Test
    fun subtractIsTheNegatedShift() {
        val result = value(
            DateMathEngine.apply(
                LocalDateTime.of(2024, 1, 1, 0, 0),
                DateMathEngine.Shift(days = 1),
                subtract = true,
            ),
        )
        assertEquals(LocalDateTime.of(2023, 12, 31, 0, 0), result.result)
        assertEquals(Duration.ofDays(-1), result.fixedDuration)
    }

    @Test
    fun zeroShiftReturnsTheStartWithANote() {
        val start = LocalDateTime.of(2024, 5, 5, 9, 30)
        val result = value(DateMathEngine.apply(start, DateMathEngine.Shift()))
        assertEquals(start, result.result)
        assertTrue(result.notes.any { it.contains("zero") })
    }

    @Test
    fun outOfRangeShiftIsReported() {
        val message = error(
            DateMathEngine.apply(LocalDate.of(2024, 1, 1), DateMathEngine.Shift(years = 1_000_000_000)),
        )
        assertEquals("The requested shift is out of range", message)
    }
}
