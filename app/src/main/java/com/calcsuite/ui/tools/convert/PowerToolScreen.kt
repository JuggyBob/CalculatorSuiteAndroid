package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.PowerFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun PowerToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = PowerFamily,
        defaultFrom = "W",
        defaultTo = "hp",
        formula = { from, to ->
            val f = PowerFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} W per ${from.symbol})"
            } else {
                "value → watt → value"
            }
        },
    )
}
