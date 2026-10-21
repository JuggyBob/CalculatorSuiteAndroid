package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.calcsuite.core.CalcResult
import com.calcsuite.engine.calculus.CalculusEngine
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.DerivativeResult
import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.EquationEngine
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.ExtremaResult
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.LimitResult
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import com.calcsuite.engine.calculus.OdeResult
import com.calcsuite.engine.calculus.RootResult
import com.calcsuite.engine.calculus.SeriesResult
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.clickableRow
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.Pill
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.theme.NumberTextStyle
import com.calcsuite.ui.tools.ToolArgs
import kotlin.math.abs
import com.calcsuite.ui.tools.prefill
import com.calcsuite.ui.tools.HistoryRecorder

/**
 * Calculus calculator.
 *
 * One expression field drives every operation; the operation selector decides
 * which panel (and which extra inputs) is shown. The same parsed tree feeds the
 * results, the worked examples and the graph, so the picture can never disagree
 * with the numbers above it.
 */
@Composable
fun CalculusToolScreen(args: ToolArgs, tool: CalculusTool) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Each tool is a tool of its own, so the operation is the tool's and is
    // not something the user chooses here: the other operations are separate
    // tools in the Calculus category.
    val operation = tool.operation

    var expression by rememberSaveable {
        mutableStateOf(args.prefill("expression") ?: tool.defaultExpression)
    }
    var variable by rememberSaveable { mutableStateOf(args.prefill("variable") ?: "x") }
    var atPoint by rememberSaveable { mutableStateOf(args.prefill("atPoint", "point") ?: "1") }
    var lowerLimit by rememberSaveable {
        mutableStateOf(args.prefill("lowerLimit", "lowerBound") ?: "0")
    }
    var upperLimit by rememberSaveable {
        mutableStateOf(args.prefill("upperLimit", "upperBound") ?: "3")
    }
    var secondVariable by rememberSaveable {
        mutableStateOf(args.prefill("secondVariable") ?: "y")
    }
    var seriesStart by rememberSaveable {
        mutableStateOf(args.prefill("seriesStart", "from", "firstTerm") ?: "1")
    }
    var seriesEnd by rememberSaveable {
        mutableStateOf(args.prefill("seriesEnd", "to", "lastTerm") ?: "10")
    }
    var centre by rememberSaveable { mutableStateOf(args.prefill("centre") ?: "0") }
    var order by rememberSaveable { mutableStateOf(args.prefill("order") ?: "6") }
    var side by rememberSaveable { mutableStateOf(BothSides) }
    var view by remember { mutableStateOf(GraphView.Default) }

    val parsed = remember(expression) { CalculusParser.parse(expression) }
    val node: Node? = (parsed as? CalcResult.Ok)?.value
    val parseError: String? = (parsed as? CalcResult.Err)?.error?.message

    val v = variable.trim().ifBlank { "x" }
    val second = secondVariable.trim().ifBlank { "y" }

    val derivative: DerivativeResult? = remember(expression, v) {
        DifferentiationEngine.derivative(expression, v).valueOrNull()
    }
    val derivativeNode: Node? = remember(derivative) {
        derivative?.let { (CalculusParser.parse(it.derivative) as? CalcResult.Ok)?.value }
    }

    ToolScaffold(
        title = tool.title,
        subtitle = if (expanded) null else tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
        // weight() is inert inside a vertically scrolling Column, so the
        // expanded view needs an unbounded column to give the graph the room.
        scrollable = !expanded,
        actions = {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (expanded) "Collapse tool" else "Expand tool",
                )
            }
        },
    ) {
        PlainField(
            label = "f($v) =",
            value = expression,
            onValueChange = { expression = it },
            monospace = true,
            placeholder = "e.g. x^3 - 2x + 1",
        )

        if (parseError != null && expression.isNotBlank()) {
            ErrorNotice(parseError)
        }

        when (operation) {
            CalcOperation.DERIVATIVE -> DerivativePanel(
                expression = expression,
                variable = v,
                secondVariable = second,
                node = node,
                derivative = derivative,
                atPoint = atPoint,
                onAtPoint = { atPoint = it },
                onVariable = { variable = it },
                onSecondVariable = { secondVariable = it },
                onRecord = args.onRecord,
            )

            CalcOperation.INTEGRAL -> IntegralPanel(
                expression = expression,
                variable = v,
                lowerLimit = lowerLimit,
                upperLimit = upperLimit,
                onLower = { lowerLimit = it },
                onUpper = { upperLimit = it },
                onRecord = args.onRecord,
            )

            CalcOperation.LIMIT -> LimitPanel(
                expression = expression,
                variable = v,
                point = atPoint,
                side = side,
                onPoint = { atPoint = it },
                onSide = { side = it },
                onRecord = args.onRecord,
            )

            CalcOperation.SERIES -> SeriesPanel(
                expression = expression,
                variable = v,
                start = seriesStart,
                end = seriesEnd,
                onStart = { seriesStart = it },
                onEnd = { seriesEnd = it },
                onRecord = args.onRecord,
            )

            CalcOperation.TAYLOR -> TaylorPanel(
                expression = expression,
                variable = v,
                centre = centre,
                order = order,
                onCentre = { centre = it },
                onOrder = { order = it },
            )

            CalcOperation.SUM -> SumProductPanel(
                expression = expression,
                variable = v,
                start = seriesStart,
                end = seriesEnd,
                onStart = { seriesStart = it },
                onEnd = { seriesEnd = it },
                onRecord = args.onRecord,
            )

            CalcOperation.EQUATION -> RootsPanel(
                expression = expression,
                lowerLimit = lowerLimit,
                upperLimit = upperLimit,
                onLower = { lowerLimit = it },
                onUpper = { upperLimit = it },
                onRecord = args.onRecord,
            )

            CalcOperation.EXTREMA -> ExtremaPanel(
                expression = expression,
                lowerLimit = lowerLimit,
                upperLimit = upperLimit,
                onLower = { lowerLimit = it },
                onUpper = { upperLimit = it },
                onRecord = args.onRecord,
            )

            // The differential equations have a screen of their own, with the
            // equation editor, the conditions, the numerical settings, the
            // slope field and the full explanation.
            CalcOperation.ODE -> OdeToolContent(args = args)

            // These four have a workspace of their own in the Calculus
            // category, so they are never reached from this screen.
            CalcOperation.GRADIENT,
            CalcOperation.FOURIER,
            CalcOperation.COMPLEX,
            CalcOperation.PDE -> InfoNote(
                "This operation has a tool of its own in the Calculus category.",
            )

            CalcOperation.GRAPH -> GraphPanel(
                expression = expression,
                derivativeNode = derivativeNode,
                lowerLimit = lowerLimit,
                upperLimit = upperLimit,
                onLower = { lowerLimit = it },
                onUpper = { upperLimit = it },
                view = view,
                onView = { view = it },
                curveColor = MaterialTheme.colorScheme.primary,
                derivativeColor = MaterialTheme.colorScheme.tertiary,
                // Built here because weight() is only available in the
                // surrounding ColumnScope, and it is inert while scrolling.
                graphCardModifier = if (expanded) Modifier.weight(1f) else Modifier,
                expanded = expanded,
            )
        }

        if (!expanded) {
            ExpandablePanel(title = "Conventions & Help") {
                CalculusHelp()
            }

        }
    }
}

