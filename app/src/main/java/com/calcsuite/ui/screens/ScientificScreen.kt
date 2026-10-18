package com.calcsuite.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.expr.Functions
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.theme.NumberTextStyle

@Composable
fun ScientificScreen(
    onBack: () -> Unit,
    onCycleAngle: () -> Unit,
    onOpenHistory: () -> Unit,
    onRecord: (String, String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ToolScaffold(
        title = "Scientific calculator",
        subtitle = if (expanded) null else "Functions, constants, memory and history",
        onBack = onBack,
        // weight() has no effect inside a vertically scrolling Column, so the
        // expanded calculator needs an unbounded (non-scrolling) column.
        scrollable = !expanded,
        actions = {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (expanded) "Collapse calculator" else "Expand calculator",
                )
            }
        },
    ) {
        CalculatorPanel(
            scientific = true,
            onCycleAngleMode = onCycleAngle,
            onRecord = onRecord,
            onOpenHistory = onOpenHistory,
            modifier = if (expanded) Modifier.weight(1f) else Modifier,
        )

        if (!expanded) {
            ExpandablePanel(title = "Functions on the keypad") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Functions.KEYPAD.entries.chunked(3).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { (name, keyLabel) ->
                                Text(
                                    "$keyLabel  ($name)",
                                    style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    InfoNote(
                        "The 2nd key switches the same row to the second function " +
                            "(sin ↔ sin⁻¹, x² ↔ x³, ln ↔ eˣ, log ↔ 10ˣ, √ ↔ ∛).",
                    )
                }
            }

            ExpandablePanel(title = "Functions you can type") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "These have no dedicated key — type the name in the expression field.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Functions.ALL
                        .filter { it.name in Functions.TYPED_ONLY }
                        .forEach { fn ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    fn.signature,
                                    style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    fn.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1.2f),
                                )
                            }
                        }
                }
            }

            ExpandablePanel(title = "Operators & syntax") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Functions.OPERATORS.forEach { (symbol, usage, meaning) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                symbol,
                                style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Column(Modifier.weight(1.6f)) {
                                Text(
                                    usage,
                                    style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    meaning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    InfoNote("Constants: π (p), e, τ, φ — for example 2π or 1e3.")
                    InfoNote("^ is right-associative and stronger than unary minus: −2^2 = −4.")
                    InfoNote("Arithmetic on decimals is exact; trigonometry and logarithms use high-precision floating point (≈ marks inexact results).")
                    InfoNote("Factorials, combinations and integer powers are computed exactly with big integers (factorial up to 10000!).")
                }
            }
        }
    }
}
