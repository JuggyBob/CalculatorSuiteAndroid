package com.calcsuite.engine.health

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Body mass index (Quetelet index) with the WHO adult weight classification.
 *
 * BMI is a screening measure derived from height and mass only. It does not
 * measure body composition, and the reported category is not a diagnosis.
 * The adult classification is deliberately never applied to under-18s: for
 * children and adolescents BMI is read against age- and sex-specific growth
 * charts, which this tool does not implement.
 *
 * Formulas:
 * - metric:   BMI = mass(kg) / height(m)²
 * - imperial: BMI = 703 × mass(lb) / height(in)²  (the 703 factor folds in the
 *             453.59237 g/lb and 2.54 cm/in definitions, so both routes agree)
 */
enum class BmiCategory(val label: String, val range: String) {
    UNDERWEIGHT("Underweight", "below 18.5"),
    HEALTHY("Healthy range", "18.5 – 24.9"),
    OVERWEIGHT("Overweight", "25.0 – 29.9"),
    OBESITY_CLASS_1("Obesity class 1", "30.0 – 34.9"),
    OBESITY_CLASS_2("Obesity class 2", "35.0 – 39.9"),
    OBESITY_CLASS_3("Obesity class 3", "40.0 and above"),
}

/** Outcome of a BMI evaluation. Values are kept at engine precision. */
data class BmiResult(
    /** Unrounded BMI (kg/m²). */
    val bmi: BigDecimal,
    /** WHO adult category, or null for under-18s (classification does not apply). */
    val category: BmiCategory?,
    val isMinor: Boolean,
    /** Lower end of the healthy adult BMI band (18.5) for this height, in kg. */
    val healthyMinKg: BigDecimal,
    /** Upper end of the healthy adult BMI band (24.9) for this height, in kg. */
    val healthyMaxKg: BigDecimal,
    val notes: List<String>,
)

object BmiEngine {

    const val MIN_HEIGHT_M = 0.5    // 50 cm
    const val MAX_HEIGHT_M = 2.5    // 250 cm
    const val MIN_MASS_KG = 1.0
    const val MAX_MASS_KG = 500.0

    private val CTX = MathContext(34, RoundingMode.HALF_UP)
    private val B18_5 = BigDecimal("18.5")
    private val B24_9 = BigDecimal("24.9")
    private val B25 = BigDecimal("25.0")
    private val B30 = BigDecimal("30.0")
    private val B35 = BigDecimal("35.0")
    private val B40 = BigDecimal("40.0")

    private const val WHO_NOTE =
        "WHO adult classification: underweight below 18.5, healthy range 18.5 – 24.9, " +
            "overweight 25.0 – 29.9, obesity class 1 at 30.0 – 34.9, class 2 at 35.0 – 39.9, " +
            "class 3 at 40.0 and above."

    private const val SCREENING_NOTE =
        "BMI is a screening measure based on height and mass only; it is not a diagnosis and " +
            "does not describe body composition."

    private const val MINOR_NOTE =
        "The adults-only weight classification does not apply to people under 18. BMI for " +
            "children and adolescents is interpreted against age- and sex-specific growth " +
            "charts, which this tool does not implement. The value shown is the same " +
            "height/mass relationship, without a category."

    /** Metric entry point. [heightM] and [massKg] are SI values. */
    fun compute(massKg: BigDecimal, heightM: BigDecimal, isMinor: Boolean = false): CalcResult<BmiResult> {
        validateMass(massKg)?.let { return err(ErrorKind.InvalidInput, it) }
        validateHeight(heightM)?.let { return err(ErrorKind.InvalidInput, it) }

        val squared = heightM.multiply(heightM, CTX)
        val bmi = massKg.divide(squared, CTX)

        val category = if (isMinor) null else classifyAdult(bmi)
        val (minKg, maxKg) = healthyRange(heightM)

        val notes = buildList {
            if (isMinor) {
                add(MINOR_NOTE)
            } else {
                add(WHO_NOTE)
                category?.let { add("This value falls in the \"${it.label}\" band (${it.range}).") }
            }
            add(SCREENING_NOTE)
            add("Category boundaries are applied to the BMI rounded to one decimal place.")
        }
        return ok(BmiResult(bmi, category, isMinor, minKg, maxKg, notes))
    }

    /**
     * Imperial entry point: mass in pounds, height as feet + inches.
     * BMI = 703 × lb / in².
     */
    fun computeImperial(
        pounds: BigDecimal,
        feet: Int,
        inches: BigDecimal,
        isMinor: Boolean = false,
    ): CalcResult<BmiResult> {
        if (feet < 0 || inches.signum() < 0) return err(ErrorKind.InvalidInput, "Height must be greater than zero")
        val heightIn = BigDecimal(feet).multiply(BigDecimal(12)).add(inches, CTX)
        if (heightIn.signum() <= 0) return err(ErrorKind.InvalidInput, "Height must be greater than zero")
        val massKg = pounds.multiply(BigDecimal("0.45359237"), CTX)
        val heightM = heightIn.multiply(BigDecimal("0.0254"), CTX)
        return compute(massKg, heightM, isMinor)
    }

    /** Healthy adult weight band (BMI 18.5 – 24.9) for [heightM], in kg. */
    fun healthyRange(heightM: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val squared = heightM.multiply(heightM, CTX)
        return squared.multiply(B18_5, CTX) to squared.multiply(B24_9, CTX)
    }

    /**
     * WHO adult category for [bmi], using the value rounded to one decimal
     * place so that the published boundaries are respected exactly.
     */
    fun classifyAdult(bmi: BigDecimal): BmiCategory {
        val rounded = bmi.setScale(1, RoundingMode.HALF_UP)
        return when {
            rounded < B18_5 -> BmiCategory.UNDERWEIGHT
            rounded < B25 -> BmiCategory.HEALTHY
            rounded < B30 -> BmiCategory.OVERWEIGHT
            rounded < B35 -> BmiCategory.OBESITY_CLASS_1
            rounded < B40 -> BmiCategory.OBESITY_CLASS_2
            else -> BmiCategory.OBESITY_CLASS_3
        }
    }

    /** Remaining validation for [healthyRange] callers that skip [compute]. */
    fun validateHeight(heightM: BigDecimal): String? = when {
        heightM.signum() <= 0 -> "Height must be greater than zero"
        heightM < BigDecimal(MIN_HEIGHT_M) || heightM > BigDecimal(MAX_HEIGHT_M) ->
            "Height must be between 50 cm and 250 cm"
        else -> null
    }

    private fun validateMass(massKg: BigDecimal): String? = when {
        massKg.signum() <= 0 -> "Body mass must be greater than zero"
        massKg < BigDecimal(MIN_MASS_KG) || massKg > BigDecimal(MAX_MASS_KG) ->
            "Body mass must be between 1 kg and 500 kg"
        else -> null
    }
}
