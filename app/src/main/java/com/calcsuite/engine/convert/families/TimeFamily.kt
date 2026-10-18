package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Time. Base unit: second (SI).
 *
 * The calendar units are convention-based averages, not exact conversions:
 * the mean Gregorian month is 365.2425 / 12 = 30.436875 days and the mean
 * Gregorian year is 365.2425 days (365 days + one leap day every 4 years,
 * minus three in every 400). Individual calendar months (28–31 days) and
 * calendar years (365 or 366 days) differ — use them only for estimates.
 */
val TimeFamily = UnitFamily(
    id = "time",
    name = "Time",
    baseUnitId = "s",
    units = listOf(
        linear("ns", "Nanoseconds", "ns", pow10(-9), "Sub-second"),
        linear("us", "Microseconds", "µs", pow10(-6), "Sub-second"),
        linear("ms", "Milliseconds", "ms", pow10(-3), "Sub-second"),
        linear("s", "Seconds", "s", bd("1"), "Common"),
        linear("min", "Minutes", "min", bd("60"), "Common"),
        linear("h", "Hours", "h", bd("3600"), "Common"),
        linear("d", "Days", "d", bd("86400"), "Common"),
        linear("wk", "Weeks", "wk", bd("604800"), "Common"),
        linear("mo", "Months (average)", "mo", bd("2629746"), "Calendar (average, convention-based)"),
        linear("yr", "Years (average)", "yr", bd("31556952"), "Calendar (average, convention-based)"),
    ),
    notes = listOf(
        "The month and year entries are averages: 1 month = 30.436875 days and 1 year = 365.2425 days (mean Gregorian).",
        "Actual calendar months have 28–31 days and calendar years 365 or 366 days — use the average only for estimates.",
        "1 day = 24 h = 1440 min exactly; 1 week = 7 days exactly.",
    ),
)
