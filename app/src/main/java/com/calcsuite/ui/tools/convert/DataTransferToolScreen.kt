package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.convert.TransferEngine
import com.calcsuite.engine.convert.UnitFamily
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Converts bit/s and B/s rates and estimates transfer times. */
@Composable
fun DataTransferToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = TransferEngine.family,
        defaultFrom = "MB_s",
        defaultTo = "Mbit_s",
        formula = { from, to ->
            val f = TransferEngine.family.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} bit/s per ${from.symbol})"
            } else {
                "value → bit/s → value"
            }
        },
        extraContent = { TransferPanels(args) },
    )
}

@Composable
private fun TransferPanels(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    val rateOptions = remember {
        TransferEngine.allUnits.map { Option(it.id, it.label, it.group, it.symbol) }
    }
    val sizeOptions = remember {
        TransferEngine.fileSizeUnits.map { (id, label) -> Option(id, label) }
    }

    ExpandablePanel(title = "Transfer time calculator") {
        var sizeText by rememberSaveable { mutableStateOf("1") }
        var sizeUnitId by rememberSaveable { mutableStateOf("GB") }
        var binarySize by rememberSaveable { mutableStateOf(false) }
        var rateText by rememberSaveable { mutableStateOf("100") }
        var rateUnitId by rememberSaveable { mutableStateOf("Mbit_s") }

        val size = Num.parseOrNull(sizeText)
        val rate = Num.parseOrNull(rateText)
        val conversion: CalcResult<BigDecimal>? = if (size != null && rate != null) {
            TransferEngine.transferTime(size, sizeUnitId, rate, rateUnitId, binarySize)
        } else {
            null
        }
        val seconds = (conversion as? CalcResult.Ok)?.value
        val error = when {
            sizeText.isBlank() || rateText.isBlank() -> null
            size == null -> "Enter a valid file size"
            rate == null -> "Enter a valid transfer rate"
            conversion is CalcResult.Err -> conversion.error.message
            else -> null
        }

        NumberField(
            label = "File size",
            value = sizeText,
            onValueChange = { sizeText = it },
            unit = sizeUnitId,
            error = if (size == null && sizeText.isNotBlank()) "Enter a valid number" else null,
        )
        SelectField(
            label = "File size unit",
            options = sizeOptions,
            selected = sizeUnitId,
            onSelect = { sizeUnitId = it },
        )
        ToggleRow(
            label = "File uses binary size (kB = 1024 B)",
            description = "Turn off to read kB/MB/GB/TB as powers of 1000; KiB/MiB/GiB are always binary",
            checked = binarySize,
            onCheckedChange = { binarySize = it },
        )
        SelectField(
            label = "Transfer rate unit",
            options = rateOptions,
            selected = rateUnitId,
            onSelect = { rateUnitId = it },
        )
        NumberField(
            label = "Transfer rate",
            value = rateText,
            onValueChange = { rateText = it },
            unit = TransferEngine.unit(rateUnitId)?.symbol ?: "",
            error = if (rate == null && rateText.isNotBlank()) "Enter a valid number" else null,
        )
        ResultCard(
            label = "Transfer time",
            value = seconds?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = "s",
            caption = seconds?.let { durationText(it) },
            error = when {
                seconds == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Transfer time") },
            copyValue = seconds?.let { Num.formatForCopy(it) },
            onSave = seconds?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(size!!)} $sizeUnitId (${if (binarySize) "binary" else "decimal"}) at " +
                            "${Num.formatForCopy(rate!!)} ${TransferEngine.unit(rateUnitId)?.symbol}",
                        "${Num.formatForCopy(it)} s",
                    )
                }
            },
        )
        InfoNote(
            "Theoretical time: protocol headers, latency and congestion reduce real throughput to roughly " +
                "60–90% of the link rate.",
        )
    }

    ExpandablePanel(title = "Required rate for a deadline") {
        var sizeText by rememberSaveable { mutableStateOf("10") }
        var sizeUnitId by rememberSaveable { mutableStateOf("GB") }
        var binarySize by rememberSaveable { mutableStateOf(false) }
        var durationText by rememberSaveable { mutableStateOf("120") }

        val size = Num.parseOrNull(sizeText)
        val duration = Num.parseOrNull(durationText)
        val conversion: CalcResult<BigDecimal>? = if (size != null && duration != null) {
            TransferEngine.requiredRate(size, sizeUnitId, duration, binarySize)
        } else {
            null
        }
        val bitsPerSecond = (conversion as? CalcResult.Ok)?.value
        val error = when {
            sizeText.isBlank() || durationText.isBlank() -> null
            size == null -> "Enter a valid file size"
            duration == null -> "Enter a valid duration"
            conversion is CalcResult.Err -> conversion.error.message
            else -> null
        }

        NumberField(
            label = "File size",
            value = sizeText,
            onValueChange = { sizeText = it },
            unit = sizeUnitId,
            error = if (size == null && sizeText.isNotBlank()) "Enter a valid number" else null,
        )
        SelectField(
            label = "File size unit",
            options = sizeOptions,
            selected = sizeUnitId,
            onSelect = { sizeUnitId = it },
        )
        ToggleRow(
            label = "File uses binary size (kB = 1024 B)",
            checked = binarySize,
            onCheckedChange = { binarySize = it },
        )
        NumberField(
            label = "Target duration",
            value = durationText,
            onValueChange = { durationText = it },
            unit = "s",
            error = if (duration == null && durationText.isNotBlank()) "Enter a valid number" else null,
        )
        ResultCard(
            label = "Required rate",
            value = bitsPerSecond?.let { fmt.num(it, precision = maxOf(fmt.precision, 10)) },
            unit = "bit/s",
            caption = bitsPerSecond?.let {
                "= ${fmt.num(it.divide(BigDecimal("1000000"), UnitFamily.CTX))} Mbit/s " +
                    "= ${fmt.num(it.divide(BigDecimal("8000000"), UnitFamily.CTX))} MB/s"
            },
            error = when {
                bitsPerSecond == null && error != null -> error
                else -> null
            },
            onCopy = { copy(it, "Required rate") },
            copyValue = bitsPerSecond?.let { Num.formatForCopy(it) },
            onSave = bitsPerSecond?.let {
                {
                    args.onRecord(
                        "${Num.formatForCopy(size!!)} $sizeUnitId in ${Num.formatForCopy(duration!!)} s",
                        "${Num.formatForCopy(it)} bit/s",
                    )
                }
            },
        )
        if (bitsPerSecond != null) {
            ResultRow(
                label = "Megabits per second",
                value = fmt.num(bitsPerSecond.divide(BigDecimal("1000000"), UnitFamily.CTX)),
                unit = "Mbit/s",
            )
            ResultRow(
                label = "Megabytes per second",
                value = fmt.num(bitsPerSecond.divide(BigDecimal("8000000"), UnitFamily.CTX)),
                unit = "MB/s",
            )
        }
    }
}

/** Compact "1 h 2 min 3 s" rendering of a duration in seconds. */
private fun durationText(seconds: BigDecimal): String {
    if (seconds.signum() <= 0) return "0 s"
    var rest = seconds.setScale(0, RoundingMode.HALF_UP).toBigInteger()
    val hours = rest.divide(BigInteger.valueOf(3600))
    rest = rest.remainder(BigInteger.valueOf(3600))
    val minutes = rest.divide(BigInteger.valueOf(60))
    val secs = rest.remainder(BigInteger.valueOf(60))
    val parts = buildList {
        if (hours.signum() > 0) add("$hours h")
        if (minutes.signum() > 0) add("$minutes min")
        if (secs.signum() > 0 || isEmpty()) add("$secs s")
    }
    return "= ${parts.joinToString(" ")}"
}
