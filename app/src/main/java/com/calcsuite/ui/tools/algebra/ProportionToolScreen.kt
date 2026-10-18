package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.algebra.ProportionEngine
import com.calcsuite.engine.algebra.ProportionMode
import com.calcsuite.engine.algebra.ProportionSlot
import com.calcsuite.engine.algebra.ProportionSolution
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun ProportionToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var modeName by rememberSaveable { mutableStateOf(ProportionMode.Direct.name) }
    val mode = ProportionMode.valueOf(modeName)
    var unknownName by rememberSaveable { mutableStateOf(ProportionSlot.D.name) }
    val unknown = ProportionSlot.valueOf(unknownName)

    var aText by rememberSaveable { mutableStateOf("2") }
    var bText by rememberSaveable { mutableStateOf("3") }
    var cText by rememberSaveable { mutableStateOf("8") }
    var dText by rememberSaveable { mutableStateOf("") }

    fun num(v: java.math.BigDecimal) = fmt.num(v, precision = maxOf(fmt.precision, 10))

    fun textOf(slot: ProportionSlot): String = when (slot) {
        ProportionSlot.A -> aText
        ProportionSlot.B -> bText
        ProportionSlot.C -> cText
        ProportionSlot.D -> dText
    }

    fun setText(slot: ProportionSlot, text: String) {
        when (slot) {
            ProportionSlot.A -> aText = text
            ProportionSlot.B -> bText = text
            ProportionSlot.C -> cText = text
            ProportionSlot.D -> dText = text
        }
    }

    val a = Num.parseOrNull(aText)
    val b = Num.parseOrNull(bText)
    val c = Num.parseOrNull(cText)
    val d = Num.parseOrNull(dText)
    val knownCount = listOf(
        ProportionSlot.A to a,
        ProportionSlot.B to b,
        ProportionSlot.C to c,
        ProportionSlot.D to d,
    ).count { it.first != unknown && it.second != null }

    val solution: CalcResult<ProportionSolution>? = if (knownCount == 3) {
        remember(mode, unknown, aText, bText, cText, dText) {
            ProportionEngine.solve(mode, a, b, c, d, unknown)
        }
    } else {
        null
    }
    val solved = (solution as? CalcResult.Ok)?.value
    val solveError: String? = when {
        solution is CalcResult.Err -> solution.error.message
        solution == null -> "Fill in the three known values; only ${unknown.label} is unknown"
        else -> null
    }

    fun fieldError(slot: ProportionSlot): String? {
        if (slot == unknown) return null
        val text = textOf(slot)
        if (text.isBlank()) return "Enter a value"
        return if (Num.parseOrNull(text) == null) "Enter a valid number" else null
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        actions = {
            TextButton(onClick = {
                aText = "2"; bText = "3"; cText = "8"; dText = ""; unknownName = ProportionSlot.D.name
            }) { Text("Reset") }
        },
    ) {
        SegmentedChoice(
            options = listOf(
                ProportionMode.Direct.name to "Direct a/b = c/d",
                ProportionMode.Inverse.name to "Inverse a·b = c·d",
            ),
            selected = modeName,
            onSelect = { modeName = it },
            label = "Mode",
        )

        SegmentedChoice(
            options = ProportionSlot.entries.map { it.name to it.label },
            selected = unknownName,
            onSelect = { slotName ->
                unknownName = slotName
                setText(ProportionSlot.valueOf(slotName), "")
            },
            label = "Unknown",
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProportionSlot.entries.forEach { slot ->
                val isUnknown = slot == unknown
                NumberField(
                    label = if (mode == ProportionMode.Direct) {
                        when (slot) {
                            ProportionSlot.A -> "a (numerator, left)"
                            ProportionSlot.B -> "b (denominator, left)"
                            ProportionSlot.C -> "c (numerator, right)"
                            ProportionSlot.D -> "d (denominator, right)"
                        }
                    } else {
                        when (slot) {
                            ProportionSlot.A -> "x₁ (first quantity)"
                            ProportionSlot.B -> "y₁ (first pairing)"
                            ProportionSlot.C -> "x₂ (second quantity)"
                            ProportionSlot.D -> "y₂ (second pairing)"
                        }
                    },
                    value = if (isUnknown) solved?.let { num(it.value) } ?: "" else textOf(slot),
                    onValueChange = { setText(slot, it) },
                    enabled = !isUnknown,
                    placeholder = if (isUnknown) "unknown" else null,
                    error = fieldError(slot),
                )
            }
        }

        ResultCard(
            label = when (mode) {
                ProportionMode.Direct -> "Solve a / b = c / d for ${unknown.label}"
                ProportionMode.Inverse -> "Solve a × b = c × d for ${unknown.label}"
            },
            value = solved?.let { num(it.value) },
            error = solveError,
            caption = solved?.equation,
            onCopy = { copy(it, "Solution") },
            copyValue = solved?.value?.let { Num.formatForCopy(it) },
            onSave = solved?.let { result ->
                {
                    args.onRecord(
                        "${mode.name.lowercase()} proportion, solving for ${unknown.label}: ${result.equation}",
                        Num.formatForCopy(result.value),
                    )
                }
            },
        )

        if (solved != null) {
            ResultRow("Verification", solved.verification)
            ResultRow("Equation", solved.equation)
        }

        if (solved != null && solved.steps.isNotEmpty()) {
            StepsCard(solved.steps)
        }

        FormulaCard(
            formula = when (mode) {
                ProportionMode.Direct -> "a / b = c / d  ⟺  a × d = b × c"
                ProportionMode.Inverse -> "a × b = c × d  (the product stays constant)"
            },
            explanation = when (mode) {
                ProportionMode.Direct -> "Two ratios are equal: as a grows, c grows in the same proportion. Three values determine the fourth."
                ProportionMode.Inverse -> "Inverse proportion: when one quantity grows the other shrinks so the product stays the same (e.g. workers × days)."
            },
            variables = listOf(
                "a, b" to "the first pair of the proportion",
                "c, d" to "the second pair of the proportion",
            ),
        )

        InfoNote(
            if (mode == ProportionMode.Direct) {
                "A zero denominator makes the proportion undefined, so b and d must not be zero."
            } else {
                "The value you solve for must not be divided by zero: the partner in its product must not be zero."
            },
        )
    }
}
