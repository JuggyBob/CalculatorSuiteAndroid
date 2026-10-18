package com.calcsuite.engine.datetime

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ok
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

/**
 * Difference between two dates or date-times.
 *
 * Two distinct quantities are reported and labelled separately:
 * - the **calendar interval** (years / months / days) counts whole calendar
 *   units between the two dates, matching how people speak about dates;
 * - the **total elapsed** figures (days / hours / minutes / seconds) count the
 *   exact time actually between the two moments.
 *
 * They differ by design: 1 January 23:00 → 2 January 01:00 is a calendar
 * interval of 1 day but only 2 hours (0 complete days) of elapsed time.
 *
 * Order is handled by a signed flag rather than by reordering the inputs:
 * when the end precedes the start, `negative` is true and every magnitude is
 * the absolute difference.
 *
 * Business days count Monday–Friday calendar dates from the start (inclusive)
 * to the end (exclusive). There is **no holiday calendar**: public and
 * company holidays are not modelled, so the count is an upper bound.
 */
object IntervalEngine {

    data class Result(
        /** True when the end precedes the start (the signed difference is negative). */
        val negative: Boolean,
        /** True when both moments are identical. */
        val sameInstant: Boolean,
        // Calendar interval (absolute magnitudes).
        val years: Int,
        val months: Int,
        val days: Int,
        // Total elapsed (absolute magnitudes).
        val totalDays: Long,
        val totalHours: Long,
        val totalMinutes: Long,
        val totalSeconds: Long,
        val weeks: Long,
        val remainingDays: Long,
        val businessDays: Long,
        val notes: List<String>,
    )

    /** Date-only interval. */
    fun between(start: LocalDate, end: LocalDate): CalcResult<Result> =
        between(start.atStartOfDay(), end.atStartOfDay())

    /** Date-time interval (wall-clock duration; no time zone is applied). */
    fun between(start: LocalDateTime, end: LocalDateTime): CalcResult<Result> {
        val negative = end.isBefore(start)
        val same = end == start
        val from = if (negative) end else start
        val to = if (negative) start else end

        val period = Period.between(from.toLocalDate(), to.toLocalDate())
        val duration = Duration.between(from, to)

        val totalDays = duration.toDays()
        val businessDays = businessDaysBetween(from.toLocalDate(), to.toLocalDate())

        val notes = buildList {
            add(
                "Calendar interval (years/months/days) counts whole calendar units; total elapsed " +
                    "days/hours/minutes/seconds count the exact time between the two moments.",
            )
            add(
                "Weeks and days split the total elapsed days (weeks = days / 7, remainder = days % 7).",
            )
            add(
                "Business days count Monday–Friday dates from the start (inclusive) to the end " +
                    "(exclusive), with no holiday calendar; public holidays are not subtracted.",
            )
            if (same) {
                add("The two moments are identical, so every difference is zero.")
            }
            val timesInvolved = from.toLocalTime() != java.time.LocalTime.MIDNIGHT ||
                to.toLocalTime() != java.time.LocalTime.MIDNIGHT
            if (timesInvolved && period.days.toLong() != totalDays) {
                add(
                    "The calendar interval and the total elapsed days differ here because the " +
                        "times of day are not both midnight.",
                )
            }
            if (period.years != 0 || period.months != 0) {
                add(
                    "Months are calendar months (28–31 days), so the year/month part of the " +
                        "calendar interval does not add up to a whole number of fixed-length months.",
                )
            }
        }

        return ok(
            Result(
                negative = negative,
                sameInstant = same,
                years = period.years,
                months = period.months,
                days = period.days,
                totalDays = totalDays,
                totalHours = duration.toHours(),
                totalMinutes = duration.toMinutes(),
                totalSeconds = duration.seconds,
                weeks = totalDays / 7,
                remainingDays = totalDays % 7,
                businessDays = businessDays,
                notes = notes,
            ),
        )
    }

    /** Weekend-aware working days from [start] (inclusive) to [end] (exclusive). */
    fun businessDaysBetween(start: LocalDate, end: LocalDate): Long {
        if (!end.isAfter(start)) return 0L
        var count = 0L
        var day = start
        while (day.isBefore(end)) {
            val dow: DayOfWeek = day.dayOfWeek
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) count++
            day = day.plusDays(1)
        }
        return count
    }
}
