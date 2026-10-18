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
    /** Records a calculation into history: (input summary, formatted result). */
    val onRecord: (String, String) -> Unit,
    /** Navigates to another tool (used by "related tools" rows). */
    val onOpenTool: (String) -> Unit,
)

/** Signature shared by all tool screens. */
typealias ToolScreen = @Composable (ToolArgs) -> Unit
