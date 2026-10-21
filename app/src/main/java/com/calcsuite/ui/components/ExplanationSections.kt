package com.calcsuite.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.electrical.ExplanationStep
import com.calcsuite.engine.electrical.FormulaVariable

/**
 * The sections of an explanation.
 *
 * Every explanation in the app is shown the same way: the answer first, then
 * the formula and what its symbols mean, then the working, then why the
 * formula applies, and then the check. The longer sections start folded so
 * that the answer can be read at a glance, and each one opens and closes on
 * its own.
 */
@Composable
fun ExplanationSections(
    title: String,
    value: String,
    meaning: String,
    formula: String,
    variables: List<FormulaVariable>,
    steps: List<ExplanationStep>,
    why: String,
    checkTitle: String,
    checks: List<ExplanationStep>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PanelGap)) {
        SectionCard(title = "Result and meaning") {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (formula.isNotBlank()) {
            ExpandablePanel(title = "Formula and variables", initiallyExpanded = true) {
                FormulaText(formula)
                if (variables.isNotEmpty()) {
                    Text(
                        "Where",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    for (variable in variables) {
                        VariableRow(variable)
                    }
                }
            }
        }

        if (steps.isNotEmpty()) {
            ExpandablePanel(title = "Step-by-step calculation") {
                for (step in steps) {
                    ExplanationStepBlock(step)
                }
            }
        }

        if (why.isNotBlank()) {
            ExpandablePanel(title = "Why this formula applies") {
                Text(
                    why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (checks.isNotEmpty()) {
            ExpandablePanel(title = checkTitle) {
                for (check in checks) {
                    ExplanationStepBlock(check)
                }
            }
        }
    }
}

/**
 * One variable of a formula: what it is, the value that was used and the
 * unit it is in.
 */
@Composable
fun VariableRow(variable: FormulaVariable) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            "${variable.symbol} — ${variable.meaning}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Used: ${variable.value} ${variable.unit}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One step of a calculation: what is worked out, the formula, the values
 * that go into it, the arithmetic and the answer.
 */
@Composable
fun ExplanationStepBlock(step: ExplanationStep) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            step.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        step.formula?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
            )
        }
        step.substitution?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
        step.calculation?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
        step.result?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
        step.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}
