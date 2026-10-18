package com.calcsuite.ui.tools.convert

import androidx.compose.runtime.Composable
import com.calcsuite.engine.convert.families.AccelerationFamily
import com.calcsuite.ui.tools.ToolArgs

@Composable
fun AccelerationToolScreen(args: ToolArgs) {
    LinearConverterScreen(
        args = args,
        family = AccelerationFamily,
        defaultFrom = "m_s2",
        defaultTo = "ft_s2",
        formula = { from, to ->
            val f = AccelerationFamily.factorBetween(from.id, to.id).valueOrNull()
            if (f != null) {
                "1 ${from.symbol} = ${f.stripTrailingZeros().toPlainString()} ${to.symbol}   " +
                    "(base: ${from.factor.stripTrailingZeros().toPlainString()} ${from.symbol} per m/s²)"
            } else {
                "value → m/s² → value"
            }
        },
    )
}
