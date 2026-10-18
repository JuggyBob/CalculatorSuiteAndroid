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
 * Compounding frequency. [periodsPerYear] is null for continuous compounding.
 */
enum class CompoundFrequency(val id: String, val label: String, val periodsPerYear: Int?) {
    ANNUAL("annual", "Annually", 1),
    SEMI_ANNUAL("semiannual", "Semi-annually", 2),
    QUARTERLY("quarterly", "Quarterly", 4),
    MONTHLY("monthly", "Monthly", 12),
    WEEKLY("weekly", "Weekly", 52),
    DAILY("daily", "Daily", 365),
    CONTINUOUS("continuous", "Continuous", null);

    val isContinuous: Boolean get() = periodsPerYear == null

    companion object {
        fun byId(id: String): CompoundFrequency? = entries.firstOrNull { it.id == id }
    }
}

enum class InterestMode(val id: String, val label: String) {
    SIMPLE("simple", "Simple"),
    COMPOUND("compound", "Compound"),
}

/**
 * Regular contributions (a savings plan).
 *
 * @param frequency how often a contribution is made; must not be continuous.
 * @param atBeginning true = beginning-of-period (annuity-due, each payment
 *        earns one extra period of interest), false = end-of-period.
 */
data class ContributionPlan(
    val amount: BigDecimal,
    val frequency: CompoundFrequency,
    val atBeginning: Boolean = false,
)

data class InterestResult(
    val mode: InterestMode,
    val principal: BigDecimal,
    val finalAmount: BigDecimal,
    val interestEarned: BigDecimal,
    /** Sum of all regular contributions made. */
    val contributionsTotal: BigDecimal,
    /** principal + contributions. */
    val totalContributed: BigDecimal,
    val effectiveAnnualRatePct: BigDecimal,
    val effectiveRateApproximate: Boolean,
    /** Compounding periods; null for continuous compounding. */
    val periods: BigDecimal?,
    /**
     * True when some step needed a double-precision fallback (fractional
     * powers or continuous compounding) — see the note on [decimalPow].
     */
    val approximate: Boolean,
)

data class InterestComparison(
    val simpleFinal: BigDecimal,
    val simpleInterest: BigDecimal,
    val compoundFinal: BigDecimal,
    val compoundInterest: BigDecimal,
    val difference: BigDecimal,
    val compoundApproximate: Boolean,
)

/**
 * Simple and compound interest.
 *
 * ## Formulas
 * - Simple: `A = P·(1 + r·t)`.
 * - Compound: `A = P·(1 + r/n)^(n·t)`; continuous `A = P·e^(r·t)`.
 * - Contributions (end of period): `FV = PMT·((1+j)^m − 1)/j`;
 *   beginning of period: `FV·(1+j)`, with `j = r / k` the rate per
 *   contribution period (`k` = contribution frequency) and `m = k·t` whole
 *   contribution periods. When the base compounds continuously the per-period
 *   rate is the equivalent effective rate `j = e^(r/k) − 1`.
 * - EAR: `(1 + r/n)^n − 1`, continuous `e^r − 1`.
 *
 * ## Precision
 * Integer exponents use [BigDecimal.pow] and stay decimal. A **fractional**
 * exponent (e.g. 0.7 years compounded monthly) has no exact BigDecimal method,
 * so `decimalPow` falls back to the double-precision identity
 * `x^y = exp(ln(x)·y)` for that final step only; such results are flagged
 * [InterestResult.approximate]. Continuous compounding is always flagged.
 */
object InterestEngine {

    val MC: MathContext = MathContext(20, RoundingMode.HALF_UP)

    private val HUNDRED = BigDecimal("100")
    private val TWELVE_MONTHS = BigDecimal("12")

    /** Simple interest: `A = P·(1 + r·t)`. Contributions are compound-only. */
    fun simple(
        principal: BigDecimal,
        annualRatePct: BigDecimal,
        durationYears: BigDecimal,
    ): CalcResult<InterestResult> {
        validate(principal, annualRatePct, durationYears)?.let { return it }
        val rate = annualRatePct.divide(HUNDRED, MC)
        val growth = BigDecimal.ONE.add(rate.multiply(durationYears, MC), MC)
        val finalAmount = principal.multiply(growth, MC)
        val interest = finalAmount.subtract(principal, MC)
        return ok(
            InterestResult(
                mode = InterestMode.SIMPLE,
                principal = principal,
                finalAmount = finalAmount,
                interestEarned = interest,
                contributionsTotal = BigDecimal.ZERO,
                totalContributed = principal,
                // Simple interest has no compounding: the effective rate equals
                // the nominal rate exactly.
                effectiveAnnualRatePct = annualRatePct,
                effectiveRateApproximate = false,
                periods = null,
                approximate = false,
            ),
        )
    }

