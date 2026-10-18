package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Power. Base unit: watt.
 *
 * - Mechanical horsepower: 550 ft·lbf/s = 550 × 0.3048 × 4.4482216152605
 *   = 745.69987158227022 W (exact product of exact definitions).
 * - Metric horsepower: 75 kgf·m/s = 75 × 9.80665 = 735.49875 W exactly.
 * - BTU/h (International Table): 1055.05585262 J / 3600 s = 0.293071070172... W.
 */
val PowerFamily = UnitFamily(
    id = "power",
    name = "Power",
    baseUnitId = "W",
    units = listOf(
        linear("W", "Watts", "W", bd("1"), "Metric (SI)"),
        linear("kW", "Kilowatts", "kW", bd("1000"), "Metric (SI)"),
        linear("MW", "Megawatts", "MW", pow10(6), "Metric (SI)"),
        linear("hp", "Mechanical horsepower", "hp", bd("745.69987158227022"), "Imperial / US"),
        linear("PS", "Metric horsepower", "PS", bd("735.49875"), "Metric (SI)"),
        linear("BTU_h", "BTU per hour (IT)", "BTU/h", bd("0.29307107017222"), "Imperial / US"),
    ),
    notes = listOf(
        "1 mechanical hp = 550 ft·lbf/s = 745.69987158227022 W.",
        "1 metric hp (PS, cv) = 75 kgf·m/s = 735.49875 W — about 1.4% less than mechanical hp.",
        "1 BTU/h (IT) ≈ 0.293071 W, from 1 BTU_IT = 1055.05585262 J over 3600 s.",
    ),
)
