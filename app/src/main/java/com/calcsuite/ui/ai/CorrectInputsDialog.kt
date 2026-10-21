package com.calcsuite.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.calcsuite.ai.tool.AiToolRegistry

/**
 * The dialog that lets the user correct what the model read.
 *
 * The arguments of the chosen tool are shown as they are, one field each, so
 * that a wrong reading can be put right before the tool runs again. The
 * values that come back are the ones the engine is given.
 */
@Composable
fun CorrectInputsDialog(
    toolId: String,
    arguments: Map<String, String>,
    onDismiss: () -> Unit,
    onRun: (Map<String, String>) -> Unit,
) {
    val spec = AiToolRegistry.byId(toolId)
    val names = spec?.arguments?.map { it.name } ?: arguments.keys.toList()
    var values by remember(toolId, arguments) {
        mutableStateOf(names.associateWith { arguments[it].orEmpty() })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec?.title ?: "The inputs") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Check what was read from the picture. The tool runs again with " +
                        "what is written here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (argument in spec?.arguments.orEmpty()) {
                    OutlinedTextField(
                        value = values[argument.name].orEmpty(),
                        onValueChange = { values = values + (argument.name to it) },
                        label = { Text(argument.name) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onRun(values) }) {
                Text("Run it")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
