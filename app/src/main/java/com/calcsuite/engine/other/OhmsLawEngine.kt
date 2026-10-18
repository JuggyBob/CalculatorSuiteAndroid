package com.calcsuite.engine.other

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** The four electrical quantities related by Ohm's law and the power law. */
enum class Quantity(val label: String, val symbol: String) {
    VOLTAGE("Voltage", "V"),
    CURRENT("Current", "I"),
    RESISTANCE("Resistance", "R"),
    POWER("Power", "P"),
}

data class OhmsResult(
    val voltage: BigDecimal,
    val current: BigDecimal,
    val resistance: BigDecimal,
    val power: BigDecimal,
    /** The relationships actually used to derive the unknown values. */
    val formulas: List<String>,
    /** The ideal-circuit assumptions behind the result. */
    val assumptions: List<String>,
    val notes: List<String>,
)

/**
 * Ohm's law solver for an ideal DC circuit.
 *
 * Relationships: V = I·R and P = V·I = I²·R = V²/R. The caller supplies
 * exactly two independent quantities (in SI units: V, A, Ω, W); the other two
 * are derived. Supplying more than two is allowed only when the extra values
 * agree with the solved values within a relative tolerance of 1e-6 — numbers
 * that contradict each other are reported as inconsistent rather than being
 * silently overridden.
 *
 * Degenerate combinations are reported explicitly instead of returning 0 or
 * an infinity: zero resistance with a non-zero voltage, zero current with
 * non-zero power, and the genuinely underdetermined cases (0 V with 0 Ω,
 * 0 A with 0 W, 0 W with 0 Ω).
 *
 * The model is a pure resistance: no AC impedance, no temperature effects and
 * no non-linear devices are considered.
 */
object OhmsLawEngine {

    private val CTX = MathContext(34, RoundingMode.HALF_UP)
    private val REL_TOL = BigDecimal("1e-6")

    private val ASSUMPTIONS = listOf(
        "Ideal DC circuit: Ohm's law (V = I·R) and the power law (P = V·I) describe the whole circuit.",
        "No AC impedance, no temperature-dependent resistance and no non-linear devices (diodes, transistors).",
        "All quantities are positive magnitudes; where a square root is needed, the principal (positive) root is used.",
    )

    /** Pair priority used to pick the two values the solution is derived from. */
    private val PAIRS = listOf(
        Quantity.VOLTAGE to Quantity.CURRENT,
        Quantity.VOLTAGE to Quantity.RESISTANCE,
        Quantity.VOLTAGE to Quantity.POWER,
        Quantity.CURRENT to Quantity.RESISTANCE,
        Quantity.CURRENT to Quantity.POWER,
        Quantity.RESISTANCE to Quantity.POWER,
    )

