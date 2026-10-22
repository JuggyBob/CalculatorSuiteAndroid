package com.calcsuite.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.calcsuite.ui.theme.NumberTextStyle

/** Copies text to the system clipboard and confirms with a toast. */
@Composable
fun rememberCopyAction(): (String, String?) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text: String, label: String? ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label ?: "Result", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    } }
}

@Composable
fun CopyIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, description: String = "Copy result") {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The prominent result block. Shows either a value or an error message so a
 * screen never looks empty while the user types.
 */
@Composable
fun ResultCard(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    unit: String? = null,
    approx: Boolean = false,
    caption: String? = null,
    error: String? = null,
    onCopy: ((String) -> Unit)? = null,
    copyValue: String? = null,
    onSave: (() -> Unit)? = null,
    /**
     * True when the card is used inside another card. Its content then
     * starts at the same edge as the content around it, instead of being
     * indented a second time.
     */
    nested: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(if (nested) Modifier else Modifier.padding(ContentIndent)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (value != null && onSave != null) {
                    IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.BookmarkAdd,
                            contentDescription = "Save to history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (value != null && onCopy != null) {
                    CopyIconButton(onClick = { onCopy(copyValue ?: value) })
                }
            }
            Spacer(Modifier.size(6.dp))
            when {
                error != null -> Text(
                    error,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                value != null -> SelectionContainer {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (approx) {
                            Text(
                                "≈ ",
                                style = NumberTextStyle.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            value,
                            style = NumberTextStyle.copy(
                                fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (unit != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                unit,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                }
                else -> Text(
                    "—",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (caption != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact label/value line for secondary results. */
@Composable
fun ResultRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    emphasis: Boolean = false,
    onCopy: ((String) -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        // A long value would otherwise take nearly all the width and squeeze the
        // weighted label into one-character-per-line wrapping. Estimate whether
        // the two fit side by side; if not, stack them.
        val charsPerLine = (maxWidth.value / 7.2f).toInt().coerceAtLeast(8)
        val stack = maxWidth < 340.dp || (label.length + value.length) > charsPerLine
        if (stack) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        value,
                        style = if (emphasis) NumberTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodyLarge.fontSize) else NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                        color = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f),
                    )
                    if (unit != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onCopy != null) {
                        CopyIconButton(onClick = { onCopy(value) }, description = "Copy $label")
                    } else {
                        Spacer(Modifier.width(36.dp))
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                )
                Text(
                    value,
                    style = if (emphasis) NumberTextStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.bodyLarge.fontSize) else NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                    color = if (emphasis) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.2f),
                )
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onCopy != null) {
                    CopyIconButton(onClick = { onCopy(value) }, description = "Copy $label")
                } else {
                    Spacer(Modifier.width(36.dp))
                }
            }
        }
    }
}

/** Step-by-step explanation list. */
@Composable
fun StepsCard(
    steps: List<String>,
    modifier: Modifier = Modifier,
    title: String = "Steps",
    /**
     * True when the list is used inside a card. It is then a plain block
     * with a heading, so that it starts at the same edge as the rest of the
     * card's content instead of being a card inside a card.
     */
    nested: Boolean = false,
) {
    if (steps.isEmpty()) return
    val body: @Composable ColumnScope.() -> Unit = {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp),
                )
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (nested) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(PanelGap)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            body()
        }
    } else {
        SectionCard(modifier, title = title) { body() }
    }
}

/** Formula with variable legend. */
@Composable
fun FormulaCard(
    formula: String,
    explanation: String,
    modifier: Modifier = Modifier,
    variables: List<Pair<String, String>> = emptyList(),
) {
    SectionCard(modifier, title = "Formula") {
        FormulaText(formula)
        if (explanation.isNotBlank()) {
            Text(
                explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (variables.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            variables.forEach { (name, meaning) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        name,
                        style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(70.dp),
                    )
                    Text(
                        meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
