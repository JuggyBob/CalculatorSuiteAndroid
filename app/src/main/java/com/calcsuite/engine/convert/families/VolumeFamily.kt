package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Volume. Base unit: litre.
 *
 * US customary: 1 US fl oz = 29.5735295625 mL, 1 US cup = 8 fl oz,
 * 1 US pint = 16 fl oz, 1 US quart = 32 fl oz, 1 US gallon = 128 fl oz
 * = 3.785411784 L exactly.
 * Imperial: 1 Imp fl oz = 28.4130625 mL, 1 Imp pint = 20 fl oz = 0.56826125 L,
 * 1 Imp quart = 1.1365225 L, 1 Imp gallon = 4.54609 L exactly.
 * Cubic factors are exact powers of the linear factors:
 * 1 in³ = 0.016387064 L, 1 ft³ = 28.316846592 L, 1 yd³ = 764.554857984 L.
 */
val VolumeFamily = UnitFamily(
    id = "volume",
    name = "Volume",
    baseUnitId = "L",
    units = listOf(
        linear("mm3", "Cubic millimetres", "mm³", pow10(-6), "Metric"),
        linear("cm3", "Cubic centimetres", "cm³", bd("0.001"), "Metric"),
        linear("mL", "Millilitres", "mL", bd("0.001"), "Metric"),
        linear("L", "Litres", "L", bd("1"), "Metric"),
        linear("m3", "Cubic metres", "m³", bd("1000"), "Metric"),
        linear("us_floz", "US fluid ounces", "fl oz", bd("0.0295735295625"), "US customary"),
        linear("us_cup", "US cups", "cup", bd("0.2365882365"), "US customary"),
        linear("us_pt", "US pints", "pt", bd("0.473176473"), "US customary"),
        linear("us_qt", "US quarts", "qt", bd("0.946352946"), "US customary"),
        linear("us_gal", "US gallons", "gal", bd("3.785411784"), "US customary"),
        linear("imp_floz", "Imperial fluid ounces", "fl oz (Imp)", bd("0.0284130625"), "Imperial (UK)"),
        linear("imp_pt", "Imperial pints", "pt (Imp)", bd("0.56826125"), "Imperial (UK)"),
        linear("imp_qt", "Imperial quarts", "qt (Imp)", bd("1.1365225"), "Imperial (UK)"),
        linear("imp_gal", "Imperial gallons", "gal (Imp)", bd("4.54609"), "Imperial (UK)"),
        linear("in3", "Cubic inches", "in³", bd("0.016387064"), "Imperial / US"),
        linear("ft3", "Cubic feet", "ft³", bd("28.316846592"), "Imperial / US"),
        linear("yd3", "Cubic yards", "yd³", bd("764.554857984"), "Imperial / US"),
    ),
    notes = listOf(
        "1 US gallon = 3.785411784 L; 1 Imperial gallon = 4.54609 L — the two gallons differ by about 20%.",
        "1 L = 1000 mL and 1 m³ = 1000 L exactly.",
        "US and Imperial fluid ounces, pints, quarts and gallons are different sizes; check the group before converting.",
    ),
)
