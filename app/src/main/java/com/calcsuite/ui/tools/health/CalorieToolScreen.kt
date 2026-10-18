package com.calcsuite.ui.tools.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.health.CalorieEngine
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

/**
 * Calorie burn estimated with MET values (2024 Adult Compendium source class).
 * The activity picker is searchable; a custom MET value can override it from
 * the advanced panel. Gross and net expenditure are reported as separate,
 * clearly labelled quantities.
 */
@Composable
fun CalorieToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()

    var useKg by rememberSaveable { mutableStateOf(true) }
    var mass by rememberSaveable { mutableStateOf("") }
    var activityId by rememberSaveable { mutableStateOf(CalorieEngine.activities.first().id) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var useCustomMet by rememberSaveable { mutableStateOf(false) }
    var customMet by rememberSaveable { mutableStateOf("") }

    val activity = CalorieEngine.activity(activityId)
    val massValue = Num.parseOrNull(mass)
    val minutesValue = Num.parseOrNull(minutes)
    val customMetValue = Num.parseOrNull(customMet)

    // Non-positive masses deliberately reach the engine so its validation
    // message ("Body mass must be greater than zero") is shown in the result card.
    val massKg: BigDecimal? = massValue?.let {
        if (useKg) it else it.multiply(BigDecimal("0.45359237"), Num.EVAL_CONTEXT)
    }
    val metValue: BigDecimal? = if (useCustomMet) customMetValue else activity?.met

    val computed: CalcResult<CalorieEngine.Result>? = when {
        massKg == null || minutesValue == null || metValue == null -> null
        useCustomMet -> CalorieEngine.compute(massKg, minutesValue, metValue)
        else -> CalorieEngine.computeForActivity(activityId, massKg, minutesValue)
    }
    val result = (computed as? CalcResult.Ok)?.value
    val error = (computed as? CalcResult.Err)?.error?.message

    val options = remember(fmt) {
        CalorieEngine.activities.map {
            Option(it.id, it.label, it.group, "${fmt.num(it.met)} MET")
        }
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NumberField(
                label = "Body mass",
                value = mass,
                onValueChange = { mass = it },
                unit = if (useKg) "kg" else "lb",
                placeholder = if (useKg) "70" else "154",
                error = parseErrorCal(mass),
                modifier = Modifier.weight(1f),
            )
            SegmentedChoice(
                options = listOf(true to "kg", false to "lb"),
                selected = useKg,
                onSelect = { useKg = it },
                label = "Unit",
                modifier = Modifier.weight(1f),
            )
        }

        SelectField(
            label = "Activity",
            options = options,
            selected = activityId,
            onSelect = { activityId = it },
            searchPlaceholder = "Search activities",
        )

        NumberField(
            label = "Duration",
            value = minutes,
            onValueChange = { minutes = it },
            unit = "min",
            placeholder = "30",
            error = parseErrorCal(minutes),
        )

        ExpandablePanel(title = "Custom MET value") {
            ToggleRow(
                label = "Use a custom MET value",
                checked = useCustomMet,
                onCheckedChange = { useCustomMet = it },
                description = "Overrides the selected activity (1 MET ≈ resting metabolism)",
            )
            NumberField(
                label = "MET value",
                value = customMet,
                onValueChange = { customMet = it },
                unit = "MET",
                enabled = useCustomMet,
                error = if (useCustomMet) parseErrorCal(customMet) else null,
            )
        }

        ResultCard(
            label = "Gross energy expenditure",
            value = result?.let { fmt.num(it.grossKcal, precision = maxOf(fmt.precision, 10)) },
            unit = "kcal",
            approx = true,
            caption = result?.let {
                "Includes the energy you would have used at rest · " +
                    "${fmt.num(it.met)} MET for ${fmt.num(it.minutes)} min"
            },
            error = error,
            onSave = result?.let { r ->
                {
                    val what = if (useCustomMet) "custom ${fmt.num(r.met)} MET" else activity?.label ?: "activity"
                    val summary = "Calories: $mass ${if (useKg) "kg" else "lb"}, " +
                        "${fmt.num(r.minutes)} min, $what"
                    args.onRecord(
                        summary,
                        "${fmt.num(r.grossKcal, precision = maxOf(fmt.precision, 10))} kcal gross / " +
                            "${fmt.num(r.netKcal, precision = maxOf(fmt.precision, 10))} kcal net",
                    )
                }
            },
        )

        if (result != null) {
            ResultRow(
                label = "Net activity expenditure",
                value = fmt.num(result.netKcal, precision = maxOf(fmt.precision, 10)),
                unit = "kcal",
                emphasis = true,
            )
            ResultRow(
                label = "Rate",
                value = fmt.num(result.kcalPerMinute, precision = maxOf(fmt.precision, 10)),
                unit = "kcal/min",
            )
            ResultRow(label = "MET value used", value = fmt.num(result.met))
            ResultRow(
                label = "Body mass used",
                value = fmt.num(result.massKg, precision = maxOf(fmt.precision, 10)),
                unit = "kg",
            )
        }

        FormulaCard(
            formula = "gross kcal = MET × 3.5 × kg ÷ 200 × minutes\nnet kcal = (MET − 1) × 3.5 × kg ÷ 200 × minutes",
            explanation = "1 MET is resting metabolism (3.5 mL O₂ per kg per minute, ≈ 1 kcal per kg " +
                "per hour). Gross includes that resting share, net is the activity's own cost.",
            variables = listOf(
                "MET" to "activity intensity from the compendium table",
                "kg" to "body mass in kilograms",
                "minutes" to "duration of the activity",
            ),
        )

        result?.notes?.forEach { InfoNote(it) }
        if (result == null) {
            InfoNote("Enter a body mass and a duration to estimate the energy expenditure.")
        }
    }
}

private fun parseErrorCal(raw: String): String? =
    if (raw.isBlank() || Num.parseOrNull(raw) != null) null else "Enter a valid number"
