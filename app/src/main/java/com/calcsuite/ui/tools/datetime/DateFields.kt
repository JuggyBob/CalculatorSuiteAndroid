package com.calcsuite.ui.tools.datetime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.engine.datetime.AgeEngine
import com.calcsuite.ui.components.NumberField
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Date entry convention for this batch: **three plain numeric fields**
 * (day / month / year) instead of a picker dialog. Every field is validated
 * against `java.time` before the calculation runs, so impossible dates such
 * as 30 February or 29 February in a common year are reported with a message
 * instead of being silently corrected. Times are entered as hour / minute
 * numeric fields on the same principle.
 */
@Composable
internal fun DateTripleField(
    label: String,
    day: String,
    month: String,
    year: String,
    onDay: (String) -> Unit,
    onMonth: (String) -> Unit,
    onYear: (String) -> Unit,
    dayError: String? = null,
    monthError: String? = null,
    yearError: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField(
                label = "Day",
                value = day,
                onValueChange = onDay,
                modifier = Modifier.fillMaxWidth(0.3f),
                error = dayError,
                placeholder = "31",
                integerOnly = true,
            )
            NumberField(
                label = "Month",
                value = month,
                onValueChange = onMonth,
                modifier = Modifier.fillMaxWidth(0.3f),
                error = monthError,
                placeholder = "12",
                integerOnly = true,
            )
            NumberField(
                label = "Year",
                value = year,
                onValueChange = onYear,
                modifier = Modifier.fillMaxWidth(0.37f),
                error = yearError,
                placeholder = "2024",
                integerOnly = true,
            )
        }
    }
}

/** Hour / minute pair, sharing the same validation convention. */
@Composable
internal fun TimePairField(
    label: String,
    hour: String,
    minute: String,
    onHour: (String) -> Unit,
    onMinute: (String) -> Unit,
    hourError: String? = null,
    minuteError: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NumberField(
                label = "Hour",
                value = hour,
                onValueChange = onHour,
                modifier = Modifier.weight(1f),
                error = hourError,
                placeholder = "14",
                integerOnly = true,
            )
            NumberField(
                label = "Minute",
                value = minute,
                onValueChange = onMinute,
                modifier = Modifier.weight(1f),
                error = minuteError,
                placeholder = "30",
                integerOnly = true,
            )
        }
    }
}

/** Parses a whole number field; null when the text is not a plain integer. */
internal fun parseWholeNumber(raw: String): Int? {
    val value = Num.parseIntOrNull(raw) ?: return null
    if (value.bitLength() > 31) return null
    return value.toInt()
}

/** Field-level error for an integer input, null while blank or valid. */
internal fun intFieldError(raw: String, label: String, min: Int, max: Int): String? {
    if (raw.isBlank()) return null
    val value = parseWholeNumber(raw) ?: return "$label must be a whole number"
    return if (value < min || value > max) "$label must be between $min and $max" else null
}

/**
 * Reads a day/month/year triple. Returns null while any field is blank,
 * an error for malformed or impossible dates, and the date otherwise.
 */
internal fun parseDateFields(
    day: String,
    month: String,
    year: String,
    what: String,
): CalcResult<LocalDate>? {
    if (day.isBlank() || month.isBlank() || year.isBlank()) return null
    val d = parseWholeNumber(day)
        ?: return err(ErrorKind.InvalidInput, "$what: day must be a whole number")
    val m = parseWholeNumber(month)
        ?: return err(ErrorKind.InvalidInput, "$what: month must be a whole number")
    val y = parseWholeNumber(year)
        ?: return err(ErrorKind.InvalidInput, "$what: year must be a whole number")
    return AgeEngine.dateOf(y, m, d)
}

/** Reads an hour/minute pair; null while either field is blank. */
internal fun parseTimeFields(hour: String, minute: String, what: String): CalcResult<LocalTime>? {
    if (hour.isBlank() || minute.isBlank()) return null
    val h = parseWholeNumber(hour)
        ?: return err(ErrorKind.InvalidInput, "$what: hour must be a whole number")
    val m = parseWholeNumber(minute)
        ?: return err(ErrorKind.InvalidInput, "$what: minute must be a whole number")
    return AgeEngine.timeOf(h, m)
}

/** ISO date for display and history summaries, e.g. `2024-02-29`. */
internal fun isoDate(date: LocalDate): String = DateTimeFormatter.ISO_LOCAL_DATE.format(date)

/** Localized full weekday name, e.g. "Thursday". */
internal fun weekdayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
