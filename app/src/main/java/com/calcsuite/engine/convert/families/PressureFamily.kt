package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.UnitFamily.Companion.CTX
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import java.math.BigDecimal

/**
 * Pressure. Base unit: pascal.
 *
 * - 1 bar = 100 000 Pa and 1 mbar = 100 Pa.
 * - 1 atm = 101 325 Pa exactly (definition of the standard atmosphere).
 * - 1 torr = 1/760 atm = 101325/760 Pa exactly; mmHg is the same size
 *   (the torr was defined as 1 mmHg) and is listed separately because both
 *   names are in common use.
 * - 1 psi = 6894.757293168361 Pa (from 1 lbf / in², the exact quotient
 *   4.4482216152605 / 0.0254²).
 */
private val TORR = bd("101325").divide(bd("760"), CTX)

val PressureFamily = UnitFamily(
    id = "pressure",
    name = "Pressure",
    baseUnitId = "Pa",
    units = listOf(
        linear("Pa", "Pascals", "Pa", bd("1"), "Metric (SI)"),
        linear("hPa", "Hectopascals", "hPa", bd("100"), "Metric (SI)"),
        linear("kPa", "Kilopascals", "kPa", bd("1000"), "Metric (SI)"),
        linear("MPa", "Megapascals", "MPa", bd("1000000"), "Metric (SI)"),
        linear("bar", "Bar", "bar", bd("100000"), "Metric (SI)"),
        linear("mbar", "Millibar", "mbar", bd("100"), "Metric (SI)"),
        linear("atm", "Standard atmospheres", "atm", bd("101325"), "Reference"),
        linear("torr", "Torr", "Torr", TORR, "Reference"),
        linear("mmHg", "Millimetres of mercury", "mmHg", TORR, "Reference"),
        linear("psi", "Pounds per square inch", "psi", bd("6894.757293168361"), "Imperial / US"),
    ),
    notes = listOf(
        "1 atm = 101 325 Pa and 1 bar = 100 000 Pa exactly.",
        "1 atm = 760 torr; 1 torr = 101325/760 Pa exactly and equals 1 mmHg.",
        "1 psi = 4.4482216152605 N / 0.0254² m² = 6894.757293168361 Pa exactly.",
    ),
)

/** 101325/760 as computed, exposed for exact comparisons in tests. */
val TORR_FACTOR: BigDecimal get() = TORR
