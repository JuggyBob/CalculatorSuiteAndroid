package com.calcsuite.ui.tools.algebra

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Algebra batch dispatcher: Percentage, Average, Proportion, Ratio, Equations,
 * GCF & LCM, Combinations, Prime checker, Number generator, Calculus and
 * Inequalities. Returns false for ids that belong to another batch so the host
 * can continue its own dispatch chain.
 */
@Composable
fun AlgebraTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "algebra.percentage" -> PercentageToolScreen(args)
        "algebra.average" -> AverageToolScreen(args)
        "algebra.proportion" -> ProportionToolScreen(args)
        "algebra.ratio" -> RatioToolScreen(args)
        "algebra.equations" -> EquationsToolScreen(args)
        "algebra.gcf_lcm" -> GcfLcmToolScreen(args)
        "algebra.combinations" -> CombinatoricsToolScreen(args)
        "algebra.primes" -> PrimeToolScreen(args)
        "algebra.random" -> RandomToolScreen(args)
        "algebra.inequalities" -> InequalityToolScreen(args)
        else -> return false
    }
    return true
}
