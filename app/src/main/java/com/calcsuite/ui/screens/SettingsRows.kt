package com.calcsuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.clickableRow

/**
 * The icon at the start of a settings row.
 *
 * It is decorative: the label beside it is the accessibility label. Adding an
 * icon therefore never changes what a screen reader announces for a control
 * that already existed.
 */
@Composable
private fun SettingsLeadingIcon(
    icon: ImageVector,
    tint: Color,
    size: Dp = 24.dp,
) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .padding(end = 16.dp)
            .size(size),
    )
}

/**
 * A settings row with a leading icon, a label, an optional description and a
 * trailing control. The whole row is clickable when [onClick] is supplied.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickableRow(onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLeadingIcon(icon, iconTint)
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

/**
 * Toggle row with a leading icon. The switch and its callback behave exactly
 * as in the shared `ToggleRow`; only the icon is added.
 */
@Composable
internal fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    SettingsRow(
        icon = icon,
        label = label,
        description = description,
        modifier = modifier,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}

/**
 * Segmented choice with a leading icon above it. The choice itself is the
 * shared [SegmentedChoice], so its options, selection and radio-button
 * semantics are unchanged; only an icon is drawn beside its label.
 */
@Composable
internal fun <T> SettingsChoiceRow(
    icon: ImageVector,
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsLeadingIcon(
                icon = icon,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 20.dp,
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        SegmentedChoice(
            options = options,
            selected = selected,
            onSelect = onSelect,
        )
    }
}

/**
 * Labelled control with a leading icon, an optional current-value caption and
 * a full-width control beneath it (used by the precision slider).
 */
@Composable
internal fun SettingsControlRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    control: @Composable () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsLeadingIcon(icon = icon, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        control()
    }
}
