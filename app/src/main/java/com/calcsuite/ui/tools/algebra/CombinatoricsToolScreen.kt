package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
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
import com.calcsuite.engine.algebra.CombinatoricsEngine
import com.calcsuite.engine.algebra.ProbabilityResult
import com.calcsuite.engine.algebra.SelectionResult
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.theme.NumberTextStyle
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal
import java.math.BigInteger

private enum class ComboMode { Combination, Permutation, Probability }

@Composable
fun CombinatoricsToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var modeName by rememberSaveable { mutableStateOf(ComboMode.Combination.name) }
    val mode = ComboMode.valueOf(modeName)
    var nText by rememberSaveable { mutableStateOf("5") }
    var rText by rememberSaveable { mutableStateOf("2") }
    var populationText by rememberSaveable { mutableStateOf("49") }
    var specialText by rememberSaveable { mutableStateOf("6") }
    var drawsText by rememberSaveable { mutableStateOf("6") }
    var matchesText by rememberSaveable { mutableStateOf("6") }

    fun num(v: BigDecimal) = fmt.num(v, precision = maxOf(fmt.precision, 10))

    val n = Num.parseIntOrNull(nText)
    val r = Num.parseIntOrNull(rText)
    val population = Num.parseIntOrNull(populationText)
    val special = Num.parseIntOrNull(specialText)
    val draws = Num.parseIntOrNull(drawsText)
    val matches = Num.parseIntOrNull(matchesText)

    fun plainProbability(value: BigDecimal): String =
        value.stripTrailingZeros().toPlainString()

    val selectionResult: CalcResult<SelectionResult>? =
        if (mode != ComboMode.Probability && n != null && r != null) {
            when (mode) {
                ComboMode.Combination -> CombinatoricsEngine.combinations(n, r)
                ComboMode.Permutation -> CombinatoricsEngine.permutations(n, r)
                ComboMode.Probability -> null
            }
        } else {
            null
        }
    val selection = (selectionResult as? CalcResult.Ok)?.value
    val selectionError = (selectionResult as? CalcResult.Err)?.error?.message

    val probabilityResult: CalcResult<ProbabilityResult>? =
        if (mode == ComboMode.Probability &&
            population != null && special != null && draws != null && matches != null
        ) {
            CombinatoricsEngine.selectionProbability(population, special, draws, matches)
        } else {
            null
        }
    val probability = (probabilityResult as? CalcResult.Ok)?.value
    val probabilityError = (probabilityResult as? CalcResult.Err)?.error?.message

    val resultText: String? = when (mode) {
        ComboMode.Probability -> probability?.let { plainProbability(it.probability) }
        else -> selection?.value?.toString()
    }
    val resultLabel: String = when (mode) {
        ComboMode.Combination -> "Combinations"
        ComboMode.Permutation -> "Permutations"
        ComboMode.Probability -> "Probability"
    }
    val errorText: String? = when {
        mode == ComboMode.Probability -> probabilityError
        n == null -> if (nText.isBlank()) "Enter n" else "n must be a whole number"
        r == null -> if (rText.isBlank()) "Enter r" else "r must be a whole number"
        else -> selectionError
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        actions = {
            TextButton(onClick = {
                nText = "5"; rText = "2"
                populationText = "49"; specialText = "6"; drawsText = "6"; matchesText = "6"
            }) { Text("Reset") }
        },
    ) {
        SegmentedChoice(
            options = listOf(
                ComboMode.Combination.name to "nCr",
                ComboMode.Permutation.name to "nPr",
                ComboMode.Probability.name to "Probability",
            ),
            selected = modeName,
            onSelect = { modeName = it },
            label = "Mode",
        )

        if (mode == ComboMode.Probability) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Population N", populationText, { populationText = it }, integerOnly = true, error = if (Num.parseIntOrNull(populationText) == null) "Enter a whole number" else null)
                NumberField("Special items K (e.g. drawn winners)", specialText, { specialText = it }, integerOnly = true, error = if (Num.parseIntOrNull(specialText) == null) "Enter a whole number" else null)
                NumberField("Items drawn n", drawsText, { drawsText = it }, integerOnly = true, error = if (Num.parseIntOrNull(drawsText) == null) "Enter a whole number" else null)
                NumberField("Exact matches k", matchesText, { matchesText = it }, integerOnly = true, error = if (Num.parseIntOrNull(matchesText) == null) "Enter a whole number" else null)
            }
            InfoNote("Hypergeometric model: N items contain K special ones; n are drawn without replacement. The result is the chance of exactly k matches.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("n (pool size)", nText, { nText = it }, integerOnly = true, error = if (Num.parseIntOrNull(nText) == null) "Enter a whole number" else null)
                NumberField("r (chosen)", rText, { rText = it }, integerOnly = true, error = if (Num.parseIntOrNull(rText) == null) "Enter a whole number" else null)
            }
        }

        ResultCard(
            label = resultLabel,
            value = resultText,
            caption = when {
                mode == ComboMode.Probability && probability != null ->
                    "Rounded to ${Num.EVAL_CONTEXT.precision} significant digits"
                selection != null && selection.value.toString().length > 24 ->
                    "Exact value — scroll the box below to read or copy every digit"
                else -> null
            },
            error = errorText,
            onCopy = { copy(it, resultLabel) },
            copyValue = selection?.value?.toString() ?: probability?.probability?.let { Num.formatForCopy(it) },
            onSave = resultText?.let { text ->
                {
                    args.onRecord(
                        when (mode) {
                            ComboMode.Combination -> "C($nText, $rText)"
                            ComboMode.Permutation -> "P($nText, $rText)"
                            ComboMode.Probability ->
                                "P(exactly $matchesText of $drawsText) from $populationText with $specialText special"
                        },
                        text,
                    )
                }
            },
        )

        if (selection != null) {
            val text = selection.value.toString()
            SectionCard(title = "Exact value") {
                SelectionContainer {
                    Text(
                        text,
                        style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    )
                }
                Text(
                    "${text.length} digits, computed with exact BigInteger arithmetic",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                ResultRow("Formula", selection.formula)
                ResultRow("n", selection.n.toString())
                ResultRow("r", selection.r.toString())
            }
            StepsCard(selection.steps)
            InfoNote(selection.note)
        }

        if (probability != null) {
            Column(Modifier.fillMaxWidth()) {
                ResultRow("Probability", num(probability.probability))
                probability.oneIn?.let { ResultRow("One in", num(it)) }
                ResultRow("Formula", probability.formula)
            }
            StepsCard(probability.steps)
            InfoNote(probability.note)
        }

        FormulaCard(
            formula = when (mode) {
                ComboMode.Combination -> "C(n, r) = n! ÷ (r! · (n − r)!)"
                ComboMode.Permutation -> "P(n, r) = n! ÷ (n − r)!"
                ComboMode.Probability -> "P(exactly k) = C(K, k) · C(N − K, n − k) ÷ C(N, n)"
            },
            explanation = when (mode) {
                ComboMode.Combination -> CombinatoricsEngine.CONCEPT_NOTE
                ComboMode.Permutation -> CombinatoricsEngine.CONCEPT_NOTE
                ComboMode.Probability -> "Every set of n drawn items is equally likely, so the probability is the number of favourable draws divided by all possible draws."
            },
            variables = listOf(
                "n" to "size of the pool",
                "r" to "how many items are selected",
                "N, K, n, k" to "population, special items, drawn items, exact matches",
            ),
        )

        if (selection != null && selection.value.bitLength() > 0 && selection.value < BigInteger.valueOf(10_000L)) {
            InfoNote("Small result: ${selection.value} — compare C and P for the same n and r to see the r! factor.")
        }
    }
}
