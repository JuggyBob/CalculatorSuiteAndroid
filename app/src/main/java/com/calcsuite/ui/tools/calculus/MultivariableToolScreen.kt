package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.engine.advanced.formatReal
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.multivariable.MultiExtras
import com.calcsuite.engine.calculus.multivariable.MultiOperation
import com.calcsuite.engine.calculus.multivariable.MultiResult
import com.calcsuite.engine.calculus.multivariable.MultivariableEngine
import com.calcsuite.engine.calculus.multivariable.MultivariableOperations
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.ExplanationCards
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.components.clickableRow
import com.calcsuite.ui.tools.ToolArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import com.calcsuite.ui.tools.prefill

/**
 * The Multivariable Calculus tool.
 *
 * The function, the variables and the point are given once, and the
 * operation decides what is computed. Every answer comes with the working
 * that produced it, and the surface is drawn from the same expression.
 */
@Composable
fun MultivariableToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Multivariable Calculus",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        MultivariableToolContent(args)
    }
}

/** The content of the tool, without the scaffold. */
@Composable
fun MultivariableToolContent(args: ToolArgs) {
    var functionText by rememberSaveable {
        mutableStateOf(args.prefill("functionText", "expression") ?: "x^2 + 3xy + y^2")
    }
    var variablesText by rememberSaveable {
        mutableStateOf(args.prefill("variablesText", "variables") ?: "x, y")
    }
    var pointText by rememberSaveable {
        mutableStateOf(args.prefill("pointText", "point") ?: "1, 2")
    }
    var operationName by rememberSaveable { mutableStateOf(MultiOperation.GRADIENT.name) }
    var orderText by rememberSaveable { mutableStateOf(args.prefill("orderText", "order") ?: "1") }
    var secondVariable by rememberSaveable { mutableStateOf("") }
    var directionText by rememberSaveable { mutableStateOf("1, 0") }
    var normalise by rememberSaveable { mutableStateOf(true) }
    var fieldText by rememberSaveable { mutableStateOf("") }
    var boundsText by rememberSaveable { mutableStateOf("0, 1, 0, 1") }
    var curveText by rememberSaveable { mutableStateOf("t, t") }
    var curveFrom by rememberSaveable { mutableStateOf("0") }
    var curveTo by rememberSaveable { mutableStateOf("1") }
    var boxText by rememberSaveable { mutableStateOf("-5, 5, -5, 5") }
    var view by remember { mutableStateOf(SurfaceView.Default) }

    val operation = MultiOperation.valueOf(operationName)
    val variables = variablesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val point = pointText.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }.toDoubleArray()

    // The variables are known, so the multiplication signs between them can be
    // put in before the expression is read.
    val parsed = remember(functionText, variablesText, variables) {
        when (val result = MultivariableEngine.parse(functionText, variables)) {
            is CalcResult.Ok -> result.value
            is CalcResult.Err -> null
        }
    }
    val parseError = remember(functionText, variablesText, variables) {
        when (val result = MultivariableEngine.parse(functionText, variables)) {
            is CalcResult.Ok -> null
            is CalcResult.Err -> result.error.message
        }
    }

    // The extra inputs for the operation.
    val extras = remember(
        operation, orderText, secondVariable, directionText, normalise, fieldText,
        boundsText, curveText, curveFrom, curveTo, boxText, variables,
    ) {
        MultiExtras(
            order = orderText.trim().toIntOrNull() ?: 1,
            secondVariable = secondVariable.trim().ifBlank { null },
            direction = directionText.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
                .toDoubleArray().takeIf { it.isNotEmpty() },
            normalise = normalise,
            vectorField = fieldText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    when (val r = MultivariableEngine.parse(line)) {
                        is CalcResult.Ok -> r.value
                        is CalcResult.Err -> null
                    }
                },
            bounds = boundsText.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
                .chunked(2).filter { it.size == 2 }.map { doubleArrayOf(it[0], it[1]) },
            curve = curveText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    when (val r = MultivariableEngine.parse(line)) {
                        is CalcResult.Ok -> r.value
                        is CalcResult.Err -> null
                    }
                },
            curveFrom = curveFrom.trim().toDoubleOrNull() ?: 0.0,
            curveTo = curveTo.trim().toDoubleOrNull() ?: 1.0,
            box = boxText.split(",").map { it.trim().toDoubleOrNull() ?: 0.0 }
                .chunked(2).filter { it.size == 2 }.map { doubleArrayOf(it[0], it[1]) },
        )
    }

    // The calculation runs off the main thread, and a newer request cancels
    // the one before it, so a stale answer cannot replace a fresh one.
    var outcome by remember { mutableStateOf<CalcResult<MultiResult>?>(null) }
    LaunchedEffect(parsed, operation, variables, point, extras) {
        if (parsed == null) {
            outcome = null
            return@LaunchedEffect
        }
        outcome = withContext(Dispatchers.Default) {
            MultivariableOperations.run(operation, parsed, variables, point, extras)
        }
    }

    FormulaCard(
        formula = "f(x, y) = x² + 3xy + y²\n" +
            "∇f = (∂f/∂x, ∂f/∂y)\n" +
            "H = ∂²f/∂(x, y)²\n" +
            "Dᵤf = ∇f · u",
        explanation = "A function of several variables can be read in three directions at " +
            "once: the partial derivatives say how it changes in each variable on its " +
            "own, the gradient puts them together, and the Hessian says how the surface " +
            "curves.",
        variables = listOf(
            "∂f/∂x" to "the partial derivative with respect to x, holding y still",
            "∇f" to "the gradient, the vector of the partial derivatives",
            "H" to "the Hessian, the matrix of the second partial derivatives",
        ),
    )

    SectionCard(title = "Function") {
        PlainField(
            label = "f(x, y) =",
            value = functionText,
            onValueChange = { functionText = it },
            monospace = true,
            placeholder = "x^2 + 3xy + y^2",
        )
        PlainField(
            label = "Variables",
            value = variablesText,
            onValueChange = { variablesText = it },
            monospace = true,
            placeholder = "x, y",
        )
        PlainField(
            label = "Point",
            value = pointText,
            onValueChange = { pointText = it },
            monospace = true,
            placeholder = "1, 2",
        )
        parseError?.let { ErrorNotice(it) }
        Text(
            "Examples:  x^2 + 3xy + y^2   ·   x^2 + y^2 + z^2   ·   sin(x)·cos(y)   ·   x·exp(y)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard(title = "Operation") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (candidate in MultiOperation.entries) {
                val selected = candidate == operation
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier.clickableRow { operationName = candidate.name },
                ) {
                    Text(
                        candidate.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    // The inputs the operation needs.
    when (operation) {
        MultiOperation.PARTIAL -> SectionCard(title = "Partial derivative") {
            NumberField(
                label = "Order",
                value = orderText,
                onValueChange = { orderText = it },
                integerOnly = true,
            )
            if (variables.size > 1) {
                PlainField(
                    label = "Second variable (for a mixed partial)",
                    value = secondVariable,
                    onValueChange = { secondVariable = it },
                    monospace = true,
                    placeholder = variables.getOrNull(1) ?: "y",
                )
            }
        }

        MultiOperation.DIRECTIONAL -> SectionCard(title = "Direction") {
            PlainField(
                label = "Direction vector",
                value = directionText,
                onValueChange = { directionText = it },
                monospace = true,
                placeholder = "1, 0",
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { normalise = true }) {
                    Text(if (normalise) "Normalised ✓" else "Normalise")
                }
                TextButton(onClick = { normalise = false }) {
                    Text(if (!normalise) "As given ✓" else "Use as given")
                }
            }
            InfoNote(
                "A normalised direction gives the rate of change per unit of distance; " +
                    "used as given, it is the rate of change per unit of that vector.",
            )
        }

        MultiOperation.JACOBIAN,
        MultiOperation.DIVERGENCE,
        MultiOperation.CURL -> SectionCard(title = "Vector field") {
            PlainField(
                label = "Components, one per line",
                value = fieldText,
                onValueChange = { fieldText = it },
                monospace = true,
                placeholder = if (operation == MultiOperation.CURL) {
                    "-y\nx\n0"
                } else {
                    "x\ny"
                },
            )
            InfoNote(
                "Give one expression for each component, in the order of the variables.",
            )
        }

        MultiOperation.DOUBLE_INTEGRAL -> SectionCard(title = "Bounds") {
            PlainField(
                label = "Bounds, low then high for each variable",
                value = boundsText,
                onValueChange = { boundsText = it },
                monospace = true,
                placeholder = "0, 1, 0, 1",
            )
        }

        MultiOperation.TRIPLE_INTEGRAL -> SectionCard(title = "Bounds") {
            PlainField(
                label = "Bounds, low then high for each variable",
                value = boundsText,
                onValueChange = { boundsText = it },
                monospace = true,
                placeholder = "0, 1, 0, 1, 0, 1",
            )
        }

        MultiOperation.LINE_INTEGRAL -> SectionCard(title = "Curve") {
            PlainField(
                label = "x(t), y(t)",
                value = curveText,
                onValueChange = { curveText = it },
                monospace = true,
                placeholder = "t, t",
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumberField(
                    label = "From t",
                    value = curveFrom,
                    onValueChange = { curveFrom = it },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "To t",
                    value = curveTo,
                    onValueChange = { curveTo = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        MultiOperation.CRITICAL -> SectionCard(title = "Region to search") {
            PlainField(
                label = "Bounds of the region",
                value = boxText,
                onValueChange = { boxText = it },
                monospace = true,
                placeholder = "-5, 5, -5, 5",
            )
            InfoNote(
                "A critical point outside the region is not found, so the region should " +
                    "be chosen to contain the ones you are looking for.",
            )
        }

        else -> Unit
    }

    // ------------------------------------------------------------ the answer

    val result = (outcome as? CalcResult.Ok)?.value
    when (val current = outcome) {
        null -> if (parseError == null) InfoNote("Working…")
        is CalcResult.Err -> ErrorNotice(current.error.message)
        is CalcResult.Ok -> {
            val answer = current.value
            ResultCard(
                label = answer.operation.label,
                value = answer.values.firstOrNull(),
                caption = answer.values.drop(1).takeIf { it.isNotEmpty() }?.joinToString("   "),
                onCopy = {
                    args.onRecord.record(
                        answer.operation.label,
                        it,
                        mapOf(
                            "functionText" to functionText,
                            "variablesText" to variablesText,
                            "pointText" to pointText,
                            "orderText" to orderText,
                        ),
                    )
                },
                copyValue = answer.values.firstOrNull(),
            )
            for (extra in answer.values.drop(1)) {
                ResultRow(label = "", value = extra)
            }
            ExplanationCards(answer.explanation)
        }
    }

    // ---------------------------------------------------------- the surface

    if (parsed != null && variables.size >= 2) {
        SectionCard(title = "Surface and gradient") {
            SurfaceView(
                function = parsed,
                xName = variables[0],
                yName = variables[1],
                view = view,
                onViewChange = { view = it },
                modifier = Modifier.height(280.dp),
                // The surface and the gradient field below it show the same
                // part of the function, so the two pictures agree.
                xMin = -5.0,
                xMax = 5.0,
                yMin = -5.0,
                yMax = 5.0,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { view = SurfaceView.Default }) { Text("Reset view") }
            }
            Text(
                "Drag to turn the surface and pinch to zoom. The colours show the height.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GradientField(
                function = parsed,
                variables = variables,
                criticalPoints = result?.points ?: emptyList(),
                modifier = Modifier.height(220.dp),
            )
            Text(
                "The level curves of the function, with the gradient drawn at sample " +
                    "points and any critical points marked.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A two-dimensional picture of the function: the level curves, the gradient
 * vectors and any critical points.
 *
 * The level curves are traced with the marching-squares rule: the values are
 * sampled on a grid, and in each small square the places where the value
 * passes a level are found and joined. The result follows the true contours
 * and, because every piece of it lies inside one small square, it can never
 * be drawn as a long line across the picture.
 *
 * The gradient arrows are all the same length on the screen and point in the
 * true direction of the gradient. There are the same number of world units
 * in a pixel on each axis, so the arrows are not distorted.
 *
 * Everything is clipped to the box, so nothing can be drawn outside it.
 */
@Composable
private fun GradientField(
    function: Node,
    variables: List<String>,
    criticalPoints: List<DoubleArray>,
    modifier: Modifier = Modifier,
) {
    val xName = variables[0]
    val yName = variables[1]
    val gx = remember(function, xName, yName) {
        MultivariableEngine.gradient(function, listOf(xName, yName)).valueOrNull()
    }
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            // The window of the picture: ten units across, and as much up and
            // down as the shape of the box allows, so that the scales on the
            // two axes agree.
            val xMin = -5.0
            val xMax = 5.0
            val unitsPerPx = (xMax - xMin) / width
            val ySpan = unitsPerPx * height
            val yMin = -ySpan / 2
            val yMax = ySpan / 2

            fun toPx(x: Double, y: Double) = Offset(
                ((x - xMin) / (xMax - xMin) * width).toFloat(),
                ((yMax - y) / (yMax - yMin) * height).toFloat(),
            )

            // --- The values on the grid -------------------------------------
            val steps = 48
            val stepX = (xMax - xMin) / steps
            val stepY = (yMax - yMin) / steps
            val values = Array(steps + 1) { DoubleArray(steps + 1) }
            var lowest = Double.POSITIVE_INFINITY
            var highest = Double.NEGATIVE_INFINITY
            for (i in 0..steps) {
                val x = xMin + stepX * i
                for (j in 0..steps) {
                    val y = yMin + stepY * j
                    val value = Evaluator.eval(function, mapOf(xName to x, yName to y))
                        .valueOrNull()
                    // A value that is not a number, or one so large that it
                    // would swamp the others, is left out.
                    val usable = value != null && value.isFinite() && abs(value) < 1e12
                    values[i][j] = if (usable) value!! else Double.NaN
                    if (usable) {
                        if (value!! < lowest) lowest = value
                        if (value > highest) highest = value
                    }
                }
            }
            if (!lowest.isFinite() || !highest.isFinite()) return@Canvas

            // --- The level curves -------------------------------------------
            // The levels are chosen by dividing the values into equal parts,
            // so each contour has about the same amount of picture to itself.
            //
            // Spreading them evenly between the smallest and largest value
            // would not do: a function such as y⁹ has a range so wide that
            // evenly spread levels all end up near the edges of the picture,
            // bunched together. Dividing the values into equal parts puts one
            // contour in each part of the picture instead.
            val levels = if (highest - lowest > 1e-9) {
                val sorted = values.asSequence()
                    .flatMap { row -> row.asSequence() }
                    .filter { it.isFinite() }
                    .sorted()
                    .toList()
                val count = 9
                (1..count)
                    .map { (sorted.size * it) / (count + 1) }
                    .map { sorted[it.coerceIn(0, sorted.size - 1)] }
                    .distinct()
            } else {
                emptyList()
            }

            val contourPath = Path()
            for (level in levels) {
                for (i in 0 until steps) {
                    for (j in 0 until steps) {
                        val v00 = values[i][j]
                        val v10 = values[i + 1][j]
                        val v11 = values[i + 1][j + 1]
                        val v01 = values[i][j + 1]
                        if (v00.isNaN() || v10.isNaN() || v11.isNaN() || v01.isNaN()) {
                            continue
                        }
                        val x0 = xMin + stepX * i
                        val y0 = yMin + stepY * j
                        val x1 = x0 + stepX
                        val y1 = y0 + stepY

                        // The case of the marching-squares rule, from the
                        // four corners of the square.
                        val index = (if (v00 > level) 1 else 0) or
                            (if (v10 > level) 2 else 0) or
                            (if (v11 > level) 4 else 0) or
                            (if (v01 > level) 8 else 0)

                        // The points where the level crosses the four edges.
                        fun bottom() = toPx(
                            x0 + stepX * (level - v00) / (v10 - v00),
                            y0,
                        )

                        fun top() = toPx(
                            x0 + stepX * (level - v01) / (v11 - v01),
                            y1,
                        )

                        fun left() = toPx(
                            x0,
                            y0 + stepY * (level - v00) / (v01 - v00),
                        )

                        fun right() = toPx(
                            x1,
                            y0 + stepY * (level - v10) / (v11 - v10),
                        )

                        // Each case joins two crossings, or four of them
                        // where the square is cut in two.
                        fun join(a: Offset, b: Offset) {
                            contourPath.moveTo(a.x, a.y)
                            contourPath.lineTo(b.x, b.y)
                        }

                        when (index) {
                            1, 14 -> join(left(), bottom())
                            2, 13 -> join(bottom(), right())
                            3, 12 -> join(left(), right())
                            4, 11 -> join(right(), top())
                            6, 9 -> join(bottom(), top())
                            7, 8 -> join(left(), top())
                            5 -> {
                                // The square is cut in two; the middle value
                                // says which of the two ways round it is.
                                val middle = (v00 + v10 + v11 + v01) / 4
                                if (middle > level) {
                                    join(left(), top())
                                    join(bottom(), right())
                                } else {
                                    join(left(), bottom())
                                    join(top(), right())
                                }
                            }
                            10 -> {
                                val middle = (v00 + v10 + v11 + v01) / 4
                                if (middle > level) {
                                    join(left(), bottom())
                                    join(top(), right())
                                } else {
                                    join(left(), top())
                                    join(bottom(), right())
                                }
                            }
                        }
                    }
                }
            }
            drawPath(
                contourPath,
                accent.copy(alpha = 0.55f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.4f),
            )

            // --- The gradient vectors ---------------------------------------
            if (gx != null) {
                val grid = 9
                val arrowPixels = size.minDimension / 22f
                for (i in 0..grid) {
                    for (j in 0..grid) {
                        val x = xMin + (xMax - xMin) * i / grid
                        val y = yMin + (yMax - yMin) * j / grid
                        val dx = Evaluator.eval(gx[0], mapOf(xName to x, yName to y))
                            .valueOrNull() ?: continue
                        val dy = Evaluator.eval(gx[1], mapOf(xName to x, yName to y))
                            .valueOrNull() ?: continue
                        if (!dx.isFinite() || !dy.isFinite()) continue
                        val magnitude = kotlin.math.hypot(dx, dy)
                        if (magnitude < 1e-12) continue
                        // The gradient points up the slope. The screen's y axis
                        // points down, so the arrow is drawn with the y part
                        // turned the other way.
                        val ux = dx / magnitude
                        val uy = -dy / magnitude
                        val start = toPx(x, y)
                        val end = Offset(
                            (start.x + ux * arrowPixels).toFloat(),
                            (start.y + uy * arrowPixels).toFloat(),
                        )
                        drawLine(accent, start, end, strokeWidth = 2f)
                        // The head of the arrow.
                        val head = 5f
                        val angle = kotlin.math.atan2(uy.toDouble(), ux.toDouble())
                        for (side in listOf(2.6, -2.6)) {
                            drawLine(
                                accent,
                                end,
                                Offset(
                                    (end.x + head * kotlin.math.cos(angle + side)).toFloat(),
                                    (end.y + head * kotlin.math.sin(angle + side)).toFloat(),
                                ),
                                strokeWidth = 2f,
                            )
                        }
                    }
                }
            }

            // --- The critical points ----------------------------------------
            for (p in criticalPoints) {
                val point = toPx(p[0], p[1])
                drawCircle(Color(0xFFFFC46B), radius = 7f, center = point)
                drawCircle(Color(0xFF101410), radius = 3f, center = point)
            }
        }
    }
}
