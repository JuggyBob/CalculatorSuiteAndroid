package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Mass (called "weight" in everyday use). Base unit: kilogram.
 *
 * 1 oz = 0.028349523125 kg, 1 lb = 16 oz = 0.45359237 kg exactly,
 * 1 stone = 14 lb = 6.35029318 kg, 1 US short ton = 2000 lb = 907.18474 kg,
 * 1 long ton = 2240 lb = 1016.0469088 kg.
 *
 * These are masses, not forces: the pound-force (lbf) and kilogram-force (kgf)
 * of the Force family include gravity and are not interchangeable with lb/kg.
 */
val WeightFamily = UnitFamily(
    id = "weight",
    name = "Weight & mass",
    baseUnitId = "kg",
    units = listOf(
        linear("mg", "Milligrams", "mg", pow10(-6), "Metric"),
        linear("g", "Grams", "g", bd("0.001"), "Metric"),
        linear("kg", "Kilograms", "kg", bd("1"), "Metric"),
        linear("t", "Tonnes", "t", bd("1000"), "Metric"),
        linear("oz", "Ounces", "oz", bd("0.028349523125"), "Imperial / US"),
        linear("lb", "Pounds", "lb", bd("0.45359237"), "Imperial / US"),
        linear("st", "Stones", "st", bd("6.35029318"), "Imperial / US"),
        linear("ton_us", "US short tons", "ton (US)", bd("907.18474"), "Imperial / US"),
        linear("ton_uk", "Long tons", "ton (UK)", bd("1016.0469088"), "Imperial / US"),
    ),
    notes = listOf(
        "These units measure mass, not force; \"weight\" here means mass in everyday language.",
        "1 lb = 0.45359237 kg exactly and 1 oz = 1/16 lb = 0.028349523125 kg exactly.",
        "1 stone = 14 lb and 1 US short ton = 2000 lb; the long ton (UK) is 2240 lb.",
    ),
)
