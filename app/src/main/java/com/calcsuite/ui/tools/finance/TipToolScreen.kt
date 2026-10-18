package com.calcsuite.ui.tools.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.finance.TipEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Pill
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

/**
 * Tip calculator with a bill split that always adds up: the grand total is
 * divided in whole cents with the largest-remainder method, so
 * `115.00 / 3 → 38.34 + 38.33 + 38.33` and no cent is lost or invented.
 */
@Composable
fun TipToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var bill by rememberSaveable { mutableStateOf("100") }
    var tax by rememberSaveable { mutableStateOf("") }
    var tipPct by rememberSaveable { mutableStateOf("15") }
    var people by rememberSaveable { mutableStateOf("1") }
    var tipOnPreTax by rememberSaveable { mutableStateOf(true) }
    var roundingId by rememberSaveable { mutableStateOf(TipEngine.RoundingOption.NONE.id) }

    val billValue = Num.parseOrNull(bill)
    val taxValue = if (tax.isBlank()) BigDecimal.ZERO else Num.parseOrNull(tax)
    val tipValue = if (tipPct.isBlank()) BigDecimal.ZERO else Num.parseOrNull(tipPct)
    val peopleBig = Num.parseIntOrNull(if (people.isBlank()) "" else people)
    val peopleValue = peopleBig?.let {
        if (it.bitLength() > 31) Int.MAX_VALUE else it.toInt()
    }
    val rounding = TipEngine.RoundingOption.byId(roundingId) ?: TipEngine.RoundingOption.NONE

    val billError = when {
        bill.isBlank() -> "Enter the bill amount"
        billValue == null -> "Enter a valid amount"
        billValue.signum() < 0 -> "Bill must not be negative"
        else -> null
    }
    val taxError = when {
        tax.isBlank() -> null
        taxValue == null -> "Enter a valid amount"
        taxValue.signum() < 0 -> "Tax must not be negative"
        else -> null
    }
    val tipError = when {
        tipPct.isBlank() -> null
        tipValue == null -> "Enter a valid percentage"
        tipValue.signum() < 0 -> "Tip percentage must not be negative"
        else -> null
    }
    val peopleError = when {
        people.isBlank() -> "Enter the number of people"
        peopleValue == null -> "Enter a whole number"
        peopleValue < 1 -> "At least one person is required"
        peopleValue > TipEngine.MAX_PEOPLE -> "At most ${TipEngine.MAX_PEOPLE} people"
        else -> null
    }

    val result: CalcResult<TipEngine.TipResult>? = if (
        billValue != null && taxValue != null && tipValue != null && peopleValue != null &&
        billError == null && taxError == null && tipError == null && peopleError == null
    ) {
        TipEngine.calculate(
            TipEngine.TipInput(
                bill = billValue,
                tax = taxValue,
                tipPct = tipValue,
                people = peopleValue,
                tipOnPreTax = tipOnPreTax,
                rounding = rounding,
            ),
        )
    } else {
        null
    }
    val value = (result as? CalcResult.Ok<TipEngine.TipResult>)?.value
    val resultError = (result as? CalcResult.Err)?.error?.message

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        NumberField(
            label = "Bill (before tax)",
            value = bill,
            onValueChange = { bill = it },
            error = billError,
            placeholder = "100",
        )
        NumberField(
            label = "Tax (optional)",
            value = tax,
            onValueChange = { tax = it },
            error = taxError,
            placeholder = "0",
        )
        NumberField(
            label = "Tip",
            value = tipPct,
            onValueChange = { tipPct = it },
            unit = "%",
            error = tipError,
            placeholder = "15",
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("10", "15", "18", "20").forEach { preset ->
                TextButton(onClick = { tipPct = preset }) { Text("$preset%") }
            }
        }
        NumberField(
            label = "Split between",
            value = people,
            onValueChange = { people = it },
            integerOnly = true,
            error = peopleError,
            placeholder = "1",
        )
        ToggleRow(
            label = "Tip on the pre-tax amount",
            checked = tipOnPreTax,
            onCheckedChange = { tipOnPreTax = it },
            description = "Off: the tip percentage is applied to bill + tax",
        )
        SegmentedChoice(
            options = TipEngine.RoundingOption.entries.map { it.id to it.label },
            selected = roundingId,
            onSelect = { roundingId = it },
            label = "Round the total",
        )

        ResultCard(
            label = "Total to pay",
            value = value?.let { fmt.num(it.total, precision = maxOf(fmt.precision, 10)) },
            approx = false,
            caption = if (value != null && rounding != TipEngine.RoundingOption.NONE) {
                "Unrounded ${fmt.num(value.unroundedTotal, precision = 10)} → " +
                    "rounded to ${rounding.label.lowercase()}; the tip absorbs the difference"
            } else {
                "The tip absorbs any cent rounding; the shares below add up exactly"
            },
            error = resultError,
            onCopy = { copy(it, "Total") },
            copyValue = value?.let { Num.formatForCopy(it.total) },
            onSave = value?.let { saved ->
                {
                    args.onRecord(
                        "${Num.formatForCopy(billValue ?: BigDecimal.ZERO)} bill, " +
                            "${Num.formatForCopy(tipValue ?: BigDecimal.ZERO)}% tip, " +
                            "${peopleValue ?: 1} way split",
                        fmt.num(saved.total, precision = maxOf(fmt.precision, 10)),
                    )
                }
            },
        )

        if (value != null) {
            SectionCard(title = "Breakdown") {
                ResultRow(
                    label = "Bill",
                    value = fmt.num(billValue ?: BigDecimal.ZERO, precision = 10),
                    onCopy = { copy(Num.formatForCopy(billValue ?: BigDecimal.ZERO), "Bill") },
                )
                if ((taxValue ?: BigDecimal.ZERO).signum() > 0) {
                    ResultRow(
                        label = "Tax",
                        value = fmt.num(taxValue ?: BigDecimal.ZERO, precision = 10),
                        onCopy = { copy(Num.formatForCopy(taxValue ?: BigDecimal.ZERO), "Tax") },
                    )
                }
                ResultRow(
                    label = "Tip (${fmt.num(tipValue ?: BigDecimal.ZERO, precision = 4)}%)",
                    value = fmt.num(value.tip, precision = 10),
                    emphasis = true,
                    onCopy = { copy(Num.formatForCopy(value.tip), "Tip") },
                )
                ResultRow(
                    label = "Tip applied to",
                    value = fmt.num(value.tipBase, precision = 10),
                    unit = if (tipOnPreTax) "(pre-tax)" else "(bill + tax)",
                )
                if (rounding != TipEngine.RoundingOption.NONE) {
                    ResultRow(
                        label = "Rounding adjustment",
                        value = fmt.num(value.roundingAdjustment, precision = 10),
                    )
                }
                ResultRow(
                    label = "Total",
                    value = fmt.num(value.total, precision = 10),
                    emphasis = true,
                )
            }

            if (peopleValue != null && peopleValue > 1) {
                val smallest = value.shares.minOrNull() ?: BigDecimal.ZERO
                val extraPayers = value.shares.count { it > smallest }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (value.evenSplit) {
                        Pill("Even split")
                    } else {
                        Pill("$extraPayers of ${value.shares.size} pay one cent more")
                    }
                }
                SectionCard(title = "Split between ${value.shares.size}") {
                    ResultRow(
                        label = "Per person",
                        value = if (value.evenSplit) {
                            fmt.num(value.shares.first(), precision = 10)
                        } else {
                            value.shares.joinToString(" / ") { fmt.num(it, precision = 10) }
                        },
                        emphasis = true,
                    )
                    ResultRow(
                        label = "Tip per person",
                        value = if (value.evenSplit) {
                            fmt.num(value.tipShares.first(), precision = 10)
                        } else {
                            value.tipShares.joinToString(" / ") { fmt.num(it, precision = 10) }
                        },
                    )
                    value.shares.forEachIndexed { index, share ->
                        ResultRow(
                            label = "Person ${index + 1} — tip ${fmt.num(value.tipShares[index], precision = 10)}",
                            value = fmt.num(share, precision = 10),
                            onCopy = { copy(Num.formatForCopy(share), "Person ${index + 1}") },
                        )
                    }
                }
            }
        }

        InfoNote(
            "Tipping practice varies by country and venue; percentages are a choice and the " +
                "calculator only does the arithmetic.",
        )

        FormulaCard(
            formula = "tip = base × tip% ÷ 100 · overall = bill + tax + tip",
            explanation = "The base is the pre-tax bill or bill + tax, depending on the " +
                "toggle. Rounding applies to the grand total only, and the split uses " +
                "whole cents with the largest-remainder method so every share sums to " +
                "the total exactly.",
            variables = listOf(
                "base" to "amount the tip percentage applies to",
                "shares" to "whole-cent split of the total",
            ),
        )
    }
}
