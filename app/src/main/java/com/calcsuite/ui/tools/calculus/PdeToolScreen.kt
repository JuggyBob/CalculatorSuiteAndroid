package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.engine.advanced.PdeClassification
import com.calcsuite.engine.advanced.PdeEngine
import com.calcsuite.engine.advanced.PdeKind
import com.calcsuite.engine.advanced.PdeSolution1D
import com.calcsuite.engine.advanced.PdeSolution2D
import com.calcsuite.engine.advanced.formatReal
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.Node
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.ExplanationCards
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.theme.fromHsl
import com.calcsuite.ui.theme.toHsl
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Partial Differential Equations tool.
 *
 * The scope is stated plainly: the heat and wave equations in one space " +
 * "dimension, and Laplace's and Poisson's equations in two, all solved by " +
 * "finite differences. The classification of a second-order equation is also " +
 * "given, because it is what tells the reader which kind of problem they " +
 * "have.
 */
@Composable
fun PdeToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Partial Differential Equations",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        PdeToolContent(args)
    }
}

/** The content of the tool, without the scaffold. */
@Composable
fun PdeToolContent(args: ToolArgs) {
    var kindName by rememberSaveable { mutableStateOf(PdeKind.HEAT.name) }
    var length by rememberSaveable { mutableStateOf("1") }
    var spaceSteps by rememberSaveable { mutableStateOf("40") }
    // Enough steps in time for the heat scheme to be stable with the
    // defaults below: the stability number is α·Δt ÷ Δx², and with Δx = 1/40
    // and Δt = 0.5/200 it is 0.4, which is inside the limit of one half.
    var timeSteps by rememberSaveable { mutableStateOf("200") }
    var timeEnd by rememberSaveable { mutableStateOf("0.5") }
    var alpha by rememberSaveable { mutableStateOf("0.1") }
    var speed by rememberSaveable { mutableStateOf("1") }
    var leftValue by rememberSaveable { mutableStateOf("0") }
    var rightValue by rememberSaveable { mutableStateOf("0") }
    var initialText by rememberSaveable { mutableStateOf("sin(pi*x)") }
    var velocityText by rememberSaveable { mutableStateOf("0") }
    // The boundary and the source are given for the two-dimensional
    // equations. The defaults are chosen so that the picture shows something:
    // a square whose edges are held at zero with heat flowing in from a point
    // in the middle is the classic example, and its answer is known.
    // The boundary is the same expression on all four sides, so it has to
    // vary to give a picture worth looking at: a constant boundary gives a
    // constant steady state, and a flat picture.
    var boundaryText by rememberSaveable { mutableStateOf("sin(pi*x)") }
    var sourceText by rememberSaveable { mutableStateOf("1") }
    var gridPoints by rememberSaveable { mutableStateOf("30") }
    var iterations by rememberSaveable { mutableStateOf("500") }
    var classificationA by rememberSaveable { mutableStateOf("1") }
    var classificationB by rememberSaveable { mutableStateOf("0") }
    var classificationC by rememberSaveable { mutableStateOf("0") }

    val kind = PdeKind.valueOf(kindName)

    FormulaCard(
        formula = "uₜ = α·uₓₓ\n" +
            "uₜₜ = c²·uₓₓ\n" +
            "uₓₓ + u_yy = f(x, y)",
        explanation = "A partial differential equation describes how a quantity " +
            "changes with several variables at once. The tool solves the equations " +
            "it supports by finite differences, and says plainly that the answer is " +
            "a numerical approximation.",
        variables = listOf(
            "α" to "the diffusivity, how fast the heat spreads",
            "c" to "the speed of the wave",
            "u" to "the quantity being modelled, such as the temperature",
        ),
    )

    // The chips carry the short names, because the full ones do not fit in
    // four equal columns on a narrow screen; the full name is given in the
    // panel below.
    SegmentedChoice(
        options = PdeKind.entries.map { it to it.shortLabel },
        selected = kind,
        onSelect = { kindName = it.name },
        label = "The equation",
    )

    SectionCard(title = "The problem") {
        Text(
            kind.formula,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            kind.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // The classification of a second-order equation.
    SectionCard(title = "Classification") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "A (uₓₓ)",
                value = classificationA,
                onValueChange = { classificationA = it },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "B (uₓy)",
                value = classificationB,
                onValueChange = { classificationB = it },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "C (u_yy)",
                value = classificationC,
                onValueChange = { classificationC = it },
                modifier = Modifier.weight(1f),
            )
        }
        val classification = PdeClassification.classify(
            classificationA.trim().toDoubleOrNull() ?: 0.0,
            classificationB.trim().toDoubleOrNull() ?: 0.0,
            classificationC.trim().toDoubleOrNull() ?: 0.0,
        )
        ResultRow(label = "The equation is", value = classification.label)
        Text(
            classification.meaning,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoNote(
            "The classification uses the discriminant B² − 4AC of the second-order part " +
                "of the equation. It assumes the equation is in two variables and that " +
                "the highest-order part is not degenerate.",
        )
    }

    var solution1D by remember { mutableStateOf<PdeSolution1D?>(null) }
    var solution2D by remember { mutableStateOf<PdeSolution2D?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(
        kind, length, spaceSteps, timeSteps, timeEnd, alpha, speed, leftValue,
        rightValue, initialText, velocityText, boundaryText, sourceText, gridPoints,
        iterations,
    ) {
        solution1D = null
        solution2D = null
        failure = null
        withContext(Dispatchers.Default) {
            when (kind) {
                PdeKind.HEAT, PdeKind.WAVE -> {
                    val initial = when (val r = CalculusParser.parse(initialText)) {
                        is CalcResult.Ok -> r.value
                        is CalcResult.Err -> null
                    }
                    if (initial == null) {
                        failure = "The initial condition could not be read."
                    } else {
                        val velocity = if (kind == PdeKind.WAVE) {
                            when (val r = CalculusParser.parse(velocityText)) {
                                is CalcResult.Ok -> r.value
                                is CalcResult.Err -> null
                            }
                        } else {
                            null
                        }
                        val answer = PdeEngine.solve1D(
                            kind = kind,
                            initial = initial,
                            variable = "x",
                            length = length.trim().toDoubleOrNull() ?: 1.0,
                            spaceSteps = spaceSteps.trim().toIntOrNull() ?: 40,
                            timeSteps = timeSteps.trim().toIntOrNull() ?: 200,
                            timeEnd = timeEnd.trim().toDoubleOrNull() ?: 0.5,
                            left = leftValue.trim().toDoubleOrNull() ?: 0.0,
                            right = rightValue.trim().toDoubleOrNull() ?: 0.0,
                            alpha = alpha.trim().toDoubleOrNull() ?: 0.1,
                            speed = speed.trim().toDoubleOrNull() ?: 1.0,
                            initialVelocity = velocity,
                        )
                        if (answer == null) {
                            failure = "The numbers were not acceptable. Check the length, " +
                                "the number of steps and the time."
                        } else {
                            solution1D = answer
                        }
                    }
                }
                PdeKind.LAPLACE, PdeKind.POISSON -> {
                    val boundary = when (val r = CalculusParser.parse(boundaryText)) {
                        is CalcResult.Ok -> r.value
                        is CalcResult.Err -> null
                    }
                    if (boundary == null) {
                        failure = "The boundary values could not be read."
                    } else {
                        val source = if (kind == PdeKind.POISSON) {
                            when (val r = CalculusParser.parse(sourceText)) {
                                is CalcResult.Ok -> r.value
                                is CalcResult.Err -> null
                            }
                        } else {
                            null
                        }
                        val answer = PdeEngine.solve2D(
                            kind = kind,
                            boundary = boundary,
                            source = source,
                            xVar = "x",
                            yVar = "y",
                            size = length.trim().toDoubleOrNull() ?: 1.0,
                            gridPoints = gridPoints.trim().toIntOrNull() ?: 30,
                            iterations = iterations.trim().toIntOrNull() ?: 500,
                        )
                        if (answer == null) {
                            failure = "The numbers were not acceptable. Check the size of " +
                                "the square and the number of grid points."
                        } else {
                            solution2D = answer
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------- the inputs

    if (kind == PdeKind.HEAT || kind == PdeKind.WAVE) {
        SectionCard(title = "The rod") {
            NumberField(label = "Length", value = length, onValueChange = { length = it })
            PlainField(
                label = "Initial condition u(x, 0)",
                value = initialText,
                onValueChange = { initialText = it },
                monospace = true,
                placeholder = "sin(pi*x)",
            )
            if (kind == PdeKind.WAVE) {
                PlainField(
                    label = "Initial velocity u_t(x, 0)",
                    value = velocityText,
                    onValueChange = { velocityText = it },
                    monospace = true,
                    placeholder = "0",
                )
                Text(
                    "The wave equation is second order in time, so it needs the velocity as " +
                        "well as the displacement. Leave it at zero for a wave that starts " +
                        "at rest.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(
                    label = "u at the left end",
                    value = leftValue,
                    onValueChange = { leftValue = it },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "u at the right end",
                    value = rightValue,
                    onValueChange = { rightValue = it },
                    modifier = Modifier.weight(1f),
                )
            }
            NumberField(
                label = "Number of points in space",
                value = spaceSteps,
                onValueChange = { spaceSteps = it },
                integerOnly = true,
            )
            NumberField(
                label = "Number of steps in time",
                value = timeSteps,
                onValueChange = { timeSteps = it },
                integerOnly = true,
            )
            NumberField(label = "Time to reach", value = timeEnd, onValueChange = { timeEnd = it })
            if (kind == PdeKind.HEAT) {
                NumberField(label = "Diffusivity α", value = alpha, onValueChange = { alpha = it })
            } else {
                NumberField(label = "Speed c", value = speed, onValueChange = { speed = it })
            }
        }
    } else {
        SectionCard(title = "The square") {
            NumberField(label = "Size of the side", value = length, onValueChange = { length = it })
            PlainField(
                label = "Boundary values",
                value = boundaryText,
                onValueChange = { boundaryText = it },
                monospace = true,
                placeholder = "0",
            )
            if (kind == PdeKind.POISSON) {
                PlainField(
                    label = "The source f(x, y)",
                    value = sourceText,
                    onValueChange = { sourceText = it },
                    monospace = true,
                    placeholder = "0",
                )
            }
            NumberField(
                label = "Points on each side",
                value = gridPoints,
                onValueChange = { gridPoints = it },
                integerOnly = true,
            )
            NumberField(
                label = "Maximum sweeps",
                value = iterations,
                onValueChange = { iterations = it },
                integerOnly = true,
            )
        }
    }

    // ------------------------------------------------------ the answer

    failure?.let { ErrorNotice(it) }

    val oneD = solution1D
    if (oneD != null) {
        ResultCard(
            label = "The solution",
            value = "u at the last time is " +
                formatReal(oneD.values.last().max()),
            caption = oneD.method,
            approx = true,
        )
        ResultRow(label = "Stability number", value = formatReal(oneD.stability.courant))
        ResultRow(
            label = "The limit is",
            value = formatReal(oneD.stability.limit),
        )
        ResultRow(
            label = "Stable",
            value = if (oneD.stability.stable) "yes" else "no — the numbers will blow up",
        )
        SectionCard(title = "Over space and time") {
            PdeFieldView(
                columns = oneD.values.first().size,
                rows = oneD.values.size,
                value = { row, column -> oneD.values[row][column] },
                modifier = Modifier.height(260.dp),
            )
            Text(
                "The horizontal axis is x, from 0 to ${formatReal(oneD.x.last())}, and the " +
                    "vertical axis is time, from 0 to ${formatReal(oneD.times.last())}. " +
                    "The colour is the value of u.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExplanationCards(
            com.calcsuite.engine.advanced.Explanation(
                definition = "The heat equation describes how heat spreads along a rod, " +
                    "and the wave equation describes how a wave travels along a string.",
                formula = oneD.kind.formula,
                symbols = listOf(
                    "u" to "the quantity being modelled, such as the temperature",
                    "Δx" to "the step in space",
                    "Δt" to "the step in time",
                ),
                conditions = listOf(
                    "The step in time has to satisfy the stability condition, or the " +
                        "numbers grow instead of settling.",
                    "The initial condition and the two boundary values have to be given.",
                ),
                method = oneD.method,
                steps = oneD.steps,
                interpretation = "Each row of the picture is the shape of the rod at one " +
                    "moment, and the rows together show how that shape changes.",
                verification = "The boundary values are imposed at every step, so they are " +
                    "satisfied exactly by the numerical solution.",
                limitations = "This is a numerical approximation with a truncation error " +
                    "of order Δx² in space. It is not the exact solution of the " +
                    "equation.",
            ),
        )
    }

    val twoD = solution2D
    if (twoD != null) {
        val range = PdeEngine.valueRange(twoD.values)
        val flat = PdeEngine.isConstant(twoD.values)
        ResultCard(
            label = "The steady state",
            value = "the values run from " + formatReal(range.first) + " to " +
                formatReal(range.second),
            caption = twoD.method,
            approx = true,
        )
        ResultRow(label = "Sweeps used", value = twoD.iterations.toString())
        if (flat) {
            // A steady state with one value everywhere is the right answer
            // for a constant boundary, but the picture is then a single
            // colour, which looks like a fault unless it is explained.
            InfoNote(
                "Every value in the field is the same to within a fraction " +
                    "of a percent, so the picture is one colour. That is what " +
                    "Laplace's equation gives when the boundary is a " +
                    "constant: the steady state is that same value all the " +
                    "way through. Give the boundary an expression that " +
                    "varies, such as sin(pi*x) or x, to see the field change.",
            )
        }
        SectionCard(title = "The field") {
            PdeFieldView(
                columns = twoD.x.size,
                rows = twoD.y.size,
                value = { row, column -> twoD.values[row][column] },
                modifier = Modifier.height(300.dp),
            )
            Text(
                "The colour is the value of u. The horizontal axis is x and the " +
                    "vertical axis is y, both from 0 to ${formatReal(twoD.x.last())}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExplanationCards(
            com.calcsuite.engine.advanced.Explanation(
                definition = "Laplace's equation says the value is an average of its " +
                    "neighbours, and Poisson's equation adds a source term.",
                formula = twoD.kind.formula,
                symbols = listOf(
                    "u" to "the quantity being modelled",
                    "f(x, y)" to "the source, for Poisson's equation",
                ),
                conditions = listOf(
                    "The boundary values have to be given, or the problem has no " +
                        "unique answer.",
                    "The relaxation is stopped when the values stop changing, so the " +
                        "answer is as accurate as the tolerance allows.",
                ),
                method = twoD.method,
                steps = twoD.steps,
                interpretation = "The picture is the steady state: the shape the " +
                    "quantity settles into once it has stopped changing.",
                verification = "The boundary values are never changed by the " +
                    "relaxation, so they hold exactly.",
                limitations = "This is a numerical approximation. The region is a " +
                    "square, and the boundary is given by a single expression along " +
                    "all four sides.",
            ),
        )
    }
}

/**
 * A colour map of a field of values.
 */
@Composable
private fun PdeFieldView(
    rows: Int,
    columns: Int,
    value: (Int, Int) -> Double,
    modifier: Modifier = Modifier,
) {
    var lowest = Double.POSITIVE_INFINITY
    var highest = Double.NEGATIVE_INFINITY
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val v = value(row, column)
            if (v < lowest) lowest = v
            if (v > highest) highest = v
        }
    }
    if (!lowest.isFinite()) lowest = 0.0
    if (!highest.isFinite()) highest = 1.0
    if (highest - lowest < 1e-12) highest = lowest + 1.0

    val middle = MaterialTheme.colorScheme.primary
    val scale = fieldScale(middle, MaterialTheme.colorScheme.tertiary)
    Canvas(modifier.fillMaxWidth()) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val v = value(row, column)
                val t = ((v - lowest) / (highest - lowest)).coerceIn(0.0, 1.0)
                drawRect(
                    color = colourFor(t, scale.first, scale.second, scale.third),
                    topLeft = Offset(column * cellWidth, size.height - (row + 1) * cellHeight),
                    size = Size(cellWidth + 0.6f, cellHeight + 0.6f),
                )
            }
        }
    }
}

/**
 * The three colours of a field: the accent's own dark shade for the low
 * values, the accent in the middle and the app's third colour for the high
 * ones.
 *
 * The picture is therefore in the app's palette, and follows the accent the
 * user has chosen, rather than in colours of its own.
 */
internal fun fieldScale(accent: Color, tertiary: Color): Triple<Color, Color, Color> {
    val hsl = accent.toHsl()
    val low = fromHsl(hsl.hue, maxOf(hsl.saturation, 0.45f), 0.28f)
    return Triple(low, accent, tertiary)
}

/**
 * The colour of a value on the scale: the low colour at the bottom, the
 * middle colour in the middle and the high colour at the top.
 */
private fun colourFor(t: Double, low: Color, middle: Color, high: Color): Color {
    return if (t < 0.5) {
        lerp(low, middle, (t / 0.5).toFloat())
    } else {
        lerp(middle, high, ((t - 0.5) / 0.5).toFloat())
    }
}

private fun lerp(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
