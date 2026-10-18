package com.calcsuite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.calcsuite.core.Num
import com.calcsuite.ui.theme.NumberTextStyle

/**
 * Numeric text field with a unit suffix and inline validation message.
 * Accepts both `.` and `,` as decimal separators (see [Num]).
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    error: String? = null,
    placeholder: String? = null,
    integerOnly: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    monospace: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        suffix = unit?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        textStyle = if (monospace) NumberTextStyle.copy(
            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
        ) else TextStyle.Default,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
            imeAction = imeAction,
        ),
    )
}

/** Plain single-line text field (for names, lists and code input). */
@Composable
fun PlainField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    monospace: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        textStyle = if (monospace) NumberTextStyle else TextStyle.Default,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
    )
}

/** Segmented selector for a small set of mutually exclusive options. */
@Composable
fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.extraSmall,
                        )
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A labelled option for dropdown-style selectors. */
data class Option<T>(val value: T, val label: String, val group: String? = null, val symbol: String? = null)

/**
 * Selector field: shows the current selection and opens a searchable sheet
 * when tapped. Used for units, currencies, activities and similar long lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String,
    options: List<Option<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    searchPlaceholder: String = "Search",
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                .clickableRow { open = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    current?.label ?: "Select",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (current?.symbol != null) {
                    Text(
                        current.symbol,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            SelectSheet(
                title = label,
                options = options,
                selected = selected,
                searchPlaceholder = searchPlaceholder,
                onSelect = {
                    onSelect(it)
                    open = false
                },
            )
        }
    }
}

@Composable
private fun <T> SelectSheet(
    title: String,
    options: List<Option<T>>,
    selected: T,
    searchPlaceholder: String,
    onSelect: (T) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter {
            it.label.contains(query, ignoreCase = true) ||
                (it.symbol?.contains(query, ignoreCase = true) == true) ||
                (it.group?.contains(query, ignoreCase = true) == true)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (options.size > 8) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .padding(top = 4.dp),
        ) {
            var lastGroup: String? = null
            items(filtered, key = { it.value.hashCode().toString() + it.label }) { option ->
                if (option.group != null && option.group != lastGroup) {
                    lastGroup = option.group
                    Text(
                        option.group,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow { onSelect(option.value) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        if (option.group != null && option.group != lastGroup) {
                            Text(option.group, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (option.symbol != null) {
                        Text(
                            option.symbol,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (option.value == selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(20.dp),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onSelect(selected) }) { Text("Close") }
        }
    }
}

/** Switch row with label and optional description. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Row with a label on the left and an interactive trailing element. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableRow(onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/** Ripple-clickable modifier used by custom rows and sheets. */
fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick, role = Role.Button)

/**
 * Renders a formula string.
 *
 * Source strings in the engines/screens separate adjacent formulas with runs of
 * spaces. Those runs collapse unpredictably on narrow screens (they can wrap a
 * few characters per line), so they are normalised here, once, for every tool:
 *  - runs of 3+ spaces become a line break (they always separate two formulas);
 *  - remaining double spaces become a single space;
 *  - explicit `\n` breaks and repeated breaks are preserved/collapsed.
 * Mathematical content is never altered.
 */
@Composable
fun FormulaText(formula: String, modifier: Modifier = Modifier) {
    val normalised = remember(formula) { normaliseFormulaText(formula) }
    Text(
        normalised,
        modifier = modifier.fillMaxWidth(),
        style = NumberTextStyle.copy(
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Shared whitespace normalisation for formula strings (pure, unit-testable). */
fun normaliseFormulaText(formula: String): String {
    val withBreaks = formula.replace(Regex(" {3,}"), "\n")
    val singleSpaced = withBreaks.replace(Regex(" {2,}"), " ")
    return singleSpaced
        .replace(Regex("\n{2,}"), "\n")
        .lines()
        .joinToString("\n") { it.trim() }
        .trim()
}

/** Fixed-width spacer helper. */
@Composable
fun HSpace(width: Int) {
    Box(Modifier.width(width.dp).size(1.dp))
}
