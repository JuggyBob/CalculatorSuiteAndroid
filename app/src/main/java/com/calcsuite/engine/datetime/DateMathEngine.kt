package com.calcsuite.engine.datetime

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Calendar-aware addition and subtraction of date and time units.
 *
 * Two modes are produced for date-times:
 *
 * 1. **Calendar mode** (the main result) adds years/months/weeks/days to the
 *    local wall clock and clamps month arithmetic to the last valid day
 *    (31 January + 1 month → 29 February in a leap year, 28 February
 *    otherwise). Because it keeps the wall clock, one calendar day across a
 *    daylight-saving transition can be 23 or 25 hours of real time.
 * 2. **Fixed-duration mode** adds exact seconds to the timeline (a week is
 *    7 × 24 h, a day is 24 h). It is only defined when the shift contains no
 *    years or months, which have no fixed length. With a [ZoneId] the engine
 *    reports the fixed result and the real elapsed time of the calendar
 *    result, which differ exactly when a DST transition lies in between.
 */
object DateMathEngine {

    data class Shift(
        val years: Long = 0,
        val months: Long = 0,
        val weeks: Long = 0,
        val days: Long = 0,
        val hours: Long = 0,
        val minutes: Long = 0,
        val seconds: Long = 0,
    ) {
        val hasCalendarFields: Boolean get() = years != 0L || months != 0L
        val hasTimeFields: Boolean get() = hours != 0L || minutes != 0L || seconds != 0L
        val isZero: Boolean
            get() = years == 0L && months == 0L && weeks == 0L && days == 0L &&
                hours == 0L && minutes == 0L && seconds == 0L

        /** Fixed-length part as a duration (weeks = 7 days, days = 24 h). */
        fun fixedDuration(): Duration {
            val secs = seconds +
                minutes * 60L +
                hours * 3600L +
                days * 86_400L +
                weeks * 7L * 86_400L
            return Duration.ofSeconds(secs)
        }
    }

    data class Result(
        /** Calendar-mode result (wall-clock arithmetic, clamped where needed). */
        val result: LocalDateTime,
        val isDateTime: Boolean,
        /** True when year/month arithmetic was clamped to the end of a month. */
        val clamped: Boolean,
        /** Human-readable explanation of the clamp, when one happened. */
        val clampNote: String?,
        /** Exact duration of the fixed-length part, null when years/months are present. */
        val fixedDuration: Duration?,
        /** Wall-clock result of applying [fixedDuration] on the timeline, when a zone was given. */
        val fixedResult: LocalDateTime?,
        /** Real elapsed time of [result] in the supplied zone. */
        val elapsed: Duration?,
        val notes: List<String>,
    )

    /** Date-only mode: hour/minute/second units require a start time. */
    fun apply(start: LocalDate, shift: Shift, subtract: Boolean = false): CalcResult<Result> {
        if (shift.hasTimeFields) {
            return err(
                ErrorKind.InvalidInput,
                "Hour, minute and second units need a start time",
            )
        }
        return apply(start.atStartOfDay(), shift, subtract, zone = null, dateOnly = true)
    }

    /**
     * Date-time mode. Pass [zone] to enable the DST-aware fixed-duration
     * comparison (behind-the-scenes instant arithmetic).
     */
    fun apply(
        start: LocalDateTime,
        shift: Shift,
        subtract: Boolean = false,
        zone: ZoneId? = null,
    ): CalcResult<Result> = apply(start, shift, subtract, zone, dateOnly = false)

    private fun apply(
        start: LocalDateTime,
        shift: Shift,
        subtract: Boolean,
        zone: ZoneId?,
        dateOnly: Boolean,
    ): CalcResult<Result> {
        val sign = if (subtract) -1L else 1L

        val result = try {
            var d = start
            val startDay = start.dayOfMonth
            d = d.plusYears(sign * shift.years)
            d = d.plusMonths(sign * shift.months)
            val clampedNow = d.dayOfMonth != startDay
            d = d.plusWeeks(sign * shift.weeks)
            d = d.plusDays(sign * shift.days)
            d = d.plusHours(sign * shift.hours)
            d = d.plusMinutes(sign * shift.minutes)
            d = d.plusSeconds(sign * shift.seconds)
            d to clampedNow
        } catch (_: DateTimeException) {
            return err(ErrorKind.InvalidInput, "The requested shift is out of range")
        }

        val (calendar, clamped) = result
        val clampNote = if (clamped) {
            "Year/month arithmetic would land on a day that does not exist in the target month, " +
                "so the result is clamped to the last day of that month."
        } else {
            null
        }

        val fixedDuration = if (!shift.hasCalendarFields) {
            val d = shift.fixedDuration()
            if (subtract) d.negated() else d
        } else {
            null
        }

        val zonedStart = zone?.let { start.atZone(it) }
        val elapsed = if (zonedStart != null && !dateOnly) {
            Duration.between(zonedStart.toInstant(), calendar.atZone(zone).toInstant())
        } else {
            null
        }
        val fixedResult = if (zonedStart != null && fixedDuration != null) {
            zonedStart.toInstant().plus(fixedDuration).atZone(zone).toLocalDateTime()
        } else {
            null
        }

        val notes = buildList {
            add(
                "Calendar mode keeps the local wall clock: adding days moves to the same time of " +
                    "day, so a calendar day across a daylight-saving change can be 23 or 25 hours.",
            )
            if (fixedDuration != null) {
                add(
                    "Fixed-duration mode adds exact seconds on the timeline (a week is 7 × 24 h, a " +
                        "day is 24 h); the two modes give the same result unless a daylight-saving " +
                        "transition falls inside the interval.",
                )
            } else {
                add(
                    "Fixed-duration mode is not defined for year/month shifts because calendar months " +
                        "have no fixed length; only calendar mode applies.",
                )
            }
            if (zone != null && elapsed != null && fixedResult != null && fixedDuration != null &&
                fixedResult != calendar
            ) {
                add(
                    "In $zone the calendar result is ${formatDuration(elapsed)} of real time, while the " +
                        "fixed-duration result is ${formatDuration(fixedDuration)} after the start " +
                        "(DST transition inside the interval).",
                )
            } else if (zone != null && elapsed != null && fixedDuration != null) {
                add("In $zone the calendar result and the fixed-duration result coincide (no DST transition in the interval).")
            }
            if (shift.isZero) {
                add("The shift is zero, so the result equals the start.")
            }
        }

        return ok(
            Result(
                result = calendar,
                isDateTime = !dateOnly,
                clamped = clamped,
                clampNote = clampNote,
                fixedDuration = fixedDuration,
                fixedResult = fixedResult,
                elapsed = elapsed,
                notes = notes,
            ),
        )
    }

    private fun formatDuration(d: Duration): String {
        val totalMinutes = d.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val seconds = d.minusMinutes(totalMinutes).seconds
        return buildString {
            if (hours != 0L) append(hours).append(" h ")
            if (minutes != 0L) append(minutes).append(" min ")
            append(seconds).append(" s")
        }.trim()
    }
}