    /**
     * Solves from the supplied [known] quantities. Throws nothing; every
     * invalid combination comes back as an [CalcResult.Err].
     */
    fun solve(known: Map<Quantity, BigDecimal>): CalcResult<OhmsResult> {
        for ((quantity, value) in known) {
            if (value.signum() < 0) {
                return err(ErrorKind.InvalidInput, "${quantity.label} cannot be negative")
            }
        }
        if (known.size < 2) {
            return err(
                ErrorKind.Insufficient,
                "Provide exactly two of voltage, current, resistance or power",
            )
        }

        val pair = PAIRS.firstOrNull { known.containsKey(it.first) && known.containsKey(it.second) }
            ?: return err(
                ErrorKind.Insufficient,
                "Provide exactly two of voltage, current, resistance or power",
            )
        val (a, b) = pair
        val va = known.getValue(a)
        val vb = known.getValue(b)

        val solved: CalcResult<Map<Quantity, BigDecimal>> = try {
            solvePair(a, va, b, vb)
        } catch (_: ArithmeticException) {
            err(ErrorKind.InvalidInput, "The supplied values cannot be combined")
        }
        val values = when (solved) {
            is CalcResult.Ok -> solved.value
            is CalcResult.Err -> return solved
        }

        val notes = mutableListOf<String>()
        // Any additional supplied quantity must agree with the solution.
        for ((quantity, provided) in known) {
            if (quantity == a || quantity == b) continue
            val computed = values.getValue(quantity)
            if (differs(provided, computed)) {
                return err(
                    ErrorKind.Inconsistent,
                    "Provided ${quantity.label.lowercase()} (${plain(provided)} ${quantity.symbol}) does not " +
                        "match the value implied by ${a.label.lowercase()} and ${b.label.lowercase()} " +
                        "(${plain(computed)} ${quantity.symbol})",
                )
            }
            notes.add(
                "The supplied ${quantity.label.lowercase()} matched the value implied by " +
                    "${a.label.lowercase()} and ${b.label.lowercase()} within the relative tolerance of 1e-6.",
            )
        }
        notes.add("All four quantities are returned in SI base units: volts, amperes, ohms and watts.")

        return ok(
            OhmsResult(
                voltage = values.getValue(Quantity.VOLTAGE),
                current = values.getValue(Quantity.CURRENT),
                resistance = values.getValue(Quantity.RESISTANCE),
                power = values.getValue(Quantity.POWER),
                formulas = formulasFor(a, b),
                assumptions = ASSUMPTIONS,
                notes = notes,
            ),
        )
    }

    // ------------------------------------------------------------------ pairs

    private fun solvePair(
        a: Quantity,
        va: BigDecimal,
        b: Quantity,
        vb: BigDecimal,
    ): CalcResult<Map<Quantity, BigDecimal>> {
        val v: BigDecimal
        val i: BigDecimal
        val r: BigDecimal
        val p: BigDecimal
        when (setOf(a, b)) {
            setOf(Quantity.VOLTAGE, Quantity.CURRENT) -> {
                v = valueOf(a, Quantity.VOLTAGE, va, b, vb)
                i = valueOf(a, Quantity.CURRENT, va, b, vb)
                if (i.signum() == 0) {
                    return if (v.signum() == 0) {
                        err(
                            ErrorKind.Insufficient,
                            "Zero voltage with zero current leaves resistance and power undetermined",
                        )
                    } else {
                        err(
                            ErrorKind.Domain,
                            "Zero current with a non-zero voltage implies infinite resistance, " +
                                "which is outside this ideal model",
                        )
                    }
                }
                r = v.divide(i, CTX)
                p = v.multiply(i, CTX)
            }
            setOf(Quantity.VOLTAGE, Quantity.RESISTANCE) -> {
                v = valueOf(a, Quantity.VOLTAGE, va, b, vb)
                r = valueOf(a, Quantity.RESISTANCE, va, b, vb)
                if (r.signum() == 0) {
                    return if (v.signum() == 0) {
                        err(
                            ErrorKind.Insufficient,
                            "Zero voltage with zero resistance leaves current and power undetermined",
                        )
                    } else {
                        err(
                            ErrorKind.Domain,
                            "Zero resistance with a non-zero voltage is not a physical " +
                                "ideal-circuit solution",
                        )
                    }
                }
                i = v.divide(r, CTX)
                p = v.multiply(v, CTX).divide(r, CTX)
            }
            setOf(Quantity.VOLTAGE, Quantity.POWER) -> {
                v = valueOf(a, Quantity.VOLTAGE, va, b, vb)
                p = valueOf(a, Quantity.POWER, va, b, vb)
                if (v.signum() == 0) {
                    return if (p.signum() == 0) {
                        err(
                            ErrorKind.Insufficient,
                            "Zero voltage with zero power leaves current and resistance undetermined",
                        )
                    } else {
                        err(
                            ErrorKind.Domain,
                            "Non-zero power with zero voltage is not a physical ideal-circuit solution",
                        )
                    }
                }
                if (p.signum() == 0) {
                    return err(
                        ErrorKind.Domain,
                        "Zero power with a non-zero voltage implies infinite resistance, " +
                            "which is outside this ideal model",
                    )
                }
                i = p.divide(v, CTX)
                r = v.multiply(v, CTX).divide(p, CTX)
            }
            setOf(Quantity.CURRENT, Quantity.RESISTANCE) -> {
                i = valueOf(a, Quantity.CURRENT, va, b, vb)
                r = valueOf(a, Quantity.RESISTANCE, va, b, vb)
                v = i.multiply(r, CTX)
                p = i.multiply(i, CTX).multiply(r, CTX)
            }
            setOf(Quantity.CURRENT, Quantity.POWER) -> {
                i = valueOf(a, Quantity.CURRENT, va, b, vb)
                p = valueOf(a, Quantity.POWER, va, b, vb)
                if (i.signum() == 0) {
                    return if (p.signum() == 0) {
                        err(
                            ErrorKind.Insufficient,
                            "Zero current with zero power leaves voltage and resistance undetermined",
                        )
                    } else {
                        err(
                            ErrorKind.Domain,
                            "Non-zero power with zero current is not a physical ideal-circuit solution",
                        )
                    }
                }
                v = p.divide(i, CTX)
                r = p.divide(i.multiply(i, CTX), CTX)
            }
            else -> { // RESISTANCE + POWER
                r = valueOf(a, Quantity.RESISTANCE, va, b, vb)
                p = valueOf(a, Quantity.POWER, va, b, vb)
                if (r.signum() == 0) {
                    return if (p.signum() == 0) {
                        err(
                            ErrorKind.Insufficient,
                            "Zero power with zero resistance leaves voltage and current undetermined",
                        )
                    } else {
                        err(
                            ErrorKind.Domain,
                            "Non-zero power with zero resistance is not a physical ideal-circuit solution",
                        )
                    }
                }
                v = sqrt(p.multiply(r, CTX))
                i = sqrt(p.divide(r, CTX))
            }
        }
        return ok(
            mapOf(
                Quantity.VOLTAGE to v,
                Quantity.CURRENT to i,
                Quantity.RESISTANCE to r,
                Quantity.POWER to p,
            ),
        )
    }

