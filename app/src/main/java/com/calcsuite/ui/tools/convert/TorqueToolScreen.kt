package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.TorqueFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun TorqueToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = TorqueFamily,
        defaultFrom = "Nm",
        defaultTo = "lbf_ft",
        formula = { from, to ->
            val f = TorqueFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} N·m per ${from.symbol})"
            } else {
                "value → N·m → value"
            }
        },
    )
}
