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
import com.calcsuite.engine.algebra.RatioEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

private enum class RatioMode { Simplify, Scale, Split, UnknownPart, Combine, Compare }

private data class RatioOutcome(
    val label: String,
    val value: String? = null,
    val copyValue: String? = null,
    val caption: String? = null,
    val error: String? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val steps: List<String> = emptyList(),
    val formula: String,
    val explanation: String = "",
)

@Composable
fun RatioToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var modeName by rememberSaveable { mutableStateOf(RatioMode.Simplify.name) }
    val mode = RatioMode.valueOf(modeName)
    var ratio by rememberSaveable { mutableStateOf("12 : 18") }
    var secondRatio by rememberSaveable { mutableStateOf("2 : 3") }
    var factor by rememberSaveable { mutableStateOf("10") }
    var total by rememberSaveable { mutableStateOf("100") }
    var decimals by rememberSaveable { mutableStateOf("2") }
    var knownIndex by rememberSaveable { mutableStateOf("1") }
    var knownValue by rememberSaveable { mutableStateOf("40") }
    var unknownIndex by rememberSaveable { mutableStateOf("2") }

    fun num(v: BigDecimal) = fmt.num(v, precision = maxOf(fmt.precision, 10))

    val outcome: RatioOutcome = remember(
        fmt, mode, ratio, secondRatio, factor, total, decimals, knownIndex, knownValue, unknownIndex,
    ) {
        when (mode) {
            RatioMode.Simplify -> {
                val simplified = RatioEngine.simplify(ratio)
                when (simplified) {
                    is CalcResult.Err -> RatioOutcome("Simplified ratio", error = simplified.error.message, formula = "a : b = (a ÷ g) : (b ÷ g)")
                    is CalcResult.Ok -> {
                        val result = simplified.value
                        RatioOutcome(
                            label = "Simplified ratio",
                            value = result.text,
                            copyValue = result.text,
                            caption = "Divided by the greatest common divisor ${result.divisor}",
                            rows = buildList {
                                add("Terms" to result.input.joinToString(" : ") { num(it) })
                                if (result.scaleFactor != java.math.BigInteger.ONE) {
                                    add("Integers after clearing decimals" to result.scaled.joinToString(" : "))
                                }
                                add("Greatest common divisor" to result.divisor.toString())
                            },
                            steps = result.steps,
                            formula = "a : b = (a ÷ g) : (b ÷ g),  g = gcd(a, b)",
                            explanation = "Dividing every term by their greatest common divisor gives the smallest integers with the same ratio.",
                        )
                    }
                }
            }

            RatioMode.Scale -> {
                val parsed = RatioEngine.parse(ratio)
                val factorValue = Num.parseOrNull(factor)
                when {
                    parsed is CalcResult.Err -> RatioOutcome("Scaled ratio", error = parsed.error.message, formula = "a : b → (k·a) : (k·b)")
                    factorValue == null -> RatioOutcome("Scaled ratio", error = "Enter a valid scale factor", formula = "a : b → (k·a) : (k·b)")
                    else -> {
                        val ratioTerms = (parsed as CalcResult.Ok).value
                        val scaled = RatioEngine.scale(ratioTerms, factorValue)
                        when (scaled) {
                            is CalcResult.Err -> RatioOutcome("Scaled ratio", error = scaled.error.message, formula = "a : b → (k·a) : (k·b)")
                            is CalcResult.Ok -> {
                                val equivalents = RatioEngine.equivalents(ratioTerms).valueOrNull()
                                RatioOutcome(
                                    label = "Ratio multiplied by ${num(factorValue)}",
                                    value = scaled.value.text,
                                    copyValue = scaled.value.text,
                                    rows = buildList {
                                        equivalents?.let { pair ->
                                            add("× 10" to pair.first.text)
                                            add("÷ 10" to pair.second.text)
                                        }
                                        add("Simplified form" to (RatioEngine.simplify(ratioTerms).valueOrNull()?.text ?: "—"))
                                    },
                                    steps = scaled.value.steps,
                                    formula = "a : b → (k · a) : (k · b)",
                                    explanation = "Scaling multiplies every term by the same positive number, so the ratio keeps its value.",
                                )
                            }
                        }
                    }
                }
            }

            RatioMode.Split -> {
                val parsed = RatioEngine.parse(ratio)
                val totalValue = Num.parseOrNull(total)
                val decimalPlaces = Num.parseIntOrNull(decimals)?.toInt()
                when {
                    parsed is CalcResult.Err -> RatioOutcome("Shares", error = parsed.error.message, formula = "shareᵢ = total × termᵢ ÷ Σterms")
                    totalValue == null -> RatioOutcome("Shares", error = "Enter a valid total", formula = "shareᵢ = total × termᵢ ÷ Σterms")
                    decimalPlaces == null || decimalPlaces < 0 || decimalPlaces > 12 ->
                        RatioOutcome("Shares", error = "Decimal places must be between 0 and 12", formula = "shareᵢ = total × termᵢ ÷ Σterms")
                    else -> {
                        val ratioTerms = (parsed as CalcResult.Ok).value
                        val split = RatioEngine.splitTotal(totalValue, ratioTerms, decimalPlaces)
                        when (split) {
                            is CalcResult.Err -> RatioOutcome("Shares", error = split.error.message, formula = "shareᵢ = total × termᵢ ÷ Σterms")
                            is CalcResult.Ok -> {
                                val result = split.value
                                val shareTotal = result.shares.fold(BigDecimal.ZERO) { acc, share -> acc.add(share) }
                                RatioOutcome(
                                    label = "Split ${num(totalValue)} in the ratio ${ratioTerms.joinToString(" : ") { num(it) }}",
                                    value = result.shares.joinToString(" + ") { num(it) },
                                    copyValue = result.shares.joinToString(", ") { Num.formatForCopy(it) },
                                    caption = when {
                                        result.unallocatedRemainder.signum() != 0 ->
                                            "Shares add up to ${num(shareTotal)}; ${num(result.unallocatedRemainder)} cannot be shown with $decimalPlaces decimal place(s)"
                                        !result.exactDivision ->
                                            "The ${num(result.remainderDistributed)} remainder was handed out one unit at a time"
                                        else -> "The shares add up to exactly ${num(shareTotal)}"
                                    },
                                    rows = result.shares.mapIndexed { index, share ->
                                        val term = ratioTerms.getOrNull(index)
                                        "Share ${index + 1}${if (term != null) " (term ${num(term)})" else ""}" to num(share)
                                    },
                                    steps = result.steps,
                                    formula = "shareᵢ = total × termᵢ ÷ (Σ of all terms)",
                                    explanation = "Every share is proportional to its term; a remainder is distributed one unit at a time, largest fractional part first.",
                                )
                            }
                        }
                    }
                }
            }

            RatioMode.UnknownPart -> {
                val parsed = RatioEngine.parse(ratio)
                val known = Num.parseOrNull(knownValue)
                val known1Based = Num.parseIntOrNull(knownIndex)?.toInt()
                val unknown1Based = Num.parseIntOrNull(unknownIndex)?.toInt()
                when {
                    parsed is CalcResult.Err -> RatioOutcome("Unknown share", error = parsed.error.message, formula = "share = known × term ÷ knownTerm")
                    known == null -> RatioOutcome("Unknown share", error = "Enter a valid known share", formula = "share = known × term ÷ knownTerm")
                    known1Based == null || unknown1Based == null ->
                        RatioOutcome("Unknown share", error = "Term numbers must be whole numbers", formula = "share = known × term ÷ knownTerm")
                    else -> {
                        val ratioTerms = (parsed as CalcResult.Ok).value
                        val result = RatioEngine.unknownPart(
                            ratioTerms,
                            known1Based - 1,
                            known,
                            unknown1Based - 1,
                        )
                        when (result) {
                            is CalcResult.Err -> RatioOutcome("Unknown share", error = result.error.message, formula = "share = known × term ÷ knownTerm")
                            is CalcResult.Ok -> {
                                val value = result.value
                                RatioOutcome(
                                    label = "Term ${unknown1Based} of the ratio ${ratioTerms.joinToString(" : ") { num(it) }}",
                                    value = num(value.value),
                                    copyValue = Num.formatForCopy(value.value),
                                    caption = value.verification,
                                    rows = listOf(
                                        "One ratio unit" to num(value.unit),
                                        "Known term ${known1Based}" to num(known),
                                    ),
                                    steps = value.steps + "Verification: ${value.verification}",
                                    formula = "unknown share = known share × its term ÷ the known term",
                                    explanation = "A ratio fixes how many units each term represents; knowing any one share reveals the unit size.",
                                )
                            }
                        }
                    }
                }
            }

            RatioMode.Combine -> {
                val first = RatioEngine.parse(ratio)
                val second = RatioEngine.parse(secondRatio)
                when {
                    first is CalcResult.Err -> RatioOutcome("Combined ratio", error = first.error.message, formula = "a : b  and  b : c  →  a : b : c")
                    second is CalcResult.Err -> RatioOutcome("Combined ratio", error = second.error.message, formula = "a : b  and  b : c  →  a : b : c")
                    else -> {
                        val firstTerms = (first as CalcResult.Ok).value
                        val secondTerms = (second as CalcResult.Ok).value
                        val combined = RatioEngine.combine(firstTerms, secondTerms)
                        when (combined) {
                            is CalcResult.Err -> RatioOutcome("Combined ratio", error = combined.error.message, formula = "a : b  and  b : c  →  a : b : c")
                            is CalcResult.Ok -> {
                                val result = combined.value
                                RatioOutcome(
                                    label = "Chained ratio",
                                    value = result.text,
                                    copyValue = result.text,
                                    caption = "${result.left.joinToString(" : ")} chained with " +
                                        "${result.right.joinToString(" : ")} through their linking term",
                                    rows = listOf(
                                        "First ratio rescaled" to result.leftScaled.joinToString(" : "),
                                        "Second ratio rescaled" to result.rightScaled.joinToString(" : "),
                                    ),
                                    steps = result.steps,
                                    formula = "a : b  and  b : c  →  a : b : c",
                                    explanation = "The last term of the first ratio and the first term of the second describe the same quantity, so both ratios are rescaled to that common value before merging.",
                                )
                            }
                        }
                    }
                }
            }

            RatioMode.Compare -> {
                val first = RatioEngine.parse(ratio)
                val second = RatioEngine.parse(secondRatio)
                when {
                    first is CalcResult.Err -> RatioOutcome("Comparison", error = first.error.message, formula = "compare cross products: a × d vs c × b")
                    second is CalcResult.Err -> RatioOutcome("Comparison", error = second.error.message, formula = "compare cross products: a × d vs c × b")
                    else -> {
                        val firstTerms = (first as CalcResult.Ok).value
                        val secondTerms = (second as CalcResult.Ok).value
                        val compared = RatioEngine.compare(firstTerms, secondTerms)
                        when (compared) {
                            is CalcResult.Err -> RatioOutcome("Comparison", error = compared.error.message, formula = "compare cross products: a × d vs c × b")
                            is CalcResult.Ok -> {
                                val result = compared.value
                                RatioOutcome(
                                    label = "Comparison",
                                    value = when (result.relation) {
                                        RatioEngine.RatioRelation.Less -> "First ratio is smaller"
                                        RatioEngine.RatioRelation.Equal -> "The ratios are equal"
                                        RatioEngine.RatioRelation.Greater -> "First ratio is greater"
                                    },
                                    caption = result.detail,
                                    rows = listOf(
                                        "First ratio as a decimal" to num(result.leftValue),
                                        "Second ratio as a decimal" to num(result.rightValue),
                                    ),
                                    steps = result.steps,
                                    formula = "a : b ⋛ c : d  ⟺  a × d ⋛ c × b",
                                    explanation = "The verdict uses exact cross multiplication; the decimal values are rounded to 12 significant digits for display.",
                                )
                            }
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
                ratio = "12 : 18"; secondRatio = "2 : 3"; factor = "10"; total = "100"
                decimals = "2"; knownIndex = "1"; knownValue = "40"; unknownIndex = "2"
            }) { Text("Reset") }
        },
    ) {
        SegmentedChoice(
            options = listOf(
                RatioMode.Simplify.name to "Simplify",
                RatioMode.Scale.name to "Scale",
                RatioMode.Split.name to "Split total",
            ),
            selected = modeName,
            onSelect = { modeName = it },
            label = "Mode",
        )
        SegmentedChoice(
            options = listOf(
                RatioMode.UnknownPart.name to "Unknown part",
                RatioMode.Combine.name to "Combine",
                RatioMode.Compare.name to "Compare",
            ),
            selected = modeName,
            onSelect = { modeName = it },
        )

        when (mode) {
            RatioMode.Simplify -> PlainField(
                label = "Ratio",
                value = ratio,
                onValueChange = { ratio = it },
                placeholder = "12 : 18",
                error = (RatioEngine.parse(ratio) as? CalcResult.Err)?.error?.message,
                monospace = true,
            )
            RatioMode.Scale -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainField(
                    label = "Ratio",
                    value = ratio,
                    onValueChange = { ratio = it },
                    placeholder = "12 : 18",
                    error = (RatioEngine.parse(ratio) as? CalcResult.Err)?.error?.message,
                    monospace = true,
                )
                NumberField("Scale factor", factor, { factor = it }, error = if (Num.parseOrNull(factor) == null) "Enter a valid number" else null)
            }
            RatioMode.Split -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainField(
                    label = "Ratio",
                    value = ratio,
                    onValueChange = { ratio = it },
                    placeholder = "2 : 3",
                    error = (RatioEngine.parse(ratio) as? CalcResult.Err)?.error?.message,
                    monospace = true,
                )
                NumberField("Total to split", total, { total = it }, error = if (Num.parseOrNull(total) == null) "Enter a valid number" else null)
                NumberField(
                    label = "Decimal places for money",
                    value = decimals,
                    onValueChange = { decimals = it },
                    integerOnly = true,
                    error = if (Num.parseIntOrNull(decimals) == null) "Enter a whole number" else null,
                )
            }
            RatioMode.UnknownPart -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainField(
                    label = "Ratio",
                    value = ratio,
                    onValueChange = { ratio = it },
                    placeholder = "2 : 3",
                    error = (RatioEngine.parse(ratio) as? CalcResult.Err)?.error?.message,
                    monospace = true,
                )
                NumberField("Known term number (1-based)", knownIndex, { knownIndex = it }, integerOnly = true, error = if (Num.parseIntOrNull(knownIndex) == null) "Enter a whole number" else null)
                NumberField("Known share", knownValue, { knownValue = it }, error = if (Num.parseOrNull(knownValue) == null) "Enter a valid number" else null)
                NumberField("Unknown term number (1-based)", unknownIndex, { unknownIndex = it }, integerOnly = true, error = if (Num.parseIntOrNull(unknownIndex) == null) "Enter a whole number" else null)
            }
            RatioMode.Combine, RatioMode.Compare -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlainField(
                    label = "First ratio",
                    value = ratio,
                    onValueChange = { ratio = it },
                    placeholder = "1 : 2",
                    error = (RatioEngine.parse(ratio) as? CalcResult.Err)?.error?.message,
                    monospace = true,
                )
                PlainField(
                    label = "Second ratio",
                    value = secondRatio,
                    onValueChange = { secondRatio = it },
                    placeholder = "2 : 3",
                    error = (RatioEngine.parse(secondRatio) as? CalcResult.Err)?.error?.message,
                    monospace = true,
                )
            }
        }

        ResultCard(
            label = outcome.label,
            value = outcome.value,
            caption = outcome.caption,
            error = outcome.error,
            onCopy = { copy(it, outcome.label) },
            copyValue = outcome.copyValue,
            onSave = outcome.copyValue?.let { text ->
                {
                    args.onRecord(
                        "${ratioModeLabel(mode)}: ${if (mode == RatioMode.Combine || mode == RatioMode.Compare) "$ratio vs $secondRatio" else ratio}",
                        text,
                    )
                }
            },
        )

        if (outcome.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                outcome.rows.forEach { (label, value) -> ResultRow(label, value) }
            }
        }

        if (outcome.steps.isNotEmpty()) {
            StepsCard(outcome.steps)
        }

        FormulaCard(formula = outcome.formula, explanation = outcome.explanation)

        if (mode == RatioMode.Combine) {
            InfoNote("Combining needs a shared linking quantity: the last term of the first ratio represents the same thing as the first term of the second.")
        }
        if (mode == RatioMode.Compare) {
            InfoNote("Ratios are compared as fractions (a ÷ b); no tolerance is needed because the cross products are exact.")
        }
    }
}

private fun ratioModeLabel(mode: RatioMode): String = when (mode) {
    RatioMode.Simplify -> "simplify"
    RatioMode.Scale -> "scale"
    RatioMode.Split -> "split total"
    RatioMode.UnknownPart -> "unknown part"
    RatioMode.Combine -> "combine"
    RatioMode.Compare -> "compare"
}
