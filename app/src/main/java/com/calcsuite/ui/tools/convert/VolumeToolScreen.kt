package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.VolumeFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun VolumeToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = VolumeFamily,
        defaultFrom = "L",
        defaultTo = "us_gal",
        formula = { from, to ->
            val f = VolumeFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} L per ${from.symbol})"
            } else {
                "value → litre → value"
            }
        },
    )
}
