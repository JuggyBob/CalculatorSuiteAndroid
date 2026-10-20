package com.calcsuite.ui.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calcsuite.ai.AiImage
import com.calcsuite.ai.tool.AiInterpretationStatus
import com.calcsuite.ai.tool.AiToolRegistry
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.FormulaText
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold

/**
 * The screen where a photograph of a mathematical problem becomes an answer.
 *
 * The picture is shown before anything is sent, so the user can be sure the
 * right picture is being read, and the problem the model saw is shown with
 * the answer, so a wrong reading is caught before it is trusted. The
 * mathematics is done by Calculator Suite itself, never by the model.
 */
@Composable
fun AiSolveScreen(
    viewModel: AiViewModel,
    configured: Boolean,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The reading that is being corrected, when the dialog is open.
    var correcting by remember { mutableStateOf<com.calcsuite.ai.tool.AiInterpretation?>(null) }

    // The photograph is taken with the camera the phone has, so no camera
    // permission is needed and the phone's own camera app does the work.
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { taken ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (taken && uri != null) viewModel.setImage(uri)
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { viewModel.setImage(it) } }

    ToolScaffold(
        title = "AI problem solver",
        subtitle = "Read a problem from a picture",
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "AI settings")
            }
        },
    ) {
        if (!configured) {
            SectionCard(title = "The AI feature is not set up yet") {
                Text(
                    "Choose a provider, enter the model you want and paste the key, " +
                        "and the app can read a photograph of a mathematical problem " +
                        "and work it out with Calculator Suite's own engines.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Set up the AI feature")
                }
            }
            return@ToolScaffold
        }

        SectionCard(title = "The problem") {
            Text(
                "Photograph the problem, or pick a picture of it. The app reads the " +
                    "question and passes it to the right Calculator Suite tool, " +
                    "which does the mathematics itself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val uri = AiCameraUri.create(context)
                        pendingCameraUri = uri
                        camera.launch(uri)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text("Take a photo", Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = {
                        gallery.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text("Pick a picture", Modifier.padding(start = 6.dp))
                }
            }
        }

        state.error?.let { ErrorNotice(it) }

        state.image?.let { image ->
            SectionCard(title = "The picture") {
                PicturePreview(image)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearImage() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Choose another")
                    }
                    Button(
                        onClick = { viewModel.analyse() },
                        enabled = !state.working,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Read the problem")
                    }
                }
            }
        }

        if (state.working) {
            SectionCard(title = "Reading the problem") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text(
                        "The model is reading the picture. The answer is worked out by " +
                            "Calculator Suite itself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.interpretation?.let { interpretation ->
            SectionCard(title = "What the model read") {
                ResultRow(label = "The problem", value = interpretation.recognizedProblem)
                interpretation.toolId?.let { id ->
                    ResultRow(label = "The tool", value = id)
                }
                if (interpretation.status == AiInterpretationStatus.AMBIGUOUS) {
                    InfoNote(
                        interpretation.clarification
                            ?: "The picture is not clear enough to be sure of the problem. " +
                            "Try a closer picture, or type the problem into the tool itself.",
                    )
                }
                if (interpretation.status == AiInterpretationStatus.UNSUPPORTED) {
                    InfoNote(
                        interpretation.clarification
                            ?: "No Calculator Suite tool can work this problem out. " +
                            "It can be done by hand in the tool that suits it.",
                    )
                }
                if (interpretation.status == AiInterpretationStatus.READY &&
                    interpretation.toolId != null
                ) {
                    OutlinedButton(
                        onClick = { correcting = interpretation },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("Correct the inputs")
                    }
                }
            }
        }

        correcting?.let { reading ->
            CorrectInputsDialog(
                toolId = reading.toolId.orEmpty(),
                arguments = reading.arguments,
                onDismiss = { correcting = null },
                onRun = { edited ->
                    correcting = null
                    viewModel.runAgain(reading.toolId.orEmpty(), edited)
                },
            )
        }

        state.outcome?.let { outcome ->
            SectionCard(title = "The answer") {
                ResultCard(
                    label = "The result",
                    value = outcome.headline,
                    approx = outcome.approximate,
                    caption = outcome.method.ifBlank { null },
                    onCopy = { onCopy(outcome.headline) },
                )
                if (outcome.expression.isNotBlank()) {
                    Column {
                        Text(
                            "The expression",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FormulaText(outcome.expression)
                    }
                }
                if (outcome.steps.isNotEmpty()) {
                    StepsCard(steps = outcome.steps, title = "How this was worked out")
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { onCopy(outcome.headline) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Copy the answer")
                    }
                    OutlinedButton(
                        onClick = { viewModel.analyse() },
                        enabled = !state.working,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Solve again")
                    }
                }
            }
        }
    }
}

/**
 * The dialog that lets the user correct what the model read.
 *
 * The arguments of the chosen tool are shown as they are, one field each,
 * so that a wrong reading can be put right before the tool runs again.
 */
@Composable
private fun CorrectInputsDialog(
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

/**
 * The picture the user chose, shown as it will be sent.
 */
@Composable
private fun PicturePreview(image: AiImage) {
    val bitmap = remember(image) { image.bytes.toBitmap() }
    if (bitmap == null) {
        InfoNote("The picture could not be shown, but it will still be sent.")
        return
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "The picture of the mathematical problem",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
