package com.calcsuite.ui.tools.algebra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.algebra.inequality.InequalityMode
import com.calcsuite.engine.algebra.inequality.InequalityResult
import com.calcsuite.engine.algebra.inequality.InequalitySolver
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import com.calcsuite.ui.tools.prefill

/**
 * The Inequalities tool.
 *
 * The screen keeps the input, the mode and the result in one place, and
 * recomputes as soon as anything changes, so a result on screen always
 * belongs to the expression that is on screen.
 */
@Composable
fun InequalityToolScreen(args: ToolArgs) {
    // The expressions are held one by one, so that a system of them can be
    // written without needing a new line in the keyboard.
    val expressions = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(
            save = { ArrayList(it) },
            restore = { mutableStateListOf<String>().also { list -> list.addAll(it) } },
        ),
    ) {
        mutableStateListOf(args.prefill("expression", "inequality") ?: "2x + 3 < 11")
    }
    var modeName by rememberSaveable { mutableStateOf(InequalityMode.SINGLE.name) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var probeX by rememberSaveable { mutableStateOf("") }
    var probeY by rememberSaveable { mutableStateOf("") }

    val mode = InequalityMode.entries.firstOrNull { it.name == modeName } ?: InequalityMode.SINGLE
    val accent = MaterialTheme.colorScheme.primary
    val poleColor = MaterialTheme.colorScheme.error

    // The solver reads one expression per line, so the fields are joined
    // with a new line.
    val expression = expressions.joinToString("\n")
    val result = remember(expression, modeName) {
        if (expression.isBlank()) null else InequalitySolver.solve(expression, mode)
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        scrollable = !expanded,
        actions = {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
        },
    ) {
        if (!expanded) {
            SectionCard(title = "Inequality") {
                // A system is written as one expression per field, so the
                // keyboard never has to produce a new line.
                val shown = if (mode == InequalityMode.SYSTEM) {
                    expressions.size.coerceAtLeast(2)
                } else {
                    1
                }
                for (index in 0 until shown) {
                    if (index >= expressions.size) expressions.add("")
                    PlainField(
                        label = if (shown == 1) "Expression" else "Expression ${index + 1}",
                        value = expressions.getOrElse(index) { "" },
                        onValueChange = { expressions[index] = it },
                        placeholder = if (index == 0) "2x + 3 < 11" else "x - 4 > 0",
                        monospace = true,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    )
                }
                if (mode == InequalityMode.SYSTEM) {
                    TextButton(onClick = { expressions.add("") }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add expression")
                    }
                }
                SegmentedChoice(
                    label = "Mode",
                    options = InequalityMode.entries.map { it to it.label },
                    selected = mode,
                    onSelect = { modeName = it.name },
                )
                Text(
                    mode.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val example = EXAMPLES[mode] ?: "2x + 3 < 11"
                        val lines = example.split("\n").filter { it.isNotBlank() }
                        expressions.clear()
                        expressions.addAll(if (lines.isEmpty()) listOf(example) else lines)
                    }) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Example")
                    }
                    TextButton(onClick = {
                        expressions.clear()
                        expressions.add("")
                        probeX = ""
                        probeY = ""
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                }
            }
        }

        when (val current = result) {
            null -> Unit

            is InequalityResult.Failed -> ResultCard(
                label = "Could not solve",
                value = null,
                error = current.message,
            )

            is InequalityResult.Plane -> PlaneResult(
                current = current,
                accent = accent,
                poleColor = poleColor,
                probeX = probeX,
                probeY = probeY,
                onProbeX = { probeX = it },
                onProbeY = { probeY = it },
                expanded = expanded,
            )

            is InequalityResult.Solved -> SolvedResult(
                current = current,
                accent = accent,
                poleColor = poleColor,
                expanded = expanded,
            )
        }
    }
}

