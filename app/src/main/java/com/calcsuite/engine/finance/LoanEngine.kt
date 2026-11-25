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
 * Fixed-rate amortising loan: payment, totals, payoff time and schedule.
 *
 * ## Rounding policy
 * Money is tracked in whole cents: every scheduled payment, every interest
 * charge and every balance is a cent amount (scale 2, HALF_UP). The level
 * payment is rounded to the nearest cent (HALF_UP); when that is *below* the
 * exact annuity payment, the resulting residue (which grows with the term)
 * is cleared by adjusting the final instalment, so the balance reaches
 * exactly zero at the end of the contractual term. Because balances are
 * updated by exact decimal subtraction, `Σ principal payments = principal`
 * holds exactly.
 *
 * ## Extras
 * An extra amount can be added to every payment and/or as a one-off lump sum
 * in a chosen period. The schedule is recomputed period by period, so the
 * payoff date and the interest saved follow from the loop — they are never
 * estimated with a shortcut formula.
 *
 * ## Interest
 * `i = annualRate% / 100 / paymentsPerYear`, `payment = P·i / (1 − (1+i)^−n)`
 * with [BigDecimal.pow] for the integer exponent. A zero rate is valid:
 * `payment = P / n` and the schedule carries no interest.
 */
object LoanEngine {

    val MC: MathContext = MathContext(20, RoundingMode.HALF_UP)

    private val HUNDRED = BigDecimal("100")

    /** Schedules are truncated to this many rows; totals still use all periods. */
    const val MAX_SCHEDULE_ROWS = 1200

    /** Hard cap on contractual periods (100 years of monthly payments). */
    const val MAX_PERIODS = 12_000

    const val DISCLAIMER =
        "Estimate only. Fees, insurance, property taxes and rate changes are excluded, " +
            "and the result is not a lender offer."

    /**
     * @param termYears term expressed in years; converted with
     *        `round(termYears × paymentsPerYear)` unless [numberOfPayments]
     *        is set.
     * @param numberOfPayments explicit period count; wins over [termYears].
     * @param extraPerPeriod added to every payment (0 = none).
     * @param oneOffExtra a single extra payment made in period
     *        [oneOffAtPeriod] (1-based); ignored when the loan pays off earlier.
     */
    data class LoanInput(
        val principal: BigDecimal,
        val annualRatePct: BigDecimal,
        val termYears: BigDecimal? = null,
        val numberOfPayments: Int? = null,
        val paymentsPerYear: Int = 12,
        val extraPerPeriod: BigDecimal = BigDecimal.ZERO,
        val oneOffExtra: BigDecimal = BigDecimal.ZERO,
        val oneOffAtPeriod: Int = 1,
        val includeSchedule: Boolean = false,
    )

    /** One row of the amortisation table; all amounts are exact cent values. */
    data class ScheduleRow(
        val period: Int,
        val payment: BigDecimal,
        val interest: BigDecimal,
        val principal: BigDecimal,
        val balance: BigDecimal,
        val cumulativeInterest: BigDecimal,
    )

    data class LoanResult(
        /** Level payment, rounded to the nearest cent (HALF_UP). */
        val scheduledPayment: BigDecimal,
        /** Contractual number of payments entered by the user. */
        val contractualPayments: Int,
        /** Periods actually needed (shorter when extras are used). */
        val payoffPeriods: Int,
        val totalPaid: BigDecimal,
        val totalInterest: BigDecimal,
        val principal: BigDecimal,
        val zeroInterest: Boolean,
        /** Empty unless [LoanInput.includeSchedule] was set. */
        val schedule: List<ScheduleRow>,
        val scheduleTruncated: Boolean,
    )

