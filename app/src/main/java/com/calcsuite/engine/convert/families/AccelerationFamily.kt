package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Acceleration. Base unit: metre per second squared.
 * Standard gravity g₀ = 9.80665 m/s² (exact, CGPM 1901).
 * 1 ft/s² = 0.3048 m/s² exactly (1 ft = 0.3048 m).
 */
val AccelerationFamily = UnitFamily(
    id = "acceleration",
    name = "Acceleration",
    baseUnitId = "m_s2",
    units = listOf(
        linear("cm_s2", "Centimetres per second squared", "cm/s²", pow10(-2), "Metric (SI)"),
        linear("m_s2", "Metres per second squared", "m/s²", bd("1"), "Metric (SI)"),
        linear("km_s2", "Kilometres per second squared", "km/s²", bd("1000"), "Metric (SI)"),
        linear("ft_s2", "Feet per second squared", "ft/s²", bd("0.3048"), "Imperial / US"),
        linear("g0", "Standard gravity", "g₀", bd("9.80665"), "Reference"),
    ),
    notes = listOf(
        "1 g₀ = 9.80665 m/s² exactly (standard gravity, CGPM 1901).",
        "1 ft/s² = 0.3048 m/s² exactly, from 1 foot = 0.3048 m.",
    ),
)
