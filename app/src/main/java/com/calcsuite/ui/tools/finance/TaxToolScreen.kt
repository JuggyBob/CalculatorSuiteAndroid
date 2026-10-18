package com.calcsuite.ui.tools.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.finance.TaxEngine
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

/** One editable tax component, serialised for [rememberSaveable]. */
private data class ComponentForm(val name: String = "", val rate: String = "") {
    fun encode(): String = listOf(name, rate).joinToString("\u001F") { it.replace('\u001F', ' ') }

    companion object {
        fun decode(raw: String): ComponentForm {
            val parts = raw.split('\u001F')
            return ComponentForm(
                name = parts.getOrElse(0) { "" },
                rate = parts.getOrElse(1) { "" },
            )
        }
    }
}

private val ComponentListSaver = listSaver<List<ComponentForm>, String>(
    save = { list -> list.map { it.encode() } },
    restore = { encoded -> encoded.map { ComponentForm.decode(it) } },
)

private data class PreparedComponent(val pair: Pair<String, BigDecimal>?, val error: String?)

private const val MAX_COMPONENTS = 6

/**
 * Sales tax / VAT / GST: add a rate to a net amount, extract the tax that is
 * already inside a gross amount, or split the rate into named components
 * (state + city + district) that all apply to the same net base.
 */
