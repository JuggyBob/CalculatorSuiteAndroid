package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.DataStorageFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun DataStorageToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = DataStorageFamily,
        defaultFrom = "MB",
        defaultTo = "MiB",
        formula = { from, to ->
            val f = DataStorageFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} bits per ${from.symbol})"
            } else {
                "value → bit → value"
            }
        },
    )
}
