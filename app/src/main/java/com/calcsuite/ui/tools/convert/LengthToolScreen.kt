package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.LengthFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun LengthToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = LengthFamily,
        defaultFrom = "km",
        defaultTo = "mi",
        formula = { from, to ->
            val f = LengthFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   (exact factor: ${from.factor.stripTrailingZeros().toPlainString()} ${from.symbol} per metre)"
            else "value → metre → value"
        },
    )
}
