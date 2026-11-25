package com.calcsuite.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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

    // Hoisted out of the conditional modifier: the very same ScrollState is
    // reused when the scrollable wrapper switches off and on, so a fullscreen
    // cycle cannot reset the scroll position.
    val scrollState = rememberScrollState()

    // Only the two spacers change size. The calculator panel is never scaled,
    // translated or given an animated height — it keeps the discrete weight()
    // flip, so the keys land exactly where the layout places them.
    val topSpacer by animateDpAsState(
        targetValue = if (expanded) 0.dp else 6.dp,
        animationSpec = tween(200),
        label = "topSpacer",
    )
    val bottomSpacer by animateDpAsState(
        targetValue = if (expanded) 0.dp else 20.dp,
        animationSpec = tween(200),
        label = "bottomSpacer",
    )
    val topEnter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)) +
        fadeIn(animationSpec = tween(200))
    val topExit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(200)) +
        fadeOut(animationSpec = tween(200))
    val bodyEnter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200))
    val bodyExit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))

    Column(
        Modifier
            .fillMaxSize()
            .then(if (expanded) Modifier else Modifier.verticalScroll(scrollState))
            .padding(horizontal = 12.dp),
    ) {
        // Both top bars share one Column slot, so a bar that has animated down
        // to zero height cannot leave a spare gap above the calculator. The
        // 10 dp gaps are explicit for the same reason.
        Box {
            this@Column.AnimatedVisibility(visible = !expanded, enter = topEnter, exit = topExit) {
                Column {
                    Spacer(Modifier.height(topSpacer))
                    Spacer(Modifier.height(10.dp))

                    // --------------------------------------------------------- header
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
                        // Disabled while this bar is leaving, so the incoming
                        // restore control is the only expand/collapse button
                        // that can be tapped during the cross-fade.
                        IconButton(onClick = { expanded = true }, enabled = !expanded) {
                            Icon(
                                Icons.Filled.Fullscreen,
                                contentDescription = "Expand calculator",
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ----------------------------------------------------------- search
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
                }
            }
            this@Column.AnimatedVisibility(visible = expanded, enter = topEnter, exit = topExit) {
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
                    // The outgoing collapse button is disabled during collapse.
                    IconButton(onClick = { expanded = false }, enabled = expanded) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Collapse calculator")
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ----------------------------------------------------------- calculator
        CalculatorPanel(
            scientific = scientific,
            onToggleScientific = { scientific = !scientific },
            onCycleAngleMode = onCycleAngle,
            onRecord = onRecord,
            onOpenHistory = onOpenHistory,
            modifier = if (expanded) Modifier.weight(1f) else Modifier,
        )

        // The menu below the calculator cross-fades in step with the bars
        // above it. Its own leading gap lives inside the AnimatedVisibility so
        // that, once shrunk away, it adds no gap to the expanded layout.
        AnimatedVisibility(visible = !expanded, enter = bodyEnter, exit = bodyExit) {
            Column {
                Spacer(Modifier.height(10.dp))

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
                    Spacer(Modifier.height(10.dp))
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
                    Spacer(Modifier.height(10.dp))
                }

                // ----------------------------------------------------------- categories
                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ToolCategory.entries.chunked(2).forEachIndexed { index, rowCategories ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
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

                Spacer(Modifier.height(10.dp))

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

                Spacer(Modifier.height(10.dp))
                Spacer(Modifier.height(bottomSpacer))
            }
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