private const val BothSides = "Both sides"
private const val FromRight = "From right"
private const val FromLeft = "From left"

// ------------------------------------------------------------------ derivative

@Composable
private fun DerivativePanel(
    expression: String,
    variable: String,
    secondVariable: String,
    node: Node?,
    derivative: DerivativeResult?,
    atPoint: String,
    onAtPoint: (String) -> Unit,
    onVariable: (String) -> Unit,
    onSecondVariable: (String) -> Unit,
    onRecord: HistoryRecorder,
) {
    FormulaCard(
        formula = "f′(x) = lim(h→0) (f(x+h) − f(x)) ÷ h  =  d/dx f(x)",
        explanation = "The derivative is the slope of the tangent line. It is applied " +
            "symbolically with the sum, product, quotient, power and chain rules, so " +
            "the printed answer is exact.",
        variables = listOf(
            "d/dx" to "Differentiate with respect to x",
            "∂f/∂y" to "Partial derivative — hold the other variables fixed",
        ),
    )

    PlainField(
        label = "Differentiate with respect to",
        value = variable,
        onValueChange = onVariable,
        monospace = true,
    )

    val numericAt = atPoint.trim().toDoubleOrNull()
    val pointLabel = numericAt?.let { NodePrinter.format(it) } ?: atPoint.ifBlank { "—" }
    val derivativeValue: Double? = remember(derivative, numericAt, variable) {
        if (numericAt == null) null
        else (CalculusParser.parse(derivative?.derivative ?: "") as? CalcResult.Ok)
            ?.let { Evaluator.eval(it.value, mapOf(variable to numericAt)).valueOrNull() }
    }
    val functionValue: Double? = remember(node, numericAt, variable) {
        if (numericAt == null) null
        else node?.let { Evaluator.eval(it, mapOf(variable to numericAt)).valueOrNull() }
    }
    val numericCheck: Double? = remember(node, numericAt, variable) {
        if (numericAt == null) null
        else node?.let { DifferentiationEngine.numericalDerivative(it, variable, numericAt).valueOrNull() }
    }

    ResultCard(
        label = "d/d$variable ($expression)",
        value = derivative?.derivative,
        caption = derivative?.steps?.firstOrNull(),
        error = if (derivative == null) "Enter a function of $variable." else null,
        onCopy = { args -> onRecord.record(
                            "d/d$variable ($expression) = $args", args,
                            mapOf(
                            "expression" to expression,
                            "variable" to variable,
                            "atPoint" to atPoint,
                            "secondVariable" to secondVariable,
                            ),
                        ) },
        copyValue = derivative?.derivative,
    )

    if (derivative != null) {
        SectionCard(title = "Checks at a point") {
            NumberField(
                label = "$variable =",
                value = atPoint,
                onValueChange = onAtPoint,
                placeholder = "e.g. 2",
            )
            ResultRow("f($variable) at $pointLabel", functionValue?.let { NodePrinter.format(it) } ?: "—")
            ResultRow(
                "f′($variable) at $pointLabel",
                derivativeValue?.let { NodePrinter.format(it) } ?: "—",
                emphasis = true,
            )
            if (numericCheck != null && derivativeValue != null) {
                val agrees = abs(numericCheck - derivativeValue) <=
                    1e-3 * maxOf(1.0, abs(numericCheck))
                ResultRow(
                    "Central-difference check",
                    NodePrinter.format(numericCheck) +
                        if (agrees) "  — agrees" else "  — differs, the expression may not be differentiable here",
                )
            }
            InfoNote("The numeric check differentiates the original function numerically, so it " +
                "confirms the symbolic answer rather than repeating it.")
        }

        ExpandablePanel(title = "Partial derivatives") {
            InfoNote(
                "A partial derivative treats every variable except the one being " +
                    "differentiated as a constant.",
            )
            PlainField(
                label = "Second variable",
                value = secondVariable,
                onValueChange = onSecondVariable,
                monospace = true,
            )
            val dFirst = remember(expression, variable) {
                DifferentiationEngine.derivative(expression, variable).valueOrNull()
            }
            val dSecond = remember(expression, secondVariable) {
                DifferentiationEngine.derivative(expression, secondVariable).valueOrNull()
            }
            val mixed = remember(expression, variable, secondVariable) {
                dFirst?.let {
                    DifferentiationEngine.derivative(it.derivative, secondVariable).valueOrNull()
                }
            }
            ResultRow("∂f/∂$variable", dFirst?.derivative ?: "—", emphasis = true)
            ResultRow("∂f/∂$secondVariable", dSecond?.derivative ?: "—")
            ResultRow("∂²f/∂$secondVariable∂$variable", mixed?.derivative ?: "—")
            InfoNote(
                "For f(x, y) = x^2·y^3 the derivatives are ∂f/∂x = 2x·y^3, " +
                    "∂f/∂y = 3x^2·y^2 and ∂²f/∂y∂x = 6x·y^2.",
            )
        }
    }
}