@Composable
fun TaxToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var opId by rememberSaveable { mutableStateOf("add") }
    var useMultiple by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("100") }
    var rate by rememberSaveable { mutableStateOf("10") }
    var components by rememberSaveable(stateSaver = ComponentListSaver) {
        mutableStateOf(listOf(ComponentForm("State", "5"), ComponentForm("City", "2")))
    }
    val adding = opId == "add"

    val amountValue = Num.parseOrNull(amount)
    val rateValue = Num.parseOrNull(rate)
    val amountError = when {
        amount.isBlank() -> "Enter an amount"
        amountValue == null -> "Enter a valid amount"
        amountValue.signum() < 0 -> "Amount must not be negative"
        else -> null
    }
    val rateError = when {
        useMultiple -> null
        rate.isBlank() -> "Enter the tax rate"
        rateValue == null -> "Enter a valid rate"
        rateValue.signum() < 0 -> "Rate must not be negative"
        else -> null
    }

    val preparedComponents = components.map { component ->
        val value = Num.parseOrNull(component.rate)
        val error = when {
            component.rate.isBlank() -> "Enter a rate"
            value == null -> "Enter a valid rate"
            value.signum() < 0 -> "Rate must not be negative"
            else -> null
        }
        PreparedComponent(
            pair = if (value != null && error == null) component.name to value else null,
            error = error,
        )
    }
    val componentPairs = preparedComponents.mapNotNull { it.pair }
    val componentsReady = useMultiple &&
        components.isNotEmpty() &&
        componentPairs.size == components.size

    val singleResult: CalcResult<TaxEngine.Breakdown>? =
        if (!useMultiple && amountValue != null && rateValue != null && amountError == null && rateError == null) {
            if (adding) TaxEngine.addTax(amountValue, rateValue)
            else TaxEngine.extractTax(amountValue, rateValue)
        } else {
            null
        }
    val multipleResult: CalcResult<TaxEngine.MultiBreakdown>? =
        if (useMultiple && amountValue != null && componentsReady && amountError == null) {
            if (adding) TaxEngine.addComponents(amountValue, componentPairs)
            else TaxEngine.extractComponents(amountValue, componentPairs)
        } else {
            null
        }

    val amountLabel = if (adding) "Net amount (before tax)" else "Gross amount (with tax)"
    val keyLabel = if (adding) "Gross (with tax)" else "Net (without tax)"

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = listOf("add" to "Add tax", "extract" to "Extract tax"),
            selected = opId,
            onSelect = { opId = it },
            label = "What do you want to do?",
        )
        ToggleRow(
            label = "Split the rate into components",
            checked = useMultiple,
            onCheckedChange = { useMultiple = it },
            description = "State + city + district rates, each applied to the same net base",
        )

        NumberField(
            label = amountLabel,
            value = amount,
            onValueChange = { amount = it },
            error = amountError,
            placeholder = "100",
        )

        if (!useMultiple) {
            NumberField(
                label = "Tax rate",
                value = rate,
                onValueChange = { rate = it },
                unit = "%",
                error = rateError,
                placeholder = "10",
            )
        } else {
            SectionCard(title = "Tax components") {
                components.forEachIndexed { index, component ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlainField(
                            label = "Name",
                            value = component.name,
                            onValueChange = { value ->
                                components = components.toMutableList()
                                    .also { it[index] = component.copy(name = value) }
                            },
                            placeholder = "State",
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            label = "Rate",
                            value = component.rate,
                            onValueChange = { value ->
                                components = components.toMutableList()
                                    .also { it[index] = component.copy(rate = value) }
                            },
                            unit = "%",
                            error = preparedComponents[index].error,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { components = components.filterIndexed { i, _ -> i != index } },
                            enabled = components.size > 1,
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove component ${index + 1}")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { components = components + ComponentForm() },
                    enabled = components.size < MAX_COMPONENTS,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add component")
                }
            }
        }

        if (!useMultiple) {
            val value = (singleResult as? CalcResult.Ok<TaxEngine.Breakdown>)?.value
            val keyAmount = value?.let { if (adding) it.gross else it.net }
            val error = (singleResult as? CalcResult.Err)?.error?.message
            ResultCard(
                label = keyLabel,
                value = keyAmount?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
                approx = false,
                caption = "Exact value — the receipt rows below are rounded to whole cents",
                error = error,
                onCopy = { copy(it, "Amount") },
                copyValue = keyAmount?.let { Num.formatForCopy(it) },
                onSave = keyAmount?.let { saved ->
                    {
                        args.onRecord(
                            "${Num.formatForCopy(amountValue ?: BigDecimal.ZERO)} at " +
                                "${Num.formatForCopy(rateValue ?: BigDecimal.ZERO)}% " +
                                (if (adding) "added tax" else "extracted tax"),
                            fmt.num(saved, precision = maxOf(fmt.precision, 10)),
                        )
                    }
                },
            )

            if (value != null) {
                SectionCard(title = "Receipt") {
                    ResultRow(
                        label = "Net",
                        value = fmt.num(TaxEngine.roundToCents(value.net), precision = maxOf(fmt.precision, 10)),
                    )
                    ResultRow(
                        label = "Tax",
                        value = fmt.num(TaxEngine.roundToCents(value.tax), precision = maxOf(fmt.precision, 10)),
                    )
                    ResultRow(
                        label = "Total",
                        value = fmt.num(TaxEngine.roundToCents(value.gross), precision = maxOf(fmt.precision, 10)),
                        emphasis = true,
                    )
                    ResultRow(
                        label = "Rate applied",
                        value = fmt.num(rateValue ?: BigDecimal.ZERO, precision = maxOf(fmt.precision, 4)),
                        unit = "%",
                    )
                    ResultRow(
                        label = "Tax (exact)",
                        value = fmt.num(value.tax, precision = maxOf(fmt.precision, 10)),
                        onCopy = { copy(Num.formatForCopy(value.tax), "Tax") },
                    )
                }
                StepsCard(
                    steps = if (adding) {
                        listOf(
                            "tax = ${fmt.num(value.net, precision = 10)} × " +
                                "${fmt.num(rateValue ?: BigDecimal.ZERO, precision = 10)}% = " +
                                fmt.num(value.tax, precision = 10),
                            "gross = ${fmt.num(value.net, precision = 10)} + " +
                                "${fmt.num(value.tax, precision = 10)} = " +
                                fmt.num(value.gross, precision = 10),
                        )
                    } else {
                        listOf(
                            "net = ${fmt.num(value.gross, precision = 10)} ÷ " +
                                "(1 + ${fmt.num(rateValue ?: BigDecimal.ZERO, precision = 10)}%) = " +
                                fmt.num(value.net, precision = 10),
                            "tax = ${fmt.num(value.gross, precision = 10)} − " +
                                "${fmt.num(value.net, precision = 10)} = " +
                                fmt.num(value.tax, precision = 10),
                        )
                    },
                )
            }
        } else {
            val value = (multipleResult as? CalcResult.Ok<TaxEngine.MultiBreakdown>)?.value
            val keyAmount = value?.let { if (adding) it.gross else it.net }
            val error = (multipleResult as? CalcResult.Err)?.error?.message
            ResultCard(
                label = keyLabel,
                value = keyAmount?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
                caption = "Exact value — the component rows below are rounded to whole cents",
                error = error,
                onCopy = { copy(it, "Amount") },
                copyValue = keyAmount?.let { Num.formatForCopy(it) },
                onSave = keyAmount?.let { saved ->
                    {
                        args.onRecord(
                            "${Num.formatForCopy(amountValue ?: BigDecimal.ZERO)} with " +
                                "${components.size} tax components " +
                                (if (adding) "added" else "extracted"),
                            fmt.num(saved, precision = maxOf(fmt.precision, 10)),
                        )
                    }
                },
            )

            if (value != null) {
                SectionCard(title = "Breakdown") {
                    ResultRow(
                        label = "Net",
                        value = fmt.num(TaxEngine.roundToCents(value.net), precision = maxOf(fmt.precision, 10)),
                    )
                    value.components.forEach { component ->
                        ResultRow(
                            label = "${component.name} (${fmt.num(component.ratePct, precision = 4)}%)",
                            value = fmt.num(TaxEngine.roundToCents(component.tax), precision = maxOf(fmt.precision, 10)),
                        )
                    }
                    ResultRow(
                        label = "Total tax",
                        value = fmt.num(TaxEngine.roundToCents(value.totalTax), precision = maxOf(fmt.precision, 10)),
                    )
                    ResultRow(
                        label = "Total",
                        value = fmt.num(TaxEngine.roundToCents(value.gross), precision = maxOf(fmt.precision, 10)),
                        emphasis = true,
                    )
                    ResultRow(
                        label = "Combined rate",
                        value = fmt.num(value.combinedRatePct, precision = maxOf(fmt.precision, 4)),
                        unit = "%",
                    )
                }
            }
        }

        InfoNote(TaxEngine.MULTI_NOTE)
        if (!adding) {
            InfoNote(
                "Extracting treats the entered amount as tax-inclusive and backs out " +
                    "net = gross ÷ (1 + rate): the tax was already paid inside the price.",
            )
        }
        InfoNote(
            "Tax rules, exemptions and rounding differ by country and shop; this tool does " +
                "the arithmetic only and is not tax advice.",
        )

        FormulaCard(
            formula = if (adding) {
                if (useMultiple) "gross = net + Σ net · rᵢ" else "gross = net × (1 + r ÷ 100)"
            } else {
                if (useMultiple) "net = gross ÷ (1 + Σrᵢ)" else "net = gross ÷ (1 + r ÷ 100)"
            },
            explanation = if (useMultiple) {
                "Each component rate is applied to the same net base and the results are " +
                    "added, so 5% + 2% behaves like one 7% rate."
            } else {
                "The rate is a percentage: 7.25 means 7.25%. Intermediate math keeps 20 " +
                    "significant digits; only the receipt rows round to cents (HALF_UP)."
            },
            variables = listOf(
                "r" to "tax rate in percent",
                "net" to "amount before tax",
                "gross" to "amount after tax",
            ),
        )
    }
}
