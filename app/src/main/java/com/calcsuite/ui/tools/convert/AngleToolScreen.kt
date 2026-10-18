package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.AngleFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun AngleToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = AngleFamily,
        defaultFrom = "deg",
        defaultTo = "rad",
        formula = { from, to ->
            val f = AngleFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(π taken to 36 digits: 180° = π rad exactly)"
            } else {
                "value → radian → value"
            }
        },
    )
}