// -------------------------------------------------------------------- integral

@Composable
private fun IntegralPanel(
    expression: String,
    variable: String,
    lowerLimit: String,
    upperLimit: String,
    onLower: (String) -> Unit,
    onUpper: (String) -> Unit,
    onRecord: HistoryRecorder,
) {
    FormulaCard(
        formula = "∫ f(x) dx = F(x) + C   where F′(x) = f(x)      ∫ₐᵇ f(x) dx = F(b) − F(a)",
        explanation = "The definite integral is the signed area between the curve and the axis. " +
            "Where no elementary antiderivative exists, the indefinite panel says so instead " +
            "of guessing, and the definite panel falls back to numeric integration.",
    )

    val indefinite = remember(expression, variable) {
        IntegrationEngine.indefinite(expression, variable)
    }
    val antiderivative = (indefinite as? CalcResult.Ok)?.value
    ResultCard(
        label = "∫ ($expression) d$variable",
        value = antiderivative?.value,
        error = (indefinite as? CalcResult.Err)?.error?.message,
        caption = antiderivative?.let { if (it.exact) "Antiderivative — found exactly" else it.method },
        onCopy = { onRecord.record(
                            "∫ $expression d$variable", it,
                            mapOf(
                            "expression" to expression,
                            "variable" to variable,
                            ),
                        ) },
        copyValue = antiderivative?.value,
    )

    SectionCard(title = "Definite integral") {
        NumberField(label = "Lower limit a", value = lowerLimit, onValueChange = onLower)
        NumberField(label = "Upper limit b", value = upperLimit, onValueChange = onUpper)
    }

    val definite = remember(expression, variable, lowerLimit, upperLimit) {
        IntegrationEngine.definite(expression, variable, lowerLimit, upperLimit)
    }
    val definiteResult = (definite as? CalcResult.Ok)?.value
    ResultCard(
        label = "∫ from ${lowerLimit.ifBlank { "a" }} to ${upperLimit.ifBlank { "b" }} of $expression",
        value = definiteResult?.value,
        approx = definiteResult?.let { !it.exact } ?: false,
        error = (definite as? CalcResult.Err)?.error?.message,
        caption = definiteResult?.method,
        onCopy = { onRecord.record(
                            "∫ from $lowerLimit to $upperLimit of $expression", it,
                            mapOf(
                            "expression" to expression,
                            "variable" to variable,
                            "lowerLimit" to lowerLimit,
                            "upperLimit" to upperLimit,
                            ),
                        ) },
        copyValue = definiteResult?.value,
    )
    definiteResult?.let { StepsCard(steps = it.steps, title = "How this was computed") }
}

