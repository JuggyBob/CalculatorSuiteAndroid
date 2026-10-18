package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.UnitFamily.Companion.CTX
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear

/**
 * Speed. Base unit: metre per second.
 *
 * 1 km/h = 1000/3600 m/s = 5/18 m/s, 1 mph = 0.44704 m/s exactly
 * (1 mile = 1609.344 m over 3600 s), 1 ft/s = 0.3048 m/s and
 * 1 knot = 1852/3600 m/s exactly (1 nautical mile per hour).
 */
val SpeedFamily = UnitFamily(
    id = "speed",
    name = "Speed",
    baseUnitId = "m_s",
    units = listOf(
        linear("m_s", "Metres per second", "m/s", bd("1"), "Metric (SI)"),
        linear("km_h", "Kilometres per hour", "km/h", bd("5").divide(bd("18"), CTX), "Metric (SI)"),
        linear("ft_s", "Feet per second", "ft/s", bd("0.3048"), "Imperial / US"),
        linear("mph", "Miles per hour", "mph", bd("0.44704"), "Imperial / US"),
        linear("kn", "Knots", "kn", bd("1852").divide(bd("3600"), CTX), "Marine / aviation"),
    ),
    notes = listOf(
        "1 m/s = 3.6 km/h exactly (km/h is defined as 1000 m per 3600 s).",
        "1 knot = 1852 m/h exactly (1 nautical mile = 1852 m).",
        "1 mph = 0.44704 m/s exactly.",
    ),
)
