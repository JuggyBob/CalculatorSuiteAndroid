package com.calcsuite.engine.finance

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.map
import com.calcsuite.core.ok
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Sales tax / VAT / GST arithmetic.
 *
 * ## Conventions (documented once, applied everywhere)
 * - Percentages are entered as percentages (`7.25` means 7.25 %).
 * - All intermediate amounts keep [MC] precision (20 significant digits);
 *   [roundToCents] is only for settlement/display.
 * - `addTax` and `extractTax` handle a single rate.
 * - `addComponents` applies **every component rate to the same pre-tax base**
 *   and adds the results: `gross = net + Σ net·rᵢ`. A component is never
 *   charged on another component's tax (no compounding, no tax-on-tax). This
 *   is the usual way split invoice lines (state + city + special district)
 *   are stated; it is *not* the same as compounding rates.
 */
object TaxEngine {

    /** Intermediate-precision context: 20 significant digits, HALF_UP. */
    val MC: MathContext = MathContext(20, RoundingMode.HALF_UP)

    private val HUNDRED = BigDecimal("100")

    /** net / tax / gross triple. */
    data class Breakdown(
        val net: BigDecimal,
        val tax: BigDecimal,
        val gross: BigDecimal,
    )

    /** One named component of a multi-rate tax. */
    data class Component(
        val name: String,
        val ratePct: BigDecimal,
        val tax: BigDecimal,
    )

    /** Multi-component result with the per-component breakdown. */
    data class MultiBreakdown(
        val net: BigDecimal,
        val totalTax: BigDecimal,
        val gross: BigDecimal,
        val components: List<Component>,
    ) {
        /** The combined rate actually applied to the net base, in percent. */
        val combinedRatePct: BigDecimal
            get() = components.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.ratePct) }
    }

    const val MULTI_NOTE =
        "Every component rate is applied to the same pre-tax base and the results are added; " +
            "no component is charged on another component's tax (no tax-on-tax compounding)."

    /** net → gross: tax = net · r, gross = net + tax. */
    fun addTax(net: BigDecimal, ratePct: BigDecimal): CalcResult<Breakdown> {
        amountError(net)?.let { return it }
        rateError(ratePct)?.let { return it }
        val tax = net.multiply(factor(ratePct), MC)
        return ok(Breakdown(net = net, tax = tax, gross = net.add(tax, MC)))
    }

    /** gross → net: net = gross / (1 + r), tax = gross − net. */
    fun extractTax(gross: BigDecimal, ratePct: BigDecimal): CalcResult<Breakdown> {
        amountError(gross)?.let { return it }
        rateError(ratePct)?.let { return it }
        val net = gross.divide(BigDecimal.ONE.add(factor(ratePct), MC), MC)
        return ok(Breakdown(net = net, tax = gross.subtract(net, MC), gross = gross))
    }

    /** The tax contained in a tax-inclusive amount: `gross − gross/(1+r)`. */
    fun containedTax(gross: BigDecimal, ratePct: BigDecimal): CalcResult<BigDecimal> =
        extractTax(gross, ratePct).map { it.tax }

    /**
     * Adds several named rates to a net base (state + city + …). Each rate is
     * applied to `net`; see [MULTI_NOTE].
     */
    fun addComponents(
        net: BigDecimal,
        components: List<Pair<String, BigDecimal>>,
    ): CalcResult<MultiBreakdown> {
        amountError(net)?.let { return it }
        if (components.isEmpty()) {
            return err(ErrorKind.Insufficient, "Add at least one tax component")
        }
        var total = BigDecimal.ZERO
        val parts = ArrayList<Component>(components.size)
        components.forEachIndexed { index, (name, ratePct) ->
            rateError(ratePct)?.let { return it }
            val tax = net.multiply(factor(ratePct), MC)
            total = total.add(tax, MC)
            parts += Component(
                name = name.ifBlank { "Component ${index + 1}" },
                ratePct = ratePct,
                tax = tax,
            )
        }
        return ok(MultiBreakdown(net = net, totalTax = total, gross = net.add(total, MC), components = parts))
    }

    /**
     * Inverse of [addComponents]: treats [components] as a single combined
     * rate `Σrᵢ` applied to the net base (`net = gross / (1 + Σrᵢ)`) and then
     * attributes the total tax to each component proportionally to its rate.
     */
    fun extractComponents(
        gross: BigDecimal,
        components: List<Pair<String, BigDecimal>>,
    ): CalcResult<MultiBreakdown> {
        amountError(gross)?.let { return it }
        if (components.isEmpty()) {
            return err(ErrorKind.Insufficient, "Add at least one tax component")
        }
        var combined = BigDecimal.ZERO
        components.forEach { (_, ratePct) ->
            rateError(ratePct)?.let { return it }
            combined = combined.add(ratePct, MC)
        }
        val net = gross.divide(BigDecimal.ONE.add(factor(combined), MC), MC)
        val totalTax = gross.subtract(net, MC)
        val parts = components.mapIndexed { index, (name, ratePct) ->
            val share = if (combined.signum() == 0) {
                BigDecimal.ZERO
            } else {
                totalTax.multiply(ratePct.divide(combined, MC), MC)
            }
            Component(
                name = name.ifBlank { "Component ${index + 1}" },
                ratePct = ratePct,
                tax = share,
            )
        }
        return ok(MultiBreakdown(net = net, totalTax = totalTax, gross = gross, components = parts))
    }

    /** Settlement rounding used by screens and receipts. */
    fun roundToCents(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)

    // -------------------------------------------------------------- internals

    private fun factor(ratePct: BigDecimal): BigDecimal = ratePct.divide(HUNDRED, MC)

    private fun amountError(amount: BigDecimal): CalcResult<Nothing>? =
        if (amount.signum() < 0) err(ErrorKind.InvalidInput, "Amount must not be negative") else null

    private fun rateError(ratePct: BigDecimal): CalcResult<Nothing>? =
        if (ratePct.signum() < 0) err(ErrorKind.InvalidInput, "Rate must not be negative") else null
}