// ---------------------------------------------------------------------- limit

@Composable
private fun LimitPanel(
    expression: String,
    variable: String,
    point: String,
    side: String,
    onPoint: (String) -> Unit,
    onSide: (String) -> Unit,
    onRecord: HistoryRecorder,
) {
    FormulaCard(
        formula = "lim($variable→a) f($variable) = the value f approaches as $variable gets close to a",
        explanation = "If direct substitution is defined and continuous the limit is that " +
            "value. Otherwise it is taken numerically from the chosen side; when the two " +
            "sides disagree the limit does not exist and is reported as such.",
    )

    PlainField(
        label = "$variable approaches",
        value = point,
        onValueChange = onPoint,
        monospace = true,
        placeholder = "a number, or Infinity / -Infinity",
    )

    SegmentedChoice(
        options = listOf(BothSides, FromRight, FromLeft).map { it to it },
        selected = side,
        onSelect = onSide,
        label = "Approach",
    )

    val limit = remember(expression, variable, point, side) {
        CalculusEngine.limit(
            expression,
            variable,
            point,
            fromRight = side != FromLeft,
            fromLeft = side != FromRight,
        )
    }
    val result: LimitResult? = (limit as? CalcResult.Ok)?.value
    ResultCard(
        label = "lim($variable → ${point.ifBlank { "a" }}) $expression",
        value = result?.value,
        error = (limit as? CalcResult.Err)?.error?.message,
        caption = result?.method,
        onCopy = { onRecord.record(
                            "lim($variable → $point) $expression", it,
                            mapOf(
                            "expression" to expression,
                            "variable" to variable,
                            "atPoint" to point,
                            ),
                        ) },
        copyValue = result?.value,
    )
    result?.let { StepsCard(steps = it.steps, title = "How this was computed") }
}

// --------------------------------------------------------------------- series