    /** Compound interest with optional regular contributions. */
    fun compound(
        principal: BigDecimal,
        annualRatePct: BigDecimal,
        durationYears: BigDecimal,
        compounding: CompoundFrequency = CompoundFrequency.ANNUAL,
        contributions: ContributionPlan? = null,
    ): CalcResult<InterestResult> {
        validate(principal, annualRatePct, durationYears)?.let { return it }
        if (contributions != null) {
            if (contributions.amount.signum() < 0) {
                return err(ErrorKind.InvalidInput, "Contribution amount must not be negative")
            }
            if (contributions.frequency.isContinuous) {
                return err(
                    ErrorKind.InvalidInput,
                    "Regular contributions need a periodic frequency, not continuous compounding",
                )
            }
        }

        val rate = annualRatePct.divide(HUNDRED, MC)
        var approximate = false

        // --- principal growth -------------------------------------------------
        val finalPrincipal: BigDecimal
        val periods: BigDecimal?
        if (compounding.isContinuous) {
            val exponent = rate.multiply(durationYears, MC)
            val factor = decimalExp(exponent)
                ?: return err(ErrorKind.Overflow, "The result is too large to represent")
            finalPrincipal = principal.multiply(factor, MC)
            periods = null
            approximate = true
        } else {
            val perYear = BigDecimal.valueOf(compounding.periodsPerYear!!.toLong())
            val perPeriod = rate.divide(perYear, MC)
            val n = durationYears.multiply(perYear, MC)
            val growth = decimalPow(BigDecimal.ONE.add(perPeriod, MC), n, MC)
                ?: return err(ErrorKind.Overflow, "The result is too large to represent")
            finalPrincipal = principal.multiply(growth.first, MC)
            periods = n
            approximate = approximate || growth.second
        }

        // --- regular contributions -------------------------------------------
        var contributionsTotal = BigDecimal.ZERO
        var contributionFutureValue = BigDecimal.ZERO
        if (contributions != null && contributions.amount.signum() > 0) {
            val perYear = BigDecimal.valueOf(contributions.frequency.periodsPerYear!!.toLong())
            val perPeriod = if (compounding.isContinuous) {
                val factor = decimalExp(rate.divide(perYear, MC))
                    ?: return err(ErrorKind.Overflow, "The contribution rate is not representable")
                approximate = true
                factor.subtract(BigDecimal.ONE, MC)
            } else {
                rate.divide(perYear, MC)
            }
            val count = durationYears.multiply(perYear, MC).setScale(0, RoundingMode.HALF_UP)
            if (count.signum() > 0) {
                val futureValue = if (perPeriod.signum() == 0) {
                    contributions.amount.multiply(count, MC)
                } else {
                    val growth = decimalPow(BigDecimal.ONE.add(perPeriod, MC), count, MC)
                        ?: return err(ErrorKind.Overflow, "The result is too large to represent")
                    approximate = approximate || growth.second
                    contributions.amount
                        .multiply(growth.first.subtract(BigDecimal.ONE, MC), MC)
                        .divide(perPeriod, MC)
                }
                contributionFutureValue = if (contributions.atBeginning) {
                    futureValue.multiply(BigDecimal.ONE.add(perPeriod, MC), MC)
                } else {
                    futureValue
                }
                contributionsTotal = contributions.amount.multiply(count, MC)
            }
        }

        val finalAmount = finalPrincipal.add(contributionFutureValue, MC)
        val earned = finalAmount.subtract(principal, MC).subtract(contributionsTotal, MC)
        val ear = effectiveAnnualRate(rate, compounding)
            ?: return err(ErrorKind.Overflow, "The effective rate is not representable")
        return ok(
            InterestResult(
                mode = InterestMode.COMPOUND,
                principal = principal,
                finalAmount = finalAmount,
                interestEarned = earned,
                contributionsTotal = contributionsTotal,
                totalContributed = principal.add(contributionsTotal, MC),
                effectiveAnnualRatePct = ear.first,
                effectiveRateApproximate = ear.second,
                periods = periods,
                approximate = approximate,
            ),
        )
    }

    /** `EAR = (1 + r/n)^n − 1` (continuous: `e^r − 1`), returned in percent. */
    fun effectiveAnnualRatePct(
        annualRatePct: BigDecimal,
        compounding: CompoundFrequency,
    ): CalcResult<BigDecimal> {
        if (annualRatePct.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Rate must not be negative")
        }
        val rate = annualRatePct.divide(HUNDRED, MC)
        val result = effectiveAnnualRate(rate, compounding)
            ?: return err(ErrorKind.Overflow, "The effective rate is not representable")
        return ok(result.first)
    }

