package com.calcsuite.ui.tools.health

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.health.BodyFatEngine
import com.calcsuite.engine.health.BodyFatInput
import com.calcsuite.engine.health.BodyFatResult
import com.calcsuite.engine.health.FormulaVariant
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs

/**
 * Body fat estimate with the US Navy circumference method.
 *
 * The screen labels the sex-specific choice as a "formula variant" because
 * the published regression differs: the two measurement sets were fitted
 * separately and are not interchangeable. All circumferences are in
 * centimetres (the metric version of the published constants).
 */
@Composable
fun BodyFatToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()

    var variant by rememberSaveable { mutableStateOf(FormulaVariant.MALE) }
    var height by rememberSaveable { mutableStateOf("") }
    var neck by rememberSaveable { mutableStateOf("") }
    var abdomen by rememberSaveable { mutableStateOf("") }
    var waist by rememberSaveable { mutableStateOf("") }
    var hip by rememberSaveable { mutableStateOf("") }
    var bodyMass by rememberSaveable { mutableStateOf("") }

    val heightValue = Num.parseOrNull(height)
    val neckValue = Num.parseOrNull(neck)
    val abdomenValue = Num.parseOrNull(abdomen)
    val waistValue = Num.parseOrNull(waist)
    val hipValue = Num.parseOrNull(hip)
    val massValue = Num.parseOrNull(bodyMass)
    val massEntered = bodyMass.isNotBlank()

    val computed: CalcResult<BodyFatResult>? =
        if ((massEntered && massValue == null) || heightValue == null || neckValue == null) {
            // A non-blank but unparseable optional mass suppresses the result so
            // the invalid field and the result area never contradict each other.
            null
        } else {
            when (variant) {
                FormulaVariant.MALE -> if (abdomenValue != null) {
                    BodyFatEngine.estimate(
                        BodyFatInput(
                            variant = variant,
                            heightCm = heightValue,
                            neckCm = neckValue,
                            abdomenCm = abdomenValue,
                            bodyMassKg = massValue,
                        ),
                    )
                } else {
                    null
                }
                FormulaVariant.FEMALE -> if (waistValue != null && hipValue != null) {
                    BodyFatEngine.estimate(
                        BodyFatInput(
                            variant = variant,
                            heightCm = heightValue,
                            neckCm = neckValue,
                            waistCm = waistValue,
                            hipCm = hipValue,
                            bodyMassKg = massValue,
                        ),
                    )
                } else {
                    null
                }
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
            options = listOf(
                FormulaVariant.MALE to "Male variant",
                FormulaVariant.FEMALE to "Female variant",
            ),
            selected = variant,
            onSelect = { variant = it },
            label = "Formula variant (US Navy method)",
        )
        InfoNote(
            "The US Navy method publishes one regression per variant; the measurement sets differ " +
                "because the underlying studies fitted them separately (typical fat distribution " +
                "differs between men and women). Pick the variant the formula was published for.",
        )

        NumberField(
            label = "Height",
            value = height,
            onValueChange = { height = it },
            unit = "cm",
            placeholder = "180",
            error = parseErrorBody(height),
        )
        NumberField(
            label = "Neck circumference",
            value = neck,
            onValueChange = { neck = it },
            unit = "cm",
            placeholder = "38",
            error = parseErrorBody(neck),
        )
        when (variant) {
            FormulaVariant.MALE -> NumberField(
                label = "Abdomen circumference (at navel level)",
                value = abdomen,
                onValueChange = { abdomen = it },
                unit = "cm",
                placeholder = "90",
                error = parseErrorBody(abdomen),
            )
            FormulaVariant.FEMALE -> {
                NumberField(
                    label = "Waist circumference (narrowest point)",
                    value = waist,
                    onValueChange = { waist = it },
                    unit = "cm",
                    placeholder = "72",
                    error = parseErrorBody(waist),
                )
                NumberField(
                    label = "Hip circumference (widest point)",
                    value = hip,
                    onValueChange = { hip = it },
                    unit = "cm",
                    placeholder = "96",
                    error = parseErrorBody(hip),
                )
            }
        }

        ExpandablePanel(title = "Optional: lean and fat mass split") {
            NumberField(
                label = "Body mass (optional)",
                value = bodyMass,
                onValueChange = { bodyMass = it },
                unit = "kg",
                placeholder = "80",
                error = if (massEntered && massValue == null) "Enter a valid number" else null,
            )
            InfoNote(
                "With a body mass the estimate is split into fat mass and lean mass; " +
                    "both inherit the ±3–4 percentage-point uncertainty of the percentage.",
            )
        }

        ResultCard(
            label = "Estimated body fat",
            value = result?.let { fmt.num(it.percent, precision = maxOf(fmt.precision, 10)) },
            unit = "%",
            approx = true,
            caption = result?.methodName,
            error = error,
            onSave = result?.let { r ->
                {
                    val measurements = when (variant) {
                        FormulaVariant.MALE -> "height $height cm, neck $neck cm, abdomen $abdomen cm"
                        FormulaVariant.FEMALE -> "height $height cm, neck $neck cm, waist $waist cm, hip $hip cm"
                    }
                    val summary = "Body fat (${if (variant == FormulaVariant.MALE) "male" else "female"} variant): $measurements"
                    args.onRecord(summary, "${fmt.num(r.percent, precision = maxOf(fmt.precision, 10))} %")
                }
            },
        )

        if (result != null) {
            ResultRow(
                label = "Method",
                value = result.methodName,
            )
            result.fatMassKg?.let {
                ResultRow(label = "Fat mass", value = fmt.num(it, precision = maxOf(fmt.precision, 10)), unit = "kg")
            }
            result.leanMassKg?.let {
                ResultRow(label = "Lean mass", value = fmt.num(it, precision = maxOf(fmt.precision, 10)), unit = "kg")
            }
            SectionCard(title = "Limitations") {
                result.limitations.forEach { limitation ->
                    Text(
                        "• $limitation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            result.notes.forEach { InfoNote(it) }
        }

        FormulaCard(
            formula = "men:   %BF = 86.010·log₁₀(abdomen − neck) − 70.041·log₁₀(height) + 36.76\n" +
                "women: %BF = 163.205·log₁₀(waist + hip − neck) − 97.684·log₁₀(height) − 78.387",
            explanation = "US Navy circumference regressions (Hodgdon–Beckett). The published " +
                "constants are defined with measurements in inches, so centimetre inputs are " +
                "converted (1 in = 2.54 cm) before the formula is evaluated.",
            variables = listOf(
                "abdomen" to "abdomen circumference (men)",
                "waist" to "waist circumference (women)",
                "hip" to "hip circumference (women)",
                "neck" to "neck circumference",
                "height" to "body height",
            ),
        )

        if (result == null) {
            InfoNote("Enter the measurements for the selected variant to see the estimate.")
        }
    }
}

private fun parseErrorBody(raw: String): String? =
    if (raw.isBlank() || Num.parseOrNull(raw) != null) null else "Enter a valid number"
