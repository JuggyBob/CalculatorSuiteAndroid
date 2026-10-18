package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.convert.UnitDef
import com.calcsuite.engine.convert.families.TemperatureFamily
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs

/**
 * Temperature is affine (each scale has its own zero point), so instead of the
 * generic converter this screen shows Celsius, Fahrenheit and Kelvin
 * simultaneously. Below absolute zero the family validator rejects the value
 * for every scale.
 */
@Composable
fun TemperatureToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var input by rememberSaveable { mutableStateOf("20") }
    var fromId by rememberSaveable { mutableStateOf("C") }

    val from: UnitDef = TemperatureFamily.requireUnit(fromId)
    val parsed = Num.parseOrNull(input)
    val units = listOf("C", "F", "K").map { TemperatureFamily.requireUnit(it) }

    val conversions = units.associateWith { unit ->
        parsed?.let { TemperatureFamily.convert(it, from, unit) }
    }
    val firstError: String? = when {
        input.isBlank() -> null
        parsed == null -> "Enter a valid number"
        else -> conversions.values.filterIsInstance<CalcResult.Err>().firstOrNull()?.error?.message
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = listOf(
                "C" to "Celsius °C",
                "F" to "Fahrenheit °F",
                "K" to "Kelvin K",
            ),
            selected = fromId,
            onSelect = { fromId = it },
            label = "Input scale",
        )

        NumberField(
            label = "Temperature",
            value = input,
            onValueChange = { input = it },
            unit = from.symbol,
            error = when {
                parsed == null && input.isNotBlank() -> "Enter a valid number"
                else -> firstError
            },
        )

        units.forEach { unit ->
            val result = conversions[unit]
            val value = (result as? CalcResult.Ok)?.value
            ResultCard(
                label = unit.label,
                value = value?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
                unit = unit.symbol,
                error = when {
                    value == null -> firstError
                    else -> null
                },
                onCopy = { copy(it, unit.label) },
                copyValue = value?.let { Num.formatForCopy(it) },
                onSave = value?.let {
                    {
                        args.onRecord(
                            "${Num.formatForCopy(parsed!!)} ${from.symbol} → ${unit.symbol}",
                            "${Num.formatForCopy(it)} ${unit.symbol}",
                        )
                    }
                },
            )
        }

        FormulaCard(
            formula = "°F = °C × 9/5 + 32    K = °C + 273.15",
            explanation = "Each scale is affine: value in kelvin = value × factor + offset. The conversion is " +
                "computed at the 34-digit working precision and rounded for display only.",
            variables = listOf(
                "°C" to "Celsius, 0 °C = 273.15 K",
                "°F" to "Fahrenheit, 32 °F = 0 °C",
                "K" to "Kelvin, thermodynamic temperature",
            ),
        )

        TemperatureFamily.notes.forEach { InfoNote(it) }
    }
}
