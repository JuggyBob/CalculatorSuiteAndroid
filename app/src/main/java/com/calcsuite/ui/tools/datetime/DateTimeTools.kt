package com.calcsuite.ui.tools.datetime

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Dispatcher for the Date & Time tools. Returns false when the tool id does
 * not belong to this domain so the host can try the next dispatcher.
 */
@Composable
fun DateTimeTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "datetime.age" -> AgeToolScreen(args)
        "datetime.add_subtract" -> DateMathToolScreen(args)
        "datetime.interval" -> IntervalToolScreen(args)
        else -> return false
    }
    return true
}
