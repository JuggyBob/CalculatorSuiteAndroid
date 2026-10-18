package com.calcsuite.engine.health

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.RoundingMode

/** The US Navy method has a separate regression for each formula variant. */
enum class FormulaVariant(val label: String) {
    MALE("Male (abdomen, neck, height)"),
    FEMALE("Female (waist, hip, neck, height)"),
}

/**
 * Which measurement set the variant uses (the screen labels this the
 * "formula variant"). The two regressions differ because the underlying
 * circumference measurements were fitted separately for men and women, whose
 * fat distribution patterns differ; the tool therefore asks for the variant
 * the formula was published for rather than inferring anything.
 */
data class BodyFatInput(
    val variant: FormulaVariant,
    val heightCm: BigDecimal,
    val neckCm: BigDecimal,
    /** Required for [FormulaVariant.MALE]. */
    val abdomenCm: BigDecimal? = null,
    /** Required for [FormulaVariant.FEMALE]. */
    val waistCm: BigDecimal? = null,
    /** Required for [FormulaVariant.FEMALE]. */
    val hipCm: BigDecimal? = null,
    /** Optional: enables the lean/fat mass split. */
    val bodyMassKg: BigDecimal? = null,
)

data class BodyFatResult(
    /** Reported estimate in percent, rounded to 0.1 pp (clamped at the floor). */
    val percent: BigDecimal,
    /** Unrounded formula output, before clamping. */
    val rawPercent: BigDecimal,
    /** True when the raw estimate fell below the reliable floor and was clamped. */
    val clamped: Boolean,
    val methodName: String,
    val limitations: List<String>,
    val fatMassKg: BigDecimal?,
    val leanMassKg: BigDecimal?,
    val notes: List<String>,
)

/**
 * US Navy circumference method (Hodgdon–Beckett regressions).
 *
 * Published constants (measurements in **inches**):
 * - men:   %BF = 86.010·log10(abdomen − neck) − 70.041·log10(height) + 36.76
 * - women: %BF = 163.205·log10(waist + hip − neck) − 97.684·log10(height) − 78.387
 *
 * The UI works in centimetres (metric version), so centimetre inputs are
 * converted to inches (1 in = 2.54 cm exactly) before the constants are
 * applied. Applying the constants to centimetre numerals would misplace the
 * logarithm and overstate the estimate by several percentage points.
 *
 * The estimate uses circumference measurements only: it carries a measurement
 * error of roughly ±3–4 percentage points and is not a diagnosis.
 */
object BodyFatEngine {

    const val METHOD_NAME = "US Navy circumference method (Hodgdon–Beckett regressions)"

    /** Floor below which the regression is not considered reliable. */
    val MIN_RELIABLE_PERCENT = BigDecimal("3.0")

    private const val CM_PER_INCH = 2.54

    val LIMITATIONS: List<String> = listOf(
        "Circumference estimates carry a measurement error of roughly ±3–4 percentage points.",
        "Accuracy differs by sex, age and ethnicity; the regressions were fitted on specific populations.",
        "The formula is statistically unreliable at the extremes (very low or very high body fat).",
        "This estimate is informational and is not a diagnosis or medical assessment.",
    )