/** The result of a one-variable solve: the answer, the number line and the working. */
@Composable
private fun SolvedResult(
    current: InequalityResult.Solved,
    accent: androidx.compose.ui.graphics.Color,
    poleColor: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
) {
    val solution = current.solution
    ResultCard(
        label = "Solution in ${current.variable}",
        value = solution.toIntervalNotation(),
        approx = current.approximate,
        caption = if (current.approximate) {
            "Computed numerically, so the critical points are approximate."
        } else {
            null
        },
    )
    ResultRow(
        label = "Set-builder form",
        value = solution.toSetBuilder(current.variable),
        emphasis = false,
    )

    if (!expanded) {
        SectionCard(title = "Number line") {
            NumberLineView(
                intervals = solution.intervals,
                criticalPoints = current.criticalPoints,
                accent = accent,
                poleColor = poleColor,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "A filled circle is included, an open circle is excluded, and a ring in red is a " +
                    "point where the expression is undefined.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (current.domainNote != null) {
        InfoNote(current.domainNote)
    }

    if (!expanded) {
        ExpandablePanel(title = "Step-by-step solution") {
            StepsCard(steps = current.steps)
        }
        ExpandablePanel(title = "Why does this work?") {
            Text(
                current.principle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        StepsCard(steps = current.steps)
    }

    if (current.signChart.isNotEmpty()) {
        SectionCard(title = "Sign chart") {
            current.signChart.forEach { row ->
                ResultRow(
                    label = row.interval,
                    value = "${row.sign} → ${if (row.accepted) "included" else "excluded"}",
                    emphasis = row.accepted,
                )
            }
        }
    }

    FormulaCard(
        formula = "a < b  ⟺  a − b < 0",
        explanation = "Moving everything to one side turns the inequality into a comparison " +
            "against zero, which is what makes a sign chart possible.",
        variables = listOf(
            "a" to "the left-hand side of the inequality",
            "b" to "the right-hand side",
        ),
    )
}

/** The result of a two-variable system: the plane and the working. */
@Composable
private fun PlaneResult(
    current: InequalityResult.Plane,
    accent: androidx.compose.ui.graphics.Color,
    poleColor: androidx.compose.ui.graphics.Color,
    probeX: String,
    probeY: String,
    onProbeX: (String) -> Unit,
    onProbeY: (String) -> Unit,
    expanded: Boolean,
) {
    val system = current.system
    ResultCard(
        label = "Feasible region",
        value = if (system.region.isEmpty()) {
            "No solution"
        } else {
            "${system.region.size} corner points"
        },
        caption = if (system.region.isEmpty()) {
            "The shaded sides do not overlap anywhere."
        } else {
            "The region shown is clipped to the visible window; it may continue beyond it."
        },
    )

    if (!expanded) {
        SectionCard(title = "Coordinate plane") {
            var planeView by remember { mutableStateOf(PlaneView.Default) }
            PlaneView(
                halfPlanes = system.halfPlanes,
                region = system.region,
                accent = accent,
                gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                view = planeView,
                onViewChange = { planeView = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { planeView = PlaneView.Default }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset view")
                }
            }
            Text(
                "A solid line is a boundary that may be included, a dashed one is strict. " +
                    "The shaded region is where every inequality holds at once. Drag the plane to " +
                    "pan it and pinch to zoom; a drag that starts beside the plane scrolls the page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (!expanded) {
        ExpandablePanel(title = "Step-by-step solution") {
            StepsCard(steps = system.steps)
        }
        ExpandablePanel(title = "Why does this work?") {
            Text(
                system.principle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        StepsCard(steps = system.steps)
    }

    SectionCard(title = "Check a point") {
        PlainField(
            label = "x",
            value = probeX,
            onValueChange = onProbeX,
            placeholder = "0",
        )
        PlainField(
            label = "y",
            value = probeY,
            onValueChange = onProbeY,
            placeholder = "0",
        )
        val x = probeX.trim().toDoubleOrNull()
        val y = probeY.trim().toDoubleOrNull()
        if (x != null && y != null) {
            val checks = system.halfPlanes.map { plane ->
                plane to plane.satisfiedBy(x, y)
            }
            val all = checks.all { it.second }
            ResultRow(
                label = "($x, $y)",
                value = if (all) "satisfies every inequality" else "does not satisfy them all",
                emphasis = all,
            )
            checks.forEach { (plane, ok) ->
                ResultRow(
                    label = plane.original,
                    value = if (ok) "holds" else "fails",
                    emphasis = ok,
                )
            }
        } else {
            Text(
                "Enter both coordinates to see whether that point satisfies the system.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A worked example for each mode, used by the Example button. */
private val EXAMPLES: Map<InequalityMode, String> = mapOf(
    InequalityMode.SINGLE to "x^2 - 5x + 6 <= 0",
    InequalityMode.COMPOUND to "-2 < 3x + 1 <= 10",
    InequalityMode.SYSTEM to "x^2 - 4 > 0\nx - 1 < 4",
    InequalityMode.TWO_VARIABLE to "y > 2x - 1\ny <= -x + 5",
)
