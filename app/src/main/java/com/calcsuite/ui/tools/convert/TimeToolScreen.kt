package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.TimeFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun TimeToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = TimeFamily,
        defaultFrom = "h",
        defaultTo = "min",
        formula = { from, to ->
            val f = TimeFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} s per ${from.symbol})"
            } else {
                "value → second → value"
            }
        },
    )
}
