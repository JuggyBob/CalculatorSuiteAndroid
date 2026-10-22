package com.calcsuite.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcsuite.core.Num
import com.calcsuite.core.ThousandsSeparator
import com.calcsuite.data.Settings
import com.calcsuite.data.ToolCategory
import com.calcsuite.data.ToolRegistry
import com.calcsuite.expr.AngleMode
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.clickableRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SettingRow
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.theme.Accent
import com.calcsuite.ui.theme.AccentPreset
import com.calcsuite.ui.theme.ThemeMode
import com.calcsuite.ui.update.AboutDialog
import com.calcsuite.ui.update.InstallOutcome
import com.calcsuite.ui.update.UpdateCard
import com.calcsuite.ui.update.UpdateUiState
import com.calcsuite.ui.update.UpdateViewModel
import com.calcsuite.updater.ReleaseInfo
import java.io.File
import java.math.BigDecimal

/** Renders the same 1 234 567.89 the app would show, for the settings preview. */
private fun sampleNumber(separator: ThousandsSeparator, decimalMark: Char): String {
    val seps = Num.Sep(decimalMark, separator.resolve(decimalMark))
    return Num.format(BigDecimal("1234567.89"), 12, seps)
}

/**
 * Opens a page of the web in whatever the phone uses for that.
 *
 * A phone without a browser has nothing to show the page in, so the failure
 * is passed over rather than taken as an error of the app.
 */