    fun compute(input: LoanInput): CalcResult<LoanResult> {
        if (input.principal.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Principal must be greater than zero")
        }
        if (input.annualRatePct.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Interest rate must not be negative")
        }
        if (input.paymentsPerYear < 1 || input.paymentsPerYear > 366) {
            return err(ErrorKind.InvalidInput, "Payments per year must be between 1 and 366")
        }
        if (input.extraPerPeriod.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Extra payment must not be negative")
        }
        if (input.oneOffExtra.signum() < 0) {
            return err(ErrorKind.InvalidInput, "One-off extra payment must not be negative")
        }

        val periods = contractualPeriods(input)
            ?: return err(ErrorKind.Insufficient, "Enter a term or a number of payments")
        if (input.termYears != null && input.termYears.signum() < 0) {
            return err(ErrorKind.InvalidInput, "Term must not be negative")
        }
        if (periods < 1) {
            return err(ErrorKind.InvalidInput, "The loan must have at least one payment")
        }
        if (periods > MAX_PERIODS) {
            return err(ErrorKind.InvalidInput, "The term is too long (at most $MAX_PERIODS payments)")
        }
        if (input.oneOffExtra.signum() > 0 && input.oneOffAtPeriod !in 1..periods) {
            return err(
                ErrorKind.InvalidInput,
                "The one-off payment period must be between 1 and $periods",
            )
        }

        val principal = input.principal.setScale(2, RoundingMode.HALF_UP)
        if (principal.signum() <= 0) {
            return err(ErrorKind.InvalidInput, "Principal must be at least 0.01")
        }
        val perYear = BigDecimal.valueOf(input.paymentsPerYear.toLong())
        val ratePerPeriod = input.annualRatePct.divide(HUNDRED, MC).divide(perYear, MC)

        val paymentExact = if (ratePerPeriod.signum() == 0) {
            principal.divide(BigDecimal.valueOf(periods.toLong()), MC)
        } else {
            val growth = BigDecimal.ONE.add(ratePerPeriod, MC).pow(periods, MC)
            val discount = BigDecimal.ONE.divide(growth, MC)
            principal.multiply(ratePerPeriod, MC)
                .divide(BigDecimal.ONE.subtract(discount, MC), MC)
        }
        val scheduledPayment = paymentExact.setScale(2, RoundingMode.HALF_UP)
        if (scheduledPayment.signum() <= 0) {
            return err(
                ErrorKind.InvalidInput,
                "The payment rounds to zero; increase the principal or shorten the term",
            )
        }

        var balance = principal
        var totalPaid = BigDecimal.ZERO
        var totalInterest = BigDecimal.ZERO
        val rows = ArrayList<ScheduleRow>()
        var period = 0

        while (balance.signum() > 0 && period < periods + 2) {
            period += 1
            val interest = balance.multiply(ratePerPeriod, MC).setScale(2, RoundingMode.HALF_UP)
            val extra = input.extraPerPeriod.add(
                if (period == input.oneOffAtPeriod) input.oneOffExtra else BigDecimal.ZERO,
            )
            val due = balance.add(interest)
            var payment = scheduledPayment.add(extra, MC)
            if (payment.compareTo(due) >= 0) {
                // Normal last payment (with or without extras): pay only what is due.
                payment = due
            } else if (period == periods) {
                // Rounding the level payment down leaves a residue after the
                // contractual term; for long terms that residue can exceed
                // one whole payment. The final instalment always absorbs it
                // so the loan ends on schedule. Every earlier period has
                // already been checked for a positive principal reduction,
                // which keeps genuinely non-amortising inputs out.
                payment = due
            }
            val principalPart = payment.subtract(interest)
            if (principalPart.signum() <= 0) {
                return err(
                    ErrorKind.InvalidInput,
                    "The periodic payment does not cover the interest; check the rate and term",
                )
            }
            balance = balance.subtract(principalPart)
            totalPaid = totalPaid.add(payment)
            totalInterest = totalInterest.add(interest)
            if (input.includeSchedule && rows.size < MAX_SCHEDULE_ROWS) {
                rows += ScheduleRow(
                    period = period,
                    payment = payment,
                    interest = interest,
                    principal = principalPart,
                    balance = balance,
                    cumulativeInterest = totalInterest,
                )
            }
        }

        if (balance.signum() > 0) {
            return err(
                ErrorKind.Undefined,
                "The loan does not amortise with these payments; check the inputs",
            )
        }

        return ok(
            LoanResult(
                scheduledPayment = scheduledPayment,
                contractualPayments = periods,
                payoffPeriods = period,
                totalPaid = totalPaid,
                totalInterest = totalInterest,
                principal = principal,
                zeroInterest = ratePerPeriod.signum() == 0,
                schedule = rows,
                scheduleTruncated = input.includeSchedule && period > rows.size,
            ),
        )
    }

    /** Payment for a loan without extras (convenience wrapper). */
    fun payment(
        principal: BigDecimal,
        annualRatePct: BigDecimal,
        numberOfPayments: Int,
        paymentsPerYear: Int = 12,
    ): CalcResult<BigDecimal> =
        compute(
            LoanInput(
                principal = principal,
                annualRatePct = annualRatePct,
                numberOfPayments = numberOfPayments,
                paymentsPerYear = paymentsPerYear,
            ),
        ).map { it.scheduledPayment }

    /** Resolves the contractual period count from term years or an explicit count. */
    fun contractualPeriods(input: LoanInput): Int? {
        input.numberOfPayments?.let { return it }
        val years = input.termYears ?: return null
        if (years.signum() < 0) return 0
        val scaled = years.multiply(BigDecimal.valueOf(input.paymentsPerYear.toLong()), MC)
            .setScale(0, RoundingMode.HALF_UP)
        return try {
            scaled.intValueExact()
        } catch (_: ArithmeticException) {
            MAX_PERIODS + 1
        }
    }

    /** Note shown under a truncated schedule, or null when nothing was cut. */
    fun scheduleNote(result: LoanResult): String? =
        if (!result.scheduleTruncated) {
            null
        } else {
            "Showing the first ${result.schedule.size} of ${result.payoffPeriods} periods."
        }

    /**
     * "5 years 0 months"-style label for a number of periods.
     * Returns "Unavailable" for a non-positive [paymentsPerYear] or a negative
     * [periods], so a malformed value can never throw while rendering a screen.
     */
    fun payoffLabel(periods: Int, paymentsPerYear: Int): String {
        if (paymentsPerYear < 1 || periods < 0) return "Unavailable"
        // Months can exceed Int range for a very long term; use Long arithmetic
        // all the way to the label instead of narrowing with toInt().
        val months = BigDecimal.valueOf(periods.toLong())
            .multiply(BigDecimal("12"))
            .divide(BigDecimal.valueOf(paymentsPerYear.toLong()), 0, RoundingMode.HALF_UP)
            .toLong()
        val years = months / 12
        val rest = months % 12
        return when {
            years == 0L -> "$rest month${if (rest == 1L) "" else "s"}"
            rest == 0L -> "$years year${if (years == 1L) "" else "s"}"
            else -> "$years year${if (years == 1L) "" else "s"} $rest month${if (rest == 1L) "" else "s"}"
        }
    }
}
