package com.calcsuite.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.calcsuite.data.ToolDef

/**
 * Renders a tool's glyph in the theme's accent, the same colour as every other
 * tool icon.
 *
 * Centralised so the catalogue list, the category grid and search results all
 * show a given tool's icon identically.
 */
@Composable
fun ToolIcon(
    tool: ToolDef,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(tool.icon, contentDescription = null, tint = tint, modifier = modifier)
}
