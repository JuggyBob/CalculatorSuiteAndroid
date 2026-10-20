package com.calcsuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calcsuite.data.ToolCategory
import com.calcsuite.data.ToolDef
import com.calcsuite.data.ToolRegistry
import com.calcsuite.ui.components.ToolIcon
import com.calcsuite.ui.components.clickableRow

@Composable
fun MainScreen(
    favorites: Set<String>,
    recents: List<String>,
    onOpenTool: (String) -> Unit,
    onOpenCategory: (ToolCategory) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenScientific: () -> Unit,
    onCycleAngle: () -> Unit,
    onRecord: (String, String) -> Unit,
) {
    var scientific by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (expanded) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!expanded) {
            Spacer(Modifier.height(6.dp))

            // ------------------------------------------------------------- header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Calc Suite",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        // The count comes from the registry, so it can never
                        // drift out of step with the tools that exist.
                        "${ToolRegistry.all.size} tools · works offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Filled.History, contentDescription = "History")
                }
                IconButton(onClick = onOpenAi) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = "Solve a problem from a picture",
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "Expand calculator",
                    )
                }
            }

            // --------------------------------------------------------------- search
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableRow(onOpenSearch),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Search tools — try “hex”, “fuel economy”, “tip”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        } else {
            // A compact bar keeps the restore control reachable while expanded.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Calculator",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "History",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { expanded = false }) {
                    Icon(Icons.Filled.FullscreenExit, contentDescription = "Collapse calculator")
                }
            }
        }

        // ----------------------------------------------------------- calculator
        CalculatorPanel(
            scientific = scientific,
            onToggleScientific = { scientific = !scientific },
            onCycleAngleMode = onCycleAngle,
            onRecord = onRecord,
            onOpenHistory = onOpenHistory,
            modifier = if (expanded) Modifier.weight(1f) else Modifier,
        )

        if (!expanded) {
            // ------------------------------------------------------------ favourites
            val favoriteTools = remember(favorites) {
                ToolRegistry.all.filter { it.id in favorites }
            }
            if (favoriteTools.isNotEmpty()) {
                ToolStrip(
                    title = "Favourites",
                    tools = favoriteTools,
                    onOpenTool = onOpenTool,
                    leadingIcon = Icons.Filled.Star,
                )
            }

            val recentTools = remember(recents, favorites) {
                // The list is keyed by the tool id, so the same tool must not
                // appear twice even if the stored list still holds an old id.
                recents.mapNotNull { ToolRegistry.byId(it) }
                    .distinctBy { it.id }
                    .filter { it.id !in favorites }
            }
            if (recentTools.isNotEmpty()) {
                ToolStrip(
                    title = "Recently used",
                    tools = recentTools,
                    onOpenTool = onOpenTool,
                )
            }

            // ----------------------------------------------------------- categories
            Text(
                "Categories",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToolCategory.entries.chunked(2).forEach { rowCategories ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowCategories.forEach { category ->
                        CategoryCard(
                            category = category,
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenCategory(category) },
                        )
                    }
                    if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            // ---------------------------------------------------------- scientific
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableRow(onOpenScientific),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("fx", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Scientific calculator", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Trigonometry, logarithms, powers, factorials, memory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }


            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ToolStrip(
    title: String,
    tools: List<ToolDef>,
    onOpenTool: (String) -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier
                        .size(14.dp)
                        .padding(end = 0.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
        ) {
            items(tools, key = { it.id }) { tool ->
                ToolChip(tool, onClick = { onOpenTool(tool.id) })
            }
        }
    }
}

@Composable
fun ToolChip(tool: ToolDef, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolIcon(
                tool,
                modifier = Modifier.size(16.dp),
            )
            Text(tool.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun CategoryCard(
    category: ToolCategory,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val count = remember(category) { ToolRegistry.inCategory(category).size }
    Surface(
        modifier = modifier.clickableRow(onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // The label is given room for two lines, so that a long name such
            // as "Advanced Mathematics" does not make its card taller than
            // the others.
            Text(
                category.label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.heightIn(min = 40.dp),
            )
            Text(
                "$count ${if (count == 1) "tool" else "tools"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
