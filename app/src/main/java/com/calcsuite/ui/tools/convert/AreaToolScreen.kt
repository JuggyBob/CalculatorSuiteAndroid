package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.AreaFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun AreaToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = AreaFamily,
        defaultFrom = "m2",
        defaultTo = "ft2",
        formula = { from, to ->
            val f = AreaFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} ${from.symbol} per m²)"
            } else {
                "value → m² → value"
            }
        },
    )
}
