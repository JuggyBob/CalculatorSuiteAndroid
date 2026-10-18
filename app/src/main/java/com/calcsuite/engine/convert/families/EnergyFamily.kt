package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Energy. Base unit: joule.
 *
 * - Small (thermochemical) calorie: 4.184 J exactly; kilocalorie = 4184 J.
 * - The food Calorie of nutrition labels is the kilocalorie (4184 J).
 * - 1 Wh = 3600 J, 1 kWh = 3 600 000 J.
 * - Elementary charge 1 e = 1.602176634e-19 C (exact, SI 2019), so
 *   1 eV = 1.602176634e-19 J exactly.
 * - BTU (International Table): 1 BTU_IT = 1055.05585262 J.
 */
val EnergyFamily = UnitFamily(
    id = "energy",
    name = "Energy",
    baseUnitId = "J",
    units = listOf(
        linear("J", "Joules", "J", bd("1"), "Metric (SI)"),
        linear("kJ", "Kilojoules", "kJ", bd("1000"), "Metric (SI)"),
        linear("MJ", "Megajoules", "MJ", pow10(6), "Metric (SI)"),
        linear("cal", "Calories (thermochemical)", "cal", bd("4.184"), "Calories & food"),
        linear("kcal", "Kilocalories", "kcal", bd("4184"), "Calories & food"),
        linear("Cal", "Food Calorie = kcal", "Cal", bd("4184"), "Calories & food"),
        linear("Wh", "Watt-hours", "Wh", bd("3600"), "Electrical"),
        linear("kWh", "Kilowatt-hours", "kWh", bd("3600000"), "Electrical"),
        linear("eV", "Electronvolts", "eV", bd("1.602176634e-19"), "Physics"),
        linear("BTU", "British thermal units (IT)", "BTU", bd("1055.05585262"), "Imperial / US"),
    ),
    notes = listOf(
        "1 kWh = 3 600 000 J and 1 Wh = 3600 J exactly.",
        "The food Calorie printed on nutrition labels equals one kilocalorie (4184 J); it is not the small thermochemical calorie.",
        "1 eV = 1.602176634×10⁻¹⁹ J exactly (SI 2019 definition of the elementary charge).",
    ),
)
