package com.calcsuite.engine.convert.families

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.UnitFamily.Companion.CTX
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.engine.convert.pow10
import java.math.BigDecimal

/**
 * Volumetric flow rate. Base unit: cubic metre per second.
 *
 * 1 m³/h = 1/3600 m³/s, 1 L/s = 0.001 m³/s, 1 L/min = 1/60000 m³/s,
 * 1 mL/s = 1e-6 m³/s. 1 US gallon per minute = 3.785411784 L/min
 * = 6.30901964e-5 m³/s exactly; 1 Imperial gallon per minute =
 * 4.54609 L/min (computed exactly as 4.54609e-3/60); 1 ft³/s = 0.028316846592 m³/s.
 */
val FlowFamily = UnitFamily(
    id = "flow",
    name = "Volumetric flow",
    baseUnitId = "m3_s",
    units = listOf(
        linear("m3_s", "Cubic metres per second", "m³/s", bd("1"), "Metric (SI)"),
        linear("m3_h", "Cubic metres per hour", "m³/h", BigDecimal.ONE.divide(bd("3600"), CTX), "Metric (SI)"),
        linear("L_s", "Litres per second", "L/s", bd("0.001"), "Metric (SI)"),
        linear("L_min", "Litres per minute", "L/min", bd("0.001").divide(bd("60"), CTX), "Metric (SI)"),
        linear("mL_s", "Millilitres per second", "mL/s", pow10(-6), "Metric (SI)"),
        linear("us_gpm", "US gallons per minute", "gpm (US)", bd("0.003785411784").divide(bd("60"), CTX), "US customary"),
        linear("imp_gpm", "Imperial gallons per minute", "gpm (Imp)", bd("0.00454609").divide(bd("60"), CTX), "Imperial (UK)"),
        linear("ft3_s", "Cubic feet per second", "ft³/s", bd("0.028316846592"), "Imperial / US"),
    ),
    notes = listOf(
        "1 US gpm = 3.785411784 L/min = 6.30901964e-5 m³/s exactly; the Imperial gpm is larger (4.54609 L/min).",
        "1 m³/s = 1000 L/s = 60 000 L/min.",
        "The US gpm is used for plumbing and pumps; cfs (ft³/s) is used for rivers and large flows.",
    ),
)

/**
 * Time/volume helpers built on [FlowFamily] and [VolumeFamily].
 *
 * Dimension handling: a flow rate is volume ÷ time, so
 * `volume = flow × time` and `time = volume ÷ flow`. A rate expressed in a
 * given flow unit is converted to m³/s through [FlowFamily] and the volume is
 * converted to m³ through [VolumeFamily] (1 m³ = 1000 L), so any pair of
 * flow and volume units can be mixed.
 */
object FlowTimeEngine {

    /** Volume unit of [VolumeFamily] that pairs naturally with a flow unit. */
    fun volumeUnitFor(flowUnitId: String): String? = when (flowUnitId) {
        "m3_s", "m3_h" -> "m3"
        "L_s", "L_min" -> "L"
        "mL_s" -> "mL"
        "us_gpm" -> "us_gal"
        "imp_gpm" -> "imp_gal"
        "ft3_s" -> "ft3"
        else -> null
    }

    /**
     * Volume delivered by [flowRate] (in [flowUnitId]) over [seconds], returned
     * in the natural volume unit of that flow unit (see [volumeUnitFor]).
     */
    fun volumeFrom(flowRate: BigDecimal, flowUnitId: String, seconds: BigDecimal): CalcResult<BigDecimal> {
        val flowUnit = FlowFamily.unit(flowUnitId)
            ?: return err(ErrorKind.InvalidInput, "Unknown flow unit")
        if (seconds.signum() < 0) return err(ErrorKind.Domain, "Time must not be negative")
        val targetVolumeUnit = volumeUnitFor(flowUnitId)
            ?: return err(ErrorKind.InvalidInput, "Unknown flow unit")
        val cubicMetres = flowRate.multiply(flowUnit.factor, CTX).multiply(seconds, CTX)
        val litres = cubicMetres.multiply(bd("1000"), CTX)
        return VolumeFamily.convert(litres, "L", targetVolumeUnit)
    }

    /**
     * Seconds needed to deliver [volume] (in [volumeUnitId]) at [flowRate]
     * (in [flowUnitId]). Mixing any flow and volume units is allowed.
     */
    fun timeFor(
        volume: BigDecimal,
        volumeUnitId: String,
        flowRate: BigDecimal,
        flowUnitId: String,
    ): CalcResult<BigDecimal> {
        val flowUnit = FlowFamily.unit(flowUnitId)
            ?: return err(ErrorKind.InvalidInput, "Unknown flow unit")
        val volumeUnit = VolumeFamily.unit(volumeUnitId)
            ?: return err(ErrorKind.InvalidInput, "Unknown volume unit")
        if (volume.signum() < 0) return err(ErrorKind.Domain, "Volume must not be negative")
        if (flowRate.signum() == 0) return err(ErrorKind.DivisionByZero, "Flow rate must be greater than zero")
        if (flowRate.signum() < 0) return err(ErrorKind.Domain, "Flow rate must be greater than zero")
        val volumeCubicMetres = volume.multiply(volumeUnit.factor, CTX).divide(bd("1000"), CTX)
        val rateCubicMetresPerSecond = flowRate.multiply(flowUnit.factor, CTX)
        return ok(volumeCubicMetres.divide(rateCubicMetresPerSecond, CTX))
    }
}