@Composable
private fun SeriesPanel(
    expression: String,
    variable: String,
    start: String,
    end: String,
    onStart: (String) -> Unit,
    onEnd: (String) -> Unit,
    onRecord: HistoryRecorder,
) {
    FormulaCard(
        formula = "∑ₖ₌₁^∞ r^k converges ⇔ |r| < 1, summing to 1 ÷ (1 − r)      ∑ₖ₌₁^∞ 1/k^2 = π^2 ÷ 6",
        explanation = "A geometric series converges exactly when the ratio has magnitude " +
            "below one. For any other term the series is reported with its partial sum.",
    )

    NumberField(label = "First index k =", value = start, onValueChange = onStart, integerOnly = true)
    NumberField(label = "Last index k =", value = end, onValueChange = onEnd, integerOnly = true)

    val from = start.trim().toLongOrNull()
    val to = end.trim().toLongOrNull()
    val series = remember(expression, variable, from, to) {
        if (from == null || to == null) null else CalculusEngine.series(expression, variable, from, to)
    }
    val result: SeriesResult? = (series as? CalcResult.Ok)?.value
    ResultCard(
        label = "Series ∑ from k = $start of $expression",
        value = result?.verdict,
        error = if (from == null || to == null) {
            "Both indices must be whole numbers."
        } else {
            (series as? CalcResult.Err)?.error?.message
        },
        onCopy = { onRecord.record(
                            "series ∑ from k = $start of $expression", it,
                            mapOf(
                            "expression" to expression,
                            "variable" to variable,
                            "seriesStart" to start,
                            "seriesEnd" to end,
                            ),
                        ) },
        copyValue = result?.verdict,
    )
    result?.let { StepsCard(steps = it.steps, title = "Convergence test") }

    SectionCard(title = "Known convergent series") {
        ResultRow("∑ from 0 to ∞ of 0.5^k", "2  (ratio 0.5)")
        ResultRow("∑ from 1 to ∞ of 1/k^2", "π^2 ÷ 6 = " + NodePrinter.format(Math.PI * Math.PI / 6))
        ResultRow("∑ from 1 to ∞ of 1/k", "Diverges (harmonic)")
        ResultRow("∑ from 0 to ∞ of 1", "Diverges (terms do not approach zero)")
    }
}

// --------------------------------------------------------------------- Taylor

@Composable
private fun TaylorPanel(
    expression: String,
    variable: String,
    centre: String,
    order: String,
    onCentre: (String) -> Unit,
    onOrder: (String) -> Unit,
) {
    FormulaCard(
        formula = "f(x) = f(a) + f′(a)(x−a) + f″(a)/2! (x−a)^2 + f‴(a)/3! (x−a)^3 + …",
        explanation = "The Taylor series about a = 0 is the Maclaurin series. Each " +
            "coefficient is the n-th derivative at the centre divided by n!.",
    )

    NumberField(label = "Centre a", value = centre, onValueChange = onCentre)
    NumberField(
        label = "Order",
        value = order,
        onValueChange = onOrder,
        integerOnly = true,
        placeholder = "0 to 12",
    )

    val taylor = remember(expression, variable, centre, order) {
        CalculusEngine.taylor(expression, variable, centre, order)
    }
    val result: SeriesResult? = (taylor as? CalcResult.Ok)?.value
    ResultCard(
        label = "Taylor series of $expression about $variable = $centre",
        value = result?.verdict,
        error = (taylor as? CalcResult.Err)?.error?.message,
        caption = "e^x about 0: 1 + x + x^2/2! + x^3/3! + x^4/4! + …",
    )
    result?.let { StepsCard(steps = it.steps, title = "Terms") }
}

// ------------------------------------------------------------------ sum / Π

@Composable
private fun SumProductPanel(
    expression: String,
    variable: String,
    start: String,
    end: String,
    onStart: (String) -> Unit,
    onEnd: (String) -> Unit,
    onRecord: HistoryRecorder,
) {
    FormulaCard(
        formula = "∑ from k=a to b of g(k)      ∏ from k=a to b of g(k)",
        explanation = "Summation adds every term, product notation multiplies them. " +
            "Each term is evaluated at every integer index in the stated range.",
    )

    NumberField(label = "From k =", value = start, onValueChange = onStart, integerOnly = true)
    NumberField(label = "To k =", value = end, onValueChange = onEnd, integerOnly = true)

    val from = start.trim().toIntOrNull()
    val to = end.trim().toIntOrNull()
    val sum = remember(expression, variable, from, to) {
        if (from == null || to == null) null else CalculusEngine.summation(expression, variable, start, end)
    }
    val sumResult: SeriesResult? = (sum as? CalcResult.Ok)?.value
    ResultCard(
        label = "∑ from $start to $end of $expression",
        value = sumResult?.verdict,
        error = if (from == null || to == null) {
            "Limits must be whole numbers."
        } else {
            (sum as? CalcResult.Err)?.error?.message
        },
        onCopy = { onRecord.record(
                            "∑ from $start to $end of $expression", it,
                            mapOf(
                            "expression" to expression,
                            "variable" to variable,
                            "seriesStart" to start,
                            "seriesEnd" to end,
                            ),
                        ) },
        copyValue = sumResult?.verdict,
    )
    sumResult?.let { StepsCard(steps = it.steps, title = "Terms evaluated") }

    val product = remember(expression, variable, from, to) {
        if (from == null || to == null) null else CalculusEngine.product(expression, variable, start, end)
    }
    val productResult: SeriesResult? = (product as? CalcResult.Ok)?.value
    ResultCard(
        label = "∏ from $start to $end of $expression",
        value = productResult?.verdict,
        error = if (from == null || to == null) {
            "Limits must be whole numbers."
        } else {
            (product as? CalcResult.Err)?.error?.message
        },
        onCopy = { onRecord.record(
                            "∏ from $start to $end of $expression", it,
                            mapOf(
                            "expression" to expression,
                            "variable" to variable,
                            "seriesStart" to start,
                            "seriesEnd" to end,
                            ),
                        ) },
        copyValue = productResult?.verdict,
    )
    productResult?.let { StepsCard(steps = it.steps, title = "Factors evaluated") }

    SectionCard(title = "Worked examples") {
        ResultRow("∑ from 1 to 10 of k", "55")
        ResultRow("∑ from 1 to 100 of 1/k^2", NodePrinter.format(1.6449340668482264))
        ResultRow("∏ from 1 to 5 of k", "120  (that is 5!)")
        ResultRow("∑ from 0 to 20 of 0.5^k", NodePrinter.format(2.0 - 1.0 / 1048576.0))
    }
}

