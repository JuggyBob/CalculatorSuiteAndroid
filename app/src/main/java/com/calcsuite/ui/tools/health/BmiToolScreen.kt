package com.calcsuite.ui.tools.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.health.BmiEngine
import com.calcsuite.engine.health.BmiResult
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Body mass index with metric/imperial entry, an explicit age band so the
 * adults-only WHO classification is never applied to under-18s, and the
 * healthy weight band for the given height. Results are computed live.
 */
@Composable
fun BmiToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var metric by rememberSaveable { mutableStateOf(true) }
    var isMinor by rememberSaveable { mutableStateOf(false) }
    var mass by rememberSaveable { mutableStateOf("") }
    var heightCm by rememberSaveable { mutableStateOf("") }
    var feet by rememberSaveable { mutableStateOf("") }
    var inches by rememberSaveable { mutableStateOf("") }

    val massValue = Num.parseOrNull(mass)
    val heightValue = Num.parseOrNull(heightCm)
    val feetValue = Num.parseIntOrNull(feet)
        ?.takeIf { it.signum() >= 0 && it <= BigInteger.valueOf(20) }
        ?.toInt()
    val inchesValue = Num.parseOrNull(inches)

    val computed: CalcResult<BmiResult>? = when {
        metric -> if (massValue != null && heightValue != null) {
            BmiEngine.compute(massValue, heightValue.movePointLeft(2), isMinor)
        } else {
            null
        }
        else -> if (massValue != null && inchesValue != null && feetValue != null) {
            BmiEngine.computeImperial(massValue, feetValue, inchesValue, isMinor)
        } else {
            null
        }
    }
    val result = (computed as? CalcResult.Ok)?.value
    val error = (computed as? CalcResult.Err)?.error?.message

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = listOf(true to "Metric", false to "Imperial"),
            selected = metric,
            onSelect = { metric = it },
            label = "Unit system",
        )

        if (metric) {
            NumberField(
                label = "Body mass",
                value = mass,
                onValueChange = { mass = it },
                unit = "kg",
                placeholder = "70",
                error = parseError(mass),
            )
            NumberField(
                label = "Height",
                value = heightCm,
                onValueChange = { heightCm = it },
                unit = "cm",
                placeholder = "175",
                error = parseError(heightCm),
            )
        } else {
            NumberField(
                label = "Body mass",
                value = mass,
                onValueChange = { mass = it },
                unit = "lb",
                placeholder = "154",
                error = parseError(mass),
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    label = "Height (feet)",
                    value = feet,
                    onValueChange = { feet = it },
                    unit = "ft",
                    placeholder = "5",
                    integerOnly = true,
                    error = if (feet.isNotBlank() && feetValue == null) "Enter whole feet 0–20" else null,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "Height (inches)",
                    value = inches,
                    onValueChange = { inches = it },
                    unit = "in",
                    placeholder = "10",
                    error = parseError(inches),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SegmentedChoice(
            options = listOf(false to "Adult (18+)", true to "Under 18"),
            selected = isMinor,
            onSelect = { isMinor = it },
            label = "Age band",
        )
        if (isMinor) {
            InfoNote(
                "The WHO weight classification is for adults. For under-18s this screen shows the " +
                    "BMI value only: pediatric interpretation uses age- and sex-specific growth " +
                    "charts, which this tool does not implement.",
            )
        }

        ResultCard(
            label = "Body mass index",
            value = result?.let { fmt.num(it.bmi, precision = maxOf(fmt.precision, 10)) },
            approx = true,
            caption = result?.let { r ->
                r.category?.let { "${it.label} (${it.range}) — WHO adult category" }
            },
            error = error,
            onCopy = { copy(it, "BMI") },
            copyValue = result?.let { Num.formatForCopy(it.bmi) },
            onSave = result?.let { r ->
                {
                    val summary = if (metric) {
                        "BMI: $mass kg, $heightCm cm${if (isMinor) ", under 18" else ""}"
                    } else {
                        "BMI: $mass lb, $feet ft $inches in${if (isMinor) ", under 18" else ""}"
                    }
                    val text = "BMI ${fmt.num(r.bmi, precision = maxOf(fmt.precision, 10))}" +
                        (r.category?.let { " — ${it.label}" } ?: " (no classification: under 18)")
                    args.onRecord(summary, text)
                }
            },
        )

        if (result != null) {
            SectionBody(result, metric)
        }

        FormulaCard(
            formula = "BMI = mass(kg) ÷ height(m)²\nBMI = 703 × mass(lb) ÷ height(in)²",
            explanation = "The imperial constant 703 folds in the exact unit definitions " +
                "(1 lb = 0.453 592 37 kg, 1 in = 2.54 cm), so both forms give the same BMI for the " +
                "same person. The category bands are applied to the BMI rounded to one decimal place.",
            variables = listOf(
                "kg" to "body mass in kilograms",
                "m" to "height in metres",
                "lb" to "body mass in pounds",
                "in" to "height in inches",
            ),
        )

        result?.notes?.forEach { InfoNote(it) }
        if (result == null) {
            InfoNote("Enter a body mass and a height to see the BMI and the healthy weight range.")
        }
    }
}

@Composable
private fun SectionBody(result: BmiResult, metric: Boolean) {
    val fmt = rememberFmt()
    ResultRow(
        label = "Category (WHO adult)",
        value = result.category?.let { "${it.label} — ${it.range}" }
            ?: "Not applied (under 18)",
    )
    ResultRow(
        label = "Healthy weight range",
        value = "${fmt.num(result.healthyMinKg.setScale(1, RoundingMode.HALF_UP))} – " +
            fmt.num(result.healthyMaxKg.setScale(1, RoundingMode.HALF_UP)),
        unit = "kg",
    )
    if (!metric) {
        val lbFactor = BigDecimal("0.45359237")
        ResultRow(
            label = "Healthy weight range",
            value = "${fmt.num(result.healthyMinKg.divide(lbFactor, Num.EVAL_CONTEXT).setScale(1, RoundingMode.HALF_UP))} – " +
                fmt.num(result.healthyMaxKg.divide(lbFactor, Num.EVAL_CONTEXT).setScale(1, RoundingMode.HALF_UP)),
            unit = "lb",
        )
    }
}

/** Blank fields carry no error while the user is still typing. */
private fun parseError(raw: String): String? =
    if (raw.isBlank() || Num.parseOrNull(raw) != null) null else "Enter a valid number"
