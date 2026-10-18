package com.calcsuite.engine.algebra

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal

/**
 * Percentage arithmetic.
 *
 * Conventions used by every function in this object:
 * - A percentage is a plain number: `15` means 15 %.
 * - Change percentages are signed: positive is an increase.
 * - The relative difference uses the mean of the two magnitudes as reference.
 */
object PercentageEngine {

    private val HUNDRED = BigDecimal("100")
    private val TWO = BigDecimal("2")

    /** Absolute change of a value, plus the signed percentage it represents. */
    data class ChangeResult(
        val original: BigDecimal,
        val change: BigDecimal,
        val finalValue: BigDecimal,
        /** Signed percentage of [original]; negative when the value fell. */
        val percent: BigDecimal,
    )

    /** `percent` % of [value]. */
    fun percentOf(percent: BigDecimal, value: BigDecimal): CalcResult<BigDecimal> =
        ok(value.multiply(percent).divide(HUNDRED, ALG_CTX))

    /** [part] expressed as a percentage of [whole]. */
    fun whatPercent(part: BigDecimal, whole: BigDecimal): CalcResult<BigDecimal> {
        if (whole.signum() == 0) {
            return err(ErrorKind.DivisionByZero, "The reference value is zero, so a percentage of it is undefined")
        }
        return ok(part.multiply(HUNDRED).divide(whole, ALG_CTX))
    }

    /** Signed percentage change from [start] to [end]: (end − start) ÷ start × 100. */
    fun percentIncrease(start: BigDecimal, end: BigDecimal): CalcResult<ChangeResult> {
        if (start.signum() == 0) {
            return err(ErrorKind.Domain, "The starting value is zero, so a percentage change is undefined")
        }
        val delta = end.subtract(start)
        val percent = delta.multiply(HUNDRED).divide(start, ALG_CTX)
        return ok(ChangeResult(start, delta, end, percent))
    }

    /** Size of the decrease from [start] to [end]: (start − end) ÷ start × 100 (negative when the value rose). */
    fun percentDecrease(start: BigDecimal, end: BigDecimal): CalcResult<ChangeResult> {
        if (start.signum() == 0) {
            return err(ErrorKind.Domain, "The starting value is zero, so a percentage change is undefined")
        }
        val delta = start.subtract(end)
        val percent = delta.multiply(HUNDRED).divide(start, ALG_CTX)
        return ok(ChangeResult(start, delta, end, percent))
    }

    /**
     * Value before a percentage change: `final = original × (1 ± pct/100)`.
     *
     * @param increase true when the change was an increase, false for a decrease.
     */
    fun recoverOriginal(finalValue: BigDecimal, percent: BigDecimal, increase: Boolean): CalcResult<BigDecimal> {
        val ratio = percent.divide(HUNDRED, ALG_CTX)
        val factor = if (increase) BigDecimal.ONE.add(ratio) else BigDecimal.ONE.subtract(ratio)
        if (factor.signum() == 0) {
            return err(
                ErrorKind.DivisionByZero,
                "A decrease of exactly 100% leaves nothing, so the original value cannot be recovered",
            )
        }
        return ok(finalValue.divide(factor, ALG_CTX))
    }

    /**
     * Applies several percentage changes one after another:
     * `final = base × Π(1 + changeᵢ/100)`.
     */
    fun successiveChange(base: BigDecimal, changes: List<BigDecimal>): CalcResult<ChangeResult> {
        if (changes.isEmpty()) return err(ErrorKind.Insufficient, "Enter at least one percentage change")
        if (base.signum() == 0) {
            return err(ErrorKind.Domain, "The starting value is zero, so a percentage change is undefined")
        }
        var factor = BigDecimal.ONE
        for (change in changes) {
            val step = BigDecimal.ONE.add(change.divide(HUNDRED, ALG_CTX))
            factor = factor.multiply(step, ALG_CTX)
        }
        val finalValue = base.multiply(factor, ALG_CTX)
        val change = finalValue.subtract(base, ALG_CTX)
        val percent = change.multiply(HUNDRED).divide(base, ALG_CTX)
        return ok(ChangeResult(base, change, finalValue, percent))
    }

    /** Difference in percentage points: [first] − [second]. */
    fun percentagePoints(first: BigDecimal, second: BigDecimal): BigDecimal =
        first.subtract(second, ALG_CTX)

    /**
     * Relative difference between two values: |a − b| ÷ |(a + b)/2| × 100.
     *
     * The mean is used as reference so the measure is symmetric (swapping the
     * two values gives the same result). Undefined when the two values cancel.
     */
    fun relativePercentDifference(a: BigDecimal, b: BigDecimal): CalcResult<BigDecimal> {
        if (a.signum() == 0 && b.signum() == 0) {
            return err(ErrorKind.Undefined, "Both values are zero, so the relative difference is undefined")
        }
        val mean = a.add(b, ALG_CTX).divide(TWO, ALG_CTX)
        if (mean.signum() == 0) {
            return err(ErrorKind.Undefined, "The two values cancel out, so the relative difference is undefined")
        }
        val difference = a.subtract(b).abs()
        return ok(difference.multiply(HUNDRED).divide(mean.abs(), ALG_CTX))
    }
}
