package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.ForceFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun ForceToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = ForceFamily,
        defaultFrom = "N",
        defaultTo = "lbf",
        formula = { from, to ->
            val f = ForceFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} N per ${from.symbol})"
            } else {
                "value → newton → value"
            }
        },
    )
}
