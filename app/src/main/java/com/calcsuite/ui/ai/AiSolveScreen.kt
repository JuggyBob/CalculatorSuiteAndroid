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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.calcsuite.ai.tool.AiToolInput
import com.calcsuite.ai.tool.AiToolRegistry
import com.calcsuite.ui.components.ActionButton
import com.calcsuite.ui.components.ActionRow
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
 * right picture is being read. The answer is presented as a piece of
 * mathematics — the problem that was submitted, the method, the working and
 * then the answer — so that the result can be checked against the work
 * that produced it.
 */
@Composable
fun AiSolveScreen(
    viewModel: AiViewModel,
    configured: Boolean,
    onOpenSettings: () -> Unit,
    onOpenTool: (String, Map<String, String>) -> Unit,
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
                ActionButton(
                    text = "Set up the AI feature",
                    onClick = onOpenSettings,
                    emphasised = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
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
            // The two actions are one above the other, so that each label
            // has the whole width of the card and neither has to wrap.
            ActionButton(
                text = "Take a photo",
                onClick = {
                    val uri = AiCameraUri.create(context)
                    pendingCameraUri = uri
                    camera.launch(uri)
                },
                icon = Icons.Filled.PhotoCamera,
                emphasised = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            ActionButton(
                text = "Pick a picture",
                onClick = {
                    gallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                icon = Icons.Filled.PhotoLibrary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        state.error?.let { ErrorNotice(it) }

        state.image?.let { image ->
            SectionCard(title = "The picture") {
                PicturePreview(image)
                ActionRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    ActionButton(
                        text = "Choose another",
                        onClick = { viewModel.clearImage() },
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        text = "Read the problem",
                        onClick = { viewModel.analyse() },
                        enabled = !state.working,
                        emphasised = true,
                        modifier = Modifier.weight(1f),
                    )
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
                    ActionButton(
                        text = "Correct the inputs",
                        onClick = { correcting = interpretation },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // The answer, presented as a piece of mathematics: the problem that
        // was submitted, the method, the working, and then the answer.
        state.confirmed?.let { confirmed ->
            ConfirmedProblem(confirmed)
        }

        state.outcome?.let { outcome ->
            SectionCard(title = "The solution") {
                if (outcome.steps.isNotEmpty()) {
                    StepsCard(
                        steps = outcome.steps,
                        title = "Step by step",
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                ResultCard(
                    label = "The answer",
                    value = outcome.headline,
                    approx = outcome.approximate,
                    caption = outcome.method.ifBlank { null },
                    onCopy = { onCopy(outcome.headline) },
                )
                if (outcome.expression.isNotBlank() &&
                    outcome.expression != state.confirmed?.arguments?.values?.firstOrNull()
                ) {
                    Column {
                        Text(
                            "The engine's own form",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FormulaText(outcome.expression)
                    }
                }
                ActionRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    ActionButton(
                        text = "Copy the answer",
                        onClick = { onCopy(outcome.headline) },
                        icon = Icons.Filled.ContentCopy,
                        modifier = Modifier.weight(1f),
                    )
                    ActionButton(
                        text = "Solve again",
                        onClick = { viewModel.analyse() },
                        icon = Icons.Filled.Refresh,
                        enabled = !state.working,
                        modifier = Modifier.weight(1f),
                    )
                }
                val confirmed = state.confirmed
                if (confirmed != null) {
                    ActionButton(
                        text = "Open in the calculator",
                        onClick = { onOpenTool(confirmed.toolId, confirmed.arguments) },
                        icon = Icons.Filled.OpenInNew,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
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
    }
}

/**
 * The problem that was submitted, shown as the tool received it.
 *
 * This is the same set of arguments the engine was given, so what is shown
 * here is exactly what was worked out.
 */
@Composable
private fun ConfirmedProblem(confirmed: AiToolInput) {
    val spec = AiToolRegistry.byId(confirmed.toolId)
    val rows = spec?.let { confirmed.meaningfulArguments(it) } ?: emptyList()
    if (rows.isEmpty() && spec == null) return
    SectionCard(title = "The problem submitted") {
        spec?.let {
            ResultRow(label = "The tool", value = it.title)
        }
        for ((name, value) in rows) {
            ResultRow(label = name, value = value)
        }
    }
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
