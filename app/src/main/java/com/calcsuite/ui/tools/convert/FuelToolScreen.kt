package com.calcsuite.ui.tools.convert

import androidx.compose.foundation.layout.Arrangement
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
import com.calcsuite.engine.convert.FuelEngine
import com.calcsuite.engine.convert.FuelFamily
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
fun FuelToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var fromId by rememberSaveable { mutableStateOf("l_100km") }
    var toId by rememberSaveable { mutableStateOf("mpg_us") }
    var input by rememberSaveable { mutableStateOf("7.84") }

    val options = remember {
        FuelFamily.unitIds.map { Option(it, FuelFamily.label(it), null, FuelFamily.symbol(it)) }
    }
    val parsed = Num.parseOrNull(input)
    val conversion: CalcResult<BigDecimal>? = parsed?.let { FuelEngine.convert(it, fromId, toId) }
    val resultValue = (conversion as? CalcResult.Ok)?.value
    val error = when {
        input.isBlank() -> null
        parsed == null -> "Enter a valid number"
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
            label = "Consumption",
            value = input,
            onValueChange = { input = it },
            unit = FuelFamily.symbol(fromId),
            error = error,
        )

        ResultCard(
            label = "Result",
            value = resultValue?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = FuelFamily.symbol(toId),
            error = when {
                resultValue == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Result") },
            copyValue = resultValue?.let { Num.formatForCopy(it) },
            onSave = resultValue?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(parsed!!)} ${FuelFamily.symbol(fromId)} → ${FuelFamily.symbol(toId)}",
                        "${Num.formatForCopy(it)} ${FuelFamily.symbol(toId)}",
                    )
                }
            },
        )

        val oneWay = remember(fromId, toId) { FuelEngine.convert(BigDecimal.ONE, fromId, toId).valueOrNull() }
        val otherWay = remember(fromId, toId) { FuelEngine.convert(BigDecimal.ONE, toId, fromId).valueOrNull() }
        if (oneWay != null && otherWay != null) {
            ResultRow(
                label = "1 ${FuelFamily.symbol(fromId)} =",
                value = fmt.num(oneWay, precision = maxOf(fmt.precision, 10)),
                unit = FuelFamily.symbol(toId),
            )
            ResultRow(
                label = "1 ${FuelFamily.symbol(toId)} =",
                value = fmt.num(otherWay, precision = maxOf(fmt.precision, 10)),
                unit = FuelFamily.symbol(fromId),
            )
        }

        FormulaCard(
            formula = "L/100 km = 100 ÷ (km/L) = 235.21 ÷ (mpg US) = 282.48 ÷ (mpg Imp)",
            explanation = "Fuel consumption is reciprocal: litres per 100 km is the canonical form and every " +
                "other unit is converted through it. Constants are computed from exact gallon and mile definitions.",
        )

        FuelTripPanel(args, fmt)
    }
}

@Composable
private fun FuelTripPanel(args: ToolArgs, fmt: Fmt) {
    val copy = rememberCopyAction()
    ExpandablePanel(title = "Trip fuel and cost") {
        var distanceText by rememberSaveable { mutableStateOf("450") }
        var consumptionText by rememberSaveable { mutableStateOf("7.84") }
        var consumptionUnit by rememberSaveable { mutableStateOf("l_100km") }
        var priceText by rememberSaveable { mutableStateOf("1.75") }

        val options = remember {
            FuelFamily.unitIds.map { Option(it, FuelFamily.label(it), null, FuelFamily.symbol(it)) }
        }
        val distance = Num.parseOrNull(distanceText)
        val consumption = Num.parseOrNull(consumptionText)
        val price = Num.parseOrNull(priceText)
        val trip: CalcResult<FuelEngine.TripFuel>? = if (distance != null && consumption != null && price != null) {
            FuelEngine.tripFuel(distance, consumption, consumptionUnit, price)
        } else {
            null
        }
        val tripValue = (trip as? CalcResult.Ok)?.value
        val error = when {
            distanceText.isBlank() || consumptionText.isBlank() || priceText.isBlank() -> null
            distance == null -> "Enter a valid distance"
            consumption == null -> "Enter a valid consumption"
            price == null -> "Enter a valid price"
            trip is CalcResult.Err -> trip.error.message
            else -> null
        }

        NumberField(
            label = "Trip distance",
            value = distanceText,
            onValueChange = { distanceText = it },
            unit = "km",
            error = if (distance == null && distanceText.isNotBlank()) "Enter a valid number" else null,
        )
        SelectField(
            label = "Consumption unit",
            options = options,
            selected = consumptionUnit,
            onSelect = { consumptionUnit = it },
        )
        NumberField(
            label = "Consumption",
            value = consumptionText,
            onValueChange = { consumptionText = it },
            unit = FuelFamily.symbol(consumptionUnit),
            error = if (consumption == null && consumptionText.isNotBlank()) "Enter a valid number" else null,
        )
        NumberField(
            label = "Price per litre",
            value = priceText,
            onValueChange = { priceText = it },
            unit = "per L",
            error = if (price == null && priceText.isNotBlank()) "Enter a valid number" else null,
        )

        ResultCard(
            label = "Fuel needed",
            value = tripValue?.let { fmt.num(it.fuelLitres, precision = maxOf(fmt.precision, 10)) },
            unit = "L",
            error = when {
                tripValue == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Fuel needed") },
            copyValue = tripValue?.let { Num.formatForCopy(it.fuelLitres) },
            onSave = tripValue?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(distance!!)} km at ${Num.formatForCopy(consumption!!)} " +
                            "${FuelFamily.symbol(consumptionUnit)}",
                        "${Num.formatForCopy(it.fuelLitres)} L, cost ${Num.formatForCopy(Num.roundTo(it.cost, 2))}",
                    )
                }
            },
        )
        if (tripValue != null) {
            ResultCard(
                label = "Fuel cost",
                value = fmt.num(Num.roundTo(tripValue.cost, 2), precision = 10),
                caption = "cost = litres × price per litre",
                onCopy = { copy(it, "Fuel cost") },
                copyValue = Num.formatForCopy(Num.roundTo(tripValue.cost, 2)),
            )
            if (consumptionUnit != "l_100km") {
                ResultRow(
                    label = "Consumption in L/100 km",
                    value = fmt.num(tripValue.litresPer100km, precision = maxOf(fmt.precision, 10)),
                    unit = "L/100km",
                )
            }
            tripValue.note?.let { InfoNote(it) }
        }
    }
}
