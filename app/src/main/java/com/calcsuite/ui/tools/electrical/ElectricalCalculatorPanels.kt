package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.calcsuite.engine.electrical.CalculatorResult
import com.calcsuite.engine.electrical.ElectricalCalculators
import com.calcsuite.engine.electrical.ElectricalUnits
import com.calcsuite.ui.components.ActionButton
import com.calcsuite.ui.components.ActionRow
import com.calcsuite.ui.components.ExplanationSections
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

/**
 * The result of a calculator, shown with the same sections as the circuit's
 * explanation.
 */
@Composable
fun CalculatorResultCard(result: CalculatorResult) {
    ResultCard(
        label = result.title,
        value = result.headline,
        caption = result.formula,
    )
    if (result.rows.isNotEmpty()) {
        SectionCard(title = "The values") {
            for ((label, value) in result.rows) {
                com.calcsuite.ui.components.ResultRow(label = label, value = value)
            }
        }
    }
    ExplanationSections(
        title = result.title,
        value = result.headline,
        meaning = result.rows.firstOrNull()?.let { "${it.first}: ${it.second}" } ?: "",
        formula = result.formula,
        variables = result.variables,
        steps = result.steps,
        why = result.why,
        checkTitle = "Check your answer",
        checks = result.checks,
    )
}

/**
 * A voltage divider.
 */
@Composable
fun DividerPanel(args: ToolArgs) {
    var voltage by rememberSaveable { mutableStateOf("12") }
    var top by rememberSaveable { mutableStateOf("10000") }
    var bottom by rememberSaveable { mutableStateOf("20000") }

    val v = Num.parseOrNull(voltage)
    val r1 = Num.parseOrNull(top)
    val r2 = Num.parseOrNull(bottom)
    val result = remember(v, r1, r2) {
        if (v == null || r1 == null || r2 == null) {
            null
        } else {
            ElectricalCalculators.voltageDivider(r1, r2, v)
        }
    }

    SectionCard(title = "A voltage divider") {
        NumberField(
            label = "The input voltage (V)",
            value = voltage,
            onValueChange = { voltage = it },
        )
        NumberField(
            label = "The resistor above the output (Ω)",
            value = top,
            onValueChange = { top = it },
        )
        NumberField(
            label = "The resistor below the output (Ω)",
            value = bottom,
            onValueChange = { bottom = it },
        )
    }

    when (result) {
        null -> InfoNote("Fill in the values to see the working.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}

/**
 * A current divider.
 */
@Composable
fun CurrentDividerPanel(args: ToolArgs) {
    var current by rememberSaveable { mutableStateOf("0.18") }
    var first by rememberSaveable { mutableStateOf("100") }
    var second by rememberSaveable { mutableStateOf("200") }

    val i = Num.parseOrNull(current)
    val r1 = Num.parseOrNull(first)
    val r2 = Num.parseOrNull(second)
    val result = remember(i, r1, r2) {
        if (i == null || r1 == null || r2 == null) {
            null
        } else {
            ElectricalCalculators.currentDivider(r1, r2, i)
        }
    }

    SectionCard(title = "A current divider") {
        NumberField(
            label = "The total current (A)",
            value = current,
            onValueChange = { current = it },
        )
        NumberField(
            label = "The resistance of the first branch (Ω)",
            value = first,
            onValueChange = { first = it },
        )
        NumberField(
            label = "The resistance of the second branch (Ω)",
            value = second,
            onValueChange = { second = it },
        )
    }

    when (result) {
        null -> InfoNote("Fill in the values to see the working.")
        is CalcResult.Err -> InfoNote(result.error.message)
        is CalcResult.Ok -> CalculatorResultCard(result.value)
    }
}
