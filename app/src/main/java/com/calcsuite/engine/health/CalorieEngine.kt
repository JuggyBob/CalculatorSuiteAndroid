package com.calcsuite.engine.health

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** One activity with its MET (metabolic equivalent of task) value. */
data class MetActivity(
    val id: String,
    val label: String,
    val met: BigDecimal,
    val group: String,
)

/**
 * MET-based energy expenditure.
 *
 * gross kcal = MET × 3.5 × mass(kg) / 200 × minutes
 * net kcal   = gross − 1 MET × 3.5 × mass(kg) / 200 × minutes
 *
 * The 3.5 mL O₂/kg/min resting rate and the ~5 kcal per litre of O₂ conversion
 * are folded into the standard 1 MET ≈ 1 kcal/kg/h approximation, so the
 * result is an estimate, not a measurement. Gross expenditure includes the
 * energy that would have been used at rest; net expenditure is the activity's
 * own additional cost. Values are informational and are not medical advice.
 */
object CalorieEngine {

    /** MET table source class. */
    const val SOURCE = "2024 Adult Compendium of Physical Activities (Ainsworth et al.)"

    private val CTX = MathContext(34, RoundingMode.HALF_UP)

    private fun bd(text: String) = BigDecimal(text)

    /** Curated MET table (~30 common activities), grouped by activity class. */
    val activities: List<MetActivity> = listOf(
        MetActivity("walking_3_2", "Walking, 3.2 km/h (level, slow)", bd("2.8"), "Walking"),
        MetActivity("walking_5_6", "Walking, 5.6 km/h (brisk)", bd("4.3"), "Walking"),
        MetActivity("running_8", "Running, 8 km/h (7:30 min/km)", bd("8.3"), "Running"),
        MetActivity("running_10", "Running, 10 km/h (6:00 min/km)", bd("9.8"), "Running"),
        MetActivity("cycling_leisure", "Cycling, leisure, under 16 km/h", bd("4.0"), "Cycling"),
        MetActivity("cycling_16_19", "Cycling, 16–19 km/h (moderate)", bd("6.8"), "Cycling"),
        MetActivity("swimming_leisure", "Swimming, leisure", bd("6.0"), "Water"),
        MetActivity("swimming_laps", "Swimming laps, vigorous", bd("9.8"), "Water"),
        MetActivity("rowing_moderate", "Rowing machine, moderate", bd("7.0"), "Cardio equipment"),
        MetActivity("elliptical", "Elliptical trainer, moderate", bd("5.0"), "Cardio equipment"),
        MetActivity("jump_rope", "Jump rope, moderate", bd("11.0"), "Cardio equipment"),
        MetActivity("weight_training", "Weight training (general)", bd("3.5"), "Strength & flexibility"),
        MetActivity("yoga", "Yoga, Hatha", bd("2.5"), "Strength & flexibility"),
        MetActivity("pilates", "Pilates", bd("3.0"), "Strength & flexibility"),
        MetActivity("dancing", "Dancing, general", bd("5.0"), "Dance"),
        MetActivity("hiking", "Hiking, general", bd("6.0"), "Outdoors"),
        MetActivity("climbing", "Rock climbing, moderate", bd("8.0"), "Outdoors"),
        MetActivity("skiing_downhill", "Skiing, downhill, moderate", bd("6.0"), "Outdoors"),
        MetActivity("skating", "Skating (ice or roller), general", bd("7.0"), "Outdoors"),
        MetActivity("surfing", "Surfing, general", bd("3.0"), "Outdoors"),
        MetActivity("gardening", "Gardening, general", bd("3.8"), "Home & garden"),
        MetActivity("housework", "Housework, general", bd("3.3"), "Home & garden"),
        MetActivity("golf", "Golf, walking and carrying clubs", bd("4.8"), "Sports"),
        MetActivity("tennis", "Tennis, singles", bd("7.3"), "Sports"),
        MetActivity("basketball", "Basketball, game", bd("7.0"), "Sports"),
        MetActivity("soccer", "Soccer, casual", bd("8.0"), "Sports"),
        MetActivity("volleyball", "Volleyball, game", bd("6.0"), "Sports"),
        MetActivity("martial_arts", "Martial arts, moderate", bd("10.3"), "Sports"),
        MetActivity("table_tennis", "Table tennis", bd("4.0"), "Sports"),
    )

    fun activity(id: String): MetActivity? = activities.firstOrNull { it.id == id }

    /** Energy estimate for one activity session. */
    data class Result(
        /** Gross energy expenditure, kcal (includes resting metabolism). */
        val grossKcal: BigDecimal,
        /** Net activity expenditure, kcal (gross − resting). */
        val netKcal: BigDecimal,
        val kcalPerMinute: BigDecimal,
        val met: BigDecimal,
        val massKg: BigDecimal,
        val minutes: BigDecimal,
        val notes: List<String>,
    )

    /** Looks up [activityId] and delegates to [compute]. */
    fun computeForActivity(activityId: String, massKg: BigDecimal, minutes: BigDecimal): CalcResult<Result> {
        val activity = activity(activityId)
            ?: return err(ErrorKind.InvalidInput, "Unknown activity")
        return compute(massKg, minutes, activity.met)
    }

    /** Core MET calculation. [minutes] may be fractional. */
    fun compute(massKg: BigDecimal, minutes: BigDecimal, met: BigDecimal): CalcResult<Result> {
        validateMass(massKg)?.let { return err(ErrorKind.InvalidInput, it) }
        if (minutes.signum() <= 0) return err(ErrorKind.InvalidInput, "Duration must be greater than zero")
        if (met.signum() <= 0) return err(ErrorKind.InvalidInput, "MET value must be greater than zero")

        // kcal per minute per MET = 3.5 × kg / 200.
        val kcalPerMinutePerMet = bd("3.5").multiply(massKg, CTX).divide(bd("200"), CTX)
        val gross = kcalPerMinutePerMet.multiply(met, CTX).multiply(minutes, CTX)
        val resting = kcalPerMinutePerMet.multiply(minutes, CTX)
        val net = gross.subtract(resting, CTX)
        val kcalPerMinute = gross.divide(minutes, CTX)

        val notes = buildList {
            add("MET values are taken from the $SOURCE (representative values by activity class).")
            add(
                "Energy expenditure varies with fitness, technique, terrain and body composition; " +
                    "treat this as an informational estimate, not a measurement.",
            )
            add(
                "Gross expenditure includes the energy you would have spent at rest; net expenditure " +
                    "subtracts one MET (3.5 mL O₂/kg/min, ≈ 1 kcal/kg/h).",
            )
            if (net.signum() < 0) {
                add("Net expenditure is negative because the MET value is below 1 (less than resting metabolism).")
            }
        }
        return ok(Result(gross, net, kcalPerMinute, met, massKg, minutes, notes))
    }

    private fun validateMass(massKg: BigDecimal): String? = when {
        massKg.signum() <= 0 -> "Body mass must be greater than zero"
        massKg < bd("20") || massKg > bd("400") -> "Body mass must be between 20 kg and 400 kg"
        else -> null
    }
}
