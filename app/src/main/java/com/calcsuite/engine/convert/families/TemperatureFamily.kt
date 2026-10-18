package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitDef
import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.UnitFamily.Companion.CTX
import com.calcsuite.engine.convert.bd
import java.math.BigDecimal

/**
 * Temperature. Base unit: kelvin — the only affine family in the app.
 *
 * - Celsius:    K = °C + 273.15               (factor 1, offset 273.15)
 * - Fahrenheit: K = (°F + 459.67) × 5/9       (factor 5/9, offset 273.15 − 32×5/9)
 * - Kelvin:     K = K
 *
 * The 5/9 factor is a repeating decimal, so Fahrenheit conversions are carried
 * out at the 34-digit working precision and rounded for display only.
 * The family validator rejects any value whose base (Kelvin) temperature is
 * below absolute zero, whatever the source unit.
 */
private val F_FACTOR: BigDecimal = bd("5").divide(bd("9"), CTX)
private val F_OFFSET: BigDecimal = bd("273.15").subtract(bd("32").multiply(F_FACTOR, CTX), CTX)

val TemperatureFamily = UnitFamily(
    id = "temperature",
    name = "Temperature",
    baseUnitId = "K",
    units = listOf(
        UnitDef("C", "Celsius", "°C", BigDecimal.ONE, bd("273.15"), "Common"),
        UnitDef("F", "Fahrenheit", "°F", F_FACTOR, F_OFFSET, "Common"),
        UnitDef("K", "Kelvin", "K", BigDecimal.ONE, BigDecimal.ZERO, "Common"),
    ),
    notes = listOf(
        "0 °C = 32 °F = 273.15 K, and −40 °C = −40 °F.",
        "Absolute zero is 0 K = −273.15 °C = −459.67 °F; values below it are rejected.",
        "Kelvin is the thermodynamic base unit; the same temperature interval is 1 K = 1 °C.",
    ),
    baseValidator = { kelvin ->
        if (kelvin.signum() < 0) {
            "Temperature cannot be below absolute zero (-273.15 °C / 0 K)"
        } else {
            null
        }
    },
)
