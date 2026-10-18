package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.EnergyFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun EnergyToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = EnergyFamily,
        defaultFrom = "kJ",
        defaultTo = "kcal",
        formula = { from, to ->
            val f = EnergyFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} J per ${from.symbol})"
            } else {
                "value → joule → value"
            }
        },
    )
}
