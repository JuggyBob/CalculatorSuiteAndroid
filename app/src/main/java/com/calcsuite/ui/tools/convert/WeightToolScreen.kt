package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.WeightFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun WeightToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = WeightFamily,
        defaultFrom = "kg",
        defaultTo = "lb",
        formula = { from, to ->
            val f = WeightFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} kg per ${from.symbol})"
            } else {
                "value → kilogram → value"
            }
        },
    )
}
