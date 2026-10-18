package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/** Direct: a / b = c / d. Inverse: a × b = c × d. */
enum class ProportionMode { Direct, Inverse }

/** The four positions of a proportion. */
enum class ProportionSlot {
    A, B, C, D;

    val label: String get() = name.lowercase()
}

data class ProportionSolution(
    val mode: ProportionMode,
    val unknown: ProportionSlot,
    val value: BigDecimal,
    /** The solved proportion with numbers substituted, e.g. "2/3 = 8/12". */
    val equation: String,
    val steps: List<String>,
    val verification: String,
)

/**
 * Solves a proportion for exactly one unknown among the four positions.
 *
 * Direct mode follows `a/b = c/d` (cross multiplication a·d = b·c); inverse
 * mode follows `a·b = c·d` (both products stay constant, e.g. more workers
 * means fewer days). At least three positions must be known.
 */
object ProportionEngine {

    fun solve(
        mode: ProportionMode,
        a: BigDecimal? = null,
        b: BigDecimal? = null,
        c: BigDecimal? = null,
        d: BigDecimal? = null,
        unknown: ProportionSlot,
    ): CalcResult<ProportionSolution> {
        val values = mapOf(
            ProportionSlot.A to a,
            ProportionSlot.B to b,
            ProportionSlot.C to c,
            ProportionSlot.D to d,
        )
        val knownCount = values.count { it.key != unknown && it.value != null }
        if (knownCount < 3) {
            return err(
                ErrorKind.Insufficient,
                "Three of the four values are needed; only $knownCount given",
            )
        }
        val va = values[ProportionSlot.A]
        val vb = values[ProportionSlot.B]
        val vc = values[ProportionSlot.C]
        val vd = values[ProportionSlot.D]
        fun known(slot: ProportionSlot): BigDecimal = values.getValue(slot)!!

        // numerator/divisor of the solved slot, plus the two multiplied factors.
        val plan = when (mode) {
            ProportionMode.Direct -> when (unknown) {
                ProportionSlot.A -> Plan(known(ProportionSlot.B), known(ProportionSlot.C), ProportionSlot.D)
                ProportionSlot.B -> Plan(known(ProportionSlot.A), known(ProportionSlot.D), ProportionSlot.C)
                ProportionSlot.C -> Plan(known(ProportionSlot.A), known(ProportionSlot.D), ProportionSlot.B)
                ProportionSlot.D -> Plan(known(ProportionSlot.B), known(ProportionSlot.C), ProportionSlot.A)
            }
            ProportionMode.Inverse -> when (unknown) {
                ProportionSlot.A -> Plan(known(ProportionSlot.C), known(ProportionSlot.D), ProportionSlot.B)
                ProportionSlot.B -> Plan(known(ProportionSlot.C), known(ProportionSlot.D), ProportionSlot.A)
                ProportionSlot.C -> Plan(known(ProportionSlot.A), known(ProportionSlot.B), ProportionSlot.D)
                ProportionSlot.D -> Plan(known(ProportionSlot.A), known(ProportionSlot.B), ProportionSlot.C)
            }
        }
        val factor1 = plan.factor1
        val factor2 = plan.factor2
        val divisorSlot = plan.divisorSlot
        val divisor: BigDecimal = known(divisorSlot)
        val numerator: BigDecimal = factor1.multiply(factor2)
        if (divisor.signum() == 0) {
            return err(
                ErrorKind.DivisionByZero,
                "${divisorSlot.label} is zero, so ${unknown.label} cannot be found by division",
            )
        }
        val value = numerator.divide(divisor, ALG_CTX)
        // The four positions with the answer filled in.
        val solvedValues: List<BigDecimal> = ProportionSlot.entries.map { slot ->
            if (slot == unknown) value else known(slot)
        }
        val solvedVa = solvedValues[0]
        val solvedVb = solvedValues[1]
        val solvedVc = solvedValues[2]
        val solvedVd = solvedValues[3]

        val relation = if (mode == ProportionMode.Direct) "${
            ProportionSlot.A.label}/${ProportionSlot.B.label} = ${ProportionSlot.C.label}/${ProportionSlot.D.label}"
        else "${ProportionSlot.A.label} × ${ProportionSlot.B.label} = ${ProportionSlot.C.label} × ${ProportionSlot.D.label}"
        val substituted = solvedValues.map { plain(it) }
        val solvedText = if (mode == ProportionMode.Direct) {
            "${substituted[0]}/${substituted[1]} = ${substituted[2]}/${substituted[3]}"
        } else {
            "${substituted[0]} × ${substituted[1]} = ${substituted[2]} × ${substituted[3]}"
        }

        val crossProduct = if (mode == ProportionMode.Direct) {
            "${plain(solvedValues[0])} × ${plain(solvedValues[3])} = " +
                "${plain(solvedValues[1])} × ${plain(solvedValues[2])}"
        } else {
            "${plain(solvedValues[0])} × ${plain(solvedValues[1])} = " +
                "${plain(solvedValues[2])} × ${plain(solvedValues[3])}"
        }
        val steps = buildList {
            add(if (mode == ProportionMode.Direct) "Write the proportion: $relation" else "Write the inverse proportion: $relation")
            add("Cross multiply: $crossProduct")
            add(
                "Isolate ${unknown.label}: ${unknown.label} = (${plain(factor1)} × ${plain(factor2)}) ÷ ${plain(divisor)}",
            )
            add("${unknown.label} = ${plain(numerator)} ÷ ${plain(divisor)}")
            add("${unknown.label} = ${plain(value)}")
            add("Check: $solvedText")
        }

        val leftSide = if (mode == ProportionMode.Direct) solvedVa.multiply(solvedVd, ALG_CTX)
        else solvedVa.multiply(solvedVb, ALG_CTX)
        val rightSide = if (mode == ProportionMode.Direct) solvedVb.multiply(solvedVc, ALG_CTX)
        else solvedVc.multiply(solvedVd, ALG_CTX)
        val difference = leftSide.subtract(rightSide).abs()
        val scale = leftSide.abs().max(rightSide.abs())
        val verification = if (scale.signum() == 0 || difference.compareTo(BigDecimal.ZERO) == 0) {
            "Both cross products are ${plain(leftSide)} — the proportion holds exactly"
        } else if (difference.compareTo(scale.movePointLeft(28)) <= 0) {
            "Both cross products agree at 34 significant digits (${plain(roundForText(leftSide))} ≈ ${plain(roundForText(rightSide))})"
        } else {
            "Cross products differ: ${plain(leftSide)} vs ${plain(rightSide)}"
        }

        return ok(
            ProportionSolution(
                mode = mode,
                unknown = unknown,
                value = value,
                equation = solvedText,
                steps = steps,
                verification = verification,
            ),
        )
    }

    fun solveDirect(
        a: BigDecimal? = null,
        b: BigDecimal? = null,
        c: BigDecimal? = null,
        d: BigDecimal? = null,
        unknown: ProportionSlot,
    ): CalcResult<ProportionSolution> = solve(ProportionMode.Direct, a, b, c, d, unknown)

    fun solveInverse(
        a: BigDecimal? = null,
        b: BigDecimal? = null,
        c: BigDecimal? = null,
        d: BigDecimal? = null,
        unknown: ProportionSlot,
    ): CalcResult<ProportionSolution> = solve(ProportionMode.Inverse, a, b, c, d, unknown)

    private data class Plan(
        val factor1: BigDecimal,
        val factor2: BigDecimal,
        val divisorSlot: ProportionSlot,
    )
}
