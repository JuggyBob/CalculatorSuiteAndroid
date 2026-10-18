package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.PressureFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun PressureToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = PressureFamily,
        defaultFrom = "bar",
        defaultTo = "psi",
        formula = { from, to ->
            val f = PressureFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} Pa per ${from.symbol})"
            } else {
                "value → pascal → value"
            }
        },
    )
}
