package com.calcsuite.ui.tools.finance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.finance.CompoundFrequency
import com.calcsuite.engine.finance.ContributionPlan
import com.calcsuite.engine.finance.InterestEngine
import com.calcsuite.engine.finance.InterestMode
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
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal
import java.math.RoundingMode

private val CONTRIBUTION_FREQUENCIES = CompoundFrequency.entries.filter { !it.isContinuous }

/**
 * Simple and compound interest with optional scheduled contributions, the
 * effective annual rate (EAR/APY) and a year-by-year growth table. Continuous
 * compounding and fractional periods use a double-precision fallback for the
 * final step and are shown with the ≈ marker.
 */
@Composable
fun InterestToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var modeId by rememberSaveable { mutableStateOf(InterestMode.COMPOUND.id) }
    var principal by rememberSaveable { mutableStateOf("1000") }
    var rate by rememberSaveable { mutableStateOf("5") }
    var duration by rememberSaveable { mutableStateOf("1") }
    var durationUnit by rememberSaveable { mutableStateOf("years") }
    var compoundingId by rememberSaveable { mutableStateOf(CompoundFrequency.MONTHLY.id) }
    var contributionAmount by rememberSaveable { mutableStateOf("") }
    var contributionFrequencyId by rememberSaveable { mutableStateOf(CompoundFrequency.MONTHLY.id) }
    var contributionAtBeginning by rememberSaveable { mutableStateOf(false) }

    val mode = InterestMode.entries.firstOrNull { it.id == modeId } ?: InterestMode.COMPOUND
    val compounding = CompoundFrequency.byId(compoundingId) ?: CompoundFrequency.MONTHLY
    val contributionFrequency = CompoundFrequency.byId(contributionFrequencyId)
        ?: CompoundFrequency.MONTHLY

    val principalValue = Num.parseOrNull(principal)
    val rateValue = Num.parseOrNull(rate)
    val durationValue = Num.parseOrNull(duration)
    val durationYears = durationValue?.let {
        if (durationUnit == "months") InterestEngine.monthsToYears(it) else it
    }
    val contributionValue = if (contributionAmount.isBlank()) null else Num.parseOrNull(contributionAmount)

    val principalError = when {
        principal.isBlank() -> "Enter the principal"
        principalValue == null -> "Enter a valid amount"
        principalValue.signum() < 0 -> "Principal must not be negative"
        else -> null
    }
    val rateError = when {
        rate.isBlank() -> "Enter the interest rate"
        rateValue == null -> "Enter a valid rate"
        rateValue.signum() < 0 -> "Rate must not be negative"
        else -> null
    }
    val durationError = when {
        duration.isBlank() -> "Enter the duration"
        durationValue == null -> "Enter a valid number"
        durationValue.signum() < 0 -> "Duration must not be negative"
        else -> null
    }
    val contributionError = when {
        contributionAmount.isBlank() -> null
        contributionValue == null -> "Enter a valid amount"
        contributionValue.signum() < 0 -> "Contribution must not be negative"
        else -> null
    }

    val contributions = if (
        mode == InterestMode.COMPOUND &&
        contributionValue != null && contributionValue.signum() > 0 &&
        contributionError == null && !contributionFrequency.isContinuous
    ) {
        ContributionPlan(
            amount = contributionValue,
            frequency = contributionFrequency,
            atBeginning = contributionAtBeginning,
        )
    } else {
        null
    }

    val result: CalcResult<com.calcsuite.engine.finance.InterestResult>? = if (
        principalValue != null && rateValue != null && durationYears != null &&
        principalError == null && rateError == null && durationError == null && contributionError == null
    ) {
        if (mode == InterestMode.SIMPLE) {
            InterestEngine.simple(principalValue, rateValue, durationYears)
        } else {
            InterestEngine.compound(principalValue, rateValue, durationYears, compounding, contributions)
        }
    } else {
        null
    }
    val value = (result as? CalcResult.Ok<com.calcsuite.engine.finance.InterestResult>)?.value
    val resultError = (result as? CalcResult.Err)?.error?.message

    val comparison = if (
        mode == InterestMode.COMPOUND && contributions == null &&
        principalValue != null && rateValue != null && durationYears != null &&
        principalError == null && rateError == null && durationError == null
    ) {
        InterestEngine.compare(principalValue, rateValue, durationYears, compounding)
    } else {
        null
    }
    val comparisonValue = (comparison as? CalcResult.Ok<com.calcsuite.engine.finance.InterestComparison>)?.value

    val growthRows: List<Pair<Int, BigDecimal>> = if (
        value != null && principalValue != null && rateValue != null && durationYears != null
    ) {
        val wholeYears = try {
            durationYears.setScale(0, RoundingMode.FLOOR).intValueExact()
        } catch (_: ArithmeticException) {
            0
        }
        val capped = wholeYears.coerceAtMost(50)
        val step = maxOf(1, (capped + 9) / 10)
        val milestones = buildList {
            if (capped >= 1) {
                var year = step
                while (year <= capped && size < 10) {
                    add(year)
                    year += step
                }
                if (isNotEmpty() && last() != capped) {
                    removeAt(lastIndex)
                    add(capped)
                }
            }
        }
        milestones.mapNotNull { year ->
            val years = BigDecimal.valueOf(year.toLong())
            val partial = if (mode == InterestMode.SIMPLE) {
                InterestEngine.simple(principalValue, rateValue, years)
            } else {
                InterestEngine.compound(principalValue, rateValue, years, compounding, contributions)
            }
            (partial as? CalcResult.Ok<com.calcsuite.engine.finance.InterestResult>)?.value?.let { year to it.finalAmount }
        }
    } else {
        emptyList()
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = InterestMode.entries.map { it.id to it.label },
            selected = modeId,
            onSelect = { modeId = it },
            label = "Interest type",
        )
        NumberField(
            label = "Principal",
            value = principal,
            onValueChange = { principal = it },
            error = principalError,
            placeholder = "1000",
        )
        NumberField(
            label = "Annual interest rate",
            value = rate,
            onValueChange = { rate = it },
            unit = "%",
            error = rateError,
            placeholder = "5",
        )
        NumberField(
            label = "Duration",
            value = duration,
            onValueChange = { duration = it },
            unit = if (durationUnit == "months") "months" else "years",
            error = durationError,
            placeholder = "1",
        )
        SegmentedChoice(
            options = listOf("years" to "Years", "months" to "Months"),
            selected = durationUnit,
            onSelect = { durationUnit = it },
            label = "Duration unit",
        )
        if (mode == InterestMode.COMPOUND) {
            SelectField(
                label = "Compounding",
                options = CompoundFrequency.entries.map { Option(it.id, it.label) },
                selected = compoundingId,
                onSelect = { compoundingId = it },
            )
        }

        if (mode == InterestMode.COMPOUND) {
            ExpandablePanel(title = "Regular contributions") {
                NumberField(
                    label = "Contribution amount",
                    value = contributionAmount,
                    onValueChange = { contributionAmount = it },
                    error = contributionError,
                    placeholder = "0",
                )
                SelectField(
                    label = "Contribution frequency",
                    options = CONTRIBUTION_FREQUENCIES.map { Option(it.id, it.label) },
                    selected = contributionFrequencyId,
                    onSelect = { contributionFrequencyId = it },
                )
                SegmentedChoice(
                    options = listOf(false to "End of period", true to "Beginning"),
                    selected = contributionAtBeginning,
                    onSelect = { contributionAtBeginning = it },
                    label = "Contribution timing",
                )
                InfoNote(
                    "Beginning-of-period contributions earn one extra period of interest " +
                        "(annuity due).",
                )
            }
        }

        ResultCard(
            label = "Final amount",
            value = value?.let { fmt.num(it.finalAmount, precision = maxOf(fmt.precision, 10)) },
            approx = value?.approximate == true,
            caption = value?.let {
                if (it.mode == InterestMode.SIMPLE) {
                    "Simple interest: linear growth, no compounding"
                } else {
                    "${it.mode.label} interest, ${compounding.label.lowercase()} compounding" +
                        (if (contributions != null) " plus regular contributions" else "")
                }
            },
            error = resultError,
            onCopy = { copy(it, "Final amount") },
            copyValue = value?.let { Num.formatForCopy(it.finalAmount) },
            onSave = value?.let { saved ->
                {
                    args.onRecord(
                        "${Num.formatForCopy(principalValue ?: BigDecimal.ZERO)} at " +
                            "${Num.formatForCopy(rateValue ?: BigDecimal.ZERO)}% for " +
                            "${Num.formatForCopy(durationValue ?: BigDecimal.ZERO)} $durationUnit" +
                            (if (mode == InterestMode.SIMPLE) ", simple" else ", ${compounding.label.lowercase()}"),
                        fmt.num(saved.finalAmount, precision = maxOf(fmt.precision, 10)),
                    )
                }
            },
        )

        if (value != null) {
            SectionCard(title = "Summary") {
                ResultRow(
                    label = "Interest earned",
                    value = fmt.num(value.interestEarned, precision = maxOf(fmt.precision, 10)),
                    emphasis = true,
                    onCopy = { copy(Num.formatForCopy(value.interestEarned), "Interest earned") },
                )
                ResultRow(
                    label = "Principal",
                    value = fmt.num(value.principal, precision = maxOf(fmt.precision, 10)),
                )
                if (value.contributionsTotal.signum() > 0) {
                    ResultRow(
                        label = "Regular contributions",
                        value = fmt.num(value.contributionsTotal, precision = maxOf(fmt.precision, 10)),
                    )
                }
                ResultRow(
                    label = "Total contributed",
                    value = fmt.num(value.totalContributed, precision = maxOf(fmt.precision, 10)),
                )
                ResultRow(
                    label = "EAR / APY",
                    value = (if (value.effectiveRateApproximate) "≈ " else "") +
                        fmt.num(value.effectiveAnnualRatePct, precision = maxOf(fmt.precision, 4)),
                    unit = "%",
                )
                value.periods?.let { periods ->
                    ResultRow(
                        label = "Compounding periods",
                        value = fmt.num(periods, precision = 6),
                    )
                }
            }

            if (value.approximate) {
                InfoNote(
                    "≈ Continuous compounding or a fractional number of periods has no exact " +
                        "decimal value; the final step uses double precision. Integer periods " +
                        "stay exact.",
                )
            }
        }

        if (comparisonValue != null) {
            SectionCard(title = "Simple vs compound (no contributions)") {
                ResultRow(
                    label = "Simple interest",
                    value = fmt.num(comparisonValue.simpleFinal, precision = maxOf(fmt.precision, 10)),
                )
                ResultRow(
                    label = "Compound interest",
                    value = fmt.num(comparisonValue.compoundFinal, precision = maxOf(fmt.precision, 10)),
                )
                ResultRow(
                    label = "Difference",
                    value = fmt.num(comparisonValue.difference, precision = maxOf(fmt.precision, 10)),
                    emphasis = true,
                )
            }
        }

        if (growthRows.isNotEmpty()) {
            SectionCard(
                title = if (mode == InterestMode.SIMPLE) {
                    "Growth over time"
                } else {
                    "Growth over time" + (if (contributions != null) " (with contributions)" else "")
                },
            ) {
                growthRows.forEach { (year, amount) ->
                    ResultRow(
                        label = "After $year year${if (year == 1) "" else "s"}",
                        value = fmt.num(amount, precision = maxOf(fmt.precision, 10)),
                    )
                }
            }
        }

        FormulaCard(
            formula = if (mode == InterestMode.SIMPLE) {
                "A = P · (1 + r · t ÷ 100)"
            } else {
                "A = P · (1 + r ÷ (100n))^(n·t)" +
                    (if (contributions != null) " + PMT · ((1 + j)^m − 1) ÷ j" else "")
            },
            explanation = if (mode == InterestMode.SIMPLE) {
                "Interest is paid on the original principal only, so growth is a straight line."
            } else {
                "Interest earns interest. n is the number of compounding periods per year; " +
                    "j is the rate per contribution period and m the number of contributions."
            },
            variables = listOf(
                "P" to "principal",
                "r" to "annual rate in percent",
                "t" to "duration in years",
                "n" to "compounding periods per year",
            ),
        )

        InfoNote(
            "Interest is compounded with 20-digit decimal arithmetic; interest and tax rules " +
                "at your bank may differ, and inflation is not accounted for.",
        )
    }
}
