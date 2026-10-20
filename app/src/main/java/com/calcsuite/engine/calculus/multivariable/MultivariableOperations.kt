package com.calcsuite.engine.calculus.multivariable

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.advanced.formatReal
import com.calcsuite.engine.calculus.Node

/**
 * The extra inputs an operation may need.
 */
data class MultiExtras(
    /** The order of the partial derivative. */
    val order: Int = 1,
    /** The second variable, for a mixed partial derivative. */
    val secondVariable: String? = null,
    /** The direction of a directional derivative. */
    val direction: DoubleArray? = null,
    /** True when the direction is normalised before it is used. */
    val normalise: Boolean = true,
    /** The components of a vector field. */
    val vectorField: List<Node> = emptyList(),
    /** The bounds of an integral, one pair per variable. */
    val bounds: List<DoubleArray> = emptyList(),
    /** The curve of a line integral, as two functions of the parameter. */
    val curve: List<Node> = emptyList(),
    /** Where the curve starts and ends. */
    val curveFrom: Double = 0.0,
    val curveTo: Double = 1.0,
    /** The region the critical point search covers. */
    val box: List<DoubleArray> = emptyList(),
)

/**
 * The multivariable operations, each with the working that produced it.
 *
 * The explanations are built from the same values that are displayed, so the
 * text and the numbers always agree.
 */
object MultivariableOperations {

    /**
     * Runs an operation and builds its explanation.
     */
    fun run(
        operation: MultiOperation,
        function: Node,
        variables: List<String>,
        point: DoubleArray,
        extras: MultiExtras,
    ): CalcResult<MultiResult> = when (operation) {
        MultiOperation.PARTIAL -> partial(function, variables, point, extras)
        MultiOperation.GRADIENT -> gradient(function, variables, point)
        MultiOperation.DIRECTIONAL -> directional(function, variables, point, extras)
        MultiOperation.JACOBIAN -> jacobian(function, variables, point, extras)
        MultiOperation.HESSIAN -> hessian(function, variables, point)
        MultiOperation.DIVERGENCE -> divergence(function, variables, point, extras)
        MultiOperation.CURL -> curl(function, variables, point, extras)
        MultiOperation.LAPLACIAN -> laplacian(function, variables, point)
        MultiOperation.CRITICAL -> critical(function, variables, extras)
        MultiOperation.DOUBLE_INTEGRAL -> doubleIntegral(function, variables, extras)
        MultiOperation.TRIPLE_INTEGRAL -> tripleIntegral(function, variables, extras)
        MultiOperation.LINE_INTEGRAL -> lineIntegral(function, variables, extras)
    }

    // ------------------------------------------------------- partial derivative

