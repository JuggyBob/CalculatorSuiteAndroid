package com.calcsuite.ui.tools.convert

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.families.FlowFamily
import com.calcsuite.engine.convert.families.FlowTimeEngine
import com.calcsuite.engine.convert.families.VolumeFamily
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

@Composable
fun FlowToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = FlowFamily,
        defaultFrom = "L_min",
        defaultTo = "us_gpm",
        formula = { from, to ->
            val f = FlowFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} m³/s per ${from.symbol})"
            } else {
                "value → m³/s → value"
            }
        },
        extraContent = { FlowTimePanels(args) },
    )
}

/** Volume ↔ time calculators built on the same flow family. */
@Composable
private fun FlowTimePanels(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()
    val flowOptions = remember {
        FlowFamily.units.map { Option(it.id, it.label, it.group, it.symbol) }
    }

    ExpandablePanel(title = "Volume from flow rate and time") {
        var rateText by rememberSaveable { mutableStateOf("10") }
        var flowUnitId by rememberSaveable { mutableStateOf("L_min") }
        var secondsText by rememberSaveable { mutableStateOf("60") }

        val rate = Num.parseOrNull(rateText)
        val seconds = Num.parseOrNull(secondsText)
        val volumeUnitId = FlowTimeEngine.volumeUnitFor(flowUnitId)
        val volumeUnitSymbol = volumeUnitId?.let { VolumeFamily.requireUnit(it).symbol } ?: ""
        val conversion: CalcResult<BigDecimal>? = if (rate != null && seconds != null) {
            FlowTimeEngine.volumeFrom(rate, flowUnitId, seconds)
        } else {
            null
        }
        val volume = (conversion as? CalcResult.Ok)?.value
        val error = when {
            rateText.isBlank() || secondsText.isBlank() -> null
            rate == null -> "Enter a valid flow rate"
            seconds == null -> "Enter a valid duration"
            conversion is CalcResult.Err -> conversion.error.message
            else -> null
        }

        SelectField(
            label = "Flow rate unit",
            options = flowOptions,
            selected = flowUnitId,
            onSelect = { flowUnitId = it },
        )
        NumberField(
            label = "Flow rate",
            value = rateText,
            onValueChange = { rateText = it },
            unit = FlowFamily.requireUnit(flowUnitId).symbol,
            error = if (rate == null && rateText.isNotBlank()) "Enter a valid number" else null,
        )
        NumberField(
            label = "Duration",
            value = secondsText,
            onValueChange = { secondsText = it },
            unit = "s",
            error = if (seconds == null && secondsText.isNotBlank()) "Enter a valid number" else null,
        )
        ResultCard(
            label = "Volume",
            value = volume?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = volumeUnitSymbol,
            error = when {
                volume == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Volume") },
            copyValue = volume?.let { Num.formatForCopy(it) },
            onSave = volume?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(rate!!)} ${FlowFamily.requireUnit(flowUnitId).symbol} for " +
                            "${Num.formatForCopy(seconds!!)} s",
                        "${Num.formatForCopy(it)} $volumeUnitSymbol",
                    )
                }
            },
        )
        InfoNote(
            "Volume = flow rate × time. The result appears in the volume unit that pairs with the " +
                "chosen flow unit (m³/s → m³, L/min → L, gpm → US gallons).",
        )
    }

    ExpandablePanel(title = "Time to deliver a volume") {
        var volumeText by rememberSaveable { mutableStateOf("100") }
        var volumeUnitId by rememberSaveable { mutableStateOf("L") }
        var rateText by rememberSaveable { mutableStateOf("10") }
        var flowUnitId by rememberSaveable { mutableStateOf("L_min") }

        val volumeOptions = remember {
            VolumeFamily.units.map { Option(it.id, it.label, it.group, it.symbol) }
        }
        val volume = Num.parseOrNull(volumeText)
        val rate = Num.parseOrNull(rateText)
        val conversion: CalcResult<BigDecimal>? = if (volume != null && rate != null) {
            FlowTimeEngine.timeFor(volume, volumeUnitId, rate, flowUnitId)
        } else {
            null
        }
        val seconds = (conversion as? CalcResult.Ok)?.value
        val error = when {
            volumeText.isBlank() || rateText.isBlank() -> null
            volume == null -> "Enter a valid volume"
            rate == null -> "Enter a valid flow rate"
            conversion is CalcResult.Err -> conversion.error.message
            else -> null
        }

        SelectField(
            label = "Volume unit",
            options = volumeOptions,
            selected = volumeUnitId,
            onSelect = { volumeUnitId = it },
        )
        NumberField(
            label = "Volume",
            value = volumeText,
            onValueChange = { volumeText = it },
            unit = VolumeFamily.requireUnit(volumeUnitId).symbol,
            error = if (volume == null && volumeText.isNotBlank()) "Enter a valid number" else null,
        )
        SelectField(
            label = "Flow rate unit",
            options = flowOptions,
            selected = flowUnitId,
            onSelect = { flowUnitId = it },
        )
        NumberField(
            label = "Flow rate",
            value = rateText,
            onValueChange = { rateText = it },
            unit = FlowFamily.requireUnit(flowUnitId).symbol,
            error = if (rate == null && rateText.isNotBlank()) "Enter a valid number" else null,
        )
        ResultCard(
            label = "Time",
            value = seconds?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = "s",
            error = when {
                seconds == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Time") },
            copyValue = seconds?.let { Num.formatForCopy(it) },
            onSave = seconds?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(volume!!)} ${VolumeFamily.requireUnit(volumeUnitId).symbol} at " +
                            "${Num.formatForCopy(rate!!)} ${FlowFamily.requireUnit(flowUnitId).symbol}",
                        "${Num.formatForCopy(it)} s",
                    )
                }
            },
        )
        if (seconds != null) {
            Column {
                ResultRow(
                    label = "Minutes",
                    value = fmt.num(seconds.divide(BigDecimal("60"), UnitFamily.CTX)),
                    unit = "min",
                )
                ResultRow(
                    label = "Hours",
                    value = fmt.num(
                        seconds.divide(BigDecimal("3600"), UnitFamily.CTX),
                    ),
                    unit = "h",
                )
            }
        }
        FormulaCard(
            formula = "time = volume ÷ flow rate",
            explanation = "The volume is converted to m³ and the rate to m³/s before dividing, so any " +
                "combination of flow and volume units is valid.",
        )
    }
}
