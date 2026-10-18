package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear

/**
 * Torque (moment of force). Base unit: newton-metre.
 *
 * 1 lbf·ft = 4.4482216152605 N × 0.3048 m = 1.3558179483314004 N·m exactly,
 * 1 lbf·in = 1.3558179483314004 / 12 = 0.1129848290276167 N·m, and
 * 1 kgf·m = 9.80665 N·m exactly.
 *
 * Torque and energy share the same dimension (force × distance) but are
 * different quantity kinds: torque is a vector moment and is never converted
 * to joules here.
 */
val TorqueFamily = UnitFamily(
    id = "torque",
    name = "Torque",
    baseUnitId = "Nm",
    units = listOf(
        linear("Nm", "Newton-metres", "N·m", bd("1"), "Metric (SI)"),
        linear("lbf_ft", "Pound-force feet", "lbf·ft", bd("1.3558179483314004"), "Imperial / US"),
        linear("lbf_in", "Pound-force inches", "lbf·in", bd("0.1129848290276167"), "Imperial / US"),
        linear("kgf_m", "Kilogram-force metres", "kgf·m", bd("9.80665"), "Metric (SI)"),
    ),
    notes = listOf(
        "Torque and energy have the same dimension but are different quantities: 1 N·m of torque is not 1 J of energy.",
        "1 lbf·ft = 1.3558179483314004 N·m exactly (exact product of the lbf and foot definitions).",
        "1 lbf·in = 1/12 lbf·ft = 0.1129848290276167 N·m exactly.",
    ),
)
