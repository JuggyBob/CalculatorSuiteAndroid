package com.calcsuite.ui.tools.other

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
import com.calcsuite.engine.other.DistanceUnit
import com.calcsuite.engine.other.FuelUnit
import com.calcsuite.engine.other.MileageEngine
import com.calcsuite.engine.other.MileageResult
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs

/**
 * Trip distance, fuel economy and trip cost.
 *
 * Distance can come from two odometer readings or be entered directly; the
 * fuel is entered in litres, US gallons or imperial gallons and the optional
 * price is interpreted per selected fuel unit. Fuel economy (L/100 km, mpg),
 * fuel consumption (km/L) and cost are separate labelled quantities.
 */
@Composable
fun MileageToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()

    var useOdometer by rememberSaveable { mutableStateOf(true) }
    var startOdometer by rememberSaveable { mutableStateOf("") }
    var endOdometer by rememberSaveable { mutableStateOf("") }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var distanceUnit by rememberSaveable { mutableStateOf(DistanceUnit.KILOMETRES) }
    var fuelText by rememberSaveable { mutableStateOf("") }
    var fuelUnit by rememberSaveable { mutableStateOf(FuelUnit.LITRES) }
    var priceText by rememberSaveable { mutableStateOf("") }

    val start = Num.parseOrNull(startOdometer)
    val end = Num.parseOrNull(endOdometer)
    val distance = Num.parseOrNull(distanceText)
    val fuel = Num.parseOrNull(fuelText)
    val price = Num.parseOrNull(priceText)
    val priceEntered = priceText.isNotBlank()

    val computed: CalcResult<MileageResult>? = when {
        fuel == null || (priceEntered && price == null) -> null
        useOdometer -> if (start != null && end != null) {
            MileageEngine.fromOdometer(start, end, distanceUnit, fuel, fuelUnit, price)
        } else {
            null
        }
        else -> if (distance != null) {
            MileageEngine.compute(distance, distanceUnit, fuel, fuelUnit, price)
        } else {
            null
        }
    }
    val result = (computed as? CalcResult.Ok)?.value
    val error = (computed as? CalcResult.Err)?.error?.message

    val fuelOptions = remember {
        FuelUnit.entries.map { Option(it, it.label, null, it.symbol) }
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = listOf(true to "Odometer", false to "Trip distance"),
            selected = useOdometer,
            onSelect = { useOdometer = it },
            label = "Distance input",
        )
        SegmentedChoice(
            options = listOf(DistanceUnit.KILOMETRES to "Kilometres", DistanceUnit.MILES to "Miles"),
            selected = distanceUnit,
            onSelect = { distanceUnit = it },
            label = "Distance unit",
        )

        if (useOdometer) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    label = "Start odometer",
                    value = startOdometer,
                    onValueChange = { startOdometer = it },
                    unit = distanceUnit.symbol,
                    placeholder = "50000",
                    error = parseErrorMileage(startOdometer),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "End odometer",
                    value = endOdometer,
                    onValueChange = { endOdometer = it },
                    unit = distanceUnit.symbol,
                    placeholder = "50300",
                    error = parseErrorMileage(endOdometer),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            NumberField(
                label = "Trip distance",
                value = distanceText,
                onValueChange = { distanceText = it },
                unit = distanceUnit.symbol,
                placeholder = "300",
                error = parseErrorMileage(distanceText),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NumberField(
                label = "Fuel used",
                value = fuelText,
                onValueChange = { fuelText = it },
                unit = fuelUnit.symbol,
                placeholder = "20",
                error = parseErrorMileage(fuelText),
                modifier = Modifier.weight(1f),
            )
            SelectField(
                label = "Fuel unit",
                options = fuelOptions,
                selected = fuelUnit,
                onSelect = { fuelUnit = it },
                modifier = Modifier.weight(1f),
            )
        }

        NumberField(
            label = "Price per ${fuelUnit.symbol} (optional)",
            value = priceText,
            onValueChange = { priceText = it },
            unit = "per ${fuelUnit.symbol}",
            placeholder = "1.50",
            error = if (priceEntered && price == null) "Enter a valid number" else null,
        )

        ResultCard(
            label = "Fuel economy",
            value = result?.let { fmt.num(it.litresPer100Km, precision = maxOf(fmt.precision, 10)) },
            unit = "L/100 km",
            approx = true,
            caption = "Volume per distance — lower is more efficient",
            error = error,
            onSave = result?.let { r ->
                {
                    val summary = if (useOdometer) {
                        "Mileage: ${startOdometer}→${endOdometer} ${distanceUnit.symbol}, " +
                            "$fuelText ${fuelUnit.symbol}"
                    } else {
                        "Mileage: $distanceText ${distanceUnit.symbol}, $fuelText ${fuelUnit.symbol}"
                    }
                    args.onRecord(
                        summary,
                        "${fmt.num(r.litresPer100Km, precision = maxOf(fmt.precision, 10))} L/100 km, " +
                            "${fmt.num(r.kmPerLitre, precision = maxOf(fmt.precision, 10))} km/L" +
                            (r.totalCost?.let { ", cost ${fmt.num(it, precision = maxOf(fmt.precision, 10))}" } ?: ""),
                    )
                }
            },
        )

        if (result != null) {
            ResultRow(
                label = "Fuel consumption",
                value = fmt.num(result.kmPerLitre, precision = maxOf(fmt.precision, 10)),
                unit = "km/L",
                emphasis = true,
            )
            ResultRow(
                label = "Fuel economy",
                value = fmt.num(result.mpgUs, precision = maxOf(fmt.precision, 10)),
                unit = "mpg (US)",
            )
            ResultRow(
                label = "Fuel economy",
                value = fmt.num(result.mpgImperial, precision = maxOf(fmt.precision, 10)),
                unit = "mpg (Imp)",
            )
            ResultRow(
                label = "Trip distance",
                value = fmt.num(result.distanceKm, precision = maxOf(fmt.precision, 10)),
                unit = "km",
            )
            ResultRow(
                label = "Trip distance",
                value = fmt.num(result.distanceMiles, precision = maxOf(fmt.precision, 10)),
                unit = "mi",
            )
            ResultRow(
                label = "Fuel used",
                value = fmt.num(result.fuelLitres, precision = maxOf(fmt.precision, 10)),
                unit = "L",
            )
            ResultRow(
                label = "Fuel used",
                value = fmt.num(result.fuelUsGallons, precision = maxOf(fmt.precision, 10)),
                unit = "gal (US)",
            )
            ResultRow(
                label = "Fuel used",
                value = fmt.num(result.fuelImperialGallons, precision = maxOf(fmt.precision, 10)),
                unit = "gal (Imp)",
            )
            result.totalCost?.let {
                ResultRow(
                    label = "Total cost",
                    value = fmt.num(it, precision = maxOf(fmt.precision, 10)),
                    emphasis = true,
                )
            }
            result.costPerKm?.let {
                ResultRow(
                    label = "Cost per km",
                    value = fmt.num(it, precision = maxOf(fmt.precision, 10)),
                )
            }
            result.costPerMile?.let {
                ResultRow(
                    label = "Cost per mile",
                    value = fmt.num(it, precision = maxOf(fmt.precision, 10)),
                )
            }
        }

        FormulaCard(
            formula = "L/100 km = 100 × litres ÷ km        km/L = km ÷ litres        mpg = miles ÷ gallons",
            explanation = "Fuel economy and fuel consumption are reciprocals of each other; the US " +
                "and imperial gallon figures differ by about 20% because the gallons differ " +
                "(1 US gal = 3.785 411 784 L, 1 Imp gal = 4.546 09 L). The trip cost is the fuel " +
                "price multiplied by the fuel volume.",
            variables = listOf(
                "L" to "litres of fuel used",
                "km" to "trip distance in kilometres",
                "mpg" to "miles per gallon (US or imperial)",
            ),
        )

        result?.notes?.forEach { InfoNote(it) }
        if (result == null) {
            InfoNote("Enter the distance, the fuel used and optionally a price to see economy and cost.")
        }
    }
}

private fun parseErrorMileage(raw: String): String? =
    if (raw.isBlank() || Num.parseOrNull(raw) != null) null else "Enter a valid number"
