package com.calcsuite.ui.tools.convert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.convert.UnitDef
import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs

/**
 * Generic screen for every linear/affine conversion family: source and target
 * selectors, immediate result, swap action, factor display, formula and notes.
 *
 * @param extraContent optional additional calculator below the converter
 *        (used e.g. by the data-transfer screen for transfer-time estimates).
 */
@Composable
fun LinearConverterScreen(
    args: ToolArgs,
    family: UnitFamily,
    defaultFrom: String? = null,
    defaultTo: String? = null,
    formula: ((UnitDef, UnitDef) -> String)? = null,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var fromId by rememberSaveable { mutableStateOf(defaultFrom ?: family.baseUnitId) }
    var toId by rememberSaveable {
        mutableStateOf(
            defaultTo ?: family.units.firstOrNull { it.id != family.baseUnitId }?.id ?: family.baseUnitId,
        )
    }
    var input by rememberSaveable { mutableStateOf("1") }

    val from = family.requireUnit(fromId)
    val to = family.requireUnit(toId)
    val options = remember(family) {
        family.units.map { Option(it.id, it.label, it.group, it.symbol) }
    }

    val parsed = Num.parseOrNull(input)
    val conversion: CalcResult<java.math.BigDecimal>? = parsed?.let { family.convert(it, from, to) }
    val error: String? = when {
        input.isBlank() -> null
        parsed == null -> "Enter a valid number"
        conversion is CalcResult.Err -> conversion.error.message
        else -> null
    }
    val resultValue = (conversion as? CalcResult.Ok)?.value

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SelectField(
                label = "From",
                options = options,
                selected = fromId,
                onSelect = { fromId = it },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val previousFrom = fromId
                    fromId = toId
                    toId = previousFrom
                    resultValue?.let { input = Num.formatForCopy(it) }
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = "Swap units",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            SelectField(
                label = "To",
                options = options,
                selected = toId,
                onSelect = { toId = it },
                modifier = Modifier.weight(1f),
            )
        }

        NumberField(
            label = "Value",
            value = input,
            onValueChange = { input = it },
            unit = from.symbol,
            error = error,
        )

        ResultCard(
            label = "Result",
            value = resultValue?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = to.symbol,
            error = when {
                resultValue == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Result") },
            copyValue = resultValue?.let { Num.formatForCopy(it) },
            onSave = resultValue?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(parsed!!)} ${from.symbol} → ${to.symbol}",
                        "${Num.formatForCopy(it)} ${to.symbol}",
                    )
                }
            },
        )

        val factor = remember(fromId, toId) { family.factorBetween(fromId, toId).valueOrNull() }
        if (factor != null) {
            Column {
                ResultRow(
                    label = "1 ${from.symbol} =",
                    value = fmt.num(factor, precision = maxOf(fmt.precision, 10)),
                    unit = to.symbol,
                )
                val inverse = remember(fromId, toId) {
                    family.factorBetween(toId, fromId).valueOrNull()
                }
                if (inverse != null) {
                    ResultRow(
                        label = "1 ${to.symbol} =",
                        value = fmt.num(inverse, precision = maxOf(fmt.precision, 10)),
                        unit = from.symbol,
                    )
                }
            }
        }

        if (formula != null) {
            FormulaCard(
                formula = formula(from, to),
                explanation = "Conversions in this family are defined relative to the ${family.name.lowercase()} base " +
                    "unit and applied without loss of precision before the result is rounded for display.",
            )
        }

        family.notes.forEach { InfoNote(it) }

        if (extraContent != null) {
            extraContent()
        }

        Spacer(Modifier.size(4.dp))
        Text(
            "Tip: tap the bookmark icon on the result to save this conversion to history.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