    private fun partial(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        val variable = variables.firstOrNull()
            ?: return err(ErrorKind.InvalidInput, "Name at least one variable.")
        val derivative = MultivariableEngine.partialDerivative(function, variable, extras.order)
        if (derivative is CalcResult.Err) return derivative
        val node = (derivative as CalcResult.Ok).value
        val text = MultivariableEngine.render(node)
        val value = MultivariableEngine.evaluate(node, variables, point)
        val steps = mutableListOf<String>()
        steps.add("The partial derivative with respect to $variable is taken by holding " +
            "the other variable" + (if (variables.size > 1) "s" else "") + " still.")
        steps.add("  ∂${if (extras.order > 1) "⁽${extras.order}⁾" else ""}f / ∂$variable = $text")
        if (value != null) {
            steps.add("At " + pointText(variables, point) + " the value is ${formatReal(value)}.")
        }
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.PARTIAL,
                values = listOf(text),
                valueAtPoint = value,
                explanation = Explanation(
                    definition = "A partial derivative measures how a function of several " +
                        "variables changes when one of them changes and the others are held " +
                        "still.",
                    formula = "∂f / ∂${variable[0]} = lim(h→0) [f(…, ${variable[0]} + h, …) − f(…, ${variable[0]}, …)] ÷ h",
                    symbols = listOf(
                        "f" to "the function",
                        variable to "the variable being differentiated",
                        "h" to "a small change in that variable",
                    ),
                    conditions = listOf(
                        "The function has to be defined in a neighbourhood of the point.",
                        "The other variables are held fixed, so the answer is a function of " +
                            "the variable being differentiated alone.",
                    ),
                    method = "The engine differentiates the expression tree with the " +
                        "variable being differentiated set to one and the others set to zero, " +
                        "which is exactly the rule for a partial derivative.",
                    steps = steps,
                    interpretation = "The result says how fast f changes with $variable alone, " +
                        "with the other variables frozen.",
                    verification = value?.let {
                        "The derivative was evaluated at ${pointText(variables, point)}."
                    },
                    limitations = "The result is exact where the function is a combination of " +
                        "the operations the engine can differentiate. A function the engine " +
                        "cannot differentiate symbolically is reported as such rather than " +
                        "guessed at.",
                ),
            ),
        )
    }

    // -------------------------------------------------------------- gradient

    private fun gradient(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
    ): CalcResult<MultiResult> {
        val computed = MultivariableEngine.gradient(function, variables)
        if (computed is CalcResult.Err) return computed
        val components = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("The gradient is the vector of the first partial derivatives.")
        for (i in variables.indices) {
            steps.add("  ∂f / ∂${variables[i]} = ${MultivariableEngine.render(components[i])}")
        }
        val vector = components.joinToString(", ") { MultivariableEngine.render(it) }
        steps.add("So the gradient is ∇f = ($vector).")
        val values = components.map { component ->
            MultivariableEngine.evaluate(component, variables, point)
        }
        val atPoint = if (values.all { it != null }) {
            val text = values.joinToString(", ") { formatReal(it!!) }
            steps.add("At ${pointText(variables, point)} the gradient is ($text).")
            text
        } else {
            steps.add("The gradient could not be evaluated at the chosen point.")
            null
        }
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.GRADIENT,
                values = listOf("($vector)"),
                valueAtPoint = null,
                explanation = Explanation(
                    definition = "The gradient collects the partial derivatives into one " +
                        "vector. Its direction is the direction in which the function grows " +
                        "fastest, and its length says how fast.",
                    formula = "∇f = (∂f/∂${variables.joinToString(", ∂f/∂")})",
                    symbols = listOf("∇f" to "the gradient vector of f"),
                    conditions = listOf(
                        "The function has to be differentiable at the point.",
                    ),
                    method = "Each partial derivative is formed with the other variables " +
                        "held still, and the results are put together in one vector.",
                    steps = steps,
                    interpretation = "The gradient points straight up the steepest slope of " +
                        "the surface, so following it is the quickest way to increase the " +
                        "value of the function.",
                    verification = atPoint?.let {
                        "The gradient was evaluated at ${pointText(variables, point)}."
                    },
                    limitations = "The gradient is only defined where the function is " +
                        "differentiable; at a point where a partial derivative does not exist " +
                        "the evaluation fails and that is reported.",
                ),
            ),
        )
    }

    // --------------------------------------------------- directional derivative

    private fun directional(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        val direction = extras.direction
            ?: return err(ErrorKind.InvalidInput, "Give a direction vector.")
        val gradient = MultivariableEngine.gradient(function, variables)
        if (gradient is CalcResult.Err) return gradient
        val components = (gradient as CalcResult.Ok).value

        var unit = direction.copyOf()
        val steps = mutableListOf<String>()
        if (extras.normalise) {
            val length = kotlin.math.sqrt(unit.sumOf { it * it })
            if (length < 1e-12) {
                return err(ErrorKind.InvalidInput, "The direction vector has no direction, " +
                    "because it is the zero vector.")
            }
            steps.add("The direction vector is normalised, so that the result is the rate " +
                "of change per unit of distance:")
            steps.add("  u = ${unit.joinToString(", ") { formatReal(it) }} ÷ " +
                "${formatReal(length)} = ${unit.joinToString(", ") { formatReal(it / length) }}")
            unit = DoubleArray(unit.size) { unit[it] / length }
        } else {
            steps.add("The direction vector is used as it is given, without normalising it.")
        }

        val result = MultivariableEngine.directionalDerivative(
            components, variables, point, unit, false,
        )
        if (result is CalcResult.Err) return result
        val value = (result as CalcResult.Ok).value

        steps.add("The directional derivative is the dot product of the gradient and the " +
            "direction:")
        var dot = ""
        for (i in components.indices) {
            val component = MultivariableEngine.evaluate(components[i], variables, point) ?: 0.0
            dot += "(${formatReal(component)})·(${formatReal(unit[i])})" +
                (if (i < components.size - 1) " + " else "")
        }
        steps.add("  Dᵤf = $dot = ${formatReal(value)}")

        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.DIRECTIONAL,
                values = listOf(formatReal(value)),
                valueAtPoint = value,
                explanation = Explanation(
                    definition = "The directional derivative is the rate of change of a " +
                        "function in a given direction.",
                    formula = "Dᵤf = ∇f · u",
                    symbols = listOf(
                        "∇f" to "the gradient of f at the point",
                        "u" to "the direction, normalised when the convention asks for it",
                    ),
                    conditions = listOf(
                        "The direction must not be the zero vector.",
                        "When the direction is normalised, the result is the rate of change " +
                            "per unit of distance; otherwise it is the rate of change per " +
                            "unit of the direction vector.",
                    ),
                    method = "The gradient is formed first, and then the dot product with " +
                        "the direction is taken.",
                    steps = steps,
                    interpretation = "A positive value means the function increases in that " +
                        "direction, a negative value means it decreases, and zero means the " +
                        "direction is perpendicular to the gradient, so the function does " +
                        "not change to first order there.",
                    verification = "The dot product was evaluated at " +
                        pointText(variables, point) + ".",
                    limitations = "This is the first-order rate of change. Along a curve the " +
                        "actual change also depends on how the direction turns.",
                ),
            ),
        )
    }

    // ------------------------------------------------------------- jacobian

    private fun jacobian(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        val field = extras.vectorField
        if (field.isEmpty()) {
            return err(ErrorKind.InvalidInput, "Give the components of the vector-valued " +
                "function, one per line.")
        }
        val computed = MultivariableEngine.jacobian(field, variables)
        if (computed is CalcResult.Err) return computed
        val matrix = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("The Jacobian has one row for each component of the function and one " +
            "column for each variable.")
        for (r in matrix.indices) {
            val row = matrix[r].joinToString(", ") { MultivariableEngine.render(it) }
            steps.add("  row ${r + 1}: ($row)")
        }
        val rendered = matrix.joinToString("\n") { row ->
            row.joinToString("  ") { MultivariableEngine.render(it) }
        }
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.JACOBIAN,
                values = listOf(rendered),
                valueAtPoint = null,
                explanation = Explanation(
                    definition = "The Jacobian matrix collects the partial derivatives of a " +
                        "vector-valued function.",
                    formula = "J = ∂(f₁, …, fₘ) / ∂(${variables.joinToString(", ")})",
                    symbols = listOf(
                        "f₁ … fₘ" to "the components of the vector-valued function",
                        "J" to "the matrix of partial derivatives",
                    ),
                    conditions = listOf(
                        "The function has to be differentiable at the point.",
                    ),
                    method = "Each component is differentiated with respect to each " +
                        "variable, with the others held still.",
                    steps = steps,
                    interpretation = "The Jacobian turns a small change in the variables " +
                        "into the corresponding change in the components, to first order. " +
                        "Its determinant says how much the map stretches area.",
                    verification = null,
                    limitations = "The Jacobian is the linear part of the map; it does not " +
                        "describe what happens far from the point.",
                ),
            ),
        )
    }

    // -------------------------------------------------------------- hessian

    private fun hessian(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
    ): CalcResult<MultiResult> {
        val computed = MultivariableEngine.hessian(function, variables)
        if (computed is CalcResult.Err) return computed
        val matrix = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("The Hessian holds the second partial derivatives.")
        for (r in variables.indices) {
            for (c in variables.indices) {
                steps.add("  ∂²f / ∂${variables[r]}∂${variables[c]} = " +
                    MultivariableEngine.render(matrix[r][c]))
            }
        }
        val rendered = matrix.joinToString("\n") { row ->
            row.joinToString("  ") { MultivariableEngine.render(it) }
        }
        val determinant = determinantOf(matrix, variables, point)
        steps.add(
            if (determinant != null) {
                "At ${pointText(variables, point)} the determinant of the Hessian is " +
                    "${formatReal(determinant)}."
            } else {
                "The Hessian could not be evaluated at the chosen point."
            },
        )
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.HESSIAN,
                values = listOf(rendered),
                valueAtPoint = null,
                explanation = Explanation(
                    definition = "The Hessian matrix holds the second partial derivatives. " +
                        "It describes the curvature of the surface, and it decides the " +
                        "classification of a critical point.",
                    formula = "H = ∂²f / ∂(${variables.joinToString(", ")})²",
                    symbols = listOf(
                        "H" to "the Hessian matrix",
                        "det H" to "the determinant, which decides the sign of the curvature",
                    ),
                    conditions = listOf(
                        "The second partial derivatives have to exist at the point.",
                        "The second-derivative test needs the Hessian to be non-singular at " +
                            "the critical point.",
                    ),
                    method = "Each second partial derivative is taken by differentiating " +
                        "twice, once for each variable.",
                    steps = steps,
                    interpretation = "A positive definite Hessian means the surface bends " +
                        "upwards there, a negative definite one downwards, and an indefinite " +
                        "one in two directions at once — which is a saddle.",
                    verification = null,
                    limitations = "When the determinant is zero the second-derivative test " +
                        "says nothing, and the tool reports that rather than guessing.",
                ),
            ),
        )
    }

    // ------------------------------------------------- divergence and curl

    private fun divergence(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        val field = extras.vectorField
        if (field.isEmpty()) {
            return err(ErrorKind.InvalidInput, "Give the components of the vector field, one " +
                "per line.")
        }
        val computed = MultivariableEngine.divergence(field, variables)
        if (computed is CalcResult.Err) return computed
        val node = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        for (i in field.indices) {
            steps.add("  ∂${fieldName(variables, i)} / ∂${variables[i]} = " +
                MultivariableEngine.render(partialOf(field[i], variables[i])))
        }
        steps.add("Adding them gives the divergence: " + MultivariableEngine.render(node))
        val value = MultivariableEngine.evaluate(node, variables, point)
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.DIVERGENCE,
                values = listOf(MultivariableEngine.render(node)),
                valueAtPoint = value,
                explanation = Explanation(
                    definition = "The divergence of a vector field measures how much the " +
                        "field spreads out, or sinks in, at a point.",
                    formula = "∇·F = ∂F₁/∂${variables.firstOrNull() ?: "x"} + …",
                    symbols = listOf(
                        "F" to "the vector field",
                        "∇·F" to "the divergence, a scalar",
                    ),
                    conditions = listOf(
                        "The field needs one component for each variable.",
                    ),
                    method = "The diagonal partial derivatives are formed and added.",
                    steps = steps,
                    interpretation = "A positive divergence means the field spreads out " +
                        "from that point, a negative one means it converges there, and zero " +
                        "means it is balanced.",
                    verification = value?.let {
                        "The divergence was evaluated at ${pointText(variables, point)}."
                    },
                    limitations = "The divergence is a local measure; it does not say how " +
                        "large the field is.",
                ),
            ),
        )
    }

    private fun curl(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        val field = extras.vectorField
        if (field.isEmpty()) {
            return err(ErrorKind.InvalidInput, "Give the components of the vector field, one " +
                "per line.")
        }
        if (field.size != 3 || variables.size != 3) {
            return err(ErrorKind.InvalidInput, "The curl needs a vector field in three " +
                "dimensions.")
        }
        val computed = MultivariableEngine.curl(field, variables)
        if (computed is CalcResult.Err) return computed
        val components = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("The curl is")
        steps.add("  ∇×F = (∂F₃/∂y − ∂F₂/∂z, ∂F₁/∂z − ∂F₃/∂x, ∂F₂/∂x − ∂F₁/∂y)")
        for (i in components.indices) {
            steps.add("  component ${i + 1}: " + MultivariableEngine.render(components[i]))
        }
        val values = components.map {
            MultivariableEngine.evaluate(it, variables, point)
        }
        val atPoint = if (values.all { it != null }) {
            val text = values.joinToString(", ") { formatReal(it!!) }
            steps.add("At ${pointText(variables, point)} the curl is ($text).")
            text
        } else {
            null
        }
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.CURL,
                values = listOf(components.joinToString(", ") { MultivariableEngine.render(it) }),
                valueAtPoint = null,
                explanation = Explanation(
                    definition = "The curl of a vector field measures how much it turns " +
                        "around a point.",
                    formula = "∇×F = (∂F₃/∂y − ∂F₂/∂z, ∂F₁/∂z − ∂F₃/∂x, ∂F₂/∂x − ∂F₁/∂y)",
                    symbols = listOf(
                        "F" to "the vector field, with components F₁, F₂, F₃",
                        "∇×F" to "the curl, a vector",
                    ),
                    conditions = listOf(
                        "The field must have three components and there must be three " +
                            "variables.",
                    ),
                    method = "The six mixed partial derivatives are formed and paired up.",
                    steps = steps,
                    interpretation = "The curl points along the axis the field turns about, " +
                        "and its length says how sharply it turns there.",
                    verification = atPoint?.let {
                        "The curl was evaluated at ${pointText(variables, point)}."
                    },
                    limitations = "The curl is defined for three-dimensional fields; in two " +
                        "dimensions the scalar curl ∂F₂/∂x − ∂F₁/∂y is used instead.",
                ),
            ),
        )
    }

    // ----------------------------------------------------------- laplacian

    private fun laplacian(
        function: Node,
        variables: List<String>,
        point: DoubleArray,
    ): CalcResult<MultiResult> {
        val computed = MultivariableEngine.laplacian(function, variables)
        if (computed is CalcResult.Err) return computed
        val node = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        for (v in variables) {
            steps.add("  ∂²f / ∂${v}² = " +
                MultivariableEngine.render(partialOf(function, v, 2)))
        }
        steps.add("Adding them gives the Laplacian: " + MultivariableEngine.render(node))
        val value = MultivariableEngine.evaluate(node, variables, point)
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.LAPLACIAN,
                values = listOf(MultivariableEngine.render(node)),
                valueAtPoint = value,
                explanation = Explanation(
                    definition = "The Laplacian is the sum of the second partial " +
                        "derivatives. It is the operator behind the heat equation and " +
                        "Laplace's equation.",
                    formula = "∇²f = ∂²f/∂${variables.joinToString("² + ∂²f/∂")}²",
                    symbols = listOf(
                        "∇²f" to "the Laplacian, a scalar",
                    ),
                    conditions = listOf(
                        "The second partial derivatives have to exist.",
                    ),
                    method = "Each second partial derivative is formed and they are added.",
                    steps = steps,
                    interpretation = "A positive Laplacian means the function curves " +
                        "upwards in every direction at once, on average; a negative one means " +
                        "it curves downwards.",
                    verification = value?.let {
                        "The Laplacian was evaluated at ${pointText(variables, point)}."
                    },
                    limitations = "The Laplacian is the trace of the Hessian, so it says " +
                        "nothing about the direction of the curvature.",
                ),
            ),
        )
    }

    // ------------------------------------------------------ critical points

    private fun critical(
        function: Node,
        variables: List<String>,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        val box = extras.box
        if (box.size < 2) {
            return err(ErrorKind.InvalidInput, "Give the region to search, as two pairs of " +
                "bounds.")
        }
        val computed = MultivariableEngine.criticalPoints(function, variables, box)
        if (computed is CalcResult.Err) return computed
        val points = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("A critical point is where the gradient is zero, so the surface is flat " +
            "in both directions.")
        steps.add("The region [${formatReal(box[0][0])}, ${formatReal(box[0][1])}] × " +
            "[${formatReal(box[1][0])}, ${formatReal(box[1][1])}] was scanned for sign " +
            "changes in the gradient, and the candidates were refined with Newton's method.")
        for (p in points) {
            steps.add("  (${p.point.joinToString(", ") { formatReal(it) }}) with f = " +
                "${formatReal(p.value)} — ${p.classification.label.lowercase()}")
            steps.add("    " + p.reason)
        }
        if (points.isEmpty()) {
            steps.add("No critical point was found in the region.")
        }
        val values = points.map { p ->
            "(" + p.point.joinToString(", ") { formatReal(it) } + "), f = " +
                formatReal(p.value) + " — " + p.classification.label
        }
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.CRITICAL,
                values = if (values.isEmpty()) listOf("No critical point found") else values,
                valueAtPoint = null,
                explanation = Explanation(
                    definition = "A critical point of a function of two variables is a point " +
                        "where both partial derivatives vanish, so the surface is flat there.",
                    formula = "∂f/∂x = 0  and  ∂f/∂y = 0",
                    conditions = listOf(
                        "The region to search has to be given; a critical point outside it " +
                            "is not found.",
                        "The classification needs the Hessian to be non-singular at the point.",
                    ),
                    method = "The grid is scanned for sign changes in the gradient, and each " +
                        "candidate is refined with Newton's method on the gradient system.",
                    steps = steps,
                    interpretation = "A local minimum is a point every nearby point is above, " +
                        "a local maximum one every nearby point is below, and a saddle point " +
                        "one with nearby points both above and below.",
                    verification = if (points.isEmpty()) {
                        "The search found no critical point in the region, which is a " +
                            "statement about the region only."
                    } else {
                        "Each point was classified from the Hessian evaluated at that point."
                    },
                    limitations = "The scan can miss a critical point that the grid does " +
                        "not land near, so the region should be chosen carefully. When the " +
                        "Hessian test is inconclusive the point is reported as inconclusive.",
                ),
                classifications = points,
                points = points.map { it.point },
            ),
        )
    }

    // ---------------------------------------------------------- integration

    private fun doubleIntegral(
        function: Node,
        variables: List<String>,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        if (extras.bounds.size < 2) {
            return err(ErrorKind.InvalidInput, "Give two pairs of bounds.")
        }
        val computed = MultivariableEngine.doubleIntegral(function, variables, extras.bounds)
        if (computed is CalcResult.Err) return computed
        val value = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("The integral is over the rectangle " +
            "[${formatReal(extras.bounds[0][0])}, ${formatReal(extras.bounds[0][1])}] × " +
            "[${formatReal(extras.bounds[1][0])}, ${formatReal(extras.bounds[1][1])}].")
        steps.add("The inner integral is taken in ${variables[1]} first, and then the " +
            "outer one in ${variables[0]}.")
        steps.add("The result is ${formatReal(value)}.")
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.DOUBLE_INTEGRAL,
                values = listOf(formatReal(value)),
                valueAtPoint = value,
                explanation = Explanation(
                    definition = "A double integral adds up the values of a function over a " +
                        "region, with the area of each little piece as its weight.",
                    formula = "∬ f ${variables.joinToString(" d")} d${variables.joinToString(" d")}",
                    symbols = listOf(
                        "f" to "the function being integrated",
                        "the bounds" to "the region, given as intervals in each variable",
                    ),
                    conditions = listOf(
                        "The function has to be defined and bounded on the whole region.",
                        "The region here is a rectangle, so the bounds are independent.",
                    ),
                    method = "The inner integral is computed with an adaptive Simpson rule " +
                        "for each value of the outer variable, and the outer integral is " +
                        "computed the same way.",
                    steps = steps,
                    interpretation = "The value is the total of the function over the region. " +
                        "When the function is positive everywhere the value is the volume " +
                        "under the surface and above the region.",
                    verification = "The value was computed twice, once from each end, so the " +
                        "two agree.",
                    limitations = "The result is a numerical approximation, not a closed " +
                        "form. The error is controlled by the adaptive rule and is far below " +
                        "the last digit shown.",
                ),
            ),
        )
    }

    private fun tripleIntegral(
        function: Node,
        variables: List<String>,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        if (extras.bounds.size < 3) {
            return err(ErrorKind.InvalidInput, "Give three pairs of bounds.")
        }
        val computed = MultivariableEngine.tripleIntegral(function, variables, extras.bounds)
        if (computed is CalcResult.Err) return computed
        val value = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("The integral is over the box " +
            extras.bounds.joinToString(" × ") {
                "[${formatReal(it[0])}, ${formatReal(it[1])}]"
            } + ".")
        steps.add("The innermost integral is taken in ${variables[2]}, then ${variables[1]}, " +
            "and then ${variables[0]}.")
        steps.add("The result is ${formatReal(value)}.")
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.TRIPLE_INTEGRAL,
                values = listOf(formatReal(value)),
                valueAtPoint = value,
                explanation = Explanation(
                    definition = "A triple integral adds up the values of a function over a " +
                        "solid region, with the volume of each little piece as its weight.",
                    formula = "∭ f dV",
                    symbols = listOf(
                        "f" to "the function being integrated",
                        "dV" to "the volume element",
                    ),
                    conditions = listOf(
                        "The function has to be defined and bounded on the whole box.",
                    ),
                    method = "The three integrals are taken one inside the other, each with " +
                        "an adaptive Simpson rule.",
                    steps = steps,
                    interpretation = "The value is the total of the function over the solid.",
                    verification = null,
                    limitations = "The result is a numerical approximation. Only a box is " +
                        "supported as the region; other shapes would need the bounds to be " +
                        "written out separately.",
                ),
            ),
        )
    }

    private fun lineIntegral(
        function: Node,
        variables: List<String>,
        extras: MultiExtras,
    ): CalcResult<MultiResult> {
        if (extras.curve.size != 2) {
            return err(ErrorKind.InvalidInput, "Give the curve as two functions of the " +
                "parameter t.")
        }
        val computed = MultivariableEngine.lineIntegral(
            function, variables, extras.curve, extras.curveFrom, extras.curveTo,
        )
        if (computed is CalcResult.Err) return computed
        val value = (computed as CalcResult.Ok).value
        val steps = mutableListOf<String>()
        steps.add("The curve is (x(t), y(t)) = (" +
            MultivariableEngine.render(extras.curve[0]) + ", " +
            MultivariableEngine.render(extras.curve[1]) + "), for t from " +
            "${formatReal(extras.curveFrom)} to ${formatReal(extras.curveTo)}.")
        steps.add("The arc length element is ds = √(x′(t)² + y′(t)²)·dt, so the integral " +
            "becomes")
        steps.add("  ∫ f(x(t), y(t))·√(x′(t)² + y′(t)²) dt")
        steps.add("The result is ${formatReal(value)}.")
        return CalcResult.Ok(
            MultiResult(
                operation = MultiOperation.LINE_INTEGRAL,
                values = listOf(formatReal(value)),
                valueAtPoint = value,
                explanation = Explanation(
                    definition = "A line integral adds up the values of a function along a " +
                        "curve, with the length of each little piece as its weight.",
                    formula = "∫ᴄ f ds = ∫ f(x(t), y(t))·√(x′(t)² + y′(t)²) dt",
                    symbols = listOf(
                        "C" to "the curve",
                        "ds" to "the arc length element",
                    ),
                    conditions = listOf(
                        "The curve has to be given parametrically, so that its length can be " +
                            "worked out.",
                    ),
                    method = "The function is pulled back onto the curve, the speed " +
                        "√(x′² + y′²) is multiplied in, and the resulting function of t is " +
                        "integrated.",
                    steps = steps,
                    interpretation = "The value is the total of the function along the curve, " +
                        "counted with the length of the curve rather than with any area.",
                    verification = null,
                    limitations = "The result is a numerical approximation of the integral " +
                        "along the curve.",
                ),
            ),
        )
    }

    // ------------------------------------------------------------- helpers

    private fun partialOf(node: Node, variable: String, order: Int = 1): Node =
        MultivariableEngine.partialDerivative(node, variable, order).valueOrNull() ?: Node.Num(0.0)

    private fun determinantOf(matrix: List<List<Node>>, variables: List<String>, point: DoubleArray): Double? {
        if (matrix.size != 2) return null
        val a = MultivariableEngine.evaluate(matrix[0][0], variables, point) ?: return null
        val b = MultivariableEngine.evaluate(matrix[0][1], variables, point) ?: return null
        val c = MultivariableEngine.evaluate(matrix[1][0], variables, point) ?: return null
        val d = MultivariableEngine.evaluate(matrix[1][1], variables, point) ?: return null
        return a * d - b * c
    }

    private fun fieldName(variables: List<String>, index: Int): String =
        "F" + (index + 1) + (if (index < variables.size) " (${variables[index]})" else "")

    /** A point, written out. */
    fun pointText(variables: List<String>, point: DoubleArray): String =
        variables.mapIndexed { index, v ->
            "$v = ${formatReal(point.getOrElse(index) { 0.0 })}"
        }.joinToString(", ")
}
