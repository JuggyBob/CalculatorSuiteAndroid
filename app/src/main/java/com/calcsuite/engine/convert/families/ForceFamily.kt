package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Force. Base unit: newton.
 *
 * 1 dyn = 1e-5 N exactly (CGS), 1 lbf = 4.4482216152605 N exactly
 * (1 lb × 9.80665 m/s² = 0.45359237 × 9.80665), 1 kgf = 9.80665 N exactly.
 */
val ForceFamily = UnitFamily(
    id = "force",
    name = "Force",
    baseUnitId = "N",
    units = listOf(
        linear("N", "Newtons", "N", bd("1"), "Metric (SI)"),
        linear("kN", "Kilonewtons", "kN", bd("1000"), "Metric (SI)"),
        linear("dyn", "Dynes", "dyn", pow10(-5), "Metric (CGS)"),
        linear("lbf", "Pounds-force", "lbf", bd("4.4482216152605"), "Imperial / US"),
        linear("kgf", "Kilograms-force", "kgf", bd("9.80665"), "Metric (SI)"),
    ),
    notes = listOf(
        "1 kgf = 9.80665 N exactly, from the standard gravity definition.",
        "1 lbf = 0.45359237 kg × 9.80665 m/s² = 4.4482216152605 N exactly.",
        "1 N = 100 000 dyn (1 dyn = 1e-5 N, CGS).",
    ),
)
