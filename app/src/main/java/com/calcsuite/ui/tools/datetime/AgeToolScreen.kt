package com.calcsuite.ui.tools.datetime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.calcsuite.core.CalcResult
import com.calcsuite.core.err
import com.calcsuite.engine.datetime.AgeEngine
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
 * Exact age between a birth date and a reference date (default: today, via
 * the "Use today" button). Dates are entered as three validated numeric
 * fields (see [DateTripleField] for the batch convention); an optional time
 * of day makes the calculation date-time aware.
 */
@Composable
fun AgeToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val today = remember { LocalDate.now() }

    var birthDay by rememberSaveable { mutableStateOf("") }
    var birthMonth by rememberSaveable { mutableStateOf("") }
    var birthYear by rememberSaveable { mutableStateOf("") }
    var refDay by rememberSaveable { mutableStateOf(today.dayOfMonth.toString()) }
    var refMonth by rememberSaveable { mutableStateOf(today.monthValue.toString()) }
    var refYear by rememberSaveable { mutableStateOf(today.year.toString()) }
    var includeTime by rememberSaveable { mutableStateOf(false) }
    var birthHour by rememberSaveable { mutableStateOf("0") }
    var birthMinute by rememberSaveable { mutableStateOf("0") }
    var refHour by rememberSaveable { mutableStateOf("0") }
    var refMinute by rememberSaveable { mutableStateOf("0") }

    val birthDate = parseDateFields(birthDay, birthMonth, birthYear, "Birth date")
    val refDate = parseDateFields(refDay, refMonth, refYear, "Reference date")
    val birthTime = if (includeTime) parseTimeFields(birthHour, birthMinute, "Birth time") else null
    val refTime = if (includeTime) parseTimeFields(refHour, refMinute, "Reference time") else null

    val computed: CalcResult<AgeEngine.Result>? = when {
        birthDate == null || refDate == null -> null
        includeTime && (birthTime == null || refTime == null) -> null
        birthDate is CalcResult.Err -> err(birthDate.error.kind, birthDate.error.message)
        refDate is CalcResult.Err -> err(refDate.error.kind, refDate.error.message)
        includeTime && birthTime is CalcResult.Err -> err(birthTime.error.kind, birthTime.error.message)
        includeTime && refTime is CalcResult.Err -> err(refTime.error.kind, refTime.error.message)
        else -> {
            val birth = (birthDate as CalcResult.Ok<LocalDate>).value
            val reference = (refDate as CalcResult.Ok<LocalDate>).value
            if (includeTime) {
                val bt = (birthTime as CalcResult.Ok<LocalTime>).value
                val rt = (refTime as CalcResult.Ok<LocalTime>).value
                AgeEngine.compute(birth.atTime(bt), reference.atTime(rt))
            } else {
                AgeEngine.compute(birth, reference)
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
            label = "Birth date",
            day = birthDay,
            month = birthMonth,
            year = birthYear,
            onDay = { birthDay = it },
            onMonth = { birthMonth = it },
            onYear = { birthYear = it },
            dayError = intFieldError(birthDay, "Day", 1, 31),
            monthError = intFieldError(birthMonth, "Month", 1, 12),
            yearError = intFieldError(birthYear, "Year", 1, 9999),
        )

        DateTripleField(
            label = "Reference date (\"as of\")",
            day = refDay,
            month = refMonth,
            year = refYear,
            onDay = { refDay = it },
            onMonth = { refMonth = it },
            onYear = { refYear = it },
            dayError = intFieldError(refDay, "Day", 1, 31),
            monthError = intFieldError(refMonth, "Month", 1, 12),
            yearError = intFieldError(refYear, "Year", 1, 9999),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            OutlinedButton(
                onClick = {
                    val now = LocalDate.now()
                    refDay = now.dayOfMonth.toString()
                    refMonth = now.monthValue.toString()
                    refYear = now.year.toString()
                    if (includeTime) {
                        val time = LocalTime.now()
                        refHour = time.hour.toString()
                        refMinute = time.minute.toString()
                    }
                },
            ) {
                Text("Use today")
            }
        }

        ToggleRow(
            label = "Include time of day",
            checked = includeTime,
            onCheckedChange = { includeTime = it },
            description = "When off, both moments are treated as midnight (date-only age)",
        )
        if (includeTime) {
            TimePairField(
                label = "Birth time",
                hour = birthHour,
                minute = birthMinute,
                onHour = { birthHour = it },
                onMinute = { birthMinute = it },
                hourError = intFieldError(birthHour, "Hour", 0, 23),
                minuteError = intFieldError(birthMinute, "Minute", 0, 59),
            )
            TimePairField(
                label = "Reference time",
                hour = refHour,
                minute = refMinute,
                onHour = { refHour = it },
                onMinute = { refMinute = it },
                hourError = intFieldError(refHour, "Hour", 0, 23),
                minuteError = intFieldError(refMinute, "Minute", 0, 59),
            )
        }

        ResultCard(
            label = "Exact age (years · months · days)",
            value = result?.let { "${it.years} y · ${it.months} m · ${it.days} d" },
            caption = "Completed calendar units from the birth date to the reference date",
            error = error,
            onSave = result?.let { r ->
                {
                    val born = "${birthYear.padStart(4, '0')}-${birthMonth.padStart(2, '0')}-${birthDay.padStart(2, '0')}"
                    val asOf = "${refYear.padStart(4, '0')}-${refMonth.padStart(2, '0')}-${refDay.padStart(2, '0')}"
                    args.onRecord(
                        "Age: born $born, as of $asOf${if (includeTime) " $refHour:$refMinute" else ""}",
                        "${r.years} y ${r.months} m ${r.days} d (${fmt.num(BigDecimal.valueOf(r.totalDays))} days)",
                    )
                }
            },
        )

        if (result != null) {
            ResultRow(
                label = "Total days",
                value = fmt.num(BigDecimal.valueOf(result.totalDays), precision = maxOf(fmt.precision, 10)),
            )
            ResultRow(
                label = "Total weeks",
                value = fmt.num(BigDecimal.valueOf(result.totalWeeks), precision = maxOf(fmt.precision, 10)),
            )
            ResultRow(
                label = "Total months",
                value = fmt.num(BigDecimal.valueOf(result.totalMonths), precision = maxOf(fmt.precision, 10)),
            )
            ResultRow(
                label = "Days until next birthday",
                value = if (result.daysToNextBirthday == 0L) {
                    "today"
                } else {
                    fmt.num(BigDecimal.valueOf(result.daysToNextBirthday), precision = maxOf(fmt.precision, 10))
                },
            )
            ResultRow(
                label = "Next birthday",
                value = isoDate(result.nextBirthday),
                unit = weekdayName(result.nextBirthday),
            )
            ResultRow(
                label = "Weekday of birth",
                value = weekdayName((birthDate as CalcResult.Ok<LocalDate>).value),
            )
        }

        FormulaCard(
            formula = "exact age = completed years, months and days between the two dates\n" +
                "totals = exact elapsed days / weeks / months",
            explanation = "Years and months use calendar arithmetic (a month is not a fixed number " +
                "of days). Totals are exact elapsed counts from the birth date up to the reference " +
                "date; the day itself is not counted as complete until its time has passed.",
        )

        result?.notes?.forEach { InfoNote(it) }
        if (result == null) {
            InfoNote("Enter the birth date to see the exact age.")
        }
    }
}
