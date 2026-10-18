package com.calcsuite.ui.tools.geometry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.core.Num
import com.calcsuite.engine.geometry.BodyEngine
import com.calcsuite.engine.geometry.BodyField
import com.calcsuite.engine.geometry.BodyKind
import com.calcsuite.engine.geometry.BodyResult
import com.calcsuite.engine.geometry.TriangularPyramidMode
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Option
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.SelectField
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.rememberCopyAction
import com.calcsuite.ui.components.rememberFmt
import com.calcsuite.ui.tools.ToolArgs
import java.math.BigDecimal

private val BodyKind.title: String
    get() = when (this) {
        BodyKind.CUBE -> "Cube"
        BodyKind.RECTANGULAR_PRISM -> "Rectangular prism"
        BodyKind.SPHERE -> "Sphere"
        BodyKind.HEMISPHERE -> "Hemisphere"
        BodyKind.CYLINDER -> "Cylinder"
        BodyKind.CONE -> "Cone"
        BodyKind.SQUARE_PYRAMID -> "Square pyramid"
        BodyKind.TRIANGULAR_PRISM -> "Triangular prism"
        BodyKind.TRIANGULAR_PYRAMID -> "Triangular pyramid"
    }

/** 3D bodies: pick a solid, fill its dimensions, calculate volume and surface. */
@Composable
fun BodiesToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var kind by rememberSaveable { mutableStateOf(BodyKind.CUBE) }
    var pyramidMode by rememberSaveable { mutableStateOf(TriangularPyramidMode.REGULAR_TETRAHEDRON) }
    var unitText by rememberSaveable { mutableStateOf("cm") }
    var inputs by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var outcome by remember { mutableStateOf<BodyOutcome?>(null) }

    val unitLabel = unitText.trim().ifBlank { "unit" }
    val fields = fieldsFor(kind, pyramidMode)
    val fieldErrors = remember(inputs, kind, pyramidMode) {
        fields.mapNotNull { field ->
            val raw = inputs[field.id]?.trim().orEmpty()
            fieldError(field, raw)?.let { field.id to it }
        }.toMap()
    }
    val bodyOptions = remember { BodyKind.entries.map { Option(it, it.title) } }
    val resultPrecision = maxOf(fmt.precision, 10)

    fun reset() {
        inputs = emptyMap()
        outcome = null
    }

    fun calculate() {
        val parsed = mutableMapOf<String, BigDecimal>()
        var failure: String? = null
        for (field in fields) {
            val raw = inputs[field.id]?.trim().orEmpty()
            if (raw.isEmpty()) {
                failure = "Enter a value for ${field.label}"
                break
            }
            val value = Num.parseOrNull(raw)
            if (value == null) {
                failure = "Enter a valid number for ${field.label}"
                break
            }
            parsed[field.id] = value
        }
        if (failure == null && fieldErrors.isNotEmpty()) {
            failure = "Fix the highlighted fields first"
        }
        if (failure != null) {
            outcome = BodyOutcome(null, failure, "")
            return
        }
        val computed = compute(kind, pyramidMode, parsed)
        outcome = when (computed) {
            is CalcResult.Ok -> BodyOutcome(
                result = computed.value,
                error = null,
                inputSummary = summarize(fields, inputs, kind, unitLabel),
            )
            is CalcResult.Err -> BodyOutcome(null, computed.error.message, "")
        }
    }

    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        SelectField(
            label = "Solid",
            options = bodyOptions,
            selected = kind,
            onSelect = {
                kind = it
                outcome = null
            },
        )

        if (kind == BodyKind.TRIANGULAR_PYRAMID) {
            SegmentedChoice(
                options = listOf(
                    TriangularPyramidMode.REGULAR_TETRAHEDRON to "Tetrahedron",
                    TriangularPyramidMode.GENERAL to "Base + height",
                ),
                selected = pyramidMode,
                onSelect = {
                    pyramidMode = it
                    outcome = null
                },
                label = "Which pyramid?",
            )
        }

        SectionCard(title = "Diagram — ${kind.title}") {
            BodyDiagram(
                kind = kind,
                pyramidMode = pyramidMode,
                values = inputs,
                unit = unitText.trim(),
            )
        }

        SectionCard(title = "Inputs") {
            PlainField(
                label = "Length unit",
                value = unitText,
                onValueChange = { unitText = it },
                placeholder = "cm",
                imeAction = ImeAction.Next,
            )
            fields.forEach { field ->
                NumberField(
                    label = if (field.optional) "${field.label} (optional)" else field.label,
                    value = inputs[field.id].orEmpty(),
                    onValueChange = { text -> inputs = inputs + (field.id to text) },
                    unit = unitText.trim().ifBlank { null },
                    error = fieldErrors[field.id],
                    imeAction = ImeAction.Done,
                )
            }
            InfoNote(
                "All lengths use the same unit (${unitText.trim().ifBlank { "unit" }}); " +
                    "volumes come back in ${unitLabel}³ and surface areas in ${unitLabel}².",
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { calculate() }, modifier = Modifier.weight(1f)) {
                Text("Calculate")
            }
            OutlinedButton(onClick = { reset() }) {
                Text("Reset")
            }
        }

        val current = outcome
        if (current != null) {
            val result = current.result
            val primary = result?.primary
            ResultCard(
                label = "Volume",
                value = primary?.let { fmt.num(it, precision = resultPrecision) },
                unit = "$unitLabel³",
                error = current.error,
                onCopy = if (primary != null) {
                    { copy(Num.formatForCopy(primary), "Volume") }
                } else {
                    null
                },
                copyValue = primary?.let { Num.formatForCopy(it) },
                onSave = if (primary != null) {
                    {
                        args.onRecord(
                            current.inputSummary,
                            "Volume = ${Num.formatForCopy(primary)} $unitLabel³",
                        )
                    }
                } else {
                    null
                },
            )
            if (result != null) {
                result.quantities.forEach { (label, value) ->
                    ResultRow(
                        label = label.replace("unit", unitLabel),
                        value = fmt.num(value, precision = resultPrecision),
                        onCopy = { copy(Num.formatForCopy(value), label.replace("unit", unitLabel)) },
                    )
                }
                if (result.unavailable.isNotEmpty()) {
                    SectionCard(title = "Not determined by these inputs") {
                        Text(
                            "These quantities need more information than the current inputs provide:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        result.unavailable.forEach { item ->
                            Text(
                                "•  ${item.replace("unit", unitLabel)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                result.validation.forEach { InfoNote(it) }
                StepsCard(result.steps)
            }
        }

        val formula = formulaFor(kind, pyramidMode)
        FormulaCard(
            formula = formula.formula,
            explanation = formula.explanation,
            variables = formula.variables,
        )

        InfoNote(
            "The diagram follows what you type; the result panel updates when you tap Calculate. " +
                "Tap the bookmark on the Volume card to keep the calculation in history.",
        )
    }
}

private class BodyOutcome(
    val result: BodyResult?,
    val error: String?,
    val inputSummary: String,
)

// ------------------------------------------------------------------ inputs

private fun fieldsFor(kind: BodyKind, pyramidMode: TriangularPyramidMode): List<BodyField> =
    when (kind) {
        BodyKind.CUBE -> listOf(BodyField("a", "Edge a"))
        BodyKind.RECTANGULAR_PRISM -> listOf(
            BodyField("a", "Edge a"),
            BodyField("b", "Edge b"),
            BodyField("c", "Edge c"),
        )
        BodyKind.SPHERE -> listOf(BodyField("radius", "Radius r"))
        BodyKind.HEMISPHERE -> listOf(BodyField("radius", "Radius r"))
        BodyKind.CYLINDER -> listOf(
            BodyField("radius", "Radius r"),
            BodyField("height", "Height h"),
        )
        BodyKind.CONE -> listOf(
            BodyField("radius", "Radius r"),
            BodyField("height", "Height h"),
        )
        BodyKind.SQUARE_PYRAMID -> listOf(
            BodyField("baseEdge", "Base edge a"),
            BodyField("height", "Height h"),
        )
        BodyKind.TRIANGULAR_PRISM -> listOf(
            BodyField("baseEdge", "Base edge a"),
            BodyField("height", "Height h"),
        )
        BodyKind.TRIANGULAR_PYRAMID -> when (pyramidMode) {
            TriangularPyramidMode.REGULAR_TETRAHEDRON -> listOf(BodyField("a", "Edge a"))
            TriangularPyramidMode.GENERAL -> listOf(
                BodyField("sideA", "Base side a"),
                BodyField("sideB", "Base side b"),
                BodyField("sideC", "Base side c"),
                BodyField("height", "Height h"),
            )
        }
    }

private fun fieldError(field: BodyField, raw: String): String? {
    if (raw.isEmpty()) return null
    val value = Num.parseOrNull(raw) ?: return "Enter a valid number"
    if (value.signum() <= 0) return "${field.label} must be greater than zero"
    return null
}

// ------------------------------------------------------------- calculation

private fun compute(
    kind: BodyKind,
    pyramidMode: TriangularPyramidMode,
    values: Map<String, BigDecimal>,
): CalcResult<BodyResult> = when (kind) {
    BodyKind.CUBE -> BodyEngine.cube(values.getValue("a"))
    BodyKind.RECTANGULAR_PRISM ->
        BodyEngine.rectangularPrism(values.getValue("a"), values.getValue("b"), values.getValue("c"))
    BodyKind.SPHERE -> BodyEngine.sphere(values.getValue("radius"))
    BodyKind.HEMISPHERE -> BodyEngine.hemisphere(values.getValue("radius"))
    BodyKind.CYLINDER -> BodyEngine.cylinder(values.getValue("radius"), values.getValue("height"))
    BodyKind.CONE -> BodyEngine.cone(values.getValue("radius"), values.getValue("height"))
    BodyKind.SQUARE_PYRAMID ->
        BodyEngine.squarePyramid(values.getValue("baseEdge"), values.getValue("height"))
    BodyKind.TRIANGULAR_PRISM ->
        BodyEngine.triangularPrism(values.getValue("baseEdge"), values.getValue("height"))
    BodyKind.TRIANGULAR_PYRAMID -> when (pyramidMode) {
        TriangularPyramidMode.REGULAR_TETRAHEDRON ->
            BodyEngine.regularTetrahedron(values.getValue("a"))
        TriangularPyramidMode.GENERAL -> BodyEngine.triangularPyramid(
            values.getValue("sideA"),
            values.getValue("sideB"),
            values.getValue("sideC"),
            values.getValue("height"),
        )
    }
}

private fun summarize(
    fields: List<BodyField>,
    inputs: Map<String, String>,
    kind: BodyKind,
    unitLabel: String,
): String {
    val parts = fields.mapNotNull { field ->
        inputs[field.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { "${field.label} = $it" }
    }
    val unitPart = if (unitLabel == "unit") "" else " ($unitLabel)"
    return "${kind.title}: ${parts.joinToString(", ")}$unitPart"
}

// ----------------------------------------------------------------- formula

private fun formulaFor(kind: BodyKind, pyramidMode: TriangularPyramidMode): BodyFormulaSpec =
    when (kind) {
        BodyKind.CUBE -> BodyFormulaSpec(
            "V = a³    S = 6a²    S_lat = 4a²    d = a√3",
            "A cube with edge a: volume, total and lateral surface, diagonals.",
            listOf("a" to "edge", "V" to "volume", "S" to "total surface", "S_lat" to "lateral surface"),
        )
        BodyKind.RECTANGULAR_PRISM -> BodyFormulaSpec(
            "V = abc    S = 2(ab + bc + ca)    d = √(a² + b² + c²)",
            "A box with edges a, b and c.",
            listOf("a, b, c" to "edges", "V" to "volume", "S" to "total surface", "d" to "space diagonal"),
        )
        BodyKind.SPHERE -> BodyFormulaSpec(
            "V = (4/3)πr³    S = 4πr²    C = 2πr",
            "A sphere of radius r; C is the great-circle circumference.",
            listOf("r" to "radius", "V" to "volume", "S" to "surface"),
        )
        BodyKind.HEMISPHERE -> BodyFormulaSpec(
            "V = (2/3)πr³    S_curved = 2πr²    S = 3πr²    base = πr²",
            "Half a sphere with the flat circular base included in the total surface.",
            listOf("r" to "radius", "V" to "volume", "S" to "total surface"),
        )
        BodyKind.CYLINDER -> BodyFormulaSpec(
            "V = πr²h    S_lat = 2πrh    S = 2πr(r + h)",
            "A right circular cylinder with radius r and height h.",
            listOf("r" to "base radius", "h" to "height", "V" to "volume", "S_lat" to "lateral surface"),
        )
        BodyKind.CONE -> BodyFormulaSpec(
            "l = √(r² + h²)    V = πr²h/3    S_lat = πrl    S = πr(r + l)",
            "A right circular cone; l is the slant height along the side.",
            listOf("r" to "base radius", "h" to "height", "l" to "slant height", "V" to "volume"),
        )
        BodyKind.SQUARE_PYRAMID -> BodyFormulaSpec(
            "l = √(h² + (a/2)²)    V = a²h/3    S_lat = 2a·l    S = a² + 2a·l",
            "A regular right square pyramid: the apex sits above the centre of the base.",
            listOf("a" to "base edge", "h" to "height", "l" to "slant height (face height)", "V" to "volume"),
        )
        BodyKind.TRIANGULAR_PRISM -> BodyFormulaSpec(
            "base = (√3/4)a²    V = base × h    S_lat = 3ah    S = 2·base + S_lat",
            "A right prism with an equilateral triangle of side a as base.",
            listOf("a" to "base edge", "h" to "prism height", "V" to "volume", "S" to "total surface"),
        )
        BodyKind.TRIANGULAR_PYRAMID -> when (pyramidMode) {
            TriangularPyramidMode.REGULAR_TETRAHEDRON -> BodyFormulaSpec(
                "V = a³/(6√2)    S = √3a²    h = a√6/3",
                "A regular tetrahedron: all four faces are equilateral triangles of side a.",
                listOf("a" to "edge", "V" to "volume", "S" to "total surface", "h" to "height"),
            )
            TriangularPyramidMode.GENERAL -> BodyFormulaSpec(
                "s = (a + b + c)/2    base = √(s(s − a)(s − b)(s − c))    V = base × h/3",
                "Any triangular base with the perpendicular height h; the lateral faces need " +
                    "the horizontal position of the apex as well.",
                listOf("a, b, c" to "base sides", "h" to "perpendicular height", "V" to "volume"),
            )
        }
    }

private class BodyFormulaSpec(
    val formula: String,
    val explanation: String,
    val variables: List<Pair<String, String>>,
)
