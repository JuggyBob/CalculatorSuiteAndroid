package com.calcsuite.ui.tools.finance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.finance.LoanEngine
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

/** A payment frequency offered by the form; a null [periodsPerYear] means custom. */
private data class PaymentFrequency(
    val id: String,
    val label: String,
    val periodsPerYear: Int?,
    val unit: String,
)

private val FREQUENCIES = listOf(
    PaymentFrequency("12", "Monthly", 12, "per month"),
    PaymentFrequency("24", "Semi-monthly", 24, "twice a month"),
    PaymentFrequency("26", "Bi-weekly", 26, "every 2 weeks"),
    PaymentFrequency("52", "Weekly", 52, "per week"),
    PaymentFrequency("4", "Quarterly", 4, "per quarter"),
    PaymentFrequency("2", "Semi-annual", 2, "per half-year"),
    PaymentFrequency("1", "Annual", 1, "per year"),
    PaymentFrequency("custom", "Custom…", null, "per period"),
)

/**
 * Fixed-rate loan calculator: level payment, payoff time, total interest and a
 * period-by-period amortisation schedule, plus a what-if comparison when extra
 * payments are added. All money math is tracked in whole cents.
 */
@Composable
fun LoanToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var principal by rememberSaveable { mutableStateOf("200000") }
    var rate by rememberSaveable { mutableStateOf("5") }
    var termMode by rememberSaveable { mutableStateOf("years") }
    var termYears by rememberSaveable { mutableStateOf("30") }
    var termPayments by rememberSaveable { mutableStateOf("360") }
    var frequencyId by rememberSaveable { mutableStateOf("12") }
    var customPpy by rememberSaveable { mutableStateOf("12") }
    var extraPerPeriod by rememberSaveable { mutableStateOf("") }
    var oneOffExtra by rememberSaveable { mutableStateOf("") }
    var oneOffAtPeriod by rememberSaveable { mutableStateOf("1") }
    var scheduleLimitId by rememberSaveable { mutableStateOf("24") }

    val principalValue = Num.parseOrNull(principal)
    val rateValue = Num.parseOrNull(rate)
    val termYearsValue = Num.parseOrNull(termYears)
    val termPaymentsBig = Num.parseIntOrNull(termPayments)
    val termPaymentsValue = termPaymentsBig?.let {
        if (it.bitLength() > 31) Int.MAX_VALUE else it.toInt()
    }
    val frequency = FREQUENCIES.firstOrNull { it.id == frequencyId } ?: FREQUENCIES.first()
    val customPpyBig = Num.parseIntOrNull(customPpy)
    val customPpyValue = customPpyBig?.let {
        if (it.bitLength() > 31) Int.MAX_VALUE else it.toInt()
    }
    val periodsPerYear = frequency.periodsPerYear ?: customPpyValue
    val extraValue = if (extraPerPeriod.isBlank()) BigDecimal.ZERO else Num.parseOrNull(extraPerPeriod)
    val oneOffValue = if (oneOffExtra.isBlank()) BigDecimal.ZERO else Num.parseOrNull(oneOffExtra)
    val oneOffPeriodBig = Num.parseIntOrNull(oneOffAtPeriod)
    val oneOffPeriodValue = oneOffPeriodBig?.let {
        if (it.bitLength() > 31) Int.MAX_VALUE else it.toInt()
    }

    val principalError = when {
        principal.isBlank() -> "Enter the loan amount"
        principalValue == null -> "Enter a valid amount"
        principalValue.signum() <= 0 -> "Principal must be greater than zero"
        else -> null
    }
    val rateError = when {
        rate.isBlank() -> "Enter the annual interest rate"
        rateValue == null -> "Enter a valid rate"
        rateValue.signum() < 0 -> "Interest rate must not be negative"
        else -> null
    }
    val termError = when (termMode) {
        "years" -> when {
            termYears.isBlank() -> "Enter the term"
            termYearsValue == null -> "Enter a valid number of years"
            termYearsValue.signum() <= 0 -> "Term must be greater than zero"
            else -> null
        }
        else -> when {
            termPayments.isBlank() -> "Enter the number of payments"
            termPaymentsValue == null -> "Enter a whole number"
            termPaymentsValue <= 0 -> "The loan must have at least one payment"
            else -> null
        }
    }
    val ppyError = when {
        frequency.periodsPerYear != null -> null
        customPpy.isBlank() -> "Enter payments per year"
        customPpyValue == null -> "Enter a whole number"
        customPpyValue !in 1..366 -> "Between 1 and 366"
        else -> null
    }
    val extraError = when {
        extraPerPeriod.isBlank() -> null
        extraValue == null -> "Enter a valid amount"
        extraValue.signum() < 0 -> "Extra payment must not be negative"
        else -> null
    }
    val oneOffError = when {
        oneOffExtra.isBlank() -> null
        oneOffValue == null -> "Enter a valid amount"
        oneOffValue.signum() < 0 -> "One-off extra must not be negative"
        else -> null
    }
    val oneOffPeriodError = when {
        oneOffExtra.isBlank() || oneOffPeriodBig == null -> null
        oneOffPeriodValue == null -> "Enter a whole number"
        oneOffPeriodValue < 1 -> "Must be at least 1"
        else -> null
    }

    val input = if (
        principalValue != null && rateValue != null &&
        periodsPerYear != null && periodsPerYear in 1..366 &&
        principalError == null && rateError == null && ppyError == null &&
        termError == null && extraError == null && oneOffError == null && oneOffPeriodError == null &&
        (if (termMode == "years") termYearsValue != null else termPaymentsValue != null)
    ) {
        LoanEngine.LoanInput(
            principal = principalValue,
            annualRatePct = rateValue,
            termYears = if (termMode == "years") termYearsValue else null,
            numberOfPayments = if (termMode == "payments") termPaymentsValue else null,
            paymentsPerYear = periodsPerYear,
            extraPerPeriod = extraValue ?: BigDecimal.ZERO,
            oneOffExtra = oneOffValue ?: BigDecimal.ZERO,
            oneOffAtPeriod = oneOffPeriodValue ?: 1,
            includeSchedule = true,
        )
    } else {
        null
    }
    val result: CalcResult<LoanEngine.LoanResult>? = input?.let { LoanEngine.compute(it) }
    val loan = (result as? CalcResult.Ok<LoanEngine.LoanResult>)?.value
    val resultError = (result as? CalcResult.Err)?.error?.message
    val hasExtras = (extraValue ?: BigDecimal.ZERO).signum() > 0 || (oneOffValue ?: BigDecimal.ZERO).signum() > 0
    val baseResult: CalcResult<LoanEngine.LoanResult>? = if (input != null && hasExtras) {
        LoanEngine.compute(input.copy(extraPerPeriod = BigDecimal.ZERO, oneOffExtra = BigDecimal.ZERO))
    } else {
        null
    }
    val baseLoan = (baseResult as? CalcResult.Ok<LoanEngine.LoanResult>)?.value

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        NumberField(
            label = "Loan amount",
            value = principal,
            onValueChange = { principal = it },
            error = principalError,
            placeholder = "200000",
        )
        NumberField(
            label = "Annual interest rate",
            value = rate,
            onValueChange = { rate = it },
            unit = "%",
            error = rateError,
            placeholder = "5",
        )
        SelectField(
            label = "Payment frequency",
            options = FREQUENCIES.map { Option(it.id, it.label) },
            selected = frequencyId,
            onSelect = { frequencyId = it },
        )
        if (frequency.periodsPerYear == null) {
            NumberField(
                label = "Payments per year",
                value = customPpy,
                onValueChange = { customPpy = it },
                integerOnly = true,
                error = ppyError,
                placeholder = "12",
            )
        }
        SegmentedChoice(
            options = listOf("years" to "Term in years", "payments" to "No. of payments"),
            selected = termMode,
            onSelect = { termMode = it },
            label = "Loan term",
        )
        if (termMode == "years") {
            NumberField(
                label = "Term",
                value = termYears,
                onValueChange = { termYears = it },
                unit = "years",
                error = termError,
                placeholder = "30",
            )
        } else {
            NumberField(
                label = "Number of payments",
                value = termPayments,
                onValueChange = { termPayments = it },
                integerOnly = true,
                error = termError,
                placeholder = "360",
            )
        }

        ExpandablePanel(title = "Extra payments") {
            NumberField(
                label = "Extra on every payment",
                value = extraPerPeriod,
                onValueChange = { extraPerPeriod = it },
                error = extraError,
                placeholder = "0",
            )
            NumberField(
                label = "One-off extra payment",
                value = oneOffExtra,
                onValueChange = { oneOffExtra = it },
                error = oneOffError,
                placeholder = "0",
            )
            NumberField(
                label = "Extra payment in period",
                value = oneOffAtPeriod,
                onValueChange = { oneOffAtPeriod = it },
                integerOnly = true,
                error = oneOffPeriodError,
                placeholder = "1",
            )
        }

        ResultCard(
            label = "Scheduled payment",
            value = loan?.let { fmt.num(it.scheduledPayment, precision = maxOf(fmt.precision, 10)) },
            unit = frequency.unit,
            caption = loan?.let {
                "Pays off in ${LoanEngine.payoffLabel(it.payoffPeriods, periodsPerYear ?: 12)} " +
                    "(${it.payoffPeriods} payments); the final instalment absorbs cent rounding"
            },
            error = resultError,
            onCopy = { copy(it, "Payment") },
            copyValue = loan?.let { Num.formatForCopy(it.scheduledPayment) },
            onSave = loan?.let { saved ->
                {
                    args.onRecord(
                        "${Num.formatForCopy(principalValue ?: BigDecimal.ZERO)} at " +
                            "${Num.formatForCopy(rateValue ?: BigDecimal.ZERO)}% · " +
                            "${frequency.label} · ${saved.contractualPayments} payments" +
                            (if (hasExtras) " with extras" else ""),
                        "${fmt.num(saved.scheduledPayment, precision = maxOf(fmt.precision, 10))} " +
                            frequency.unit,
                    )
                }
            },
        )

        if (loan != null) {
            SectionCard(title = "Loan totals") {
                ResultRow(
                    label = "Principal",
                    value = fmt.num(loan.principal, precision = maxOf(fmt.precision, 10)),
                )
                ResultRow(
                    label = "Total paid",
                    value = fmt.num(loan.totalPaid, precision = maxOf(fmt.precision, 10)),
                    onCopy = { copy(Num.formatForCopy(loan.totalPaid), "Total paid") },
                )
                ResultRow(
                    label = "Total interest",
                    value = fmt.num(loan.totalInterest, precision = maxOf(fmt.precision, 10)),
                    emphasis = true,
                    onCopy = { copy(Num.formatForCopy(loan.totalInterest), "Total interest") },
                )
                ResultRow(
                    label = "Payoff time",
                    value = LoanEngine.payoffLabel(loan.payoffPeriods, periodsPerYear ?: 12),
                )
                ResultRow(
                    label = "Contractual payments",
                    value = loan.contractualPayments.toString(),
                )
                if (loan.zeroInterest) {
                    ResultRow(label = "Interest", value = "None — 0% rate")
                }
                loan.schedule.lastOrNull()?.let { last ->
                    ResultRow(
                        label = "Final payment",
                        value = fmt.num(last.payment, precision = maxOf(fmt.precision, 10)),
                    )
                }
            }

            if (baseLoan != null) {
                val interestSaved = baseLoan.totalInterest.subtract(loan.totalInterest)
                val periodsSaved = baseLoan.payoffPeriods - loan.payoffPeriods
                SectionCard(title = "Effect of the extra payments") {
                    ResultRow(
                        label = "Interest saved",
                        value = fmt.num(interestSaved, precision = maxOf(fmt.precision, 10)),
                        emphasis = true,
                        onCopy = { copy(Num.formatForCopy(interestSaved), "Interest saved") },
                    )
                    ResultRow(
                        label = "Payoff shortened by",
                        value = "${LoanEngine.payoffLabel(periodsSaved.coerceAtLeast(0), periodsPerYear ?: 12)} " +
                            "(${periodsSaved.coerceAtLeast(0)} payments)",
                    )
                    ResultRow(
                        label = "Without extras",
                        value = fmt.num(baseLoan.totalInterest, precision = maxOf(fmt.precision, 10)),
                        unit = "total interest",
                    )
                }
            }

            StepsCard(
                steps = listOf(
                    "i = ${fmt.num(rateValue ?: BigDecimal.ZERO, precision = 4)}% ÷ 100 ÷ " +
                        "${periodsPerYear ?: 12} = " +
                        fmt.num(
                            (rateValue ?: BigDecimal.ZERO)
                                .divide(BigDecimal("100"), LoanEngine.MC)
                                .divide(BigDecimal.valueOf((periodsPerYear ?: 12).toLong()), LoanEngine.MC),
                            precision = 8,
                        ) + " per period",
                    "n = ${loan.contractualPayments} payments",
                    "payment = P × i ÷ (1 − (1 + i)^−n) = " +
                        "${fmt.num(loan.scheduledPayment, precision = maxOf(fmt.precision, 10))} " +
                        frequency.unit,
                ),
            )

            ExpandablePanel(title = "Amortisation schedule (${loan.schedule.size} rows shown)") {
                SegmentedChoice(
                    options = listOf("12" to "12", "24" to "24", "60" to "60", "all" to "All"),
                    selected = scheduleLimitId,
                    onSelect = { scheduleLimitId = it },
                    label = "Show periods",
                )
                val visible = when (scheduleLimitId) {
                    "12" -> loan.schedule.take(12)
                    "24" -> loan.schedule.take(24)
                    "60" -> loan.schedule.take(60)
                    else -> loan.schedule
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    items(visible, key = { it.period }) { row ->
                        ResultRow(
                            label = "Period ${row.period}",
                            value = fmt.num(row.payment, precision = maxOf(fmt.precision, 10)),
                            unit = "int. ${fmt.num(row.interest, precision = maxOf(fmt.precision, 10))} · " +
                                "bal. ${fmt.num(row.balance, precision = maxOf(fmt.precision, 10))}",
                        )
                    }
                }
                LoanEngine.scheduleNote(loan)?.let { note ->
                    InfoNote(note)
                }
            }
        }

        InfoNote(LoanEngine.DISCLAIMER)

        FormulaCard(
            formula = "payment = P · i ÷ (1 − (1 + i)^−n)",
            explanation = "Balances, interest and payments are tracked in whole cents (HALF_UP). " +
                "The level payment is rounded to the nearest cent and the final instalment " +
                "clears the small residue, so Σ principal payments equals the principal exactly.",
            variables = listOf(
                "P" to "loan principal",
                "i" to "interest rate per payment period",
                "n" to "number of payments",
            ),
        )
    }
}
