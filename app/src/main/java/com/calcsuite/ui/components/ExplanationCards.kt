package com.calcsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.advanced.Explanation

/**
 * The explanation of a result, shown as collapsible cards.
 *
 * The sections are the same for every advanced tool, so a reader learns
 * where to look: the definition and the formula first, then the conditions
 * the method needs, then the working, and finally what the result means and
 * what it does not cover.
 *
 * Every part comes from the calculation that produced the result, so the
 * explanation and the numbers cannot disagree.
 */
@Composable
fun ExplanationCards(
    explanation: Explanation,
    modifier: Modifier = Modifier,
    /** The title of the working card. */
    stepsTitle: String = "Step-by-step work",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ScreenGap)) {
        if (explanation.definition != null) {
            ExpandablePanel(title = "Definition") {
                Text(
                    explanation.definition,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (explanation.formula != null) {
            ExpandablePanel(title = "Formula") {
                FormulaText(explanation.formula)
                if (explanation.symbols.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 6.dp))
                    for ((symbol, meaning) in explanation.symbols) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                symbol,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(140.dp),
                            )
                            Text(
                                meaning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (explanation.conditions.isNotEmpty()) {
            ExpandablePanel(title = "Conditions") {
                for (condition in explanation.conditions) {
                    Text(
                        "• $condition",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }

        if (explanation.method != null) {
            ExpandablePanel(title = "Method") {
                Text(
                    explanation.method,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (explanation.steps.isNotEmpty()) {
            ExpandablePanel(title = stepsTitle) {
                StepsCard(steps = explanation.steps, title = "Working")
            }
        }

        if (explanation.interpretation != null) {
            ExpandablePanel(title = "Interpretation") {
                Text(
                    explanation.interpretation,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (explanation.verification != null) {
            ExpandablePanel(title = "Verification") {
                Text(
                    explanation.verification,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (explanation.limitations != null) {
            ExpandablePanel(title = "Limitations") {
                InfoNote(explanation.limitations)
            }
        }
    }
}