    /** Simple vs compound for the same principal, rate and duration. */
    fun compare(
        principal: BigDecimal,
        annualRatePct: BigDecimal,
        durationYears: BigDecimal,
        compounding: CompoundFrequency = CompoundFrequency.ANNUAL,
    ): CalcResult<InterestComparison> {
        val simpleResult = simple(principal, annualRatePct, durationYears)
        if (simpleResult is CalcResult.Err) return simpleResult
        val compoundResult = compound(principal, annualRatePct, durationYears, compounding)
        if (compoundResult is CalcResult.Err) return compoundResult
        val simpleValue = (simpleResult as CalcResult.Ok).value
        val compoundValue = (compoundResult as CalcResult.Ok).value
        return ok(
            InterestComparison(
                simpleFinal = simpleValue.finalAmount,
                simpleInterest = simpleValue.interestEarned,
                compoundFinal = compoundValue.finalAmount,
                compoundInterest = compoundValue.interestEarned,
                difference = compoundValue.finalAmount.subtract(simpleValue.finalAmount, MC),
                compoundApproximate = compoundValue.approximate,
            ),
        )
    }

    /** Converts a duration given in months into years. */
    fun monthsToYears(months: BigDecimal): BigDecimal = months.divide(TWELVE_MONTHS, MC)

    // -------------------------------------------------------------- internals

    private fun validate(
        principal: BigDecimal,
        annualRatePct: BigDecimal,
        durationYears: BigDecimal,
    ): CalcResult<Nothing>? = when {
        principal.signum() < 0 -> err(ErrorKind.InvalidInput, "Principal must not be negative")
        annualRatePct.signum() < 0 -> err(ErrorKind.InvalidInput, "Rate must not be negative")
        durationYears.signum() < 0 -> err(ErrorKind.InvalidInput, "Duration must not be negative")
        else -> null
    }

    /** Returns (EAR in percent, approximate) or null when not representable. */
    private fun effectiveAnnualRate(
        rate: BigDecimal,
        compounding: CompoundFrequency,
    ): Pair<BigDecimal, Boolean>? {
        if (compounding.isContinuous) {
            val factor = decimalExp(rate) ?: return null
            return factor.subtract(BigDecimal.ONE, MC).multiply(HUNDRED, MC) to true
        }
        val perYear = BigDecimal.valueOf(compounding.periodsPerYear!!.toLong())
        val perPeriod = rate.divide(perYear, MC)
        val growth = decimalPow(BigDecimal.ONE.add(perPeriod, MC), perYear, MC) ?: return null
        return growth.first.subtract(BigDecimal.ONE, MC).multiply(HUNDRED, MC) to growth.second
    }

    /**
     * `base^exponent` in decimal arithmetic.
     *
     * Integer exponents are exact for the 20-digit [MC] (BigDecimal.pow).
     * **Fractional exponents have no exact BigDecimal method**: this falls back
     * to `exp(ln(x)·y)` in double precision for the final step, and the returned
     * `approximate` flag is true. Returns null when the result is not finite
     * (overflow), so callers can report it instead of emitting Infinity.
     */
    internal fun decimalPow(
        base: BigDecimal,
        exponent: BigDecimal,
        mc: MathContext = MC,
    ): Pair<BigDecimal, Boolean>? {
        val stripped = exponent.stripTrailingZeros()
        if (stripped.scale() <= 0) {
            val integer = try {
                stripped.toBigIntegerExact()
            } catch (_: ArithmeticException) {
                null
            }
            if (integer != null && integer.abs() <= BigInteger.valueOf(100_000L)) {
                val e = integer.toInt()
                val result = if (e >= 0) {
                    base.pow(e, mc)
                } else {
                    BigDecimal.ONE.divide(base.pow(-e, mc), mc)
                }
                return result to false
            }
        }
        return doublePow(base, exponent)
    }

    /** `e^x` in double precision; null when not finite. */
    internal fun decimalExp(x: BigDecimal): BigDecimal? {
        val value = Math.exp(x.toDouble())
        return if (value.isFinite()) BigDecimal.valueOf(value) else null
    }

    private fun doublePow(base: BigDecimal, exponent: BigDecimal): Pair<BigDecimal, Boolean>? {
        val x = base.toDouble()
        val y = exponent.toDouble()
        if (!x.isFinite() || !y.isFinite() || x < 0.0) return null
        val value = Math.pow(x, y) // exp(ln(x)·y) — double fallback, marked approximate
        return if (value.isFinite()) BigDecimal.valueOf(value) to true else null
    }
}