private fun openPage(context: Context, address: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, address.toUri()))
    } catch (e: Exception) {
        // There is nothing on the phone that can show a web page.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    historyCount: Int,
    onBack: () -> Unit,
    onSetTheme: (ThemeMode) -> Unit,
    onSetAngle: (AngleMode) -> Unit,
    onSetPrecision: (Int) -> Unit,
    onSetGrouping: (Boolean) -> Unit,
    onSetThousandsSeparator: (ThousandsSeparator) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    /** The accent the user has chosen, and the way to change it. */
    accent: Accent = Accent.DEFAULT,
    onSetAccent: (Accent) -> Unit = {},
    onClearHistory: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    // The updater, and the About screen that shows the notes of the newest
    // release.
    val context = LocalContext.current
    val updater: UpdateViewModel = viewModel()
    val updateState by updater.state.collectAsStateWithLifecycle()
    val installedVersion = remember { updater.installedVersion() }
    var showAbout by remember { mutableStateOf(false) }
    var cachedRelease by remember { mutableStateOf<ReleaseInfo?>(null) }

    // The release that is on show: the one that was just read, or the last one
    // that was read on this device, so that the notes are there offline too.
    val freshRelease: ReleaseInfo? = when (val state = updateState) {
        is UpdateUiState.Available -> state.release
        is UpdateUiState.Downloading -> state.release
        is UpdateUiState.Ready -> state.release
        is UpdateUiState.NoPackage -> state.release
        is UpdateUiState.UpToDate -> state.release
        else -> null
    }
    val aboutRelease: ReleaseInfo? = freshRelease ?: cachedRelease
    val notesAreCached = freshRelease == null && cachedRelease != null

    // When the About screen opens, the notes of the last release that was read
    // are shown until a check brings newer ones.
    LaunchedEffect(showAbout) {
        if (showAbout && cachedRelease == null) {
            cachedRelease = updater.cachedRelease()
        }
    }

    /** Opens the installer for a downloaded update. */
    fun installUpdate(file: File) {
        when (val outcome = updater.install(file)) {
            is InstallOutcome.Started -> {
                try {
                    context.startActivity(updater.installIntent(file))
                } catch (e: Exception) {
                    updater.reportProblem("The installer could not be opened on this phone.")
                }
            }
            is InstallOutcome.NeedsPermission -> {
                try {
                    context.startActivity(updater.unknownSourcesIntent())
                } catch (e: Exception) {
                    updater.reportProblem("The settings screen of the phone could not be opened.")
                }
            }
            is InstallOutcome.Refused -> updater.reportProblem(outcome.message)
        }
    }

    // The same decimal mark the rest of the app displays with, so the warning
    // below can never disagree with what is on screen.
    val decimalMark = rememberFmt().decimalSeparator

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Appearance") {
                SegmentedChoice(
                    label = "Theme",
                    options = listOf(
                        ThemeMode.System to "System",
                        ThemeMode.Light to "Light",
                        ThemeMode.Dark to "Dark",
                    ),
                    selected = settings.themeMode,
                    onSelect = onSetTheme,
                )
                ToggleRow(
                    label = "Use system colours",
                    description = "Follow the Android wallpaper palette (Android 12+)",
                    checked = settings.dynamicColor,
                    onCheckedChange = onSetDynamicColor,
                )
                AccentRow(accent = accent, onSetAccent = onSetAccent)
            }

            SectionCard(title = "Calculation") {
                SegmentedChoice(
                    label = "Default angle unit",
                    options = listOf(
                        AngleMode.DEG to "Degrees",
                        AngleMode.RAD to "Radians",
                        AngleMode.GRAD to "Gradians",
                    ),
                    selected = settings.angleMode,
                    onSelect = onSetAngle,
                )
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "Result precision",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${settings.precision} significant digits",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.precision.toFloat(),
                        onValueChange = { onSetPrecision(it.toInt()) },
                        valueRange = Num.MIN_PRECISION.toFloat()..Num.MAX_PRECISION.toFloat(),
                        steps = Num.MAX_PRECISION - Num.MIN_PRECISION - 1,
                    )
                }
                ToggleRow(
                    label = "Thousands separators",
                    description = "Group digits in large numbers for easier reading",
                    checked = settings.grouping,
                    onCheckedChange = onSetGrouping,
                )
                SegmentedChoice(
                    label = "Separator style",
                    options = ThousandsSeparator.entries.map { it to it.label },
                    selected = settings.thousandsSeparator,
                    onSelect = onSetThousandsSeparator,
                    modifier = if (settings.grouping) Modifier else Modifier.alpha(0.4f),
                )
                if (settings.grouping) {
                    Text(
                        "Large numbers now read " + sampleNumber(settings.thousandsSeparator, decimalMark),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoNote(
                    "Affects how results are displayed only. You can still type, paste and " +
                        "calculate with 1,234.56 or 1 234,56 whichever way you like — the decimal " +
                        "point always follows your device's region.",
                )
                // A separator identical to the decimal mark makes a grouped decimal
                // ambiguous to retype. Warn, but keep the choice.
                if (settings.grouping &&
                    settings.thousandsSeparator.resolve(decimalMark) == decimalMark
                ) {
                    InfoNote(
                        "Your region uses \"$decimalMark\" as the decimal point, which is also the " +
                            "separator you picked. A grouped decimal such as 1${decimalMark}234" +
                            "${decimalMark}56 cannot be typed back in unambiguously — pick another " +
                            "separator if you retype results often.",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
                ToggleRow(
                    label = "Haptic feedback",
                    description = "Vibrate slightly when pressing calculator keys",
                    checked = settings.haptics,
                    onCheckedChange = onSetHaptics,
                )
            }

            SectionCard(title = "History") {
                SettingRow(
                    label = "Saved calculations",
                    description = "History never leaves your device",
                ) {
                    Text("$historyCount", style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = historyCount > 0,
                ) { Text("Clear all history") }
            }

            SectionCard(title = "Data sources") {
                InfoNote(
                    "Currency conversion uses daily reference rates from open.er-api.com, cached on this " +
                        "device for offline use. Reference rates may differ from the rates, fees and " +
                        "spreads applied by banks, card issuers or exchange services.",
                )
                InfoNote(
                    "All other calculations run entirely on your device.",
                )
            }

            UpdateCard(
                state = updateState,
                installedVersion = installedVersion,
                onCheck = { updater.check() },
                onDownload = { updater.download() },
                onCancel = { updater.cancelDownload() },
                onInstall = {
                    val state = updateState
                    if (state is UpdateUiState.Ready) installUpdate(state.file)
                },
                onOpenReleasePage = { openPage(context, it) },
            )

            SectionCard(title = "About") {
                SettingRow(
                    label = "About Calc Suite",
                    description = "The installed version, the newest release and what changed in it",
                ) {
                    TextButton(onClick = { showAbout = true }) { Text("Open") }
                }
            }

            Text(
                "Calc Suite $installedVersion \u00b7 ${ToolRegistry.all.size} tools across " +
                    "${ToolCategory.entries.size} categories",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("This permanently deletes $historyCount saved calculations.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }

    if (showAbout) {
        AboutDialog(
            installedVersion = installedVersion,
            release = aboutRelease,
            fromCache = notesAreCached,
            state = updateState,
            onCheck = { updater.check() },
            onDownload = { updater.download() },
            onCancel = { updater.cancelDownload() },
            onInstall = {
                val state = updateState
                if (state is UpdateUiState.Ready) installUpdate(state.file)
            },
            onOpenReleasePage = { openPage(context, it) },
            onDismiss = { showAbout = false },
        )
    }
}

/**
 * The accent colour: a row of swatches for the presets, and a button that
 * opens the picker for a colour of the user's own choosing.
 */
@Composable
private fun AccentRow(
    accent: Accent,
    onSetAccent: (Accent) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }

    Column {
        Text(
            "Accent colour",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            "The colour used for buttons, highlights and the curves in the graphs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (preset in AccentPreset.entries) {
                val selected = accent.preset == preset && !accent.isCustom
                AccentSwatch(
                    colour = preset.colour,
                    selected = selected,
                    label = preset.label,
                    onClick = { onSetAccent(Accent(preset = preset)) },
                )
            }
            // The custom colour, shown with the colour the user chose.
            AccentSwatch(
                colour = accent.colour,
                selected = accent.isCustom,
                label = if (accent.isCustom) "Custom colour" else "Custom colour",
                onClick = { picking = true },
            )
        }
        if (accent.isCustom) {
            TextButton(
                onClick = { onSetAccent(Accent(preset = AccentPreset.DEFAULT)) },
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Back to the default green")
            }
        }
    }

    if (picking) {
        AccentPickerSheet(
            initial = accent.colour,
            onApply = {
                onSetAccent(Accent(preset = null, custom = it))
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * One round swatch, with a ring around it when it is the one in use.
 */
@Composable
private fun AccentSwatch(
    colour: Color,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colour)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    },
                )
                .clickableRow { onClick() }
                .semantics {
                    contentDescription = if (selected) "$label, in use" else label
                },
        )
    }
}
