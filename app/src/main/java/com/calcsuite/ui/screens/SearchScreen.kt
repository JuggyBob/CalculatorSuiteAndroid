package com.calcsuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.calcsuite.data.ToolCategory
import com.calcsuite.data.ToolDef
import com.calcsuite.data.ToolRegistry
import com.calcsuite.ui.components.Pill
import com.calcsuite.ui.components.ToolIcon
import com.calcsuite.ui.components.clickableRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    favorites: Set<String>,
    onBack: () -> Unit,
    onOpenTool: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val results: List<ToolDef> = remember(query) {
        if (query.isBlank()) ToolRegistry.all
        else ToolRegistry.search(query)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    // A little shorter than the default, which keeps the touch
                    // target comfortable and the list of results in view.
                    .heightIn(min = 48.dp, max = 52.dp)
                    .focusRequester(focusRequester),
                // The count comes from the registry, so it is always the number
                // of tools the user can actually find.
                placeholder = { Text("Search ${ToolRegistry.all.size} tools") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
            )

            if (results.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No tools match “$query”", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Try “percent”, “hex”, “mpg”, “bmi”, “ohm” …",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    var lastCategory: ToolCategory? = null
                    items(results, key = { it.id }) { tool ->
                        if (query.isBlank() && tool.category != lastCategory) {
                            lastCategory = tool.category
                            Text(
                                tool.category.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
                            )
                        }
                        ToolResultRow(
                            tool = tool,
                            isFavorite = tool.id in favorites,
                            onClick = { onOpenTool(tool.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ToolResultRow(tool: ToolDef, isFavorite: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableRow(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ToolIcon(
                tool,
                modifier = Modifier
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tool.title, style = MaterialTheme.typography.titleSmall)
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(13.dp),
                    )
                }
            }
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Pill(tool.category.label)
    }
}

/** Small helper used by tool screens that want to render a "related" row. */
@Composable
fun RelatedToolsRow(current: ToolDef, onOpenTool: (String) -> Unit) {
    val related = remember(current) {
        ToolRegistry.inCategory(current.category).filter { it.id != current.id }
    }
    if (related.isEmpty()) return
    Column {
        Text(
            "Also in ${current.category.label}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            related.take(3).forEach { tool ->
                ToolChip(tool, onClick = { onOpenTool(tool.id) })
            }
        }
    }
}
