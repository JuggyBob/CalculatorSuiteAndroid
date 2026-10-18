package com.calcsuite.engine.convert.families

import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.engine.convert.UnitFamily.Companion.CTX
import com.calcsuite.engine.convert.bd
import com.calcsuite.engine.convert.linear
import com.calcsuite.expr.Consts
import java.math.BigDecimal

/**
 * Angle. Base unit: radian.
 *
 * π is taken from [Consts.PI] (36 significant digits) so that one full turn is
 * 2π to the full working precision. All factors are π divided by an integer,
 * so 180° = π rad, 180/π° = 1 rad, 400 grad = 2π rad, 1 turn = 2π rad,
 * 1 arcminute = 1/60 of a degree and 1 arcsecond = 1/60 of an arcminute.
 *
 * The conversions are linear, so negative values (clockwise) and values above
 * one revolution convert without special handling.
 */
val AngleFamily = UnitFamily(
    id = "angle",
    name = "Angle",
    baseUnitId = "rad",
    units = listOf(
        linear("deg", "Degrees", "°", Consts.PI.divide(bd("180"), CTX), "Common"),
        linear("rad", "Radians", "rad", bd("1"), "Common"),
        linear("grad", "Gradians", "grad", Consts.PI.divide(bd("200"), CTX), "Common"),
        linear("turn", "Turns (revolutions)", "turn", Consts.PI.multiply(bd("2"), CTX), "Common"),
        linear("mrad", "Milliradians", "mrad", bd("0.001"), "Common"),
        linear("arcmin", "Arcminutes", "′", Consts.PI.divide(bd("10800"), CTX), "Subdivisions"),
        linear("arcsec", "Arcseconds", "″", Consts.PI.divide(bd("648000"), CTX), "Subdivisions"),
    ),
    notes = listOf(
        "180° = π rad, 400 grad = 2π rad and 1 turn = 2π rad.",
        "1° = 60 arcminutes = 3600 arcseconds.",
        "Negative angles (clockwise) and angles above one revolution are fully supported.",
    ),
)

/** The 36-digit value of π used by the angle family, exposed for tests/precision displays. */
val ANGLE_PI_DIGITS_36: BigDecimal get() = Consts.PI
