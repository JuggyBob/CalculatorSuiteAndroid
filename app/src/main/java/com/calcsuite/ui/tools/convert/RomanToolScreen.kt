package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.Num
import com.calcsuite.core.err
import com.calcsuite.engine.convert.RomanEngine
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigInteger

private const val MODE_NUMBER_TO_ROMAN = "n2r"
private const val MODE_ROMAN_TO_NUMBER = "r2n"

@Composable
fun RomanToolScreen(args: ToolArgs) {
    val copy = rememberCopyAction()

    var mode by rememberSaveable { mutableStateOf(MODE_NUMBER_TO_ROMAN) }
    var numberText by rememberSaveable { mutableStateOf("1994") }
    var romanText by rememberSaveable { mutableStateOf("MCMXCIV") }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            options = listOf(
                MODE_NUMBER_TO_ROMAN to "Number → Roman",
                MODE_ROMAN_TO_NUMBER to "Roman → Number",
            ),
            selected = mode,
            onSelect = { mode = it },
        )

        if (mode == MODE_NUMBER_TO_ROMAN) {
            val parsed = Num.parseIntOrNull(numberText)
            val conversion: CalcResult<String>? = when {
                parsed == null -> null
                parsed > BigInteger.valueOf(RomanEngine.MAX_VALUE.toLong()) ->
                    err(ErrorKind.Domain, "Roman numerals are limited to ${RomanEngine.MAX_VALUE} (MMMCMXCIX)")
                parsed < BigInteger.ZERO ->
                    err(ErrorKind.Domain, "Roman numerals cannot represent negative numbers")
                else -> RomanEngine.toRoman(parsed.toInt())
            }
            val roman = (conversion as? CalcResult.Ok)?.value
            val error = when {
                numberText.isBlank() -> null
                parsed == null -> "Enter a whole number"
                conversion is CalcResult.Err -> conversion.error.message
                else -> null
            }

            NumberField(
                label = "Number",
                value = numberText,
                onValueChange = { numberText = it },
                integerOnly = true,
                unit = "1–${RomanEngine.MAX_VALUE}",
                error = error,
            )
            ResultCard(
                label = "Roman numeral",
                value = roman,
                error = when {
                    roman == null && error != null -> error
                    else -> null
                },
                onCopy = { copy(it, "Roman numeral") },
                copyValue = roman,
                onSave = roman?.let {
                    {
                        args.onRecord("Roman($parsed)", it)
                    }
                },
            )
            if (roman != null) {
                ResultRow(
                    label = "Length",
                    value = roman.length.toString(),
                    unit = if (roman.length == 1) "symbol" else "symbols",
                )
            }
        } else {
            val conversion = if (romanText.isBlank()) null else RomanEngine.parse(romanText)
            val parsed = (conversion as? CalcResult.Ok)?.value
            val error = when {
                romanText.isBlank() -> null
                conversion is CalcResult.Err -> conversion.error.message
                else -> null
            }

            PlainField(
                label = "Roman numeral",
                value = romanText,
                onValueChange = { romanText = it },
                error = error,
                placeholder = "MCMXCIV",
            )
            ResultCard(
                label = "Number",
                value = parsed?.toString(),
                error = when {
                    parsed == null && error != null -> error
                    else -> null
                },
                caption = parsed?.let { "canonical form: ${RomanEngine.canonical(it)}" },
                onCopy = { copy(it, "Number") },
                copyValue = parsed?.toString(),
                onSave = parsed?.let {
                    {
                        args.onRecord(it.toString().uppercase(), RomanEngine.canonical(it) ?: "$it")
                    }
                },
            )
        }

        FormulaCard(
            formula = "I = 1   V = 5   X = 10   L = 50   C = 100   D = 500   M = 1000",
            explanation = "Subtractive notation is used for 4, 9, 40, 90, 400 and 900 (IV, IX, XL, XC, CD, CM). " +
                "Only canonical spellings are accepted: IIII, VX or IC are rejected and the canonical form is given.",
        )

        ExpandablePanel(title = "Symbols and subtraction rules") {
            RomanEngine.symbolLegend.forEach { (symbol, value, rule) ->
                ResultRow(label = "$symbol  =  $value", value = rule)
            }
            InfoNote("Roman numerals have no zero and the standard range stops at 3999 (MMMCMXCIX).")
        }
    }
}
