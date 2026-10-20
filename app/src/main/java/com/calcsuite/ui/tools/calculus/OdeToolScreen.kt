package com.calcsuite.ui.tools.calculus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.ode.NumericalMethod
import com.calcsuite.engine.calculus.ode.NumericalResult
import com.calcsuite.engine.calculus.ode.NumericalSettings
import com.calcsuite.engine.calculus.ode.OdeCondition
import com.calcsuite.engine.calculus.ode.OdeConditionParser
import com.calcsuite.engine.calculus.ode.OdeParser
import com.calcsuite.engine.calculus.ode.OdeRequest
import com.calcsuite.engine.calculus.ode.OdeResult
import com.calcsuite.engine.calculus.ode.OdeSolution
import com.calcsuite.engine.calculus.ode.SolveMode
import com.calcsuite.ui.components.ErrorNotice
import com.calcsuite.ui.components.ExpandablePanel
import com.calcsuite.ui.components.FormulaCard
import com.calcsuite.ui.components.InfoNote
import com.calcsuite.ui.components.NumberField
import com.calcsuite.ui.components.PlainField
import com.calcsuite.ui.components.ResultCard
import com.calcsuite.ui.components.ResultRow
import com.calcsuite.ui.components.SectionCard
import com.calcsuite.ui.components.SegmentedChoice
import com.calcsuite.ui.components.StepsCard
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The Differential Equations tool.
 *
 * The equation is typed in the notation people use, the method is chosen by
 * the tool or by the user, and every answer comes with the working that
 * produced it: the classification of the equation, why the method applies, the
 * transformations step by step, how the conditions were used, and a check
 * that the answer really is a solution.
 */
@Composable
fun OdeToolScreen(args: ToolArgs) {
    ToolScaffold(
        title = "Differential Equations",
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        OdeToolContent(args)
    }
}

/**
 * The content of the tool, without the surrounding scaffold.
 *
 * The shared calculus screen reuses this for its differential-equation
 * operation, so both entry points show exactly the same thing.
 */