    /** Evaluates [input] with the US Navy method. All lengths are centimetres. */
    fun estimate(input: BodyFatInput): CalcResult<BodyFatResult> {
        if (input.heightCm.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Height must be greater than zero")
        }
        if (input.neckCm.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Neck circumference must be greater than zero")
        }

        val raw: Double = when (input.variant) {
            FormulaVariant.MALE -> {
                val abdomen = input.abdomenCm
                    ?: return err(ErrorKind.InvalidInput, "The male formula needs an abdomen circumference")
                if (abdomen.signum() <= 0) {
                    return err(ErrorKind.InvalidInput, "Abdomen circumference must be greater than zero")
                }
                if (abdomen <= input.neckCm) {
                    return err(
                        ErrorKind.InvalidInput,
                        "Abdomen circumference must be greater than the neck circumference",
                    )
                }
                malePercent(input.heightCm, input.neckCm, abdomen)
            }
            FormulaVariant.FEMALE -> {
                val waist = input.waistCm
                    ?: return err(ErrorKind.InvalidInput, "The female formula needs a waist circumference")
                val hip = input.hipCm
                    ?: return err(ErrorKind.InvalidInput, "The female formula needs a hip circumference")
                if (waist.signum() <= 0) {
                    return err(ErrorKind.InvalidInput, "Waist circumference must be greater than zero")
                }
                if (hip.signum() <= 0) {
                    return err(ErrorKind.InvalidInput, "Hip circumference must be greater than zero")
                }
                if (waist.add(hip) <= input.neckCm) {
                    return err(
                        ErrorKind.InvalidInput,
                        "Waist plus hip circumference must exceed the neck circumference",
                    )
                }
                femalePercent(input.heightCm, input.neckCm, waist, hip)
            }
        }

        val rawValue = BigDecimal.valueOf(raw)
        val clamped = rawValue < MIN_RELIABLE_PERCENT
        val percent = (if (clamped) MIN_RELIABLE_PERCENT else rawValue).setScale(1, RoundingMode.HALF_UP)

        var fatMass: BigDecimal? = null
        var leanMass: BigDecimal? = null
        input.bodyMassKg?.let { mass ->
            if (mass.signum() <= 0) {
                return err(ErrorKind.InvalidInput, "Body mass must be greater than zero")
            }
            fatMass = mass.multiply(percent).divide(BigDecimal("100"), java.math.MathContext.DECIMAL64)
                .setScale(2, RoundingMode.HALF_UP)
            leanMass = mass.subtract(fatMass!!).setScale(2, RoundingMode.HALF_UP)
        }

        val notes = buildList {
            add(
                "Published constants are defined for measurements in inches; centimetre inputs are " +
                    "converted (1 in = 2.54 cm) before the regression is evaluated.",
            )
            if (clamped) {
                add(
                    "The raw estimate (${rawValue.setScale(2, RoundingMode.HALF_UP)}%) fell below the " +
                        "formula's reliable floor and is reported as ${percent}%; the value is " +
                        "unreliable at this extreme.",
                )
            }
            if (input.bodyMassKg != null) {
                add("Lean and fat mass are derived from the estimated percentage and the supplied body mass.")
            }
        }
        return ok(
            BodyFatResult(
                percent = percent,
                rawPercent = rawValue,
                clamped = clamped,
                methodName = METHOD_NAME,
                limitations = LIMITATIONS,
                fatMassKg = fatMass,
                leanMassKg = leanMass,
                notes = notes,
            ),
        )
    }

    /** Male regression, centimetres in, percentage out. */
    fun malePercent(heightCm: BigDecimal, neckCm: BigDecimal, abdomenCm: BigDecimal): Double {
        val abdomenMinusNeck = (abdomenCm.toDouble() - neckCm.toDouble()) / CM_PER_INCH
        val heightIn = heightCm.toDouble() / CM_PER_INCH
        return 86.010 * Math.log10(abdomenMinusNeck) - 70.041 * Math.log10(heightIn) + 36.76
    }

    /** Female regression, centimetres in, percentage out. */
    fun femalePercent(
        heightCm: BigDecimal,
        neckCm: BigDecimal,
        waistCm: BigDecimal,
        hipCm: BigDecimal,
    ): Double {
        val waistPlusHipMinusNeck =
            (waistCm.toDouble() + hipCm.toDouble() - neckCm.toDouble()) / CM_PER_INCH
        val heightIn = heightCm.toDouble() / CM_PER_INCH
        return 163.205 * Math.log10(waistPlusHipMinusNeck) - 97.684 * Math.log10(heightIn) - 78.387
    }
}
