package com.calcsuite.ui.tools.datetime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.err
import com.calcsuite.engine.datetime.DateMathEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Adds or subtracts calendar units from a start date (or date-time).
 *
 * Conventions documented on-screen: dates are three validated numeric fields
 * ([DateTripleField]); month arithmetic is calendar-aware and clamps to the
 * last valid day (31 Jan + 1 month → 29 Feb in a leap year); when a start time
 * is included, the result card shows the calendar result plus, where it
 * applies, the DST-safe fixed-duration result for the device time zone.
 */
@Composable
fun DateMathToolScreen(args: ToolArgs) {
    var day by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var includeTime by rememberSaveable { mutableStateOf(false) }
    var hour by rememberSaveable { mutableStateOf("12") }
    var minute by rememberSaveable { mutableStateOf("0") }
    var add by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("days") }

    val startDate = parseDateFields(day, month, year, "Start date")
    val startTime = if (includeTime) parseTimeFields(hour, minute, "Start time") else null
    val amountValue = parseWholeNumber(amount)?.takeIf { it >= 0 && it <= 1_000_000 }
    val shift: DateMathEngine.Shift? = amountValue?.let { a ->
        val v = a.toLong()
        when (unit) {
            "years" -> DateMathEngine.Shift(years = v)
            "months" -> DateMathEngine.Shift(months = v)
            "weeks" -> DateMathEngine.Shift(weeks = v)
            "days" -> DateMathEngine.Shift(days = v)
            "hours" -> DateMathEngine.Shift(hours = v)
            "minutes" -> DateMathEngine.Shift(minutes = v)
            else -> DateMathEngine.Shift(seconds = v)
        }
    }

    val computed: CalcResult<DateMathEngine.Result>? = when {
        startDate == null || shift == null -> null
        includeTime && startTime == null -> null
        startDate is CalcResult.Err -> err(startDate.error.kind, startDate.error.message)
        includeTime && startTime is CalcResult.Err -> err(startTime.error.kind, startTime.error.message)
        else -> {
            val start = (startDate as CalcResult.Ok<LocalDate>).value
            if (includeTime) {
                val time = (startTime as CalcResult.Ok<LocalTime>).value
                DateMathEngine.apply(
                    start.atTime(time),
                    shift,
                    subtract = !add,
                    zone = ZoneId.systemDefault(),
                )
            } else {
                DateMathEngine.apply(start, shift, subtract = !add)
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
            label = "Start date",
            day = day,
            month = month,
            year = year,
            onDay = { day = it },
            onMonth = { month = it },
            onYear = { year = it },
            dayError = intFieldError(day, "Day", 1, 31),
            monthError = intFieldError(month, "Month", 1, 12),
            yearError = intFieldError(year, "Year", 1, 9999),
        )

        ToggleRow(
            label = "Include a start time",
            checked = includeTime,
            onCheckedChange = { includeTime = it },
            description = "Needed for hour, minute and second units and for the DST-safe comparison",
        )
        if (includeTime) {
            TimePairField(
                label = "Start time",
                hour = hour,
                minute = minute,
                onHour = { hour = it },
                onMinute = { minute = it },
                hourError = intFieldError(hour, "Hour", 0, 23),
                minuteError = intFieldError(minute, "Minute", 0, 59),
            )
        }

        SegmentedChoice(
            options = listOf(true to "Add", false to "Subtract"),
            selected = add,
            onSelect = { add = it },
            label = "Operation",
        )

        NumberField(
            label = "Amount",
            value = amount,
            onValueChange = { amount = it },
            integerOnly = true,
            placeholder = "1",
            error = when {
                amount.isBlank() -> null
                parseWholeNumber(amount) == null -> "Enter a whole number"
                amountValue == null -> "Amount must be between 0 and 1000000"
                else -> null
            },
        )

        SelectField(
            label = "Unit",
            options = unitOptions,
            selected = unit,
            onSelect = {
                unit = it
                if (it in timeUnits) includeTime = true
            },
        )

        ResultCard(
            label = "Result date",
            value = result?.let { r ->
                isoDate(r.result.toLocalDate()) +
                    if (r.isDateTime) " ${r.result.toLocalTime()}" else ""
            },
            caption = result?.let { r ->
                when {
                    r.clamped -> "Clamped to the last day of the month"
                    else -> "Calendar arithmetic"
                }
            },
            error = error,
            onSave = result?.let { r ->
                {
                    val op = if (add) "+" else "−"
                    val start = "$year-$month-$day" + if (includeTime) " $hour:$minute" else ""
                    args.onRecord(
                        "Date math: $start $op $amount $unit",
                        isoDate(r.result.toLocalDate()) +
                            if (r.isDateTime) " ${r.result.toLocalTime()}" else "",
                    )
                }
            },
        )

        if (result != null) {
            ResultRow(
                label = "Day of week",
                value = weekdayName(result.result.toLocalDate()),
            )
            result.clampNote?.let { note ->
                InfoNote(note)
            }
            if (result.fixedResult != null) {
                ResultRow(
                    label = "Fixed-duration result (${ZoneId.systemDefault().id})",
                    value = isoDate(result.fixedResult.toLocalDate()) + " ${result.fixedResult.toLocalTime()}",
                )
            }
            result.elapsed?.let { elapsed ->
                ResultRow(
                    label = "Real elapsed time of the calendar result",
                    value = formatDuration(elapsed),
                )
            }
        }

        FormulaCard(
            formula = "calendar: add years/months first, then weeks/days/time\nday/month clamp: min(day, last day of target month)",
            explanation = "Months and years use calendar arithmetic, so 31 January + 1 month is " +
                "29 February in a leap year and 28 February otherwise (the day is clamped). " +
                "Weeks, days and time are then added exactly. When a start time is given, the " +
                "fixed-duration result adds exact seconds on the timeline: the two differ only " +
                "when a daylight-saving transition lies inside the interval.",
        )

        result?.notes?.forEach { InfoNote(it) }
        if (result == null) {
            InfoNote("Enter a start date and an amount to see the result.")
        }
    }
}

private val unitOptions = listOf(
    Option("years", "Years"),
    Option("months", "Months"),
    Option("weeks", "Weeks"),
    Option("days", "Days"),
    Option("hours", "Hours"),
    Option("minutes", "Minutes"),
    Option("seconds", "Seconds"),
)

private val timeUnits = setOf("hours", "minutes", "seconds")

private fun formatDuration(duration: Duration): String {
    val negative = duration.isNegative
    val abs = if (negative) duration.negated() else duration
    val hours = abs.toHours()
    val minutes = abs.toMinutes() % 60
    val seconds = abs.seconds % 60
    return buildString {
        if (negative) append("−")
        if (hours != 0L) append(hours).append(" h ")
        if (minutes != 0L) append(minutes).append(" min ")
        append(seconds).append(" s")
    }.trim()
}
