package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.SpeedFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun SpeedToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = SpeedFamily,
        defaultFrom = "km_h",
        defaultTo = "mph",
        formula = { from, to ->
            val f = SpeedFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} m/s per ${from.symbol})"
            } else {
                "value → m/s → value"
            }
        },
    )
}
