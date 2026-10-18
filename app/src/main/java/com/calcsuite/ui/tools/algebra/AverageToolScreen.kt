package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

private enum class AverageMode { Mean, Weighted, Median, Mode, Geometric }

private data class ModeOutcome(
    val label: String,
    val value: BigDecimal?,
    val caption: String? = null,
    val error: String? = null,
    val steps: List<String> = emptyList(),
    val formula: String,
    val explanation: String,
    val variables: List<Pair<String, String>> = emptyList(),
)

@Composable
fun AverageToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var modeName by rememberSaveable { mutableStateOf(AverageMode.Mean.name) }
    val mode = AverageMode.valueOf(modeName)
    var dataset by rememberSaveable { mutableStateOf("2, 4, 6") }
    var weights by rememberSaveable { mutableStateOf("1, 1, 1") }

    fun num(v: BigDecimal) = fmt.num(v, precision = maxOf(fmt.precision, 10))

    val parsed = AverageEngine.parseDataset(dataset)
    val parsedWeights = AverageEngine.parseDataset(weights)
    val values = (parsed as? CalcResult.Ok)?.value
    val weightValues = (parsedWeights as? CalcResult.Ok)?.value
    val datasetError = (parsed as? CalcResult.Err)?.error?.message
    val weightError: String? = when {
        mode != AverageMode.Weighted -> null
        parsedWeights is CalcResult.Err -> parsedWeights.error.message
        values != null && weightValues != null && values.size != weightValues.size ->
            "Provide one weight per value: ${values.size} values but ${weightValues.size} weights"
        else -> null
    }

    val summary = values?.let { AverageEngine.summarize(it).valueOrNull() }

    val outcome: ModeOutcome = remember(fmt, mode, dataset, weights) {
        val baseFormula = "mean = Σxᵢ ÷ n"
        if (values == null) {
            return@remember ModeOutcome(
                label = "Result",
                value = null,
                error = datasetError ?: "Enter at least one number",
                formula = baseFormula,
                explanation = "Enter the data set as numbers separated by commas, semicolons or new lines.",
            )
        }
        when (mode) {
            AverageMode.Mean -> {
                val mean = AverageEngine.arithmeticMean(values)
                ModeOutcome(
                    label = "Arithmetic mean of ${values.size} value(s)",
                    value = (mean as? CalcResult.Ok)?.value,
                    error = (mean as? CalcResult.Err)?.error?.message,
                    steps = listOf(
                        "Add every value: ${values.joinToString(" + ") { num(it) }} = ${num(values.fold(BigDecimal.ZERO) { a, b -> a.add(b) })}",
                        "Divide by the count: ${num(values.fold(BigDecimal.ZERO) { a, b -> a.add(b) })} ÷ ${values.size}",
                    ),
                    formula = baseFormula,
                    explanation = "The arithmetic mean spreads the total evenly across all values.",
                    variables = listOf("Σxᵢ" to "sum of all values", "n" to "how many values there are"),
                )
            }

            AverageMode.Weighted -> {
                if (weightValues == null || weightValues.size != values.size) {
                    ModeOutcome(
                        label = "Weighted mean",
                        value = null,
                        error = weightError ?: "Provide one non-negative weight per value",
                        formula = "weighted mean = Σ(xᵢ·wᵢ) ÷ Σwᵢ",
                        explanation = "Each value is multiplied by its weight before the average is taken.",
                    )
                } else {
                    val result = AverageEngine.weightedMean(values, weightValues)
                    when (result) {
                        is CalcResult.Ok -> ModeOutcome(
                            label = "Weighted mean of ${values.size} value(s)",
                            value = result.value.mean,
                            caption = "Total weight: ${num(result.value.weightSum)}",
                            steps = listOf(
                                "Multiply each value by its weight: ${values.zip(weightValues).joinToString(", ") { (v, w) -> "${num(v)}×${num(w)}" }}",
                                "Sum of the products: ${num(result.value.weightedSum)}",
                                "Divide by the total weight: ${num(result.value.weightedSum)} ÷ ${num(result.value.weightSum)}",
                            ),
                            formula = "weighted mean = Σ(xᵢ·wᵢ) ÷ Σwᵢ",
                            explanation = "Weights state how much each value contributes; the weights must be non-negative and must not all be zero.",
                        )
                        is CalcResult.Err -> ModeOutcome(
                            label = "Weighted mean",
                            value = null,
                            error = result.error.message,
                            formula = "weighted mean = Σ(xᵢ·wᵢ) ÷ Σwᵢ",
                            explanation = "Weights state how much each value contributes.",
                        )
                    }
                }
            }

            AverageMode.Median -> {
                val median = AverageEngine.median(values)
                val sorted = values.sorted()
                val middle = when {
                    values.size % 2 == 1 -> "the middle value is ${num(sorted[sorted.size / 2])}"
                    else -> "the two middle values are ${num(sorted[sorted.size / 2 - 1])} and ${num(sorted[sorted.size / 2])}; average them"
                }
                ModeOutcome(
                    label = "Median of ${values.size} value(s)",
                    value = (median as? CalcResult.Ok)?.value,
                    error = (median as? CalcResult.Err)?.error?.message,
                    steps = listOf(
                        "Sort the values: ${sorted.joinToString(", ") { num(it) }}",
                        "With ${values.size} values, $middle",
                    ),
                    formula = "median = middle value of the sorted list (mean of the two middles when n is even)",
                    explanation = "The median splits the data in half and ignores how extreme the values are.",
                )
            }

            AverageMode.Mode -> {
                val modes = AverageEngine.mode(values)
                when (modes) {
                    is CalcResult.Ok -> if (modes.value.isEmpty()) {
                        ModeOutcome(
                            label = "Mode",
                            value = null,
                            caption = "Every value appears exactly once, so there is no mode",
                            formula = "mode = value(s) with the highest frequency",
                            explanation = "A data set has no mode when all values occur equally often.",
                        )
                    } else {
                        ModeOutcome(
                            label = if (modes.value.size == 1) "Mode" else "Modes (${modes.value.size})",
                            value = null,
                            caption = modes.value.joinToString(", ") { num(it) },
                            steps = listOf(
                                "Count how often each value appears",
                                if (modes.value.size == 1) "${num(modes.value[0])} appears most often"
                                else "Several values share the highest frequency: ${modes.value.joinToString(", ") { num(it) }}",
                            ),
                            formula = "mode = value(s) with the highest frequency",
                            explanation = "A data set can have one mode, several modes (multi-modal) or none at all.",
                        )
                    }
                    is CalcResult.Err -> ModeOutcome(
                        label = "Mode",
                        value = null,
                        error = modes.error.message,
                        formula = "mode = value(s) with the highest frequency",
                        explanation = "A data set can have one mode, several modes (multi-modal) or none at all.",
                    )
                }
            }

            AverageMode.Geometric -> {
                val result = AverageEngine.geometricMean(values)
                when (result) {
                    is CalcResult.Ok -> ModeOutcome(
                        label = "Geometric mean of ${values.size} value(s)",
                        value = result.value,
                        caption = if (result.value.precision() >= 15) "Approximate — irrational root" else null,
                        steps = listOf(
                            "Multiply all values: ${values.joinToString(" × ") { num(it) }} = ${num(values.fold(BigDecimal.ONE) { a, b -> a.multiply(b) })}",
                            "Take the ${values.size}-th root of the product",
                        ),
                        formula = "geometric mean = (x₁ × x₂ × … × xₙ)^(1/n)",
                        explanation = "Useful for growth rates and ratios; every value must be strictly positive.",
                    )
                    is CalcResult.Err -> ModeOutcome(
                        label = "Geometric mean",
                        value = null,
                        error = result.error.message,
                        formula = "geometric mean = (x₁ × x₂ × … × xₙ)^(1/n)",
                        explanation = "Useful for growth rates and ratios; every value must be strictly positive.",
                    )
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
            TextButton(onClick = { dataset = "2, 4, 6"; weights = "1, 1, 1" }) { Text("Reset") }
        },
    ) {
        SegmentedChoice(
            options = listOf(
                AverageMode.Mean.name to "Mean",
                AverageMode.Weighted.name to "Weighted",
                AverageMode.Median.name to "Median",
                AverageMode.Mode.name to "Mode",
                AverageMode.Geometric.name to "Geometric",
            ),
            selected = modeName,
            onSelect = { modeName = it },
            label = "Result",
        )

        PlainField(
            label = "Data set",
            value = dataset,
            onValueChange = { dataset = it },
            placeholder = "2, 4, 6",
            error = datasetError,
            singleLine = false,
            monospace = true,
            imeAction = androidx.compose.ui.text.input.ImeAction.Next,
        )

        if (mode == AverageMode.Weighted) {
            PlainField(
                label = "Weights",
                value = weights,
                onValueChange = { weights = it },
                placeholder = "1, 1, 1",
                error = weightError,
                singleLine = false,
                monospace = true,
            )
        }

        ResultCard(
            label = outcome.label,
            value = outcome.value?.let { num(it) },
            caption = outcome.caption,
            error = outcome.error,
            onCopy = { copy(it, outcome.label) },
            copyValue = outcome.value?.let { Num.formatForCopy(it) },
            onSave = outcome.value?.let { value ->
                {
                    args.onRecord(
                        "${averageModeLabel(mode)} of ${values?.size ?: 0} value(s)",
                        Num.formatForCopy(value),
                    )
                }
            },
        )

        if (summary != null) {
            SectionCard(title = "Data summary") {
                ResultRow("Count", summary.count.toString())
                ResultRow("Sum", num(summary.sum))
                ResultRow("Minimum", num(summary.min))
                ResultRow("Maximum", num(summary.max))
                ResultRow("Sorted", summary.sorted.joinToString(", ") { num(it) })
                val modes = AverageEngine.mode(summary.values).valueOrNull()
                ResultRow(
                    "Modes",
                    if (modes.isNullOrEmpty()) "none (all values unique)" else modes.joinToString(", ") { num(it) },
                )
            }
        }

        if (outcome.steps.isNotEmpty()) {
            StepsCard(outcome.steps)
        }

        if (mode == AverageMode.Weighted && values != null && weightValues != null && values.size == weightValues.size) {
            Column(Modifier.fillMaxWidth()) {
                values.zip(weightValues).forEachIndexed { index, (value, weight) ->
                    ResultRow("Value ${index + 1} × weight", "${num(value)} × ${num(weight)} = ${num(value.multiply(weight))}")
                }
            }
        }

        FormulaCard(
            formula = outcome.formula,
            explanation = outcome.explanation,
            variables = outcome.variables,
        )

        InfoNote("Separate numbers with commas, semicolons or new lines. Spaces inside a number are treated as digit grouping (1 234 = 1234).")
    }
}

private fun averageModeLabel(mode: AverageMode): String = when (mode) {
    AverageMode.Mean -> "mean"
    AverageMode.Weighted -> "weighted mean"
    AverageMode.Median -> "median"
    AverageMode.Mode -> "mode"
    AverageMode.Geometric -> "geometric mean"
}
