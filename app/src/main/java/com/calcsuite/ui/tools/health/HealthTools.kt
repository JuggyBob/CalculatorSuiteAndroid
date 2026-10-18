package com.calcsuite.ui.tools.health

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Dispatcher for the Health tools. Returns false when the tool id does not
 * belong to this domain so the host can try the next dispatcher.
 */
@Composable
fun HealthTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "health.bmi" -> BmiToolScreen(args)
        "health.calories" -> CalorieToolScreen(args)
        "health.body_fat" -> BodyFatToolScreen(args)
        else -> return false
    }
    return true
}
