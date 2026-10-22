package com.calcsuite.ui.tools.electrical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.calcsuite.engine.electrical.ComponentKind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.electrical.Circuit
import com.calcsuite.engine.electrical.CircuitCodec
import com.calcsuite.engine.electrical.CircuitExamples
import com.calcsuite.engine.electrical.CircuitExplainer
import com.calcsuite.engine.electrical.CircuitValidator
import com.calcsuite.engine.electrical.CircuitState
import com.calcsuite.engine.electrical.DcSolution
import com.calcsuite.engine.electrical.ElectricalCalculators
import com.calcsuite.ui.LocalApp
import com.calcsuite.ui.components.ActionButton
import com.calcsuite.ui.components.ActionRow
import com.calcsuite.ui.components.ExplanationSections
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import kotlinx.coroutines.flow.first

/**
 * The two things the tool offers: the circuit builder and the calculators.
 */
private enum class ElectricalTab(val label: String) {
    BUILDER("Circuit builder"),
    CALCULATORS("Calculators"),
}

/**
 * The Electrical Circuits tool.
 *
 * The builder is the main part: parts are put on the canvas, they are
 * wired together, and the circuit is worked out. The calculators are for
 * the ordinary questions that do not need a circuit to be drawn.
 */
@Composable
fun ElectricalCircuitsScreen(args: ToolArgs) {
    var tab by rememberSaveable { mutableStateOf(ElectricalTab.BUILDER.name) }
    val current = ElectricalTab.entries.firstOrNull { it.name == tab } ?: ElectricalTab.BUILDER

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SegmentedChoice(
            label = "What to do",
            options = ElectricalTab.entries.map { it.name to it.label },
            selected = current.name,
            onSelect = { tab = it },
        )
        if (current == ElectricalTab.BUILDER) {
            CircuitBuilder(args)
        } else {
            ElectricalCalculatorsPanel(args)
        }
    }
}

/**
 * What the last calculation found.
 *
 * The circuit it was made from is kept as well, so that it is known whether
 * the circuit has been changed since.
 */
private data class SolvedCircuit(
    val circuit: Circuit,
    val solution: DcSolution,
    val explanation: com.calcsuite.engine.electrical.CircuitExplanation,
)

/**
 * The circuit builder.
 */
