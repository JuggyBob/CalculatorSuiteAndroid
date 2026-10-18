package com.calcsuite.ui.tools.finance

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Finance batch dispatcher: maps the six `finance.*` tool ids to their screens
 * and returns false for any other tool so the host can try another batch.
 */
@Composable
fun FinanceTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "finance.currency" -> CurrencyToolScreen(args)
        "finance.unit_price" -> UnitPriceToolScreen(args)
        "finance.tax" -> TaxToolScreen(args)
        "finance.tip" -> TipToolScreen(args)
        "finance.loan" -> LoanToolScreen(args)
        "finance.interest" -> InterestToolScreen(args)
        else -> return false
    }
    return true
}
