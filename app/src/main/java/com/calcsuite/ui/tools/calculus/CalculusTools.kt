package com.calcsuite.ui.tools.calculus

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Calculus batch dispatcher: the nine calculus tools of the Calculus category.
 *
 * Returns false for ids that belong to another batch so the host can continue
 * its own dispatch chain.
 */
@Composable
fun CalculusTools(args: ToolArgs): Boolean {
    val tool = CalculusTool.forId(args.tool.id) ?: return false
    if (tool == CalculusTool.DIFFERENTIAL_EQUATIONS) {
        // The differential equations tool has a screen of its own, because it
        // needs an equation editor, conditions, numerical settings, a slope
        // field and a long explanation.
        OdeToolScreen(args)
        return true
    }
    if (tool == CalculusTool.MULTIVARIABLE) {
        MultivariableToolScreen(args)
        return true
    }
    if (tool == CalculusTool.FOURIER) {
        FourierToolScreen(args)
        return true
    }
    if (tool == CalculusTool.COMPLEX) {
        ComplexToolScreen(args)
        return true
    }
    if (tool == CalculusTool.PDE) {
        PdeToolScreen(args)
        return true
    }
    CalculusToolScreen(args, tool)
    return true
}
