package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Area. Base unit: square metre.
 *
 * Squared factors are exact squares of the corresponding length factors:
 * 1 in² = 0.0254² m², 1 ft² = 0.3048² m², 1 yd² = 0.9144² m²,
 * 1 mi² = 1609.344² m². 1 acre = 4046.8564224 m² (international acre,
 * exact) and 1 hectare = 10 000 m² exactly.
 */
val AreaFamily = UnitFamily(
    id = "area",
    name = "Area",
    baseUnitId = "m2",
    units = listOf(
        linear("mm2", "Square millimetres", "mm²", pow10(-6), "Metric (SI)"),
        linear("cm2", "Square centimetres", "cm²", pow10(-4), "Metric (SI)"),
        linear("m2", "Square metres", "m²", bd("1"), "Metric (SI)"),
        linear("km2", "Square kilometres", "km²", pow10(6), "Metric (SI)"),
        linear("ha", "Hectares", "ha", bd("10000"), "Land"),
        linear("acre", "Acres", "ac", bd("4046.8564224"), "Land"),
        linear("in2", "Square inches", "in²", bd("0.00064516"), "Imperial / US"),
        linear("ft2", "Square feet", "ft²", bd("0.09290304"), "Imperial / US"),
        linear("yd2", "Square yards", "yd²", bd("0.83612736"), "Imperial / US"),
        linear("mi2", "Square miles", "mi²", bd("2589988.110336"), "Imperial / US"),
    ),
    notes = listOf(
        "1 ha = 10 000 m² and 1 acre = 4046.8564224 m² exactly (international acre).",
        "Square foot, yard and mile factors are exact squares of the linear factors.",
    ),
)
