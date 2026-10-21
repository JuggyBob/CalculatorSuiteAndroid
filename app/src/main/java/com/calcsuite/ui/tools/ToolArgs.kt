package com.calcsuite.ui.tools

import androidx.compose.runtime.Composable
import com.calcsuite.data.ToolDef

/**
 * Everything a tool screen needs from its host: navigation, favourite state and
 * the history recorder. Keeping this stable lets every screen share one shape.
 */
data class ToolArgs(
    val tool: ToolDef,
    val favorite: Boolean,
    val onBack: () -> Unit,
    val onToggleFavorite: () -> Unit,
    /** Records a calculation into history. */
    val onRecord: HistoryRecorder,
    /** Navigates to another tool (used by "related tools" rows). */
    val onOpenTool: (String) -> Unit,
    /**
     * The values the tool should open with, by the name the tool registry
     * uses.
     *
     * They are there when a problem is handed over from elsewhere, such as
     * from the AI problem solver, so that the tool opens with the problem
     * already in its fields. A screen asks for the field it wants with
     * [prefill].
     */
    val prefillValues: Map<String, String> = emptyMap(),
)

/**
 * Records a calculation into the history.
 *
 * The values are the ones the tool's own fields hold, by the name those
 * fields use, so that the saved calculation can be opened again with the
 * problem already in it. A tool with nothing to keep leaves them out, and
 * the entry then opens the tool with that tool's own defaults.
 */
class HistoryRecorder(
    private val write: (String, String, Map<String, String>) -> Unit,
) {
    /**
     * Records a calculation that has no values of its own to keep.
     *
     * This is what most tools do, and it reads the same as a plain call.
     */
    operator fun invoke(input: String, result: String) {
        write(input, result, emptyMap())
    }

    /** Records a calculation together with the values it was made from. */
    fun record(input: String, result: String, values: Map<String, String> = emptyMap()) {
        write(input, result, values)
    }
}

/** Signature shared by all tool screens. */
typealias ToolScreen = @Composable (ToolArgs) -> Unit
