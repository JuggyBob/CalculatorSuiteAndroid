package com.calcsuite.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.calcsuite.ui.components.ActionButton
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SettingRow
import com.calcsuite.updater.NoteBlock
import com.calcsuite.updater.NoteSpan
import com.calcsuite.updater.ReleaseInfo
import com.calcsuite.updater.ReleaseNotes
import com.calcsuite.updater.formatPublishedAt

/**
 * The card in the settings that checks for a new release.
 *
 * It shows one line of status at a time and the button that follows from it,
 * so that the flow of the update is visible without any extra screens.
 */
@Composable
fun UpdateCard(
    state: UpdateUiState,
    installedVersion: String,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier, title = "Updates") {
        SettingRow(
            label = "Check for updates",
            description = statusLine(state, installedVersion),
        ) {
            TextButton(
                onClick = onCheck,
                enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading,
            ) {
                Text(if (state is UpdateUiState.Checking) "Checking…" else "Check")
            }
        }

        when (state) {
            is UpdateUiState.Downloading -> {
                val total = state.total
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { (state.written.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    if (total > 0) {
                        "Downloading ${megabytes(state.written)} of ${megabytes(total)}"
                    } else {
                        "Downloading ${megabytes(state.written)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionButton("Cancel the download", onClick = onCancel)
            }

            is UpdateUiState.Available -> {
                ActionButton(
                    "Download the update",
                    onClick = onDownload,
                    emphasised = true,
                )
                if (state.release.htmlUrl.isNotEmpty()) {
                    ActionButton(
                        "Open the release page",
                        onClick = { onOpenReleasePage(state.release.htmlUrl) },
                    )
                }
            }

            is UpdateUiState.Ready -> {
                ActionButton("Install the update", onClick = onInstall, emphasised = true)
                InfoNote(
                    "Android will ask you to confirm the installation. The app cannot install " +
                        "anything by itself.",
                )
            }

            is UpdateUiState.NoPackage -> {
                InfoNote(
                    "The release was published, but it has no package that can be installed " +
                        "without guessing which one is meant. The release page has the files.",
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                if (state.release.htmlUrl.isNotEmpty()) {
                    ActionButton(
                        "Open the release page",
                        onClick = { onOpenReleasePage(state.release.htmlUrl) },
                    )
                }
            }

            is UpdateUiState.Failed -> {
                InfoNote(state.message, tint = MaterialTheme.colorScheme.error)
            }

            else -> Unit
        }
    }
}

/**
 * The About screen: the version of the app, the newest release and its notes.
 *
 * The notes come from the release on GitHub, so they always say what the
 * release really says. When there is no connection the notes of the last
 * release that was read are shown instead, and the screen says so.
 */
@Composable
fun AboutDialog(
    installedVersion: String,
    release: ReleaseInfo?,
    fromCache: Boolean,
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About Calc Suite") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Installed version: $installedVersion",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val latest = release?.version?.text
                if (latest != null) {
                    val published = formatPublishedAt(release.publishedAt)
                    Text(
                        if (published != null) {
                            "Latest release: $latest · published $published"
                        } else {
                            "Latest release: $latest"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "The latest release could not be read.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (fromCache) {
                    InfoNote(
                        "These are the notes of the last release that was read on this device.",
                    )
                }
                when (state) {
                    is UpdateUiState.UpToDate -> Text(
                        "You have the newest version.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is UpdateUiState.Available -> Text(
                        "Version ${state.release.version?.text ?: ""} is available to download.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is UpdateUiState.Downloading -> Text(
                        "Downloading the update…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is UpdateUiState.Ready -> Text(
                        "The update is ready to install.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is UpdateUiState.NoPackage -> Text(
                        "The newest release has no package that can be installed from here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    is UpdateUiState.Failed -> Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> Unit
                }

                if (release != null && release.body.isNotBlank()) {
                    Text(
                        "What's new",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ReleaseNoteBlocks(release.body)
                } else if (release != null) {
                    Text(
                        "This release has no notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateUiState.Available -> ActionButton("Download", onClick = onDownload)
                is UpdateUiState.Downloading -> ActionButton("Cancel", onClick = onCancel)
                is UpdateUiState.Ready -> ActionButton("Install", onClick = onInstall)
                is UpdateUiState.UpToDate -> Unit
                else -> ActionButton("Check for updates", onClick = onCheck)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (release != null && release.htmlUrl.isNotEmpty()) {
                    TextButton(onClick = { onOpenReleasePage(release.htmlUrl) }) {
                        Text("Release page")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/**
 * Draws the notes of a release.
 *
 * Only the small subset of Markdown that a release note uses is read, and
 * everything is drawn as ordinary text: nothing in the notes can run.
 */
@Composable
private fun ReleaseNoteBlocks(body: String) {
    val blocks = remember(body) { ReleaseNotes.parse(body) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (block in blocks) {
            when (block) {
                is NoteBlock.Heading -> Text(
                    block.spans.toAnnotated(),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleMedium
                        2 -> MaterialTheme.typography.titleSmall
                        else -> MaterialTheme.typography.labelLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                )
                is NoteBlock.Bullet -> Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = (block.level * 12).dp),
                    )
                    Text(
                        block.spans.toAnnotated(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is NoteBlock.Paragraph -> Text(
                    block.spans.toAnnotated(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The styled text of a piece of a note. */
private fun List<NoteSpan>.toAnnotated(): AnnotatedString = buildAnnotatedString {
    for (span in this@toAnnotated) {
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
        )
        withStyle(style) { append(span.text) }
    }
}

/** The one line that says what the updater is doing. */
private fun statusLine(state: UpdateUiState, installedVersion: String): String = when (state) {
    is UpdateUiState.Idle -> "See whether a newer version has been published on GitHub."
    is UpdateUiState.Checking -> "Looking for a newer release…"
    is UpdateUiState.UpToDate -> "You have the newest version ($installedVersion)."
    is UpdateUiState.Available -> {
        val version = state.release.version?.text ?: "A newer version"
        "Version $version is available."
    }
    is UpdateUiState.Downloading -> "Downloading the update…"
    is UpdateUiState.Ready -> "The update is ready to install."
    is UpdateUiState.NoPackage -> {
        val version = state.release.version?.text ?: "A newer version"
        "Version $version is published, but it has no package that can be installed from here."
    }
    is UpdateUiState.Failed -> state.message
}

/** A size in megabytes, for the progress of the download. */
private fun megabytes(bytes: Long): String {
    val value = bytes.toDouble() / (1024.0 * 1024.0)
    return if (value >= 10.0) "%.0f MB".format(value) else "%.1f MB".format(value)
}
