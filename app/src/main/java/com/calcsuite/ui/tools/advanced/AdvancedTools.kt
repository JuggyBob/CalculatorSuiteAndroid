package com.calcsuite.ui.tools.advanced

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * The Advanced Mathematics tools.
 *
 * These are learning environments as much as calculators: each one computes
 * real values for the example in front of it and explains the mathematics
 * behind them, and each one is explicit about the limits of what it does.
 */
@Composable
fun AdvancedTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "advanced.measure" -> MeasureTheoryToolScreen(args)
        "advanced.topology" -> TopologyToolScreen(args)
        "advanced.functional" -> FunctionalAnalysisToolScreen(args)
        else -> return false
    }
    return true
}