// --------------------------------------------------------------------- roots

@Composable
private fun RootsPanel(
    expression: String,
    lowerLimit: String,
    upperLimit: String,
    onLower: (String) -> Unit,
    onUpper: (String) -> Unit,
    onRecord: HistoryRecorder,
) {
    FormulaCard(
        formula = "Solve f(x) = 0 on [a, b]",
        explanation = "The interval is scanned for sign changes and each bracket is " +
            "refined by bisection, which always converges. Roots that touch the axis " +
            "without changing sign are found too.",
    )

    NumberField(label = "Search from", value = lowerLimit, onValueChange = onLower)
    NumberField(label = "Search to", value = upperLimit, onValueChange = onUpper)

    val roots = remember(expression, lowerLimit, upperLimit) {
        EquationEngine.solveRoots(expression, lowerLimit, upperLimit)
    }
    val result: RootResult? = (roots as? CalcResult.Ok)?.value
    ResultCard(
        label = "Roots of $expression = 0",
        value = result?.let { r ->
            if (r.roots.isEmpty()) "none in [$lowerLimit, $upperLimit]"
            else r.roots.joinToString(", ") { NodePrinter.format(it) }
        },
        error = (roots as? CalcResult.Err)?.error?.message,
        caption = result?.let { "${it.roots.size} root(s) in [$lowerLimit, $upperLimit]" },
        onCopy = { onRecord.record(
                            "roots of $expression = 0", it,
                            mapOf(
                            "expression" to expression,
                            "lowerLimit" to lowerLimit,
                            "upperLimit" to upperLimit,
                            ),
                        ) },
    )
    result?.let { StepsCard(steps = it.steps, title = "How this was computed") }

    SectionCard(title = "Worked examples") {
        ResultRow("x^2 - 4 = 0", "-2, 2")
        ResultRow(
            "x^2 - 2 = 0",
            "-" + NodePrinter.format(Math.sqrt(2.0)) + ", " + NodePrinter.format(Math.sqrt(2.0)),
        )
        ResultRow("x^3 - x = 0", "-1, 0, 1")
    }
}

// -------------------------------------------------------------------- extrema

@Composable
private fun ExtremaPanel(
    expression: String,
    lowerLimit: String,
    upperLimit: String,
    onLower: (String) -> Unit,
    onUpper: (String) -> Unit,
    onRecord: HistoryRecorder,
) {
    FormulaCard(
        formula = "Extrema: solve f′(x) = 0, then classify with f″(x)",
        explanation = "A negative second derivative marks a local maximum, a positive one " +
            "a local minimum.",
    )

    NumberField(label = "Interval from", value = lowerLimit, onValueChange = onLower)
    NumberField(label = "Interval to", value = upperLimit, onValueChange = onUpper)

    val extrema = remember(expression, lowerLimit, upperLimit) {
        EquationEngine.extrema(expression, lowerLimit, upperLimit)
    }
    val result: ExtremaResult? = (extrema as? CalcResult.Ok)?.value
    ResultCard(
        label = "Stationary points of $expression on [$lowerLimit, $upperLimit]",
        value = result?.let { r ->
            if (r.points.isEmpty()) "none in this interval"
            else r.points.joinToString("\n") {
                "x = ${NodePrinter.format(it.first)}    f(x) = ${NodePrinter.format(it.second)}    ${it.third}"
            }
        },
        error = (extrema as? CalcResult.Err)?.error?.message,
        caption = result?.let { "${it.points.size} point(s)" },
        onCopy = { onRecord.record(
                            "extrema of $expression", it,
                            mapOf(
                            "expression" to expression,
                            "lowerLimit" to lowerLimit,
                            "upperLimit" to upperLimit,
                            ),
                        ) },
    )
    result?.let { StepsCard(steps = it.steps, title = "How this was computed") }
}

