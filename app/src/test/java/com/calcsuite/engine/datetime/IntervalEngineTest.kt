package com.calcsuite.engine.datetime

import com.calcsuite.core.CalcResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class IntervalEngineTest {

    private fun value(result: CalcResult<IntervalEngine.Result>): IntervalEngine.Result {
        assertTrue("Expected success, was $result", result is CalcResult.Ok)
        return (result as CalcResult.Ok).value
    }

    @Test
    fun briefExampleJanuaryToMarch() {
        val result = value(IntervalEngine.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)))
        // Calendar arithmetic: 1 Jan → 1 Mar is exactly 2 calendar months.
        assertEquals(0, result.years)
        assertEquals(2, result.months)
        assertEquals(0, result.days)
        // Total elapsed: 31 (Jan) + 29 (leap Feb) = 60 days.
        assertEquals(60L, result.totalDays)
        assertEquals(8L, result.weeks)
        assertEquals(4L, result.remainingDays)
        assertFalse(result.negative)
    }

    @Test
    fun identicalDatesGiveZeroWithANote() {
        val result = value(IntervalEngine.between(LocalDate.of(2024, 5, 5), LocalDate.of(2024, 5, 5)))
        assertTrue(result.sameInstant)
        assertEquals(0, result.years)
        assertEquals(0, result.months)
        assertEquals(0, result.days)
        assertEquals(0L, result.totalDays)
        assertEquals(0L, result.totalHours)
        assertEquals(0L, result.totalMinutes)
        assertEquals(0L, result.totalSeconds)
        assertEquals(0L, result.businessDays)
        assertTrue(result.notes.any { it.contains("identical") })
    }

    @Test
    fun reversedOrderIsSignalledNotReorderedSilently() {
        val result = value(IntervalEngine.between(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 1, 1)))
        assertTrue(result.negative)
        assertFalse(result.sameInstant)
        assertEquals(2, result.months)
        assertEquals(60L, result.totalDays)
    }

    @Test
    fun businessDaysBetweenMondaysCountsTheFiveWeekdays() {
        val result = value(IntervalEngine.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 8)))
        assertEquals(5L, result.businessDays)
        assertEquals(5L, IntervalEngine.businessDaysBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 8)))
    }

    @Test
    fun businessDaysAreZeroForEmptyOrReversedSpans() {
        assertEquals(0L, IntervalEngine.businessDaysBetween(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 1)))
        assertEquals(0L, IntervalEngine.businessDaysBetween(LocalDate.of(2024, 1, 8), LocalDate.of(2024, 1, 8)))
    }

    @Test
    fun businessDaysSkipWeekendsAcrossALongerSpan() {
        // 1 Jan 2024 (Mon) → 1 Mar 2024 (Fri): 23 weekdays in Jan + 21 in Feb.
        val result = value(IntervalEngine.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 1)))
        assertEquals(44L, result.businessDays)
    }

    @Test
    fun datetimeIntervalSeparatesCalendarFromElapsed() {
        val result = value(
            IntervalEngine.between(
                LocalDateTime.of(2024, 1, 1, 23, 0),
                LocalDateTime.of(2024, 1, 2, 1, 0),
            ),
        )
        // Calendar interval: one calendar day.
        assertEquals(1, result.days)
        // Total elapsed: only two hours, zero complete days.
        assertEquals(0L, result.totalDays)
        assertEquals(2L, result.totalHours)
        assertEquals(120L, result.totalMinutes)
        assertEquals(7200L, result.totalSeconds)
        assertTrue(result.notes.any { it.contains("times of day") })
    }

    @Test
    fun leapDaysAreCountedCorrectly() {
        assertEquals(2L, value(IntervalEngine.between(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1))).totalDays)
        assertEquals(1L, value(IntervalEngine.between(LocalDate.of(2023, 2, 28), LocalDate.of(2023, 3, 1))).totalDays)
        assertEquals(366L, value(IntervalEngine.between(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1))).totalDays)
    }

    @Test
    fun monthsAcrossAYearBoundary() {
        val result = value(IntervalEngine.between(LocalDate.of(2023, 11, 15), LocalDate.of(2024, 2, 15)))
        assertEquals(0, result.years)
        assertEquals(3, result.months)
        assertEquals(0, result.days)
        assertEquals(92L, result.totalDays)
        assertTrue(result.notes.any { it.contains("Months are calendar months") })
    }

    @Test
    fun weeksAndDaysSplitIsConsistent() {
        val result = value(IntervalEngine.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10)))
        assertEquals(9L, result.totalDays)
        assertEquals(1L, result.weeks)
        assertEquals(2L, result.remainingDays)
    }

    @Test
    fun notesDocumentBusinessDayConvention() {
        val result = value(IntervalEngine.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 8)))
        assertTrue(result.notes.any { it.contains("no holiday calendar") })
        assertTrue(result.notes.any { it.contains("Calendar interval") })
    }
}
