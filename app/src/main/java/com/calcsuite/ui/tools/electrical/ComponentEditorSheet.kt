package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.calcsuite.core.Num
import com.calcsuite.engine.electrical.CircuitComponent
import com.calcsuite.engine.electrical.ComponentKind
import com.calcsuite.engine.electrical.ComponentResult
import com.calcsuite.engine.electrical.ElectricalUnits
import com.calcsuite.ui.components.ActionButton
import com.calcsuite.ui.components.ActionRow
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SectionCard
import androidx.compose.material3.Icon

/**
 * The sheet that edits one part.
 *
 * It shows the part's name, the values it can be given, and what the
 * calculation found for it. The values can be left and come back, and
 * nothing is changed until they are applied.
 */
@Composable
fun ComponentEditorSheet(
    component: CircuitComponent,
    result: ComponentResult?,
    onApply: (CircuitComponent) -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember(component.id) { mutableStateOf(component.label) }
    var valueText by remember(component.id) {
        mutableStateOf(
            when (component.kind) {
                ComponentKind.RESISTOR -> component.resistance?.toPlainString() ?: ""
                ComponentKind.VOLTAGE_SOURCE -> component.voltage?.toPlainString() ?: ""
                ComponentKind.CURRENT_SOURCE -> component.current?.toPlainString() ?: ""
                else -> ""
            },
        )
    }
    var prefixId by remember(component.id) {
        mutableStateOf(
            when (component.kind) {
                ComponentKind.RESISTOR -> "none"
                ComponentKind.VOLTAGE_SOURCE -> "none"
                ComponentKind.CURRENT_SOURCE -> "none"
                else -> "none"
            },
        )
    }
    var closed by remember(component.id) { mutableStateOf(component.switchClosed) }
    var ratingText by remember(component.id) {
        mutableStateOf(component.powerRating?.toPlainString() ?: "")
    }

    val unit = when (component.kind) {
        ComponentKind.RESISTOR -> ElectricalUnits.Unit.RESISTANCE
        ComponentKind.VOLTAGE_SOURCE -> ElectricalUnits.Unit.VOLTAGE
        ComponentKind.CURRENT_SOURCE -> ElectricalUnits.Unit.CURRENT
        else -> ElectricalUnits.Unit.VOLTAGE
    }
    val parsed = Num.parseOrNull(valueText)
    val error = if (valueText.isNotBlank() && parsed == null) {
        "Enter a valid number"
    } else if (component.kind == ComponentKind.RESISTOR &&
        parsed != null &&
        parsed.signum() <= 0
    ) {
        "A resistor must have a resistance greater than zero"
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(component.label) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlainField(
                    label = "Name",
                    value = label,
                    onValueChange = { label = it },
                    monospace = true,
                )
                if (component.kind == ComponentKind.SWITCH) {
                    SegmentedChoice(
                        label = "The switch",
                        options = listOf(true to "Closed", false to "Open"),
                        selected = closed,
                        onSelect = { closed = it },
                    )
                } else if (component.kind != ComponentKind.GROUND) {
                    NumberField(
                        label = "Value (${unit.symbol})",
                        value = valueText,
                        onValueChange = { valueText = it },
                        error = error,
                    )
                    SegmentedChoice(
                        label = "SI prefix",
                        options = ElectricalUnits.prefixes.map { it.id to it.shortLabel },
                        selected = prefixId,
                        onSelect = { prefixId = it },
                    )
                }
                if (component.kind == ComponentKind.RESISTOR) {
                    NumberField(
                        label = "Power rating (W)",
                        value = ratingText,
                        onValueChange = { ratingText = it },
                    )
                }
                if (result != null) {
                    Text(
                        "Now: ${result.voltage} V, ${result.current} A, ${result.power} W",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val base = ElectricalUnits.toBaseOrNull(parsed, prefixId)
                    onApply(
                        when (component.kind) {
                            ComponentKind.RESISTOR -> component.copy(
                                label = label,
                                resistance = base,
                                powerRating = Num.parseOrNull(ratingText),
                            )
                            ComponentKind.VOLTAGE_SOURCE -> component.copy(
                                label = label,
                                voltage = base,
                            )
                            ComponentKind.CURRENT_SOURCE -> component.copy(
                                label = label,
                                current = base,
                            )
                            ComponentKind.SWITCH -> component.copy(
                                label = label,
                                switchClosed = closed,
                            )
                            else -> component.copy(label = label)
                        },
                    )
                },
                enabled = error == null,
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRotate) {
                    Icon(Icons.Filled.RotateRight, contentDescription = null)
                    Text("Turn")
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