@Composable
fun OdeToolContent(args: ToolArgs) {
    var equation by rememberSaveable { mutableStateOf("dy/dx = 2y") }
    var modeName by rememberSaveable { mutableStateOf(SolveMode.SYMBOLIC.name) }
    var condition by rememberSaveable { mutableStateOf("y(0) = 1") }
    var secondCondition by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("0") }
    var to by rememberSaveable { mutableStateOf("4") }
    var step by rememberSaveable { mutableStateOf("0.1") }
    var methodName by rememberSaveable { mutableStateOf(NumericalMethod.RK4.name) }
    var showField by rememberSaveable { mutableStateOf(true) }
    var view by remember { mutableStateOf(PlotViewport(span = 12.0)) }

    val mode = SolveMode.valueOf(modeName)
    val method = NumericalMethod.valueOf(methodName)

    // The parsed equation, so the order and the variables are known before
    // anything is solved.
    val parsed = remember(equation) {
        when (val result = OdeParser.parse(equation)) {
            is com.calcsuite.core.CalcResult.Ok -> result.value
            is com.calcsuite.core.CalcResult.Err -> null
        }
    }

    // The conditions, with a message when they cannot be read.
    val firstCondition = remember(condition, parsed) {
        OdeConditionParser.parse(condition, parsed?.dependent ?: "y")
    }
    val secondConditionParsed = remember(secondCondition, parsed) {
        OdeConditionParser.parse(secondCondition, parsed?.dependent ?: "y")
    }
    val conditionProblem = remember(condition, secondCondition, parsed) {
        if (condition.isBlank() || parsed == null) null
        else if (firstCondition == null && secondCondition.isNotBlank()) {
            "The condition could not be read. Write it as y(0) = 3."
        } else if (firstCondition == null) {
            "The condition could not be read. Write it as y(0) = 3."
        } else {
            null
        }
    }

    // The symbolic mode always gives the general solution, so the conditions
    // only take part in the initial-value and numerical modes.
    val conditions = remember(firstCondition, secondConditionParsed, mode) {
        if (mode == SolveMode.SYMBOLIC) emptyList()
        else listOfNotNull(firstCondition, secondConditionParsed)
    }

    val request = remember(
        equation,
        mode,
        conditions,
        from,
        to,
        step,
        method,
    ) {
        OdeRequest(
            equation = equation,
            mode = mode,
            conditions = conditions,
            numerical = NumericalSettings(
                from = from.trim().toDoubleOrNull() ?: 0.0,
                to = to.trim().toDoubleOrNull() ?: 4.0,
                step = step.trim().toDoubleOrNull() ?: 0.1,
                method = method,
            ),
        )
    }

    // The solve runs off the main thread, and a newer request cancels the one
    // before it, so a stale answer can never replace a fresh one.
    var result by remember { mutableStateOf<OdeResult?>(null) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(request) {
        running = true
        val solved = withContext(Dispatchers.Default) { OdeSolver(request) }
        result = solved
        running = false
    }

    FormulaCard(
        formula = "y′ = f(x, y)\naₙ·y⁽ⁿ⁾ + … + a₁·y′ + a₀·y = f(x, y, y′)",
        explanation = "A differential equation relates the rate of change of y to x and y " +
            "itself. The tool reads the equation, works out its order and whether it is " +
            "linear, separable or of constant-coefficient form, and then chooses a method " +
            "that is known to work for that shape — and shows the working.",
        variables = listOf(
            "y′" to "dy/dx — the rate of change of y with respect to x",
            "y″" to "d²y/dx² — the second derivative",
            "f(x, y)" to "the right-hand side, which may mention x and y",
        ),
    )

    SectionCard(title = "Equation") {
        PlainField(
            label = "Differential equation",
            value = equation,
            onValueChange = { equation = it },
            monospace = true,
            placeholder = "dy/dx = 2y",
        )
        InfoNote(
            "Write the derivative as dy/dx, y′ or y″. The left-hand side may be a sum of " +
                "derivatives, such as y″ − 3y′ + 2y, and the right-hand side may mention " +
                "x and y.",
        )
        Text(
            "Examples:  dy/dx = 2y   ·   y′ + y = x   ·   y″ − 3y′ + 2y = 0   ·   dy/dx = x·y",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        parsed?.let {
            ResultRow(
                label = "Order",
                value = OdeConditionParser.orderText(it.order),
                emphasis = false,
            )
        }
    }

    SegmentedChoice(
        options = SolveMode.entries.map { it to it.label },
        selected = mode,
        onSelect = { modeName = it.name },
        label = "How to solve it",
    )
    Text(
        mode.fullLabel + when (mode) {
            SolveMode.SYMBOLIC -> ": the tool works out the order and the shape of the " +
                "equation, and solves it in closed form where it can."
            SolveMode.INITIAL_VALUE -> ": the equation is solved and the constants are " +
                "fixed by the conditions you give."
            SolveMode.NUMERICAL -> ": the solution is stepped forward from the starting " +
                "point, without a closed form."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (mode != SolveMode.SYMBOLIC) {
        SectionCard(
            title = if (mode == SolveMode.INITIAL_VALUE) "Initial condition" else "Starting value",
        ) {
            PlainField(
                label = "Condition",
                value = condition,
                onValueChange = { condition = it },
                monospace = true,
                placeholder = "y(0) = 3",
            )
            if (parsed != null && parsed.order >= 2) {
                PlainField(
                    label = "Second condition (for a ${OdeConditionParser.orderText(parsed.order)} equation)",
                    value = secondCondition,
                    onValueChange = { secondCondition = it },
                    monospace = true,
                    placeholder = "y′(0) = 1",
                )
            }
            conditionProblem?.let { ErrorNotice(it) }
            InfoNote(
                if (mode == SolveMode.INITIAL_VALUE) {
                    "A first-order equation needs one condition; a second-order equation " +
                        "needs one for y and one for y′. The tool will not force a particular " +
                        "solution when the conditions are missing or contradict each other."
                } else {
                    "The numerical method starts from this point and steps forward. The " +
                        "solution that comes out is an approximation, not an exact answer."
                },
            )
        }
    }

    if (mode == SolveMode.NUMERICAL) {
        SectionCard(title = "Numerical settings") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumberField(
                    label = "From x",
                    value = from,
                    onValueChange = { from = it },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "To x",
                    value = to,
                    onValueChange = { to = it },
                    modifier = Modifier.weight(1f),
                )
            }
            NumberField(
                label = "Step size",
                value = step,
                onValueChange = { step = it },
            )
            SegmentedChoice(
                options = NumericalMethod.entries.map { it to it.label },
                selected = method,
                onSelect = { methodName = it.name },
                label = "Method",
            )
            Text(
                method.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ------------------------------------------------------------- the answer

    when (val current = result) {
        null -> if (running) {
            InfoNote("Solving…")
        }
        is OdeResult.Invalid -> ErrorNotice(current.message)
        is OdeResult.Unsupported -> {
            SectionCard(title = "No closed form") {
                Text(
                    current.failure.message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                current.failure.suggestedMethod?.let {
                    ResultRow(label = "Method that would apply", value = it.label)
                }
                current.failure.suggestion?.let {
                    InfoNote(it)
                }
            }
        }
        is OdeResult.Solved -> OdeAnswer(
            solution = current.solution,
            mode = mode,
            args = args,
            view = view,
            onViewChange = { view = it },
            showField = showField,
            onToggleField = { showField = !showField },
            parsed = parsed,
        )
    }
}

/** Runs a solve off the main thread. */
private fun OdeSolver(request: OdeRequest): OdeResult =
    com.calcsuite.engine.calculus.ode.OdeSolver.solve(request)

/**
 * The answer: the result card, the explanation panels and the graph.
 */
@Composable
private fun OdeAnswer(
    solution: OdeSolution,
    mode: SolveMode,
    args: ToolArgs,
    view: PlotViewport,
    onViewChange: (PlotViewport) -> Unit,
    showField: Boolean,
    onToggleField: () -> Unit,
    parsed: com.calcsuite.engine.calculus.ode.ParsedOde?,
) {
    val headline = solution.particularSolution
        ?: solution.generalSolution
        ?: solution.numerical?.let {
            "y(${formatShort(it.to)}) ≈ ${formatShort(it.points.lastOrNull()?.y)}"
        }

    ResultCard(
        label = when {
            solution.particularSolution != null -> "Solution"
            solution.generalSolution != null -> "General solution"
            else -> "Numerical solution"
        },
        value = headline,
        caption = solution.method.label,
        approx = false,
        onCopy = { args.onRecord("${solution.equation}", it) },
        copyValue = headline,
    )

    // The classification of the equation.
    ExpandablePanel(title = "Equation and classification") {
        Text(
            solution.equation,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            solution.classification.summary,
            style = MaterialTheme.typography.bodyMedium,
        )
        ResultRow(label = "Order", value = OdeConditionParser.orderText(solution.classification.order))
        ResultRow(
            label = "Linear in y",
            value = if (solution.classification.isLinear) "yes" else "no",
        )
        ResultRow(
            label = "Forcing term",
            value = if (solution.classification.isHomogeneous) "none — the equation is homogeneous" else "present",
        )
        if (solution.classification.separable != null) {
            ResultRow(label = "Separable", value = "yes")
        }
        if (solution.classification.isFirstOrderLinear) {
            ResultRow(label = "First-order linear", value = "yes")
        }
        if (solution.classification.hasConstantCoefficients) {
            ResultRow(label = "Constant coefficients", value = "yes")
        }
    }

    // Why the method applies.
    ExpandablePanel(title = "Why this method") {
        Text(
            "The tool chose ${solution.method.label}.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            solution.method.assumption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (solution.classification.separable != null) {
            ResultRow(
                label = "Separated form",
                value = "y′ = f(x)·g(y)",
            )
        }
    }

    // The derivation.
    ExpandablePanel(title = "Step-by-step derivation") {
        StepsCard(steps = solution.steps)
    }

    // The conditions.
    if (solution.constants.isNotEmpty()) {
        ExpandablePanel(title = "Applying the conditions") {
            for ((name, value) in solution.constants) {
                ResultRow(label = name, value = value, emphasis = true)
            }
        }
    }

    // The verification.
    ExpandablePanel(title = "Verification") {
        val verification = solution.verification
        if (verification == null) {
            Text(
                "This answer could not be checked automatically, because it is given in a form " +
                    "that cannot be substituted back into the equation. The derivation above " +
                    "is the working that produced it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                if (verification.verified) {
                    "The answer was substituted back into the equation and it holds."
                } else {
                    "The answer was substituted back into the equation and the two sides did " +
                        "not agree, so treat the result with care."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                verification.what,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            verification.detail?.let {
                ResultRow(label = "Residual", value = it)
            }
        }
    }

    // What the answer means.
    ExpandablePanel(title = "Interpretation") {
        Text(solution.interpretation, style = MaterialTheme.typography.bodyMedium)
    }

    // The assumptions and the limits.
    ExpandablePanel(title = "Assumptions and limits") {
        for (assumption in solution.assumptions) {
            Text("• $assumption", style = MaterialTheme.typography.bodyMedium)
        }
        solution.limitation?.let {
            InfoNote(it)
        }
    }

    // ------------------------------------------------------------- the graph

    val numerical = solution.numerical
    val accent = MaterialTheme.colorScheme.primary
    val second = MaterialTheme.colorScheme.tertiary
    val curves = remember(solution, view, accent, second) {
        buildCurves(solution, numerical, parsed, view, accent, second)
    }

    SectionCard(title = "Slope field and solution") {
        OdeGraph(
            rhs = parsed?.normalisedRhs,
            xName = parsed?.independent ?: "x",
            yName = parsed?.dependent ?: "y",
            curves = curves,
            view = view,
            onViewChange = onViewChange,
            showField = showField,
            fieldColor = MaterialTheme.colorScheme.primary,
            // The picture is square, so there are the same number of world
            // units in a pixel on each axis and the slopes are drawn at their
            // true angles.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onToggleField) {
                Text(if (showField) "Hide slope field" else "Show slope field")
            }
            TextButton(onClick = { onViewChange(PlotViewport(span = 12.0)) }) {
                Text("Reset view")
            }
        }
        if (curves.isEmpty()) {
            InfoNote(
                "The slope field is drawn from the equation itself, so it is always tied to " +
                    "what you typed. A solution curve is drawn as well once there is a single " +
                    "curve to draw: give a condition to fix the constant, or run the " +
                    "numerical method.",
            )
        }
    }

    // --------------------------------------------------- the numerical table

    if (numerical != null) {
        SectionCard(title = "Numerical values") {
            Text(
                "${numerical.method.label} from x = ${formatValue(numerical.from)} to " +
                    "x = ${formatValue(numerical.to)} with a step of ${formatValue(numerical.step)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            NumericalTable(numerical)
            numerical.errorIndicator?.let {
                InfoNote(it)
            }
            numerical.comparison?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The curves to draw: the numerical points, and the symbolic solution when
 * there is one, so the two can be compared.
 */
private fun buildCurves(
    solution: OdeSolution,
    numerical: NumericalResult?,
    parsed: com.calcsuite.engine.calculus.ode.ParsedOde?,
    view: PlotViewport,
    accent: Color,
    second: Color,
): List<OdeCurve> {
    val curves = mutableListOf<OdeCurve>()
    val x = parsed?.independent ?: "x"

    solution.solutionNode?.let { node ->
        // The curve is sampled across the visible window. Where the function
        // is not defined the piece ends, so the line is never drawn through a
        // gap.
        val pieces = mutableListOf<List<Pair<Double, Double>>>()
        var current = mutableListOf<Pair<Double, Double>>()
        val steps = 300
        for (i in 0..steps) {
            val at = view.xMin + view.span * i / steps
            val value = Evaluator.eval(node, mapOf(x to at)).valueOrNull()
            if (value == null || !value.isFinite() || abs(value) > 1e12) {
                if (current.size > 1) pieces.add(current)
                current = mutableListOf()
                continue
            }
            current.add(at to value)
        }
        if (current.size > 1) pieces.add(current)
        if (pieces.isNotEmpty()) {
            curves.add(
                OdeCurve(
                    pieces = pieces,
                    color = accent,
                    label = "the solution",
                ),
            )
        }
    }

    numerical?.let { result ->
        val points = result.points.filter { it.y.isFinite() }.map { it.x to it.y }
        if (points.size > 1) {
            curves.add(
                OdeCurve(
                    pieces = listOf(points),
                    color = second,
                    label = "the numerical solution",
                    dashed = true,
                ),
            )
        }
    }
    return curves
}

/** The table of numerical values, thinned out so it stays readable. */
@Composable
private fun NumericalTable(numerical: NumericalResult) {
    val points = numerical.points
    if (points.isEmpty()) return
    // Keep at most a dozen rows, evenly spread through the interval.
    val stride = (points.size / 12).coerceAtLeast(1)
    val rows = points.filterIndexed { index, _ -> index % stride == 0 || index == points.lastIndex }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "x",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "y",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        for (point in rows) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatValue(point.x),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatValue(point.y),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A number written with at most six decimals, for a headline. */
private fun formatShort(value: Double?): String {
    if (value == null) return "—"
    if (value.isNaN()) return "undefined"
    if (value.isInfinite()) return if (value > 0) "∞" else "−∞"
    if (value == kotlin.math.floor(value) && abs(value) < 1e15) return value.toLong().toString()
    var text = String.format(java.util.Locale.ROOT, "%.6f", value)
    text = text.trimEnd('0').trimEnd('.')
    return if (text.isEmpty() || text == "-") "0" else text
}

/** A number written the way the rest of the app writes them. */
private fun formatValue(value: Double?): String {
    if (value == null) return "—"
    if (value.isNaN()) return "undefined"
    if (value.isInfinite()) return if (value > 0) "∞" else "−∞"
    if (value == kotlin.math.floor(value) && abs(value) < 1e15) return value.toLong().toString()
    var text = String.format(java.util.Locale.ROOT, "%.8f", value)
    text = text.trimEnd('0').trimEnd('.')
    return if (text.isEmpty() || text == "-") "0" else text
}
