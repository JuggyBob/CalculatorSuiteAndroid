package com.calcsuite.ui.tools.convert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.calcsuite.engine.convert.CookingEngine
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.Fmt
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
import java.math.BigDecimal

@Composable
fun CookingToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var fromId by rememberSaveable { mutableStateOf("cup_us") }
    var toId by rememberSaveable { mutableStateOf("mL") }
    var input by rememberSaveable { mutableStateOf("1") }

    val options = remember {
        CookingEngine.units.map { Option(it.id, it.label, it.group, it.symbol) }
    }
    val from = CookingEngine.unit(fromId)
    val to = CookingEngine.unit(toId)
    val parsed = Num.parseOrNull(input)
    val sameDimension = from != null && to != null && from.dimension == to.dimension
    val conversion: CalcResult<BigDecimal>? =
        if (parsed != null && sameDimension) CookingEngine.convert(parsed, fromId, toId) else null
    val resultValue = (conversion as? CalcResult.Ok)?.value
    val error = when {
        input.isBlank() -> null
        parsed == null -> "Enter a valid number"
        !sameDimension -> "Volume ↔ mass needs a density — open the panel below"
        conversion is CalcResult.Err -> conversion.error.message
        else -> null
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectField(
                label = "From",
                options = options,
                selected = fromId,
                onSelect = { fromId = it },
                modifier = Modifier.weight(1f),
            )
            SelectField(
                label = "To",
                options = options,
                selected = toId,
                onSelect = { toId = it },
                modifier = Modifier.weight(1f),
            )
        }

        NumberField(
            label = "Amount",
            value = input,
            onValueChange = { input = it },
            unit = from?.symbol ?: "",
            error = error,
        )

        ResultCard(
            label = "Result",
            value = resultValue?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = to?.symbol,
            error = when {
                resultValue == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Result") },
            copyValue = resultValue?.let { Num.formatForCopy(it) },
            onSave = resultValue?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(parsed!!)} ${from?.symbol} → ${to?.symbol}",
                        "${Num.formatForCopy(it)} ${to?.symbol}",
                    )
                }
            },
        )

        val factor = remember(fromId, toId) {
            if (sameDimension) CookingEngine.convert(BigDecimal.ONE, fromId, toId).valueOrNull() else null
        }
        if (factor != null) {
            ResultRow(
                label = "1 ${from?.symbol} =",
                value = fmt.num(factor, precision = maxOf(fmt.precision, 10)),
                unit = to?.symbol,
            )
        }

        FormulaCard(
            formula = "mass = volume × density        volume = mass ÷ density",
            explanation = "Spoons, cups and millilitres are volumes; grams, ounces and pounds are masses. " +
                "Within one dimension the conversion is a fixed factor; across dimensions a density is required.",
            variables = listOf(
                "density" to "ingredient density in g/mL",
                "cup (US)" to "236.5882365 mL",
                "cup (Imp)" to "284.130625 mL",
            ),
        )

        DensityPanel(args, fmt, fromId, toId, input, parsed)
    }
}

@Composable
private fun DensityPanel(
    args: ToolArgs,
    fmt: Fmt,
    fromId: String,
    toId: String,
    input: String,
    parsed: BigDecimal?,
) {
    val copy = rememberCopyAction()
    ExpandablePanel(title = "Convert between mass and volume (density)") {
        var ingredientName by rememberSaveable { mutableStateOf(CookingEngine.ingredients.first().name) }
        var densityText by rememberSaveable { mutableStateOf("1.0") }

        val ingredientOptions = remember {
            CookingEngine.ingredients.map { Option(it.name, it.name) }
        }
        val density = Num.parseOrNull(densityText)
        val from = CookingEngine.unit(fromId)
        val to = CookingEngine.unit(toId)
        val conversion: CalcResult<BigDecimal>? =
            if (parsed != null && density != null) {
                CookingEngine.convertWithDensity(parsed, fromId, toId, density)
            } else {
                null
            }
        val resultValue = (conversion as? CalcResult.Ok)?.value
        val error = when {
            input.isBlank() || densityText.isBlank() -> null
            parsed == null -> "Enter a valid amount"
            density == null -> "Enter a valid density"
            conversion is CalcResult.Err -> conversion.error.message
            else -> null
        }

        SelectField(
            label = "Ingredient",
            options = ingredientOptions,
            selected = ingredientName,
            onSelect = { name ->
                ingredientName = name
                CookingEngine.ingredient(name)?.let { densityText = it.densityGPerMl.toPlainString() }
            },
        )
        NumberField(
            label = "Density",
            value = densityText,
            onValueChange = { densityText = it },
            unit = "g/mL",
            error = if (density == null && densityText.isNotBlank()) "Enter a valid number" else null,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                ResultRow(label = "From", value = from?.label ?: fromId, unit = from?.symbol)
            }
            Column(Modifier.weight(1f)) {
                ResultRow(label = "To", value = to?.label ?: toId, unit = to?.symbol)
            }
        }
        ResultCard(
            label = "Result (with density)",
            value = resultValue?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = to?.symbol,
            caption = "using ${ingredientName.lowercase()} at ${densityText.ifBlank { "?" }} g/mL",
            error = when {
                resultValue == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Result") },
            copyValue = resultValue?.let { Num.formatForCopy(it) },
            onSave = resultValue?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(parsed!!)} ${from?.symbol} ${fromId} → ${to?.symbol} " +
                            "at ${densityText} g/mL",
                        "${Num.formatForCopy(it)} ${to?.symbol}",
                    )
                }
            },
        )
        InfoNote(
            "Densities follow USDA / King Arthur Baking ingredient weight charts and vary with packing: " +
                "a cup of sifted flour weighs noticeably less than a packed cup.",
        )
        ResultRow(
            label = "Typical water reference",
            value = "${fmt.num(BigDecimal.ONE, precision = 4)} g/mL",
        )
    }
}
