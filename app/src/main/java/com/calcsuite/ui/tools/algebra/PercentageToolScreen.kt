package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.calcsuite.engine.algebra.AverageEngine
import com.calcsuite.engine.algebra.PercentageEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

private enum class PercentMode { PercentOf, FindWhole, WhatPercent, Change, Original, Successive, Points, Relative }

private data class PercentOutcome(
    val label: String,
    val value: String? = null,
    val raw: BigDecimal? = null,
    val unit: String? = null,
    val error: String? = null,
    val caption: String? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val steps: List<String> = emptyList(),
    val formula: String,
    val explanation: String = "",
    val variables: List<Pair<String, String>> = emptyList(),
)

@Composable
fun PercentageToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var modeName by rememberSaveable { mutableStateOf(PercentMode.PercentOf.name) }
    val mode = PercentMode.valueOf(modeName)
    var percent by rememberSaveable { mutableStateOf("15") }
    var value by rememberSaveable { mutableStateOf("200") }
    var wholePart by rememberSaveable { mutableStateOf("50") }
    var wholeWhole by rememberSaveable { mutableStateOf("200") }
    var from by rememberSaveable { mutableStateOf("120") }
    var to by rememberSaveable { mutableStateOf("150") }
    var finalValue by rememberSaveable { mutableStateOf("132") }
    var originalPercent by rememberSaveable { mutableStateOf("10") }
    var wasIncrease by rememberSaveable { mutableStateOf(true) }
    var base by rememberSaveable { mutableStateOf("100") }
    var changes by rememberSaveable { mutableStateOf("+10, -10") }
    var points1 by rememberSaveable { mutableStateOf("5") }
    var points2 by rememberSaveable { mutableStateOf("7") }
    var relA by rememberSaveable { mutableStateOf("10") }
    var relB by rememberSaveable { mutableStateOf("12") }

    fun num(v: BigDecimal) = fmt.num(v, precision = maxOf(fmt.precision, 10))
    fun parseError(text: String): String? =
        if (text.isBlank()) "Enter a value" else if (Num.parseOrNull(text) == null) "Enter a valid number" else null

    val outcome: PercentOutcome = remember(
        fmt, mode, percent, value, wholePart, wholeWhole, from, to, finalValue,
        originalPercent, wasIncrease, base, changes, points1, points2, relA, relB,
    ) {
        when (mode) {
            PercentMode.PercentOf -> {
                val p = Num.parseOrNull(percent)
                val v = Num.parseOrNull(value)
                when {
                    p == null -> PercentOutcome("Result", error = "Enter a valid percentage", formula = "amount = value × p ÷ 100")
                    v == null -> PercentOutcome("Result", error = "Enter a valid number", formula = "amount = value × p ÷ 100")
                    else -> {
                        val result = PercentageEngine.percentOf(p, v)
                        PercentOutcome(
                            label = "${num(p)}% of ${num(v)}",
                            value = result.valueOrNull()?.let { num(it) },
                            raw = result.valueOrNull(),
                            rows = listOf(
                                "Percent as a fraction" to num(p.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT)),
                                "Base value" to num(v),
                            ),
                            steps = listOf(
                                "Write the percentage as a fraction: ${num(p)}% = ${num(p)} ÷ 100",
                                "Multiply by the value: ${num(v)} × ${num(p)} ÷ 100",
                                "Result: ${result.valueOrNull()?.let { num(it) } ?: "—"}",
                            ),
                            formula = "amount = value × percentage ÷ 100",
                            explanation = "A percentage is a fraction of 100, so p% of a value is value × p ÷ 100.",
                            variables = listOf("value" to "the amount the percentage is taken from", "p" to "the percentage, e.g. 15 for 15%"),
                        )
                    }
                }
            }

            PercentMode.FindWhole -> {
                val p = Num.parseOrNull(percent)
                val part = Num.parseOrNull(wholePart)
                when {
                    p == null -> PercentOutcome("Whole", error = "Enter a valid percentage", formula = "whole = part × 100 ÷ p")
                    part == null -> PercentOutcome("Whole", error = "Enter a valid part", formula = "whole = part × 100 ÷ p")
                    p.signum() == 0 -> PercentOutcome(
                        "Whole",
                        error = "A part can never be 0% of a value, so the whole is undefined",
                        formula = "whole = part × 100 ÷ p",
                    )
                    else -> {
                        val whole = part.multiply(BigDecimal("100")).divide(p, com.calcsuite.core.Num.EVAL_CONTEXT)
                        PercentOutcome(
                            label = "${num(part)} is ${num(p)}% of",
                            value = num(whole),
                            raw = whole,
                            rows = listOf("Percent as a fraction" to num(p.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT))),
                            steps = listOf(
                                "part = whole × p ÷ 100",
                                "Rearrange: whole = part × 100 ÷ p",
                                "whole = ${num(part)} × 100 ÷ ${num(p)} = ${num(whole)}",
                            ),
                            formula = "whole = part × 100 ÷ percentage",
                            explanation = "Invert p% = part ÷ whole × 100 to find the whole from a part and its percentage.",
                        )
                    }
                }
            }

            PercentMode.WhatPercent -> {
                val part = Num.parseOrNull(wholePart)
                val whole = Num.parseOrNull(wholeWhole)
                when {
                    part == null -> PercentOutcome("Percentage", error = "Enter a valid part", formula = "p = part ÷ whole × 100")
                    whole == null -> PercentOutcome("Percentage", error = "Enter a valid whole", formula = "p = part ÷ whole × 100")
                    else -> {
                        val result = PercentageEngine.whatPercent(part, whole)
                        when (result) {
                            is CalcResult.Err -> PercentOutcome(
                                "Percentage",
                                error = result.error.message,
                                formula = "p = part ÷ whole × 100",
                            )
                            is CalcResult.Ok -> PercentOutcome(
                                label = "${num(part)} as a percentage of ${num(whole)}",
                                value = num(result.value),
                                raw = result.value,
                                unit = "%",
                                rows = listOf(
                                    "Ratio" to num(part.divide(whole, com.calcsuite.core.Num.EVAL_CONTEXT)),
                                    "Fraction of the whole" to "${num(part)} / ${num(whole)}",
                                ),
                                steps = listOf(
                                    "Divide the part by the whole: ${num(part)} ÷ ${num(whole)}",
                                    "Multiply by 100 to get a percentage: × 100",
                                    "Result: ${num(result.value)}%",
                                ),
                                formula = "percentage = part ÷ whole × 100",
                                explanation = "Expresses a part as a share of a reference value, scaled to 100.",
                                variables = listOf("part" to "the amount being expressed", "whole" to "the reference value (must not be zero)"),
                            )
                        }
                    }
                }
            }

            PercentMode.Change -> {
                val start = Num.parseOrNull(from)
                val end = Num.parseOrNull(to)
                when {
                    start == null -> PercentOutcome("Change", error = "Enter a valid starting value", formula = "change % = (end − start) ÷ start × 100")
                    end == null -> PercentOutcome("Change", error = "Enter a valid final value", formula = "change % = (end − start) ÷ start × 100")
                    else -> {
                        val increase = PercentageEngine.percentIncrease(start, end)
                        when (increase) {
                            is CalcResult.Err -> PercentOutcome("Change", error = increase.error.message, formula = "change % = (end − start) ÷ start × 100")
                            is CalcResult.Ok -> {
                                val change = increase.value
                                val direction = if (change.percent.signum() > 0) "increase" else if (change.percent.signum() < 0) "decrease" else "no change"
                                PercentOutcome(
                                    label = "Change from ${num(start)} to ${num(end)}",
                                    value = num(change.percent),
                                    raw = change.percent,
                                    unit = "%",
                                    caption = "A ${num(change.percent.abs())}% $direction",
                                    rows = listOf(
                                        "Absolute change" to num(change.change),
                                        "Ratio (end ÷ start)" to num(end.divide(start, com.calcsuite.core.Num.EVAL_CONTEXT)),
                                        "Square it: ×2 change would be" to num(change.change.multiply(BigDecimal("2"))),
                                    ),
                                    steps = listOf(
                                        "Subtract the starting value from the final value: ${num(end)} − ${num(start)} = ${num(change.change)}",
                                        "Divide by the starting value: ${num(change.change)} ÷ ${num(start)}",
                                        "Multiply by 100: ${num(change.percent)}%",
                                    ),
                                    formula = "change % = (end − start) ÷ start × 100",
                                    explanation = "Positive results are increases, negative results are decreases. The starting value must not be zero.",
                                )
                            }
                        }
                    }
                }
            }

            PercentMode.Original -> {
                val final = Num.parseOrNull(finalValue)
                val p = Num.parseOrNull(originalPercent)
                when {
                    final == null -> PercentOutcome("Original value", error = "Enter a valid final value", formula = "original = final ÷ (1 ± p ÷ 100)")
                    p == null -> PercentOutcome("Original value", error = "Enter a valid percentage", formula = "original = final ÷ (1 ± p ÷ 100)")
                    else -> {
                        val recovered = PercentageEngine.recoverOriginal(final, p, wasIncrease)
                        when (recovered) {
                            is CalcResult.Err -> PercentOutcome("Original value", error = recovered.error.message, formula = "original = final ÷ (1 ± p ÷ 100)")
                            is CalcResult.Ok -> {
                                val sign = if (wasIncrease) "+" else "−"
                                PercentOutcome(
                                    label = "Original value before a ${num(p)}% ${if (wasIncrease) "increase" else "decrease"}",
                                    value = num(recovered.value),
                                    raw = recovered.value,
                                    rows = listOf(
                                        "Multiplier" to "1 $sign ${num(p)} ÷ 100",
                                        "Final value" to num(final),
                                        "Check: original × multiplier" to num(recovered.value.multiply(
                                            if (wasIncrease) BigDecimal.ONE.add(p.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT))
                                            else BigDecimal.ONE.subtract(p.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT)),
                                            com.calcsuite.core.Num.EVAL_CONTEXT,
                                        )),
                                    ),
                                    steps = listOf(
                                        "final = original × (1 $sign ${num(p)} ÷ 100)",
                                        "original = final ÷ (1 $sign ${num(p)} ÷ 100)",
                                        "original = ${num(final)} ÷ ${num(if (wasIncrease) BigDecimal.ONE.add(p.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT)) else BigDecimal.ONE.subtract(p.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT)))} = ${num(recovered.value)}",
                                    ),
                                    formula = "original = final ÷ (1 ± percentage ÷ 100)",
                                    explanation = "Undo a percentage change by dividing by the multiplier that produced the final value.",
                                )
                            }
                        }
                    }
                }
            }

            PercentMode.Successive -> {
                val start = Num.parseOrNull(base)
                val parsedChanges = AverageEngine.parseDataset(changes)
                when {
                    start == null -> PercentOutcome("Final value", error = "Enter a valid starting value", formula = "final = base × (1 + c₁ ÷ 100) × (1 + c₂ ÷ 100) × …")
                    parsedChanges is CalcResult.Err -> PercentOutcome("Final value", error = parsedChanges.error.message, formula = "final = base × (1 + c₁ ÷ 100) × (1 + c₂ ÷ 100) × …")
                    else -> {
                        val list = (parsedChanges as CalcResult.Ok).value
                        val result = PercentageEngine.successiveChange(start, list)
                        when (result) {
                            is CalcResult.Err -> PercentOutcome("Final value", error = result.error.message, formula = "final = base × (1 + c₁ ÷ 100) × (1 + c₂ ÷ 100) × …")
                            is CalcResult.Ok -> {
                                val change = result.value
                                var running: BigDecimal = start
                                val runningRows = list.map { step ->
                                    running = running.multiply(
                                        BigDecimal.ONE.add(step.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT)),
                                        com.calcsuite.core.Num.EVAL_CONTEXT,
                                    )
                                    "After ${if (step.signum() >= 0) "+" else ""}${num(step)}%" to num(running)
                                }
                                PercentOutcome(
                                    label = "Final value after ${list.size} change(s)",
                                    value = num(change.finalValue),
                                    raw = change.finalValue,
                                    caption = "Effective change: ${if (change.percent.signum() >= 0) "+" else ""}${num(change.percent)}%",
                                    rows = runningRows + listOf(
                                        "Total change" to num(change.change),
                                        "Simple sum of the percentages" to num(list.fold(BigDecimal.ZERO) { acc, item -> acc.add(item) }),
                                    ),
                                    steps = buildList {
                                        add("Start from ${num(start)}")
                                        list.forEach { add("Apply ${if (it.signum() >= 0) "+" else ""}${num(it)}%: multiply by ${num(BigDecimal.ONE.add(it.divide(BigDecimal("100"), com.calcsuite.core.Num.EVAL_CONTEXT)))}") }
                                        add("Final value: ${num(change.finalValue)}")
                                        add("Percentages compound — you cannot simply add them (${num(list.fold(BigDecimal.ZERO) { a, b -> a.add(b) })}% vs ${num(change.percent)}%)")
                                    },
                                    formula = "final = base × Π(1 + changeᵢ ÷ 100)",
                                    explanation = "A change is applied to the result of the previous change, so percentage changes multiply rather than add.",
                                    variables = listOf("base" to "starting value", "changeᵢ" to "each signed percentage (+ up, − down)"),
                                )
                            }
                        }
                    }
                }
            }

            PercentMode.Points -> {
                val first = Num.parseOrNull(points1)
                val second = Num.parseOrNull(points2)
                when {
                    first == null -> PercentOutcome("Difference", error = "Enter a valid first percentage", formula = "points = p₁ − p₂")
                    second == null -> PercentOutcome("Difference", error = "Enter a valid second percentage", formula = "points = p₁ − p₂")
                    else -> {
                        val points = PercentageEngine.percentagePoints(first, second)
                        PercentOutcome(
                            label = "${num(first)}% compared with ${num(second)}%",
                            value = num(points),
                            raw = points,
                            unit = "percentage points",
                            rows = listOf(
                                "Reversed difference" to num(PercentageEngine.percentagePoints(second, first)),
                                "Relative change from ${num(second)}%" to num(
                                    if (second.signum() == 0) BigDecimal.ZERO
                                    else points.multiply(BigDecimal("100")).divide(second, com.calcsuite.core.Num.EVAL_CONTEXT),
                                ) + "%",
                            ),
                            steps = listOf(
                                "Subtract the second percentage from the first",
                                "${num(first)} − ${num(second)} = ${num(points)} percentage points",
                                "Percentage points compare two percentages directly; the relative change divides by the starting percentage",
                            ),
                            formula = "percentage points = p₁ − p₂",
                            explanation = "Going from 5% to 7% is +2 percentage points, which is also a +40% relative change.",
                            variables = listOf("p₁" to "the later percentage", "p₂" to "the earlier percentage"),
                        )
                    }
                }
            }

            PercentMode.Relative -> {
                val first = Num.parseOrNull(relA)
                val second = Num.parseOrNull(relB)
                when {
                    first == null -> PercentOutcome("Relative difference", error = "Enter a valid first value", formula = "|a − b| ÷ |(a + b) ÷ 2| × 100")
                    second == null -> PercentOutcome("Relative difference", error = "Enter a valid second value", formula = "|a − b| ÷ |(a + b) ÷ 2| × 100")
                    else -> {
                        val result = PercentageEngine.relativePercentDifference(first, second)
                        when (result) {
                            is CalcResult.Err -> PercentOutcome("Relative difference", error = result.error.message, formula = "|a − b| ÷ |(a + b) ÷ 2| × 100")
                            is CalcResult.Ok -> PercentOutcome(
                                label = "Relative difference between ${num(first)} and ${num(second)}",
                                value = num(result.value),
                                raw = result.value,
                                unit = "%",
                                rows = listOf(
                                    "Absolute difference" to num(first.subtract(second).abs()),
                                    "Mean (reference)" to num(first.add(second).divide(BigDecimal("2"), com.calcsuite.core.Num.EVAL_CONTEXT)),
                                ),
                                steps = listOf(
                                    "Absolute difference: |${num(first)} − ${num(second)}| = ${num(first.subtract(second).abs())}",
                                    "Mean reference: (${num(first)} + ${num(second)}) ÷ 2 = ${num(first.add(second).divide(BigDecimal("2"), com.calcsuite.core.Num.EVAL_CONTEXT))}",
                                    "Divide and scale to 100: ${num(result.value)}%",
                                ),
                                formula = "relative difference = |a − b| ÷ |(a + b) ÷ 2| × 100",
                                explanation = "The mean is the reference, so the result is symmetric: swapping the two values gives the same difference.",
                            )
                        }
                    }
                }
            }
        }
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        actions = {
            TextButton(onClick = {
                when (mode) {
                    PercentMode.PercentOf -> { percent = "15"; value = "200" }
                    PercentMode.FindWhole -> { percent = "25"; wholePart = "50" }
                    PercentMode.WhatPercent -> { wholePart = "50"; wholeWhole = "200" }
                    PercentMode.Change -> { from = "120"; to = "150" }
                    PercentMode.Original -> { finalValue = "132"; originalPercent = "10"; wasIncrease = true }
                    PercentMode.Successive -> { base = "100"; changes = "+10, -10" }
                    PercentMode.Points -> { points1 = "5"; points2 = "7" }
                    PercentMode.Relative -> { relA = "10"; relB = "12" }
                }
            }) { Text("Reset") }
        },
    ) {
        SegmentedChoice(
            options = listOf(
                PercentMode.PercentOf.name to "Percent of",
                PercentMode.FindWhole.name to "% of",
                PercentMode.WhatPercent.name to "What %",
                PercentMode.Change.name to "Change",
            ),
            selected = modeName,
            onSelect = { modeName = it },
            label = "Mode",
        )
        SegmentedChoice(
            options = listOf(
                PercentMode.Original.name to "Original",
                PercentMode.Successive.name to "Successive",
                PercentMode.Points.name to "Points",
                PercentMode.Relative.name to "Relative",
            ),
            selected = modeName,
            onSelect = { modeName = it },
        )

        when (mode) {
            PercentMode.PercentOf -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Percentage", percent, { percent = it }, unit = "%", error = parseError(percent))
                NumberField("Value", value, { value = it }, error = parseError(value))
            }
            PercentMode.FindWhole -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Part", wholePart, { wholePart = it }, error = parseError(wholePart))
                NumberField("Percentage", percent, { percent = it }, unit = "%", error = parseError(percent))
            }
            PercentMode.WhatPercent -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Part", wholePart, { wholePart = it }, error = parseError(wholePart))
                NumberField("Whole (reference)", wholeWhole, { wholeWhole = it }, error = parseError(wholeWhole))
            }
            PercentMode.Change -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Starting value", from, { from = it }, error = parseError(from))
                NumberField("Final value", to, { to = it }, error = parseError(to))
            }
            PercentMode.Original -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Final value", finalValue, { finalValue = it }, error = parseError(finalValue))
                NumberField("Percentage change", originalPercent, { originalPercent = it }, unit = "%", error = parseError(originalPercent))
                ToggleRow("The change was an increase", wasIncrease, { wasIncrease = it }, description = "Turn off when the final value came from a decrease")
            }
            PercentMode.Successive -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Starting value", base, { base = it }, error = parseError(base))
                PlainField(
                    label = "Percentage changes",
                    value = changes,
                    onValueChange = { changes = it },
                    placeholder = "+10, -10",
                    error = when (val parsed = AverageEngine.parseDataset(changes)) {
                        is CalcResult.Err -> parsed.error.message
                        is CalcResult.Ok -> null
                    },
                )
                InfoNote("Positive numbers increase the value, negative numbers decrease it. Separate the changes with commas or new lines.")
            }
            PercentMode.Points -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("First percentage", points1, { points1 = it }, unit = "%", error = parseError(points1))
                NumberField("Second percentage", points2, { points2 = it }, unit = "%", error = parseError(points2))
            }
            PercentMode.Relative -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("First value", relA, { relA = it }, error = parseError(relA))
                NumberField("Second value", relB, { relB = it }, error = parseError(relB))
            }
        }

        ResultCard(
            label = outcome.label,
            value = outcome.value,
            unit = outcome.unit,
            caption = outcome.caption,
            error = outcome.error,
            onCopy = { copy(it, outcome.label) },
            copyValue = outcome.raw?.let { Num.formatForCopy(it) },
            onSave = outcome.raw?.let { raw ->
                {
                    args.onRecord(
                        "${outcome.label} · mode ${modeLabel(mode)}",
                        outcome.unit?.let { unit -> "${Num.formatForCopy(raw)} $unit" } ?: Num.formatForCopy(raw),
                    )
                }
            },
        )

        if (outcome.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                outcome.rows.forEach { (label, rowValue) -> ResultRow(label, rowValue) }
            }
        }

        if (outcome.steps.isNotEmpty()) {
            StepsCard(outcome.steps)
        }

        FormulaCard(
            formula = outcome.formula,
            explanation = outcome.explanation,
            variables = outcome.variables,
        )
    }
}

private fun modeLabel(mode: PercentMode): String = when (mode) {
    PercentMode.PercentOf -> "percent of"
    PercentMode.FindWhole -> "% of (find whole)"
    PercentMode.WhatPercent -> "what %"
    PercentMode.Change -> "change"
    PercentMode.Original -> "original"
    PercentMode.Successive -> "successive"
    PercentMode.Points -> "points"
    PercentMode.Relative -> "relative"
}