@Composable
private fun CircuitBuilder(args: ToolArgs) {
    val container = LocalApp.current
    var state by remember { mutableStateOf(CircuitEditorState()) }
    var showValues by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var showExamples by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var solved by remember { mutableStateOf<SolvedCircuit?>(null) }

    // A part that is being dragged out of the palette, and where it is.
    var draggedKind by remember { mutableStateOf<ComponentKind?>(null) }
    var draggedAt by remember { mutableStateOf<Offset?>(null) }
    var canvasBounds by remember { mutableStateOf<Rect?>(null) }

    // The state of the circuit as it is now: the parts are checked and the
    // numbers are worked out as they are typed.
    val assessment = remember(state.circuit) { CircuitValidator.assess(state.circuit) }
    val liveResults = assessment.solution?.components.orEmpty()

    // The circuit is put back when the tool is opened again, or when one is
    // handed over from the history.
    val handedOver = remember(args.tool.id) {
        args.prefillValues["circuit"]?.let { CircuitCodec.decode(it) }
    }
    LaunchedEffect(args.tool.id) {
        val restored = handedOver ?: container.circuits.circuit(args.tool.id).first()
            ?.let { CircuitCodec.decode(it) }
        if (restored != null) {
            state = state.copy(circuit = restored)
        }
    }

    // The circuit is kept, so that it is still there next time.
    LaunchedEffect(state.circuit) {
        if (state.circuit.components.isNotEmpty()) {
            container.circuits.save(args.tool.id, CircuitCodec.encode(state.circuit))
        }
    }

    // The results on show are from the last calculation. When the circuit has
    // been changed since, they are marked as out of date.
    val lastSolved = solved
    val stale = lastSolved != null && lastSolved.circuit != state.circuit

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The bar of actions.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = { state = CircuitEditor.undo(state) },
                    enabled = state.undo.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = { state = CircuitEditor.redo(state) },
                    enabled = state.redo.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Redo, contentDescription = "Redo")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showExamples = true }) {
                    Text("Examples")
                }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = state.circuit.components.isNotEmpty(),
                ) {
                    Text("Clear")
                }
            }
        }

        // The canvas.
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .onGloballyPositioned { canvasBounds = it.boundsInRoot() },
        ) {
            CircuitCanvas(
                state = state,
                onState = { state = it },
                results = liveResults,
                showValues = showValues,
                dragKind = draggedKind,
                dragPosition = draggedAt,
                onSelect = { id ->
                    if (id == null) {
                        showSheet = false
                    } else {
                        state = state.copy(selected = id)
                        showSheet = true
                    }
                },
            )
        }

        // The state of the circuit.
        when (assessment.state) {
            CircuitState.EMPTY -> InfoNote("The canvas is empty. Choose a part below to begin.")
            CircuitState.INCOMPLETE -> InfoNote(
                assessment.message ?: "The circuit is not finished yet.",
            )
            CircuitState.INVALID -> InfoNote(
                assessment.message ?: "The circuit cannot be worked out as it is.",
            )
            CircuitState.SOLVED -> ResultCard(
                label = "The circuit",
                value = assessment.explanation?.value,
                caption = "Worked out with the circuit's own equations",
            )
        }

        // The button that works the circuit out.
        ActionRow(Modifier.fillMaxWidth()) {
            ActionButton(
                text = "Calculate the circuit",
                onClick = {
                    val result = CircuitValidator.assess(state.circuit)
                    val explanation = result.explanation
                    val solution = result.solution
                    if (result.state == CircuitState.SOLVED &&
                        explanation != null &&
                        solution != null
                    ) {
                        solved = SolvedCircuit(state.circuit, solution, explanation)
                        args.onRecord.record(
                            input = CircuitCodec.summary(state.circuit),
                            result = explanation.value,
                            values = mapOf("circuit" to CircuitCodec.encode(state.circuit)),
                        )
                    } else {
                        solved = null
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = assessment.state == CircuitState.SOLVED,
                emphasised = true,
            )
        }

        // The note that the results on show are from an older circuit.
        if (stale) {
            InfoNote(
                "The circuit has been changed since it was last worked out. " +
                    "Calculate it again to see the new numbers.",
            )
        }

        // The palette.
        SectionCard(title = "Parts") {
            ComponentPalette(
                onPick = { kind ->
                    state = state.copy(action = EditorAction.Placing(kind))
                },
                onDragStart = { kind, position ->
                    draggedKind = kind
                    draggedAt = position
                },
                onDrag = { _, position ->
                    draggedAt = position
                },
                onDragEnd = { kind, position ->
                    val bounds = canvasBounds
                    val where = draggedAt ?: position
                    draggedKind = null
                    draggedAt = null
                    if (bounds != null && bounds.contains(where)) {
                        // The part is put down where the finger let go.
                        val x = state.viewCentreX + (where.x - bounds.left - bounds.width / 2) * state.zoom
                        val y = state.viewCentreY - (where.y - bounds.top - bounds.height / 2) * state.zoom
                        state = CircuitEditor.addComponent(state, kind, x, y)
                            .copy(action = EditorAction.Idle)
                        showSheet = true
                    }
                },
            )
            Text(
                "Choose a part, then tap the canvas to put it down. " +
                    "Tap a terminal to start a wire, and tap the terminal it goes to.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        // The results of the last calculation.
        val shown = lastSolved
        if (shown != null) {
            ExplanationSections(
                title = "The circuit",
                value = shown.explanation.value,
                meaning = shown.explanation.meaning,
                formula = shown.explanation.formula,
                variables = shown.explanation.variables,
                steps = shown.explanation.steps,
                why = shown.explanation.why,
                checkTitle = shown.explanation.checkTitle,
                checks = shown.explanation.checks,
            )

            SectionCard(title = "Every part") {
                for (component in state.circuit.components) {
                    val result = shown.solution.components[component.id]
                    ResultRow(
                        label = component.label,
                        value = if (result == null) {
                            ""
                        } else {
                            "${CircuitExplainer.number(result.voltage)} V · " +
                                "${CircuitExplainer.number(result.current)} A · " +
                                "${CircuitExplainer.number(result.power)} W"
                        },
                    )
                }
            }

            ActionRow(Modifier.fillMaxWidth()) {
                ActionButton(
                    text = if (showValues) "Hide the values" else "Show the values",
                    onClick = { showValues = !showValues },
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (assessment.state == CircuitState.SOLVED) {
            InfoNote("Press Calculate the circuit to see how it works out.")
        }

        // The part that is selected.
        val selected = state.selectedComponent
        if (selected != null && showSheet) {
            ComponentEditorSheet(
                component = selected,
                result = liveResults[selected.id],
                onApply = { updated ->
                    state = CircuitEditor.updateComponent(state, updated.id) { updated }
                    showSheet = false
                },
                onRotate = {
                    state = CircuitEditor.rotateComponent(state, selected.id)
                    showSheet = false
                },
                onDelete = {
                    state = CircuitEditor.deleteComponent(state, selected.id)
                    showSheet = false
                },
                onDismiss = { showSheet = false },
            )
        }
    }

    // The examples.
    if (showExamples) {
        AlertDialog(
            onDismissRequest = { showExamples = false },
            title = { Text("Example circuits") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (example in CircuitExamples.all) {
                        // The name of the example is on the left, with dots
                        // filling the space to its right, so that the list
                        // reads like a table of contents.
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    state = state.copy(
                                        circuit = example.circuit,
                                        selected = null,
                                        action = EditorAction.Idle,
                                        undo = state.undo + state.circuit,
                                        redo = emptyList(),
                                    )
                                    showExamples = false
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    example.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                DotLeader(
                                    Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp, end = 2.dp)
                                        .height(12.dp),
                                )
                            }
                            Text(
                                example.teaching,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExamples = false }) {
                    Text("Close")
                }
            },
        )
    }

    // The confirmation before the circuit is thrown away.
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the circuit?") },
            text = { Text("Everything on the canvas will be removed. This can be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state = CircuitEditor.clear(state)
                        confirmClear = false
                    },
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Keep it")
                }
            },
        )
    }
}

