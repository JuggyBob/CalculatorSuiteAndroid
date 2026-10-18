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
import com.calcsuite.engine.geometry.CircleMode
import com.calcsuite.engine.geometry.PolygonMode
import com.calcsuite.engine.geometry.RhombusMode
import com.calcsuite.engine.geometry.ShapeEngine
import com.calcsuite.engine.geometry.ShapeField
import com.calcsuite.engine.geometry.ShapeKind
import com.calcsuite.engine.geometry.ShapeResult
import com.calcsuite.engine.geometry.TriangleMode
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
import java.math.BigInteger

private val ShapeKind.title: String
    get() = when (this) {
        ShapeKind.SQUARE -> "Square"
        ShapeKind.RECTANGLE -> "Rectangle"
        ShapeKind.TRIANGLE -> "Triangle"
        ShapeKind.EQUILATERAL_TRIANGLE -> "Equilateral triangle"
        ShapeKind.PARALLELOGRAM -> "Parallelogram"
        ShapeKind.RHOMBUS -> "Rhombus"
        ShapeKind.TRAPEZOID -> "Trapezoid"
        ShapeKind.CIRCLE -> "Circle"
        ShapeKind.SEMICIRCLE -> "Semicircle"
        ShapeKind.ELLIPSE -> "Ellipse"
        ShapeKind.REGULAR_POLYGON -> "Regular polygon"
    }