// ----------------------------------------------------------------------- ODE


// ---------------------------------------------------------------------- graph

@Composable
private fun GraphPanel(
    expression: String,
    derivativeNode: Node?,
    lowerLimit: String,
    upperLimit: String,
    onLower: (String) -> Unit,
    onUpper: (String) -> Unit,
    view: GraphView,
    onView: (GraphView) -> Unit,
    curveColor: Color,
    derivativeColor: Color,
    graphCardModifier: Modifier,
    expanded: Boolean,
) {
    val node = remember(expression) { (CalculusParser.parse(expression) as? CalcResult.Ok)?.value }
    val areaFrom = lowerLimit.trim().toDoubleOrNull()
    val areaTo = upperLimit.trim().toDoubleOrNull()

    SectionCard(title = "View") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                if (view.showFunction) "f shown" else "f hidden",
                color = if (view.showFunction) curveColor else MaterialTheme.colorScheme.outline,
            )
            Pill(
                if (view.showDerivative) "f′ shown" else "f′ hidden",
                color = if (view.showDerivative) derivativeColor else MaterialTheme.colorScheme.outline,
            )
            Pill(
                if (view.showIntegral) "area shown" else "area hidden",
                color = if (view.showIntegral) curveColor else MaterialTheme.colorScheme.outline,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { onView(view.copy(showDerivative = !view.showDerivative)) }) {
                Text(if (view.showDerivative) "Hide f′" else "Show f′")
            }
            TextButton(onClick = { onView(view.copy(showIntegral = !view.showIntegral)) }) {
                Text(if (view.showIntegral) "Hide area" else "Show area")
            }
            TextButton(onClick = { onView(GraphView.Default) }) { Text("Reset view") }
        }
        if (expanded) {
            // Fullscreen: the two limits share a row so the plot gets the room.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumberField(
                    label = "Area from a",
                    value = lowerLimit,
                    onValueChange = onLower,
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "Area to b",
                    value = upperLimit,
                    onValueChange = onUpper,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            NumberField(label = "Shade area from a", value = lowerLimit, onValueChange = onLower)
            NumberField(label = "Shade area to b", value = upperLimit, onValueChange = onUpper)
        }
    }

    // The plot gets a box of its own with a caption immediately above it, so
    // the curve can never share space with the text around it. In fullscreen
    // the box flexes, but never below the height it has normally — expanding
    // must always give more room, not less.
    SectionCard(
        modifier = graphCardModifier.fillMaxWidth(),
        title = "Graph",
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) {
                        Modifier.weight(1f).heightIn(min = GraphHeight)
                    } else {
                        Modifier.height(GraphHeight)
                    },
                ),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            CalculusGraph(
                function = node,
                derivative = derivativeNode,
                integralArea = if (view.showIntegral && areaFrom != null && areaTo != null) {
                    areaFrom to areaTo
                } else {
                    null
                },
                view = view,
                onViewChange = onView,
                curveColor = curveColor,
                derivativeColor = derivativeColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // Explanatory notes sit outside the box and are dropped in fullscreen, the
    // same way the other calculators drop their subtitles.
    if (!expanded) {
        InfoNote("Drag to pan, pinch to zoom about the point you touch, Reset view to start over.")
        InfoNote("The dashed curve is f′; the shaded band is the area under f from a to b.")
    }

    if (node == null) {
        ErrorNotice("Enter a function to plot.")
    } else if (areaFrom != null) {
        ResultRow("f($lowerLimit)", NodePrinter.format(Evaluator.eval(node, mapOf("x" to areaFrom)).valueOrNull() ?: Double.NaN))
    }
}

// ------------------------------------------------------------------- help text

/** One symbol or syntax row inside the help panels. */
@Composable
private fun HelpLine(symbol: String, meaning: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            symbol,
            style = NumberTextStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(0.38f),
        )
        Text(
            meaning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.62f),
        )
    }
}