/**
 * The calculators, for the questions that do not need a circuit drawn.
 */
@Composable
private fun ElectricalCalculatorsPanel(args: ToolArgs) {
    var which by rememberSaveable { mutableStateOf("ohms") }
    val options = listOf(
        Option("ohms", "Ohm's law and power", group = "The basics"),
        Option("series", "Resistors in series", group = "The basics"),
        Option("parallel", "Resistors in parallel", group = "The basics"),
        Option("mixed", "A series part and a parallel part", group = "The basics"),
        Option("divider", "Voltage divider", group = "The basics"),
        Option("current", "Current divider", group = "The basics"),
        Option("convert", "Write a value in another prefix", group = "Around the circuit"),
        Option("colour", "Resistor colour code", group = "Around the circuit"),
        Option("capacitor", "A capacitor", group = "Around the circuit"),
        Option("timeconstant", "How long a circuit takes to settle", group = "Around the circuit"),
        Option("ac", "An alternating circuit", group = "Around the circuit"),
    )
    SelectField(
        label = "The calculation",
        options = options,
        selected = which,
        onSelect = { which = it },
    )

    when (which) {
        "ohms" -> OhmsLawPanel(args)
        "series" -> ResistorListPanel(title = "Resistors in series", parallel = false)
        "parallel" -> ResistorListPanel(title = "Resistors in parallel", parallel = true)
        "mixed" -> MixedNetworkPanel()
        "divider" -> DividerPanel(args)
        "current" -> CurrentDividerPanel(args)
        "convert" -> UnitConversionPanel()
        "colour" -> ColourCodePanel()
        "capacitor" -> CapacitorPanel()
        "timeconstant" -> TimeConstantPanel()
        "ac" -> AcPanel()
    }
}

/**
 * The line of dots that fills the space beside a name.
 *
 * It is drawn as a dashed line, so the dots are the same whatever the
 * width of the space is, and they follow the text's own colour.
 */
@Composable
private fun DotLeader(modifier: Modifier = Modifier) {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val middle = size.height / 2f
        drawLine(
            color = colour,
            start = Offset(0f, middle),
            end = Offset(size.width, middle),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(2f, 3.5f),
            ),
        )
    }
}
