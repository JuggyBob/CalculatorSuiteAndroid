package com.calcsuite.ui.tools.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.data.RatesSnapshot
import com.calcsuite.engine.finance.CurrencyCatalog
import com.calcsuite.engine.finance.CurrencyEngine
import com.calcsuite.ui.LocalApp
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** `d MMM yyyy, HH:mm` in the device locale — used for rate timestamps. */
private fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

/**
 * Currency converter with live reference rates and an offline cache.
 *
 * Rates come from [com.calcsuite.data.CurrencyRepository] (cached in DataStore,
 * refreshed from the provider). The screen always shows *something*: cached
 * rates while the network is down, a clear staleness warning after 24 h and
 * the exact date the shown rates were fetched.
 */
@Composable
fun CurrencyToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()
    val repository = LocalApp.current.currency
    val scope = rememberCoroutineScope()

    var amount by rememberSaveable { mutableStateOf("1") }
    var fromCode by rememberSaveable { mutableStateOf("USD") }
    var toCode by rememberSaveable { mutableStateOf("EUR") }

    val cached by repository.cached.collectAsState(initial = null)
    var snapshot by remember { mutableStateOf<RatesSnapshot?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }

    // Keep the newest cached snapshot when no refresh has happened yet.
    LaunchedEffect(cached) {
        if (snapshot == null && cached != null) snapshot = cached
    }

    // First composition: read the cache once; only fetch when there is none.
    LaunchedEffect(Unit) {
        val cachedFirst = try {
            repository.cached.first()
        } catch (_: Exception) {
            null
        }
        if (cachedFirst != null) {
            if (snapshot == null) snapshot = cachedFirst
        } else {
            refreshing = true
            when (val result = repository.refresh()) {
                is CalcResult.Ok -> snapshot = result.value
                is CalcResult.Err -> refreshError = result.error.message
            }
            refreshing = false
        }
    }

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            refreshError = null
            when (val result = repository.refresh()) {
                is CalcResult.Ok -> snapshot = result.value
                is CalcResult.Err -> refreshError = result.error.message
            }
            refreshing = false
        }
    }

    val fromCurrency = CurrencyCatalog.byCode(fromCode)
    val toCurrency = CurrencyCatalog.byCode(toCode)
    val options = remember {
        CurrencyCatalog.currencies.map { currency ->
            Option(
                value = currency.code,
                label = "${currency.code} — ${currency.name}",
                group = null,
                symbol = currency.symbol,
            )
        }
    }

    val parsed = Num.parseOrNull(amount)
    val conversion = remember(parsed, fromCode, toCode, snapshot) {
        val active = snapshot
        if (parsed == null || active == null) null
        else CurrencyEngine.convert(parsed, fromCode, toCode, active)
    }
    val inverseRate = remember(fromCode, toCode, snapshot) {
        val active = snapshot
        if (active == null) null else CurrencyEngine.pairRate(toCode, fromCode, active)
    }
    val fieldError = when {
        amount.isBlank() -> null
        parsed == null -> "Enter a valid number"
        parsed.signum() < 0 -> "Amount must not be negative"
        else -> null
    }
    val panelError = when {
        conversion is CalcResult.Err -> conversion.error.message
        snapshot == null -> "Rates are not available yet — tap Refresh to download them"
        else -> null
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SelectField(
                label = "From",
                options = options,
                selected = fromCode,
                onSelect = { fromCode = it },
                modifier = Modifier.weight(1f),
                searchPlaceholder = "Search code, name or symbol",
            )
            IconButton(
                onClick = {
                    val previousFrom = fromCode
                    fromCode = toCode
                    toCode = previousFrom
                    (conversion as? CalcResult.Ok<CurrencyEngine.Conversion>)?.let { input ->
                        amount = Num.formatForCopy(input.value.exactResult)
                    }
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = "Swap currencies",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            SelectField(
                label = "To",
                options = options,
                selected = toCode,
                onSelect = { toCode = it },
                modifier = Modifier.weight(1f),
                searchPlaceholder = "Search code, name or symbol",
            )
        }

        NumberField(
            label = "Amount",
            value = amount,
            onValueChange = { amount = it },
            unit = fromCurrency?.symbol,
            error = fieldError,
        )

        val value = (conversion as? CalcResult.Ok<CurrencyEngine.Conversion>)?.value
        ResultCard(
            label = "Converted amount",
            value = value?.displayResult?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = toCode,
            caption = value?.let {
                "Settled to ${it.to.minorUnits} decimal place${if (it.to.minorUnits == 1) "" else "s"} for ${it.to.code}"
            },
            error = if (value == null) panelError else null,
            onCopy = { copy(it, "Converted amount") },
            copyValue = value?.exactResult?.let { Num.formatForCopy(it) },
            onSave = value?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(it.amount)} ${it.from.code} → ${it.to.code}",
                        "${fmt.num(it.displayResult, precision = maxOf(fmt.precision, 10))} ${it.to.code}",
                    )
                }
            },
        )

        if (value != null) {
            Column {
                ResultRow(
                    label = "1 ${value.from.code} =",
                    value = CurrencyEngine.formatRate(value.pairRate),
                    unit = value.to.code,
                    emphasis = true,
                )
                (inverseRate as? CalcResult.Ok<BigDecimal>)?.let { inverse ->
                    ResultRow(
                        label = "1 ${value.to.code} =",
                        value = CurrencyEngine.formatRate(inverse.value),
                        unit = value.from.code,
                    )
                }
                ResultRow(
                    label = "Full-precision rate",
                    value = Num.formatForCopy(value.pairRate),
                    onCopy = { copy(Num.formatForCopy(value.pairRate), "Rate") },
                )
            }
        }

        SectionCard(title = "Rate data") {
            val active = snapshot
            if (active != null) {
                val status = CurrencyEngine.statusOf(active)
                ResultRow(
                    label = "Last updated",
                    value = formatTimestamp(active.fetchedAtMillis),
                )
                ResultRow(
                    label = "Fetched",
                    value = CurrencyEngine.describeAge(status.ageMillis),
                )
                ResultRow(
                    label = "Provider update",
                    value = formatTimestamp(active.providerUpdatedAtMillis),
                )
                ResultRow(
                    label = "Source",
                    value = active.sourceLabel,
                )
                if (status.stale) {
                    ErrorNotice(
                        "Rates are from ${formatTimestamp(active.fetchedAtMillis)} — tap Refresh for current rates",
                    )
                } else {
                    InfoNote("Showing cached rates fetched ${CurrencyEngine.describeAge(status.ageMillis)}.")
                }
            } else {
                InfoNote("No cached rates yet. Tap Refresh to download reference rates (needs internet).")
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { refresh() }, enabled = !refreshing) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (refreshing) "Refreshing…" else "Refresh")
                }
                if (refreshing) {
                    Text(
                        "Keeping the current rates visible while downloading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            refreshError?.let { ErrorNotice(it) }
        }

        InfoNote(
            "These are reference rates published daily for information only. Banks, cards and " +
                "transfer services add their own margin and fees, so the rate you actually get " +
                "will differ.",
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.tertiary,
        )

        FormulaCard(
            formula = "amount × rate(to) ÷ rate(from)",
            explanation = "Cross rates are derived from a single USD-based snapshot; the display " +
                "amount is rounded to the target currency's minor unit and the full-precision " +
                "value stays available for copying.",
            variables = listOf(
                "rate(x)" to "provider rate for currency x against the snapshot base",
            ),
        )
    }
}