@Composable
private fun CalculusHelp() {
    SectionCard(title = "Symbols") {
        HelpLine("f′(x), df/dx", "Derivative of f with respect to x.")
        HelpLine("f″(x)", "Second derivative.")
        HelpLine("∂f/∂y", "Partial derivative with respect to y.")
        HelpLine("∫ f(x) dx", "Indefinite integral; the answer includes + C.")
        HelpLine("∫ₐᵇ f(x) dx", "Definite integral from a to b — the signed area.")
        HelpLine("lim(x→a) f(x)", "Limit as x approaches a.")
        HelpLine("Σ, ∑", "Summation: add every term from k = a to b.")
        HelpLine("Π, ∏", "Product notation: multiply every term from k = a to b.")
        HelpLine("dy/dx = f(x, y)", "Differential equation; enter only the right-hand side.")
    }

    SectionCard(title = "Input syntax") {
        HelpLine("^", "Power: x^2, e^x. Right-associative, so 2^3^2 = 2^9.")
        HelpLine("2x, 3(x+1)", "Implicit multiplication.")
        HelpLine("2sin(x)", "A number next to a function is a product.")
        HelpLine("π, τ, φ, e", "Constants; p is also accepted for π.")
        HelpLine("sin cos tan", "Angles are in radians, as in the scientific calculator.")
        HelpLine("asin acos atan", "Inverse trigonometric functions.")
        HelpLine("ln, log, log2, exp", "Natural, base-10 and base-2 logarithms, and e^x.")
        HelpLine("sqrt, cbrt, root(x, n)", "Square root, cube root, n-th root.")
        HelpLine("abs sign floor ceil round", "Sign and rounding helpers.")
        HelpLine("!", "Factorial, so 5! = 120.")
        HelpLine("min(a, b), max(a, b)", "Two-argument minimum and maximum.")
    }

    SectionCard(title = "What each operation does") {
        HelpLine(
            "d/dx",
            "Exact symbolic differentiation using the sum, product, quotient, power " +
                "and chain rules. Choose any variable to get a partial derivative.",
        )
        HelpLine(
            "∫ indefinite",
            "Exact for polynomials, 1/x, e^x, sin, cos and sums of those. When no " +
                "elementary antiderivative exists it says so rather than inventing one.",
        )
        HelpLine(
            "∫ definite",
            "Adaptive Simpson refinement, accurate to roughly 1 part in 10^12.",
        )
        HelpLine(
            "lim",
            "Direct substitution when the function is continuous, otherwise a numeric " +
                "limit that reports failure when the two sides disagree.",
        )
        HelpLine(
            "Series",
            "Ratio test for geometric series, and partial sums for any other term.",
        )
        HelpLine("Taylor", "Repeated symbolic differentiation, order 0 to 12.")
        HelpLine("Roots", "Scans for sign changes, then bisects each bracket to 1e-12.")
        HelpLine("Extrema", "Solves f′(x) = 0 and classifies each point with f″(x).")
        HelpLine("y′ = f(x, y)", "Classic fourth-order Runge–Kutta integration.")
        HelpLine("Graph", "Plots f and, on request, f′ and the area under the curve.")
    }

    SectionCard(title = "Worked examples") {
        HelpLine("d/dx x^3", "3x^2 — the power rule.")
        HelpLine("d/dx sin(x^2)", "cos(x^2)·2x — the chain rule.")
        HelpLine("d/dx x·sin(x)", "sin(x) + x·cos(x) — the product rule.")
        HelpLine("∫ x^2 dx", "x^3 ÷ 3 + C — exact.")
        HelpLine("∫ 1/x dx", "ln(x) + C — exact.")
        HelpLine("∫ 0 to π sin(x) dx", "2 — the area under one half-wave.")
        HelpLine("lim(x→0) sin(x)/x", "1 — an indeterminate 0 ÷ 0 form.")
        HelpLine("Σ from 1 to 10 of k", "55.")
        HelpLine("Π from 1 to 5 of k", "120, that is 5!.")
        HelpLine("roots of x^2 - 4 = 0", "-2 and 2.")
        HelpLine("extrema of x^2 - 4x", "Local minimum at x = 2 where f(2) = -4.")
        HelpLine("y′ = y, y(0) = 1", "y(2) ≈ 7.389, the value of e².")
    }

    InfoNote("Symbolic results are exact. Anything computed numerically is marked with ≈.")
    InfoNote(
        "The graph always plots along x, so use x as the independent variable. " +
            "For a partial derivative, change the “differentiate with respect to” field.",
    )
}

/** Operations offered by the tool, in the order they appear in the selector. */
