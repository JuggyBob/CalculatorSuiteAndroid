package com.calcsuite.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.calcsuite.ai.AiClient
import com.calcsuite.ai.AiProvider
import com.calcsuite.ai.AiSettings
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.ToolScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the AI feature is set up.
 *
 * The provider, the model and the key are all the user's own, and the key is
 * kept by the phone's keystore rather than in the app's settings.
 */
@Composable
fun AiSettingsScreen(
    settings: AiSettings,
    onSetProvider: (AiProvider) -> Unit,
    onSetModel: (String) -> Unit,
    onSetApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: suspend (AiProvider, String, String) -> String?,
    onBack: () -> Unit,
    scope: CoroutineScope,
) {
    var model by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var keyShown by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    val provider = settings.provider

    ToolScaffold(
        title = "AI settings",
        subtitle = "The provider, the model and the key",
        onBack = onBack,
    ) {
        SectionCard(title = "The provider") {
            // There are ten providers, so they are picked from the app's own
            // searchable list rather than from a row of buttons.
            SelectField(
                label = "Who answers",
                options = AiProvider.entries.map { Option(it.id, it.label) },
                selected = provider.id,
                onSelect = { id ->
                    AiProvider.byId(id)?.let(onSetProvider)
                    testResult = null
                    testError = null
                },
                searchPlaceholder = "Search the providers",
            )
            Text(
                provider.visionNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionCard(title = "The model") {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model name") },
                placeholder = { Text("for example gpt-4o") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "The name is used exactly as it is typed, so any model the provider " +
                    "offers can be used, including one that is new.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedButton(
                onClick = { onSetModel(model) },
                enabled = model.isNotBlank() && model != settings.model,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Save the model")
            }
        }

        SectionCard(title = "The key") {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (keyShown) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyShown = !keyShown }) {
                        Icon(
                            if (keyShown) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (keyShown) {
                                "Hide the key"
                            } else {
                                "Show the key"
                            },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (settings.hasKey) {
                    "A key is saved for ${provider.label}. Type a new one to replace it."
                } else {
                    "The key is kept by the phone's keystore, not in the app's settings."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        onSetApiKey(apiKey)
                        apiKey = ""
                        testResult = null
                        testError = null
                    },
                    enabled = apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save the key")
                }
                OutlinedButton(
                    onClick = {
                        onClearApiKey()
                        apiKey = ""
                        testResult = null
                    },
                    enabled = settings.hasKey,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Remove the key")
                }
            }
        }

        SectionCard(title = "The connection") {
            Text(
                "A short message is sent to the provider to check the key and the " +
                    "model. It costs almost nothing, and it tells you straight away " +
                    "whether the pair works.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    testing = true
                    testResult = null
                    testError = null
                    scope.launch {
                        val message = withContext(Dispatchers.IO) {
                            onTestConnection(provider, model, apiKey)
                        }
                        testResult = message
                        testError = null
                        testing = false
                    }
                },
                enabled = !testing && model.isNotBlank(),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(if (testing) "Testing…" else "Test the connection")
            }
            testResult?.let {
                ResultRow(label = "The connection", value = it)
            }
            testError?.let { ErrorNotice(it) }
        }

        SectionCard(title = "What the app sends") {
            Text(
                "The picture and the question are sent only to " +
                    "${provider.label}, the provider you chose. No other service " +
                    "sees them, and nothing is sent until you press the button.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "The model is asked to read the problem and to choose a tool. " +
                    "Calculator Suite's own engines then do the mathematics, so " +
                    "the answer is the app's answer and not the model's.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            InfoNote(
                "The model must be one that reads pictures. ${provider.visionNote}",
            )
        }
    }
}
