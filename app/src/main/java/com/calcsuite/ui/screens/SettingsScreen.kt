package com.calcsuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.calcsuite.core.Num
import com.calcsuite.core.ThousandsSeparator
import com.calcsuite.data.Settings
import com.calcsuite.data.ToolRegistry
import com.calcsuite.expr.AngleMode
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SettingRow
import com.calcsuite.ui.components.ToggleRow
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.theme.ThemeMode
import java.math.BigDecimal

/** Renders the same 1 234 567.89 the app would show, for the settings preview. */
private fun sampleNumber(separator: ThousandsSeparator, decimalMark: Char): String {
    val seps = Num.Sep(decimalMark, separator.resolve(decimalMark))
    return Num.format(BigDecimal("1234567.89"), 12, seps)
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
    onClearHistory: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

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

            Text(
                "Calc Suite 1.0 · ${ToolRegistry.all.size} tools across 7 categories",
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
}
