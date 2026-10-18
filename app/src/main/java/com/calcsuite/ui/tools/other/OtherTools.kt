package com.calcsuite.ui.tools.other

import androidx.compose.runtime.Composable
import com.calcsuite.ui.tools.ToolArgs

/**
 * Dispatcher for the Other tools. Returns false when the tool id does not
 * belong to this domain so the host can try the next dispatcher.
 */
@Composable
fun OtherTools(args: ToolArgs): Boolean {
    when (args.tool.id) {
        "other.mileage" -> MileageToolScreen(args)
        "other.ohms_law" -> OhmsLawToolScreen(args)
        else -> return false
    }
    return true
}