/** 2D shapes: pick a shape, fill its dimensions, calculate area and friends. */
@Composable
fun ShapesToolScreen(args: ToolArgs) {
    val fmt = rememberFmt()
    val copy = rememberCopyAction()

    var kind by rememberSaveable { mutableStateOf(ShapeKind.SQUARE) }
    var triangleMode by rememberSaveable { mutableStateOf(TriangleMode.THREE_SIDES) }
    var circleMode by rememberSaveable { mutableStateOf(CircleMode.RADIUS) }
    var rhombusMode by rememberSaveable { mutableStateOf(RhombusMode.DIAGONALS) }
    var polygonMode by rememberSaveable { mutableStateOf(PolygonMode.SIDE) }
    var unitText by rememberSaveable { mutableStateOf("cm") }
    var inputs by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
    var outcome by remember { mutableStateOf<ShapeOutcome?>(null) }

    val unitLabel = unitText.trim().ifBlank { "unit" }
    val fields = fieldsFor(kind, triangleMode, circleMode, rhombusMode, polygonMode)
    val fieldErrors = remember(inputs, kind, triangleMode, circleMode, rhombusMode, polygonMode) {
        fields.mapNotNull { field ->
            val raw = inputs[field.id]?.trim().orEmpty()
            fieldError(field, raw)?.let { field.id to it }
        }.toMap()
    }
    val shapeOptions = remember { ShapeKind.entries.map { Option(it, it.title) } }
    val resultPrecision = maxOf(fmt.precision, 10)

    fun reset() {
        inputs = emptyMap()
        outcome = null
    }

    fun calculate() {
        val parsed = mutableMapOf<String, BigDecimal>()
        var sides = 6
        var failure: String? = null
        for (field in fields) {
            val raw = inputs[field.id]?.trim().orEmpty()
            if (raw.isEmpty()) {
                if (!field.optional) {
                    failure = "Enter a value for ${field.label}"
                    break
                }
                continue
            }
            if (field.id == "sides") {
                sides = Num.parseIntOrNull(raw)?.toInt() ?: 6
            } else {
                val value = Num.parseOrNull(raw)
                if (value == null) {
                    failure = "Enter a valid number for ${field.label}"
                    break
                }
                parsed[field.id] = value
            }
        }
        if (failure == null && fieldErrors.isNotEmpty()) {
            failure = "Fix the highlighted fields first"
        }
        if (failure != null) {
            outcome = ShapeOutcome(null, failure, "")
            return
        }
        val computed = compute(
            kind = kind,
            triangleMode = triangleMode,
            circleMode = circleMode,
            rhombusMode = rhombusMode,
            polygonMode = polygonMode,
            values = parsed,
            sides = sides,
        )
        outcome = when (computed) {
            is CalcResult.Ok -> ShapeOutcome(
                result = computed.value,
                error = null,
                inputSummary = summarize(fields, inputs, kind, unitLabel),
            )
            is CalcResult.Err -> ShapeOutcome(null, computed.error.message, "")
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
            label = "Shape",
            options = shapeOptions,
            selected = kind,
            onSelect = {
                kind = it
                outcome = null
            },
        )

        when (kind) {
            ShapeKind.TRIANGLE -> SegmentedChoice(
                options = listOf(
                    TriangleMode.THREE_SIDES to "3 sides",
                    TriangleMode.BASE_HEIGHT to "Base & height",
                    TriangleMode.TWO_SIDES_INCLUDED_ANGLE to "2 sides + angle",
                ),
                selected = triangleMode,
                onSelect = {
                    triangleMode = it
                    outcome = null
                },
                label = "How is the triangle given?",
            )
            ShapeKind.CIRCLE -> SegmentedChoice(
                options = listOf(
                    CircleMode.RADIUS to "Radius",
                    CircleMode.DIAMETER to "Diameter",
                    CircleMode.CIRCUMFERENCE to "Circumference",
                ),
                selected = circleMode,
                onSelect = {
                    circleMode = it
                    outcome = null
                },
                label = "What do you know?",
            )
            ShapeKind.RHOMBUS -> SegmentedChoice(
                options = listOf(
                    RhombusMode.DIAGONALS to "Diagonals",
                    RhombusMode.SIDE_ANGLE to "Side + angle",
                ),
                selected = rhombusMode,
                onSelect = {
                    rhombusMode = it
                    outcome = null
                },
                label = "How is the rhombus given?",
            )
            ShapeKind.REGULAR_POLYGON -> SegmentedChoice(
                options = listOf(
                    PolygonMode.SIDE to "Side length",
                    PolygonMode.CIRCUMRADIUS to "Circumradius",
                ),
                selected = polygonMode,
                onSelect = {
                    polygonMode = it
                    outcome = null
                },
                label = "How is the polygon given?",
            )
            else -> Unit
        }

        SectionCard(title = "Diagram — ${kind.title}") {
            ShapeDiagram(
                kind = kind,
                triangleMode = triangleMode,
                circleMode = circleMode,
                rhombusMode = rhombusMode,
                polygonMode = polygonMode,
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
                    integerOnly = field.id == "sides",
                    placeholder = if (field.id == "sides") "6" else null,
                    imeAction = ImeAction.Done,
                )
            }
            InfoNote(
                "All lengths use the same unit (${unitText.trim().ifBlank { "unit" }}); " +
                    "areas come back in ${unitLabel}².",
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
                label = "Area",
                value = primary?.let { fmt.num(it, precision = resultPrecision) },
                unit = "$unitLabel²",
                error = current.error,
                onCopy = if (primary != null) {
                    { copy(Num.formatForCopy(primary), "Area") }
                } else {
                    null
                },
                copyValue = primary?.let { Num.formatForCopy(it) },
                onSave = if (primary != null) {
                    {
                        args.onRecord(
                            current.inputSummary,
                            "Area = ${Num.formatForCopy(primary)} $unitLabel²",
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

        val formula = formulaFor(kind, triangleMode, circleMode, rhombusMode, polygonMode)
        FormulaCard(
            formula = formula.formula,
            explanation = formula.explanation,
            variables = formula.variables,
        )

        InfoNote(
            "The diagram follows what you type; the result panel updates when you tap Calculate. " +
                "Tap the bookmark on the Area card to keep the calculation in history.",
        )
    }
}

private class ShapeOutcome(
    val result: ShapeResult?,
    val error: String?,
    val inputSummary: String,
)

private class FormulaSpec(
    val formula: String,
    val explanation: String,
    val variables: List<Pair<String, String>>,
)

// ------------------------------------------------------------------ inputs

private fun fieldsFor(
    kind: ShapeKind,
    triangleMode: TriangleMode,
    circleMode: CircleMode,
    rhombusMode: RhombusMode,
    polygonMode: PolygonMode,
): List<ShapeField> = when (kind) {
    ShapeKind.SQUARE -> listOf(ShapeField("side", "Side s"))
    ShapeKind.RECTANGLE -> listOf(
        ShapeField("width", "Width w"),
        ShapeField("height", "Height h"),
    )
    ShapeKind.TRIANGLE -> when (triangleMode) {
        TriangleMode.BASE_HEIGHT -> listOf(
            ShapeField("base", "Base b"),
            ShapeField("height", "Height h"),
        )
        TriangleMode.THREE_SIDES -> listOf(
            ShapeField("sideA", "Side a"),
            ShapeField("sideB", "Side b"),
            ShapeField("sideC", "Side c"),
        )
        TriangleMode.TWO_SIDES_INCLUDED_ANGLE -> listOf(
            ShapeField("sideA", "Side a"),
            ShapeField("sideB", "Side b"),
            ShapeField("angle", "Included angle C (°)"),
        )
    }
    ShapeKind.EQUILATERAL_TRIANGLE -> listOf(ShapeField("side", "Side s"))
    ShapeKind.PARALLELOGRAM -> listOf(
        ShapeField("base", "Base b"),
        ShapeField("height", "Height h"),
        ShapeField("side", "Slanted side a", optional = true),
    )
    ShapeKind.RHOMBUS -> when (rhombusMode) {
        RhombusMode.DIAGONALS -> listOf(
            ShapeField("diagP", "Diagonal p"),
            ShapeField("diagQ", "Diagonal q"),
        )
        RhombusMode.SIDE_ANGLE -> listOf(
            ShapeField("side", "Side a"),
            ShapeField("angle", "Angle θ (°)"),
        )
    }
    ShapeKind.TRAPEZOID -> listOf(
        ShapeField("baseA", "Base a"),
        ShapeField("baseB", "Base b"),
        ShapeField("height", "Height h"),
        ShapeField("leg1", "Leg 1", optional = true),
        ShapeField("leg2", "Leg 2", optional = true),
    )
    ShapeKind.CIRCLE -> when (circleMode) {
        CircleMode.RADIUS -> listOf(ShapeField("radius", "Radius r"))
        CircleMode.DIAMETER -> listOf(ShapeField("diameter", "Diameter d"))
        CircleMode.CIRCUMFERENCE -> listOf(ShapeField("circumference", "Circumference C"))
    }
    ShapeKind.SEMICIRCLE -> listOf(ShapeField("radius", "Radius r"))
    ShapeKind.ELLIPSE -> listOf(
        ShapeField("semiMajor", "Semi-axis a"),
        ShapeField("semiMinor", "Semi-axis b"),
    )
    ShapeKind.REGULAR_POLYGON -> when (polygonMode) {
        PolygonMode.SIDE -> listOf(
            ShapeField("sides", "Number of sides n"),
            ShapeField("side", "Side s"),
        )
        PolygonMode.CIRCUMRADIUS -> listOf(
            ShapeField("sides", "Number of sides n"),
            ShapeField("circumradius", "Circumradius R"),
        )
    }
}

private fun fieldError(field: ShapeField, raw: String): String? {
    if (raw.isEmpty()) return null
    if (field.id == "sides") {
        val sides = Num.parseIntOrNull(raw) ?: return "Enter a whole number"
        if (sides < BigInteger.valueOf(3)) return "A polygon needs at least 3 sides"
        if (sides > BigInteger.valueOf(10_000)) return "Too many sides to handle"
        return null
    }
    val value = Num.parseOrNull(raw) ?: return "Enter a valid number"
    if (value.signum() <= 0) return "${field.label} must be greater than zero"
    return null
}

// ------------------------------------------------------------- calculation

private fun compute(
    kind: ShapeKind,
    triangleMode: TriangleMode,
    circleMode: CircleMode,
    rhombusMode: RhombusMode,
    polygonMode: PolygonMode,
    values: Map<String, BigDecimal>,
    sides: Int,
): CalcResult<ShapeResult> = when (kind) {
    ShapeKind.SQUARE -> ShapeEngine.square(values.getValue("side"))
    ShapeKind.RECTANGLE -> ShapeEngine.rectangle(values.getValue("width"), values.getValue("height"))
    ShapeKind.TRIANGLE -> when (triangleMode) {
        TriangleMode.BASE_HEIGHT ->
            ShapeEngine.triangleBaseHeight(values.getValue("base"), values.getValue("height"))
        TriangleMode.THREE_SIDES -> ShapeEngine.triangleThreeSides(
            values.getValue("sideA"),
            values.getValue("sideB"),
            values.getValue("sideC"),
        )
        TriangleMode.TWO_SIDES_INCLUDED_ANGLE -> ShapeEngine.triangleTwoSidesAngle(
            values.getValue("sideA"),
            values.getValue("sideB"),
            values.getValue("angle"),
        )
    }
    ShapeKind.EQUILATERAL_TRIANGLE -> ShapeEngine.equilateralTriangle(values.getValue("side"))
    ShapeKind.PARALLELOGRAM -> ShapeEngine.parallelogram(
        values.getValue("base"),
        values.getValue("height"),
        values["side"],
    )
    ShapeKind.RHOMBUS -> when (rhombusMode) {
        RhombusMode.DIAGONALS ->
            ShapeEngine.rhombusFromDiagonals(values.getValue("diagP"), values.getValue("diagQ"))
        RhombusMode.SIDE_ANGLE ->
            ShapeEngine.rhombusFromSideAngle(values.getValue("side"), values.getValue("angle"))
    }
    ShapeKind.TRAPEZOID -> ShapeEngine.trapezoid(
        values.getValue("baseA"),
        values.getValue("baseB"),
        values.getValue("height"),
        values["leg1"],
        values["leg2"],
    )
    ShapeKind.CIRCLE -> ShapeEngine.circle(
        circleMode,
        when (circleMode) {
            CircleMode.RADIUS -> values.getValue("radius")
            CircleMode.DIAMETER -> values.getValue("diameter")
            CircleMode.CIRCUMFERENCE -> values.getValue("circumference")
        },
    )
    ShapeKind.SEMICIRCLE -> ShapeEngine.semicircle(values.getValue("radius"))
    ShapeKind.ELLIPSE -> ShapeEngine.ellipse(values.getValue("semiMajor"), values.getValue("semiMinor"))
    ShapeKind.REGULAR_POLYGON -> when (polygonMode) {
        PolygonMode.SIDE -> ShapeEngine.regularPolygonFromSide(sides, values.getValue("side"))
        PolygonMode.CIRCUMRADIUS ->
            ShapeEngine.regularPolygonFromCircumradius(sides, values.getValue("circumradius"))
    }
}

private fun summarize(
    fields: List<ShapeField>,
    inputs: Map<String, String>,
    kind: ShapeKind,
    unitLabel: String,
): String {
    val parts = fields.mapNotNull { field ->
        inputs[field.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { "${field.label} = $it" }
    }
    val unitPart = if (unitLabel == "unit") "" else " ($unitLabel)"
    return "${kind.title}: ${parts.joinToString(", ")}$unitPart"
}

// ----------------------------------------------------------------- formula

private fun formulaFor(
    kind: ShapeKind,
    triangleMode: TriangleMode,
    circleMode: CircleMode,
    rhombusMode: RhombusMode,
    polygonMode: PolygonMode,
): FormulaSpec = when (kind) {
    ShapeKind.SQUARE -> FormulaSpec(
        "A = s²    P = 4s    d = s√2",
        "A square with side s: area, perimeter and diagonal.",
        listOf("s" to "side length", "A" to "area", "P" to "perimeter", "d" to "diagonal (irrational)"),
    )
    ShapeKind.RECTANGLE -> FormulaSpec(
        "A = w × h    P = 2(w + h)    d = √(w² + h²)",
        "A rectangle with width w and height h.",
        listOf("w" to "width", "h" to "height", "d" to "diagonal"),
    )
    ShapeKind.TRIANGLE -> when (triangleMode) {
        TriangleMode.BASE_HEIGHT -> FormulaSpec(
            "A = ½ × b × h",
            "A base and the matching height fix the area only: the perimeter and the angles " +
                "are not determined by them.",
            listOf("b" to "base", "h" to "perpendicular height", "A" to "area"),
        )
        TriangleMode.THREE_SIDES -> FormulaSpec(
            "s = (a + b + c)/2    A = √(s(s − a)(s − b)(s − c))",
            "Heron's formula. The three sides determine the whole triangle; the angles follow " +
                "from the law of cosines.",
            listOf("a, b, c" to "side lengths", "s" to "semi-perimeter", "A" to "area"),
        )
        TriangleMode.TWO_SIDES_INCLUDED_ANGLE -> FormulaSpec(
            "A = ½ab·sin C    c = √(a² + b² − 2ab·cos C)",
            "Two sides and the angle between them; the third side follows from the law of cosines.",
            listOf("a, b" to "the two known sides", "C" to "angle between a and b", "c" to "third side"),
        )
    }
    ShapeKind.EQUILATERAL_TRIANGLE -> FormulaSpec(
        "A = (√3/4)s²    P = 3s    h = (√3/2)s    r = s√3/6    R = s√3/3",
        "An equilateral triangle with side s. The circumradius is exactly twice the inradius.",
        listOf("s" to "side length", "h" to "height", "r" to "inradius", "R" to "circumradius"),
    )
    ShapeKind.PARALLELOGRAM -> FormulaSpec(
        "A = b × h    P = 2(b + a)",
        "The base and the perpendicular height give the area; the slanted side is needed for " +
            "the perimeter and the angles.",
        listOf("b" to "base", "h" to "height", "a" to "slanted side", "A" to "area"),
    )
    ShapeKind.RHOMBUS -> when (rhombusMode) {
        RhombusMode.DIAGONALS -> FormulaSpec(
            "A = p·q/2    a = √((p/2)² + (q/2)²)    P = 4a",
            "A rhombus with diagonals p and q; the diagonals bisect each other at a right angle.",
            listOf("p, q" to "diagonals", "a" to "side", "A" to "area"),
        )
        RhombusMode.SIDE_ANGLE -> FormulaSpec(
            "A = a²·sin θ    d± = a√(2 ± 2cos θ)    P = 4a",
            "A rhombus with side a and interior angle θ; the diagonals sit on either side of " +
                "2a·cos(θ/2) and 2a·sin(θ/2).",
            listOf("a" to "side", "θ" to "interior angle", "d±" to "the two diagonals"),
        )
    }
    ShapeKind.TRAPEZOID -> FormulaSpec(
        "A = (a + b)/2 × h    P = a + b + c + d",
        "Parallel sides a and b with height h; the legs c and d are needed for the perimeter.",
        listOf("a, b" to "parallel sides", "h" to "height between them", "c, d" to "legs", "A" to "area"),
    )
    ShapeKind.CIRCLE -> FormulaSpec(
        "A = πr²    C = 2πr    d = 2r",
        "A circle with radius r; give exactly one of radius, diameter or circumference.",
        listOf("r" to "radius", "d" to "diameter", "C" to "circumference", "A" to "area"),
    )
    ShapeKind.SEMICIRCLE -> FormulaSpec(
        "A = πr²/2    arc = πr    P = πr + 2r",
        "Half of a disc: the perimeter includes the straight edge along the diameter.",
        listOf("r" to "radius", "arc" to "πr", "A" to "area", "P" to "perimeter (arc + diameter)"),
    )
    ShapeKind.ELLIPSE -> FormulaSpec(
        "A = πab    P ≈ π[3(a + b) − √((3a + b)(a + 3b))]",
        "Semi-axes a and b. The perimeter has no exact elementary form, so Ramanujan's second " +
            "approximation is used.",
        listOf("a" to "semi-axis (horizontal)", "b" to "semi-axis (vertical)", "A" to "area", "P" to "perimeter"),
    )
    ShapeKind.REGULAR_POLYGON -> when (polygonMode) {
        PolygonMode.SIDE -> FormulaSpec(
            "A = (n/4)s²·cot(π/n)    P = n·s    ρ = s/(2·tan(π/n))",
            "A regular n-gon with side s.",
            listOf("n" to "number of sides", "s" to "side length", "ρ" to "apothem", "A" to "area"),
        )
        PolygonMode.CIRCUMRADIUS -> FormulaSpec(
            "A = (n/2)R²·sin(2π/n)    s = 2R·sin(π/n)",
            "A regular n-gon inscribed in a circle of radius R.",
            listOf("n" to "number of sides", "R" to "circumradius", "s" to "side length", "A" to "area"),
        )
    }
}
