package com.calcsuite.ui.tools.datetime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.err
import com.calcsuite.engine.datetime.IntervalEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Duration between two dates or date-times.
 *
 * Dates are three validated numeric fields ([DateTripleField]). The screen
 * keeps the engine's distinction between the calendar interval (years,
 * months, days) and the total elapsed time (days, hours, minutes, seconds),
 * and shows the business-day count with its documented convention
 * (Monday–Friday dates, start inclusive, end exclusive, no holiday calendar).
 */
@Composable
fun IntervalToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()

    var startDay by rememberSaveable { mutableStateOf("") }
    var startMonth by rememberSaveable { mutableStateOf("") }
    var startYear by rememberSaveable { mutableStateOf("") }
    var endDay by rememberSaveable { mutableStateOf("") }
    var endMonth by rememberSaveable { mutableStateOf("") }
    var endYear by rememberSaveable { mutableStateOf("") }
    var includeTime by rememberSaveable { mutableStateOf(false) }
    var startHour by rememberSaveable { mutableStateOf("0") }
    var startMinute by rememberSaveable { mutableStateOf("0") }
    var endHour by rememberSaveable { mutableStateOf("0") }
    var endMinute by rememberSaveable { mutableStateOf("0") }

    val startDate = parseDateFields(startDay, startMonth, startYear, "Start date")
    val endDate = parseDateFields(endDay, endMonth, endYear, "End date")
    val startTime = if (includeTime) parseTimeFields(startHour, startMinute, "Start time") else null
    val endTime = if (includeTime) parseTimeFields(endHour, endMinute, "End time") else null

    val computed: CalcResult<IntervalEngine.Result>? = when {
        startDate == null || endDate == null -> null
        includeTime && (startTime == null || endTime == null) -> null
        startDate is CalcResult.Err -> err(startDate.error.kind, startDate.error.message)
        endDate is CalcResult.Err -> err(endDate.error.kind, endDate.error.message)
        includeTime && startTime is CalcResult.Err -> err(startTime.error.kind, startTime.error.message)
        includeTime && endTime is CalcResult.Err -> err(endTime.error.kind, endTime.error.message)
        else -> {
            val start = (startDate as CalcResult.Ok<LocalDate>).value
            val end = (endDate as CalcResult.Ok<LocalDate>).value
            if (includeTime) {
                val st = (startTime as CalcResult.Ok<LocalTime>).value
                val et = (endTime as CalcResult.Ok<LocalTime>).value
                IntervalEngine.between(start.atTime(st), end.atTime(et))
            } else {
                IntervalEngine.between(start, end)
            }
        }
    }
    val result = (computed as? CalcResult.Ok)?.value
    val error = (computed as? CalcResult.Err)?.error?.message

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        DateTripleField(
            label = "Start",
            day = startDay,
            month = startMonth,
            year = startYear,
            onDay = { startDay = it },
            onMonth = { startMonth = it },
            onYear = { startYear = it },
            dayError = intFieldError(startDay, "Day", 1, 31),
            monthError = intFieldError(startMonth, "Month", 1, 12),
            yearError = intFieldError(startYear, "Year", 1, 9999),
        )
        DateTripleField(
            label = "End",
            day = endDay,
            month = endMonth,
            year = endYear,
            onDay = { endDay = it },
            onMonth = { endMonth = it },
            onYear = { endYear = it },
            dayError = intFieldError(endDay, "Day", 1, 31),
            monthError = intFieldError(endMonth, "Month", 1, 12),
            yearError = intFieldError(endYear, "Year", 1, 9999),
        )

        ToggleRow(
            label = "Include time of day",
            checked = includeTime,
            onCheckedChange = { includeTime = it },
            description = "When off, both moments are treated as midnight",
        )
        if (includeTime) {
            TimePairField(
                label = "Start time",
                hour = startHour,
                minute = startMinute,
                onHour = { startHour = it },
                onMinute = { startMinute = it },
                hourError = intFieldError(startHour, "Hour", 0, 23),
                minuteError = intFieldError(startMinute, "Minute", 0, 59),
            )
            TimePairField(
                label = "End time",
                hour = endHour,
                minute = endMinute,
                onHour = { endHour = it },
                onMinute = { endMinute = it },
                hourError = intFieldError(endHour, "Hour", 0, 23),
                minuteError = intFieldError(endMinute, "Minute", 0, 59),
            )
        }

        ResultCard(
            label = "Signed difference (calendar interval)",
            value = result?.let { r ->
                (if (r.negative) "−" else "") + "${r.years} y · ${r.months} m · ${r.days} d"
            },
            caption = result?.let { r ->
                when {
                    r.sameInstant -> "The two moments are identical"
                    r.negative -> "Negative: the end is before the start"
                    else -> "Positive: the end is after the start"
                }
            },
            error = error,
            onSave = result?.let { r ->
                {
                    val start = "$startYear-$startMonth-$startDay" + if (includeTime) " $startHour:$startMinute" else ""
                    val end = "$endYear-$endMonth-$endDay" + if (includeTime) " $endHour:$endMinute" else ""
                    args.onRecord(
                        "Interval: $start → $end",
                        "${if (r.negative) "−" else ""}${r.years} y ${r.months} m ${r.days} d " +
                            "(${fmt.num(BigDecimal.valueOf(r.totalDays))} days)",
                    )
                }
            },
        )

        if (result != null) {
            ResultRow(label = "Total elapsed days", value = fmt.num(BigDecimal.valueOf(result.totalDays), precision = maxOf(fmt.precision, 10)))
            ResultRow(label = "Total elapsed hours", value = fmt.num(BigDecimal.valueOf(result.totalHours), precision = maxOf(fmt.precision, 10)))
            ResultRow(label = "Total elapsed minutes", value = fmt.num(BigDecimal.valueOf(result.totalMinutes), precision = maxOf(fmt.precision, 10)))
            ResultRow(label = "Total elapsed seconds", value = fmt.num(BigDecimal.valueOf(result.totalSeconds), precision = maxOf(fmt.precision, 10)))
            ResultRow(
                label = "Weeks + days (elapsed)",
                value = "${result.weeks} weeks ${result.remainingDays} days",
            )
            ResultRow(
                label = "Business days (Mon–Fri)",
                value = fmt.num(BigDecimal.valueOf(result.businessDays), precision = maxOf(fmt.precision, 10)),
                emphasis = true,
            )
        }

        FormulaCard(
            formula = "calendar interval = whole years / months / days between the dates\n" +
                "total elapsed = exact days / hours / minutes / seconds",
            explanation = "The two lines answer different questions: the calendar interval matches " +
                "how people count dates (2024-01-01 → 2024-03-01 is 2 months), while the total " +
                "elapsed figures count the exact time between the two moments. Business days " +
                "count Monday–Friday dates from the start (inclusive) to the end (exclusive); " +
                "there is no holiday calendar, so public holidays are not subtracted.",
        )

        result?.notes?.forEach { InfoNote(it) }
        if (result == null) {
            InfoNote("Enter a start and an end date to see the difference.")
        }
    }
}
