package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.engine.convert.NumericBaseEngine
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigInteger

private const val WIDTH_OFF = 0

private fun baseLabel(base: Int): String = when (base) {
    2 -> "Base 2 — binary"
    8 -> "Base 8 — octal"
    10 -> "Base 10 — decimal"
    16 -> "Base 16 — hexadecimal"
    36 -> "Base 36"
    else -> "Base $base"
}

@Composable
fun NumericBaseToolScreen(args: ToolArgs) {
    val copy = rememberCopyAction()

    var inputText by rememberSaveable { mutableStateOf("255") }
    var inputBase by rememberSaveable { mutableStateOf(10) }
    var outputBase by rememberSaveable { mutableStateOf(16) }
    var uppercase by rememberSaveable { mutableStateOf(false) }
    var prefixes by rememberSaveable { mutableStateOf(false) }
    var groupBinary by rememberSaveable { mutableStateOf(true) }
    var width by rememberSaveable { mutableStateOf(WIDTH_OFF) }
    var signed by rememberSaveable { mutableStateOf(true) }

    val baseOptions = remember {
        (NumericBaseEngine.MIN_BASE..NumericBaseEngine.MAX_BASE).map { Option(it, baseLabel(it)) }
    }

    val parsed: CalcResult<BigInteger> = when {
        inputText.isBlank() -> err(ErrorKind.InvalidInput, "Enter a value")
        else -> NumericBaseEngine.parse(inputText, inputBase)
    }
    val value = (parsed as? CalcResult.Ok)?.value
    val parseError = (parsed as? CalcResult.Err)?.error?.message

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        PlainField(
            label = "Value",
            value = inputText,
            onValueChange = { inputText = it },
            error = if (inputText.isNotBlank()) parseError else null,
            placeholder = if (inputBase == 16) "ff" else "255",
            monospace = true,
        )
        SelectField(
            label = "Input base",
            options = baseOptions,
            selected = inputBase,
            onSelect = { inputBase = it },
        )

        if (value != null) {
            SectionCard(title = "Value in other bases") {
                ResultRow(
                    label = "Binary",
                    value = NumericBaseEngine.render(value, 2, groupBinary = groupBinary),
                    unit = "base 2",
                    onCopy = { copy(it, "Binary") },
                )
                ResultRow(
                    label = "Octal",
                    value = NumericBaseEngine.render(value, 8),
                    unit = "base 8",
                    onCopy = { copy(it, "Octal") },
                )
                ResultRow(
                    label = "Decimal",
                    value = NumericBaseEngine.render(value, 10),
                    unit = "base 10",
                    onCopy = { copy(it, "Decimal") },
                )
                ResultRow(
                    label = "Hexadecimal",
                    value = NumericBaseEngine.render(value, 16, uppercase = uppercase),
                    unit = "base 16",
                    onCopy = { copy(it, "Hex") },
                )
            }
        }

        ExpandablePanel(title = "Output base and display options") {
            SegmentedChoice(
                options = listOf(false to "lowercase", true to "UPPERCASE"),
                selected = uppercase,
                onSelect = { uppercase = it },
                label = "Letter digits (bases above 10)",
            )
            ToggleRow(
                label = "Add 0b / 0o / 0x prefixes",
                description = "Only shown for bases 2, 8 and 16",
                checked = prefixes,
                onCheckedChange = { prefixes = it },
            )
            ToggleRow(
                label = "Group binary digits in 4s",
                description = "1111 0000 instead of 11110000",
                checked = groupBinary,
                onCheckedChange = { groupBinary = it },
            )
            SelectField(
                label = "Output base",
                options = baseOptions,
                selected = outputBase,
                onSelect = { outputBase = it },
            )
            val rendered = value?.let {
                NumericBaseEngine.render(
                    it,
                    outputBase,
                    uppercase = uppercase,
                    prefix = prefixes,
                    groupBinary = groupBinary,
                )
            }
            ResultCard(
                label = "Result in ${baseLabel(outputBase)}",
                value = rendered,
                error = when {
                    value == null -> parseError
                    else -> null
                },
                onCopy = { copy(it, "Result") },
                copyValue = rendered,
                onSave = rendered?.let {
                    {
                        args.onRecord(
                            "${inputText.trim()} (base $inputBase) → base $outputBase",
                            it,
                        )
                    }
                },
            )
        }

        ExpandablePanel(title = "Fixed width and two's complement") {
            SegmentedChoice(
                options = listOf(
                    WIDTH_OFF to "Off",
                    8 to "8 bit",
                    16 to "16 bit",
                    32 to "32 bit",
                    64 to "64 bit",
                ),
                selected = width,
                onSelect = { width = it },
                label = "Fixed width",
            )
            if (width != WIDTH_OFF) {
                ToggleRow(
                    label = "Signed (two's complement)",
                    description = "Negative values use the two's-complement bit pattern",
                    checked = signed,
                    onCheckedChange = { signed = it },
                )
                val fixed = value?.let { NumericBaseEngine.toFixedWidth(it, width, signed) }
                val pattern = (fixed as? CalcResult.Ok)?.value
                val fixedError = (fixed as? CalcResult.Err)?.error?.message
                ResultCard(
                    label = "$width-bit binary",
                    value = pattern?.let { NumericBaseEngine.render(BigInteger(it, 2), 2, groupBinary = true) },
                    error = fixedError ?: if (value == null) parseError else null,
                    caption = when {
                        pattern == null -> null
                        else -> "range: ${NumericBaseEngine.rangeText(width, signed)}"
                    },
                    onCopy = { copy(it, "Bit pattern") },
                    copyValue = pattern,
                    onSave = pattern?.let {
                        {
                            args.onRecord(
                                "${inputText.trim()} as $width-bit ${if (signed) "signed" else "unsigned"}",
                                it,
                            )
                        }
                    },
                )
                if (pattern != null) {
                    val interpreted = NumericBaseEngine.fromTwosComplement(pattern, 2, width)
                        .valueOrNull()
                    ResultRow(
                        label = "Interpreted as signed $width-bit",
                        value = interpreted?.toString() ?: "—",
                        unit = "base 10",
                    )
                }
            }
            InfoNote(
                "In signed mode the top bit is the sign bit: −1 is all ones (${NumericBaseEngine.render(BigInteger.valueOf(-1), 2)} " +
                    "padded to the width). Values outside the range are reported instead of being truncated.",
            )
        }
    }
}
