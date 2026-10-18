package com.calcsuite.engine.datetime

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.Year
import java.time.temporal.ChronoUnit

/**
 * Age between a birth date and a reference date.
 *
 * The completed years/months/days are a calendar period (the same units a
 * birthday count uses), while the totals are exact elapsed counts. For
 * date-times the reference time is respected: if the reference time of day is
 * earlier than the birth time of day, the last day is not yet complete and the
 * period is measured one day earlier.
 *
 * Leap-year convention: someone born on 29 February has no 29 February in a
 * common year; this engine counts their birthday on **1 March** of common
 * years (see [birthdayInYear]), which is the convention used by many civil
 * registries. The convention is repeated in the returned notes.
 */
object AgeEngine {

    data class Result(
        val years: Int,
        val months: Int,
        val days: Int,
        /** Exact elapsed days between birth and reference. */
        val totalDays: Long,
        /** Completed weeks (totalDays / 7). */
        val totalWeeks: Long,
        /** Completed calendar months. */
        val totalMonths: Long,
        val daysToNextBirthday: Long,
        val nextBirthday: LocalDate,
        val weekdayOfBirth: DayOfWeek,
        val notes: List<String>,
    )

    /** Date-only age. */
    fun compute(birth: LocalDate, reference: LocalDate): CalcResult<Result> =
        compute(birth.atStartOfDay(), reference.atStartOfDay())

    /**
     * Date-time age: the period is measured up to the last completed day when
     * the reference time of day precedes the birth time of day.
     */
    fun compute(birth: LocalDateTime, reference: LocalDateTime): CalcResult<Result> {
        if (reference.isBefore(birth)) {
            return err(ErrorKind.InvalidInput, "Birth date must be on or before the reference date")
        }

        val effectiveReferenceDate =
            if (reference.toLocalTime().isBefore(birth.toLocalTime())) reference.toLocalDate().minusDays(1)
            else reference.toLocalDate()

        val period = Period.between(birth.toLocalDate(), effectiveReferenceDate)
        val totalDays = ChronoUnit.DAYS.between(birth.toLocalDate(), effectiveReferenceDate)
        val totalMonths = ChronoUnit.MONTHS.between(birth.toLocalDate(), effectiveReferenceDate)

        val next = nextBirthday(birth.toLocalDate(), reference.toLocalDate())
        val daysToNext = ChronoUnit.DAYS.between(reference.toLocalDate(), next)

        val notes = buildList {
            add("Years, months and days are a calendar period (completed units), not a sum of fixed-length months.")
            add("Total days/weeks/months are exact elapsed counts; calendar months vary from 28 to 31 days.")
            if (birth.toLocalDate().monthValue == 2 && birth.toLocalDate().dayOfMonth == 29) {
                add(
                    "Born on 29 February: in common years the next birthday is counted on 1 March " +
                        "(this engine's documented convention).",
                )
            }
            if (next.year > reference.toLocalDate().year) {
                add("The next birthday falls in ${next.year}.")
            }
        }
        return ok(
            Result(
                years = period.years,
                months = period.months,
                days = period.days,
                totalDays = totalDays,
                totalWeeks = totalDays / 7,
                totalMonths = totalMonths,
                daysToNextBirthday = daysToNext,
                nextBirthday = next,
                weekdayOfBirth = birth.toLocalDate().dayOfWeek,
                notes = notes,
            ),
        )
    }

    /**
     * The birth date's celebration day in [year]: the same month/day, or
     * 1 March when the birth date is 29 February and [year] is a common year.
     */
    fun birthdayInYear(birth: LocalDate, year: Int): LocalDate {
        if (birth.monthValue == 2 && birth.dayOfMonth == 29 && !Year.isLeap(year.toLong())) {
            return LocalDate.of(year, 3, 1)
        }
        return LocalDate.of(year, birth.monthValue, birth.dayOfMonth)
    }

    /** Next occurrence of the birthday on or after [reference]. */
    fun nextBirthday(birth: LocalDate, reference: LocalDate): LocalDate {
        val candidate = birthdayInYear(birth, reference.year)
        return if (candidate.isBefore(reference)) birthdayInYear(birth, reference.year + 1) else candidate
    }

    /**
     * Builds a calendar date from user-entered parts, reporting impossible
     * dates (29 February in a common year, 30 February, month 13, …) instead
     * of throwing.
     */
    fun dateOf(year: Int, month: Int, day: Int): CalcResult<LocalDate> = try {
        ok(LocalDate.of(year, month, day))
    } catch (_: DateTimeException) {
        err(ErrorKind.InvalidInput, "%04d-%02d-%02d is not a valid calendar date".format(year, month, day))
    }

    /** Builds a time from user-entered parts, reporting invalid values. */
    fun timeOf(hour: Int, minute: Int, second: Int = 0): CalcResult<LocalTime> = try {
        ok(LocalTime.of(hour, minute, second))
    } catch (_: DateTimeException) {
        err(ErrorKind.InvalidInput, "%02d:%02d:%02d is not a valid time of day".format(hour, minute, second))
    }
}
