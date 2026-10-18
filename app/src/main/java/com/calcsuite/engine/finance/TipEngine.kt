package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Tip and bill-splitting math with cent-accurate allocation.
 *
 * The grand total is split with the **largest-remainder method** in whole
 * cents, so the shares always add up to the total exactly — no cent is lost
 * and no cent is invented (`115.00 / 3 → 38.34 + 38.33 + 38.33`).
 */
object TipEngine {

    val MC: MathContext = MathContext(20, RoundingMode.HALF_UP)

    private val HUNDRED = BigDecimal("100")

    /** Upper bound so the share list stays a usable UI element. */
    const val MAX_PEOPLE = 999

    /**
     * How the grand total is rounded to a cash-friendly amount.
     *
     * Rounding is applied to the grand total only; the tip absorbs the
     * difference (it is recomputed as `total − bill − tax`). This matches how
     * restaurants round a bill, and is documented in the UI.
     */
    enum class RoundingOption(val id: String, val label: String, val step: BigDecimal?) {
        NONE("none", "No rounding", null),
        NEAREST_005("0.05", "Nearest 0.05", BigDecimal("0.05")),
        NEAREST_100("1.00", "Nearest 1.00", BigDecimal.ONE);

        companion object {
            fun byId(id: String): RoundingOption? = entries.firstOrNull { it.id == id }
        }
    }

    /**
     * @param bill pre-tax subtotal.
     * @param tax tax added on top of [bill]; kept separate so the "tip on
     *        pre-tax vs post-tax" choice is visible and exact.
     * @param tipOnPreTax when false the tip base is `bill + tax`.
     */
    data class TipInput(
        val bill: BigDecimal,
        val tax: BigDecimal = BigDecimal.ZERO,
        val tipPct: BigDecimal,
        val people: Int,
        val tipOnPreTax: Boolean,
        val rounding: RoundingOption,
    )

    data class TipResult(
        /** The amount the percentage was applied to. */
        val tipBase: BigDecimal,
        val tip: BigDecimal,
        val total: BigDecimal,
        /** Total before the rounding option was applied. */
        val unroundedTotal: BigDecimal,
        val roundingAdjustment: BigDecimal,
        /** Split of [total]; sums to [total] exactly. */
        val shares: List<BigDecimal>,
        /** Split of [tip]; sums to [tip] exactly. */
        val tipShares: List<BigDecimal>,
        val evenSplit: Boolean,
    )

    fun calculate(input: TipInput): CalcResult<TipResult> {
        if (input.bill.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Bill must not be negative")
        }
        if (input.tax.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Tax must not be negative")
        }
        if (input.tipPct.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Tip percentage must not be negative")
        }
        if (input.people < 1) {
            return err(ErrorKind.InvalidInput, "At least one person is required")
        }
        if (input.people > MAX_PEOPLE) {
            return err(ErrorKind.InvalidInput, "At most $MAX_PEOPLE people can share one bill")
        }

        val tipBase = if (input.tipOnPreTax) input.bill else input.bill.add(input.tax, MC)
        val rawTip = tipBase.multiply(input.tipPct, MC).divide(HUNDRED, MC)
        val unroundedTotal = input.bill.add(input.tax, MC).add(rawTip, MC)
        val total = applyRounding(unroundedTotal, input.rounding)
        val tip = total.subtract(input.bill, MC).subtract(input.tax, MC)
            .coerceAtLeastMin(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
        val settledTotal = input.bill.add(input.tax, MC).add(tip, MC).setScale(2, RoundingMode.HALF_UP)

        val shares = split(settledTotal, input.people)
        val tipShares = split(tip, input.people)
        return ok(
            TipResult(
                tipBase = tipBase,
                tip = tip,
                total = settledTotal,
                unroundedTotal = unroundedTotal,
                roundingAdjustment = settledTotal.subtract(unroundedTotal, MC),
                shares = shares,
                tipShares = tipShares,
                evenSplit = shares.distinct().size <= 1,
            ),
        )
    }

    /** Applies a [RoundingOption] to a total. `null` step means cents only. */
    fun applyRounding(total: BigDecimal, option: RoundingOption): BigDecimal {
        val step = option.step ?: return total.setScale(2, RoundingMode.HALF_UP)
        return total.divide(step, 0, RoundingMode.HALF_UP).multiply(step).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Largest-remainder split of [total] into [people] whole-cent shares.
     * The first `totalCents mod people` shares receive one extra cent.
     */
    fun split(total: BigDecimal, people: Int): List<BigDecimal> {
        require(people >= 1) { "people must be at least 1" }
        val totalCents = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toBigIntegerExact()
        return splitCents(totalCents, people).map { cents -> BigDecimal(cents).movePointLeft(2) }
    }

    private fun splitCents(totalCents: BigInteger, people: Int): List<BigInteger> {
        val count = BigInteger.valueOf(people.toLong())
        val base = totalCents.divide(count)
        val remainder = totalCents.remainder(count).toInt() // 0 until people
        return (0 until people).map { index ->
            if (index < remainder) base.add(BigInteger.ONE) else base
        }
    }

    private fun BigDecimal.coerceAtLeastMin(min: BigDecimal): BigDecimal =
        if (this < min) min else this
}