    private fun valueOf(
        a: Quantity,
        wanted: Quantity,
        va: BigDecimal,
        b: Quantity,
        vb: BigDecimal,
    ): BigDecimal = if (a == wanted) va else vb

    private fun formulasFor(a: Quantity, b: Quantity): List<String> = when (setOf(a, b)) {
        setOf(Quantity.VOLTAGE, Quantity.CURRENT) -> listOf("R = V ÷ I", "P = V × I")
        setOf(Quantity.VOLTAGE, Quantity.RESISTANCE) -> listOf("I = V ÷ R", "P = V² ÷ R")
        setOf(Quantity.VOLTAGE, Quantity.POWER) -> listOf("I = P ÷ V", "R = V² ÷ P")
        setOf(Quantity.CURRENT, Quantity.RESISTANCE) -> listOf("V = I × R", "P = I² × R")
        setOf(Quantity.CURRENT, Quantity.POWER) -> listOf("V = P ÷ I", "R = P ÷ I²")
        else -> listOf("V = √(P × R)", "I = √(P ÷ R)")
    }

    /** True when [provided] and [computed] disagree beyond relative tolerance. */
    private fun differs(provided: BigDecimal, computed: BigDecimal): Boolean {
        val diff = provided.subtract(computed).abs()
        val scale = provided.abs().max(computed.abs())
        if (scale.signum() == 0) return false
        return diff.divide(scale, CTX).compareTo(REL_TOL) > 0
    }

    private fun sqrt(value: BigDecimal): BigDecimal {
        if (value.signum() == 0) return BigDecimal.ZERO
        return BigDecimal.valueOf(Math.sqrt(value.toDouble()))
    }

    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
