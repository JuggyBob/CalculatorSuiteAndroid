package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10

/**
 * Length. Base unit: metre (exact definitions).
 * 1 in = 25.4 mm, 1 ft = 12 in, 1 yd = 3 ft, 1 mile = 5280 ft,
 * 1 nautical mile = 1852 m (exact).
 */
val LengthFamily = UnitFamily(
    id = "length",
    name = "Length",
    baseUnitId = "m",
    units = listOf(
        linear("nm", "Nanometres", "nm", pow10(-9), "Metric (SI)"),
        linear("um", "Micrometres", "µm", pow10(-6), "Metric (SI)"),
        linear("mm", "Millimetres", "mm", pow10(-3), "Metric (SI)"),
        linear("cm", "Centimetres", "cm", pow10(-2), "Metric (SI)"),
        linear("m", "Metres", "m", bd("1"), "Metric (SI)"),
        linear("km", "Kilometres", "km", bd("1000"), "Metric (SI)"),
        linear("in", "Inches", "in", bd("0.0254"), "Imperial / US"),
        linear("ft", "Feet", "ft", bd("0.3048"), "Imperial / US"),
        linear("yd", "Yards", "yd", bd("0.9144"), "Imperial / US"),
        linear("mi", "Miles", "mi", bd("1609.344"), "Imperial / US"),
        linear("nmi", "Nautical miles", "nmi", bd("1852"), "Marine / aviation"),
        linear("au", "Astronomical units", "au", bd("149597870700"), "Astronomy"),
        linear("ly", "Light years", "ly", bd("9460730472580800"), "Astronomy"),
        linear("pc", "Parsecs", "pc", bd("30856775814913673"), "Astronomy"),
    ),
    notes = listOf(
        "1 inch = 25.4 mm exactly; 1 nautical mile = 1852 m exactly.",
        "Astronomical units, light years and parsecs use IAU 2012 exact definitions.",
    ),
)
