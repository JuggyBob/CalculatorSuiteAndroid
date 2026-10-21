package com.calcsuite.ai.tool

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.core.formatNumber
import com.calcsuite.core.ok
import com.calcsuite.engine.advanced.ComplexAnalysis
import com.calcsuite.engine.advanced.ComplexNum
import com.calcsuite.engine.advanced.FourierEngine
import com.calcsuite.engine.advanced.LinearAlgebra
import com.calcsuite.engine.advanced.Matrix
import com.calcsuite.engine.advanced.MeasureEngine
import com.calcsuite.engine.advanced.Interval
import com.calcsuite.engine.advanced.RealSet
import com.calcsuite.engine.advanced.PdeEngine
import com.calcsuite.engine.advanced.PdeKind
import com.calcsuite.engine.algebra.CombinatoricsEngine
import com.calcsuite.engine.algebra.GcfLcmEngine
import com.calcsuite.engine.algebra.PrimeEngine
import com.calcsuite.engine.algebra.inequality.InequalityMode
import com.calcsuite.engine.algebra.inequality.InequalityResult
import com.calcsuite.engine.algebra.inequality.InequalitySolver
import com.calcsuite.engine.algebra.inequality.SolutionSet
import com.calcsuite.engine.calculus.CalculusEngine
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.EquationEngine
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import com.calcsuite.engine.calculus.multivariable.MultiExtras
import com.calcsuite.engine.calculus.multivariable.MultiOperation
import com.calcsuite.engine.calculus.multivariable.MultivariableEngine
import com.calcsuite.engine.calculus.multivariable.MultivariableOperations
import com.calcsuite.engine.calculus.ode.NumericalMethod
import com.calcsuite.engine.calculus.ode.NumericalSettings
import com.calcsuite.engine.calculus.ode.OdeRequest
import com.calcsuite.engine.calculus.ode.OdeResult
import com.calcsuite.engine.calculus.ode.OdeSolver
import com.calcsuite.engine.calculus.ode.SolveMode
import java.math.BigDecimal
import java.math.BigInteger

/**
 * The answer a tool gives.
 *
 * @param headline the answer itself, ready to show.
 * @param steps the working, when the engine produced any.
 * @param expression the engine's own rendering of the problem, when it has
 *   one. It is the engine's working, not the user's input, so it is shown
 *   apart from the problem that was asked.
 * @param approximate true when the number is a rounded or numerical answer
 *   rather than an exact one, so that it is shown as an approximation.
 * @param method how the engine found the answer, in the engine's own words.
 * @param input the exact input the engine was given, with the tool's own
 *   defaults filled in. This is what was worked out, and it is what the
 *   screen shows as the problem.
 */
data class AiToolOutcome(
    val headline: String,
    val steps: List<String> = emptyList(),
    val expression: String = "",
    val approximate: Boolean = false,
    val method: String = "",
    val input: AiToolInput? = null,
)

/**
 * Runs a tool from the registry.
 *
 * The arguments are checked before anything is called, and the engine that
 * does the work is the one the tool's own screen uses, so the answer is the
 * same answer the app gives when the problem is typed in by hand.
 */
object AiToolExecutor {

    /** The longest expression that is read. */
    private const val MAX_EXPRESSION_LENGTH = 400

    /** The longest text an argument may hold. */
    private const val MAX_ARGUMENT_LENGTH = 400

    /**
     * Runs the tool with the given arguments.
     *
     * The tool has to be one the registry knows: a name that is not in the
     * registry is refused rather than looked up.
     */
    fun run(toolId: String, arguments: Map<String, String>): CalcResult<AiToolOutcome> {
        val spec = AiToolRegistry.byId(toolId)
            ?: return err(ErrorKind.InvalidInput, "There is no mathematical tool called \"$toolId\".")
        // The arguments are checked and the tool's own defaults are filled
        // in once, here, so that the engine and the screen are working with
        // exactly the same input.
        val prepared = when (val result = prepare(spec, arguments)) {
            is CalcResult.Err -> return result
            is CalcResult.Ok -> result.value
        }
        return try {
            when (val result = execute(spec, prepared)) {
                is CalcResult.Err -> result
                is CalcResult.Ok -> ok(result.value.copy(input = AiToolInput(toolId, prepared)))
            }
        } catch (e: Exception) {
            err(ErrorKind.InvalidInput, e.message ?: "The tool could not be run.")
        }
    }

    /**
     * Checks the arguments against the tool's own list and fills in the
     * defaults.
     *
     * The result is the one set of arguments the engine will be given, and
     * the one the screen will show, so that the problem that is shown and
     * the problem that is worked out cannot drift apart.
     */
    fun prepare(
        spec: AiToolSpec,
        raw: Map<String, String>,
    ): CalcResult<Map<String, String>> {
        val values = mutableMapOf<String, String>()
        for (argument in spec.arguments) {
            val given = raw[argument.name]?.trim().orEmpty()
            if (given.isEmpty()) {
                if (argument.required) {
                    return err(
                        ErrorKind.InvalidInput,
                        "The ${spec.title.lowercase()} needs a ${argument.name}.",
                    )
                }
                argument.defaultText?.let { values[argument.name] = it }
                continue
            }
            if (given.length > MAX_ARGUMENT_LENGTH) {
                return err(ErrorKind.InvalidInput, "The ${argument.name} is too long.")
            }
            values[argument.name] = given
        }
        return ok(values)
    }

    private fun execute(
        spec: AiToolSpec,
        values: Map<String, String>,
    ): CalcResult<AiToolOutcome> {
        return when (spec.id) {
            "calculus.derivative" -> derivative(values)
            "calculus.integral" -> integral(values)
            "calculus.limit" -> limit(values)
            "calculus.summation" -> summation(values)
            "calculus.taylor" -> taylor(values)
            "calculus.series" -> series(values)
            "calculus.roots" -> roots(values)
            "calculus.extrema" -> extrema(values)
            "calculus.ode" -> ode(values)
            "calculus.partial" -> partial(values)
            "algebra.linear" -> linear(values)
            "algebra.quadratic" -> quadratic(values)
            "algebra.system" -> system(values)
            "algebra.inequality" -> inequality(values)
            "algebra.gcf" -> gcf(values)
            "algebra.lcm" -> lcm(values)
            "algebra.prime" -> prime(values)
            "algebra.combinatorics" -> combinatorics(values)
            "advanced.linear_algebra" -> linearAlgebra(values)
            "advanced.measure" -> measure(values)
            "advanced.complex" -> complex(values)
            "advanced.fourier" -> fourier(values)
            "advanced.pde" -> pde(values)
            else -> err(ErrorKind.InvalidInput, "The ${spec.title.lowercase()} is not ready yet.")
        }
    }

    // ------------------------------------------------------------- calculus

    private fun derivative(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "x"
        checkExpression(expression)
        val result = DifferentiationEngine.derivative(expression, variable)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = value.derivative,
                        steps = value.steps,
                        expression = value.expression,
                        method = "Symbolic differentiation",
                    ),
                )
            }
        }
    }

    private fun integral(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "x"
        checkExpression(expression)
        val lower = values["lowerBound"]
        val upper = values["upperBound"]
        val result = if (lower != null && upper != null) {
            IntegrationEngine.definite(expression, variable, lower, upper)
        } else {
            IntegrationEngine.indefinite(expression, variable)
        }
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = value.value,
                        steps = value.steps,
                        expression = value.expression,
                        approximate = !value.exact,
                        method = value.method,
                    ),
                )
            }
        }
    }

    private fun limit(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "x"
        val point = values.getValue("point")
        checkExpression(expression)
        val result = CalculusEngine.limit(expression, variable, point, fromRight = true, fromLeft = true)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = value.value,
                        steps = value.steps,
                        expression = value.expression,
                        approximate = value.method.startsWith("Numerical"),
                        method = value.method,
                    ),
                )
            }
        }
    }

    private fun summation(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "n"
        checkExpression(expression)
        val result = CalculusEngine.summation(
            expression,
            variable,
            values.getValue("from"),
            values.getValue("to"),
        )
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = value.verdict,
                        steps = value.steps,
                        expression = value.expression,
                    ),
                )
            }
        }
    }

    private fun taylor(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "x"
        checkExpression(expression)
        val result = CalculusEngine.taylor(
            expression,
            variable,
            values["centre"] ?: "0",
            values["order"] ?: "5",
        )
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = value.verdict,
                        steps = value.steps,
                        expression = value.expression,
                    ),
                )
            }
        }
    }

    private fun series(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "n"
        checkExpression(expression)
        val first = values.getValue("firstTerm").trim().toLongOrNull()
            ?: return err(ErrorKind.InvalidInput, "The first index must be a whole number.")
        val last = values.getValue("lastTerm").trim().toLongOrNull()
            ?: return err(ErrorKind.InvalidInput, "The last index must be a whole number.")
        val result = CalculusEngine.series(expression, variable, first, last)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = value.verdict,
                        steps = value.steps,
                        expression = value.expression,
                    ),
                )
            }
        }
    }

    private fun roots(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        checkExpression(expression)
        val result = EquationEngine.solveRoots(
            expression,
            values.getValue("from"),
            values.getValue("to"),
        )
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = if (value.roots.isEmpty()) {
                            "No roots were found in that interval."
                        } else {
                            value.roots.joinToString(", ") { root -> formatNumber(root) }
                        },
                        steps = value.steps,
                        expression = value.expression,
                        approximate = true,
                        method = "Found by sampling the interval",
                    ),
                )
            }
        }
    }

    private fun extrema(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        checkExpression(expression)
        val result = EquationEngine.extrema(
            expression,
            values.getValue("from"),
            values.getValue("to"),
        )
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = if (value.points.isEmpty()) {
                            "No stationary points were found in that interval."
                        } else {
                            value.points.joinToString("\n") { (x, y, kind) ->
                                "x = ${formatNumber(x)}, y = ${formatNumber(y)} ($kind)"
                            }
                        },
                        steps = value.steps,
                        expression = value.expression,
                        approximate = true,
                        method = "Found by sampling the interval",
                    ),
                )
            }
        }
    }

    private fun ode(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val equation = values.getValue("equation")
        if (equation.length > MAX_EXPRESSION_LENGTH) {
            return err(ErrorKind.InvalidInput, "The equation is too long.")
        }
        val request = OdeRequest(
            equation = equation,
            mode = SolveMode.NUMERICAL,
            conditions = emptyList(),
            numerical = NumericalSettings(
                from = values["initialPoint"]?.toDoubleOrNull() ?: 0.0,
                to = 5.0,
                step = 0.1,
                method = NumericalMethod.RK4,
            ),
        )
        val result = OdeSolver.solve(request)
        return when (result) {
            is OdeResult.Invalid -> err(ErrorKind.InvalidInput, result.message)
            is OdeResult.Unsupported -> err(
                ErrorKind.Unavailable,
                result.failure.message ?: "That equation cannot be solved here.",
            )
            is OdeResult.Solved -> {
                val value = result.solution
                val last = value.numerical?.points?.lastOrNull()
                val headline = if (last != null) {
                    "The solution ends at y = ${formatNumber(last.y)}"
                } else {
                    value.particularSolution ?: value.generalSolution ?: "The equation was solved."
                }
                ok(
                    AiToolOutcome(
                        headline = headline,
                        steps = value.steps,
                        expression = value.equation,
                        approximate = true,
                        method = "Solved numerically",
                    ),
                )
            }
        }
    }

    private fun partial(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "x"
        checkExpression(expression)
        // The multivariable parser reads the variables from the text, so the
        // second variable has to be known before the expression is parsed.
        val second = values["secondVariable"]
        val variables = if (second != null) listOf(variable, second) else listOf(variable)
        val parsed: CalcResult<Node> = MultivariableEngine.parse(expression, variables)
        return when (parsed) {
            is CalcResult.Err -> parsed
            is CalcResult.Ok -> {
                val node = parsed.value
                val extras = MultiExtras(
                    order = values["order"]?.toIntOrNull() ?: 1,
                    secondVariable = second,
                )
                val result = MultivariableOperations.run(
                    MultiOperation.PARTIAL,
                    node,
                    variables,
                    DoubleArray(variables.size),
                    extras,
                )
                when (result) {
                    is CalcResult.Err -> result
                    is CalcResult.Ok -> {
                        val value = result.value
                        ok(
                            AiToolOutcome(
                                headline = value.values.joinToString("\n"),
                                steps = value.explanation.steps,
                                expression = MultivariableEngine.render(node),
                            ),
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------- algebra

    private fun linear(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val equation = values.getValue("equation")
        val variable = values["variable"] ?: "x"
        val result = com.calcsuite.engine.algebra.EquationEngine.solveLinearEquation(equation, variable)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = value.root?.let { root -> "x = ${formatNumber(root.toDouble())}" }
                            ?: value.verification,
                        steps = value.steps,
                        expression = value.standardForm,
                    ),
                )
            }
        }
    }

    private fun quadratic(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val equation = values.getValue("equation")
        val variable = values["variable"] ?: "x"
        val result = com.calcsuite.engine.algebra.EquationEngine.solveQuadraticEquation(equation, variable)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = buildString {
                            value.realRoots.forEach { root ->
                                append("x = ${formatNumber(root.toDouble())}\n")
                            }
                            value.complexRoots.forEach { root ->
                                append("x = ${root.text}\n")
                            }
                            if (value.realRoots.isEmpty() && value.complexRoots.isEmpty()) {
                                append("There are no roots.")
                            }
                        }.trim(),
                        steps = value.steps,
                        expression = value.standardForm,
                    ),
                )
            }
        }
    }

    private fun system(values: Map<String, String>): CalcResult<AiToolOutcome> {
        // The engine takes the six coefficients of the two equations, which
        // is what the app's own equations screen takes as well.
        val numbers = mutableMapOf<String, BigDecimal>()
        for (name in listOf("a1", "b1", "c1", "a2", "b2", "c2")) {
            val text = values.getValue(name)
            val value = text.trim().toBigDecimalOrNull()
                ?: return err(ErrorKind.InvalidInput, "The $name must be a number.")
            numbers[name] = value
        }
        val a1 = numbers.getValue("a1")
        val b1 = numbers.getValue("b1")
        val c1 = numbers.getValue("c1")
        val a2 = numbers.getValue("a2")
        val b2 = numbers.getValue("b2")
        val c2 = numbers.getValue("c2")
        val result = com.calcsuite.engine.algebra.EquationEngine.solveSystem2(a1, b1, c1, a2, b2, c2)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                val x = value.x
                val y = value.y
                // The answer is put back into the two equations, so that a
                // wrong set of coefficients is caught here rather than shown
                // as a result.
                if (x != null && y != null) {
                    val first = a1.multiply(x).add(b1.multiply(y))
                    val second = a2.multiply(x).add(b2.multiply(y))
                    if (first.compareTo(c1) != 0 || second.compareTo(c2) != 0) {
                        return err(
                            ErrorKind.InvalidInput,
                            "The answer does not satisfy the two equations. " +
                                "Check the coefficients.",
                        )
                    }
                }
                ok(
                    AiToolOutcome(
                        headline = buildString {
                            x?.let { append("x = ${formatNumber(it.toDouble())}\n") }
                            y?.let { append("y = ${formatNumber(it.toDouble())}\n") }
                            if (x == null && y == null) append(value.message)
                        }.trim(),
                        steps = value.steps,
                        expression = value.message,
                        method = "Solved by Cramer's rule",
                    ),
                )
            }
        }
    }

    private fun inequality(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val text = values.getValue("inequality")
        val result = InequalitySolver.solve(text, InequalityMode.SINGLE)
        return when (result) {
            is InequalityResult.Failed -> err(ErrorKind.InvalidInput, result.message)
            is InequalityResult.Solved -> {
                val value = result
                ok(
                    AiToolOutcome(
                        headline = describeSolution(value.solution),
                        steps = value.steps,
                        expression = text,
                    ),
                )
            }
            is InequalityResult.Plane -> err(
                ErrorKind.InvalidInput,
                "That is a system of inequalities; use the plane picture for it.",
            )
        }
    }

    private fun gcf(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val pair = parseIntegers(values.getValue("first"), values.getValue("second"))
        if (pair is CalcResult.Err) return pair
        val (first, second) = (pair as CalcResult.Ok).value
        val result = GcfLcmEngine.gcf(first, second)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> ok(
                AiToolOutcome(
                    headline = formatNumber(result.value.toDouble()),
                    expression = "$first and $second",
                ),
            )
        }
    }

    private fun lcm(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val pair = parseIntegers(values.getValue("first"), values.getValue("second"))
        if (pair is CalcResult.Err) return pair
        val (first, second) = (pair as CalcResult.Ok).value
        val result = GcfLcmEngine.lcm(first, second)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> ok(
                AiToolOutcome(
                    headline = formatNumber(result.value.toDouble()),
                    expression = "$first and $second",
                ),
            )
        }
    }

    private fun prime(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val number = values.getValue("number").trim().toBigIntegerOrNull()
            ?: return err(ErrorKind.InvalidInput, "The number must be a whole number.")
        val result = PrimeEngine.factorize(number)
        return when (result) {
            is CalcResult.Err -> result
            is CalcResult.Ok -> {
                val value = result.value
                ok(
                    AiToolOutcome(
                        headline = if (value.size == 1 && value[0] == number) {
                            "$number is prime."
                        } else {
                            value.joinToString(" × ") { factor -> formatNumber(factor.toDouble()) }
                        },
                        expression = number.toString(),
                    ),
                )
            }
        }
    }

    private fun combinatorics(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val n = values.getValue("n").trim().toBigIntegerOrNull()
            ?: return err(ErrorKind.InvalidInput, "n must be a whole number.")
        val r = values.getValue("r").trim().toBigIntegerOrNull()
            ?: return err(ErrorKind.InvalidInput, "r must be a whole number.")
        val combinations = CombinatoricsEngine.nCr(n, r)
        if (combinations is CalcResult.Err) return combinations
        val permutations = CombinatoricsEngine.nPr(n, r)
        if (permutations is CalcResult.Err) return permutations
        return ok(
            AiToolOutcome(
                headline = buildString {
                    append("${formatNumber((combinations as CalcResult.Ok).value.toDouble())} ways to choose\n")
                    append("${formatNumber((permutations as CalcResult.Ok).value.toDouble())} ways to order them")
                },
                expression = "$n and $r",
            ),
        )
    }

    // ------------------------------------------------------------- advanced

    private fun linearAlgebra(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val operation = values.getValue("operation").lowercase().replace(' ', '_')
        val first = parseVector(values.getValue("firstVector"))
        if (first is CalcResult.Err) return first
        val second = values["secondVector"]?.let { parseVector(it) }
        if (second != null && second is CalcResult.Err) return second
        val u = (first as CalcResult.Ok).value
        val v = (second as? CalcResult.Ok)?.value

        return when (operation) {
            "norm" -> ok(AiToolOutcome(formatNumber(LinearAlgebra.norm(u))))
            "distance" -> {
                val other = v ?: return err(ErrorKind.InvalidInput, "The distance needs a second vector.")
                ok(AiToolOutcome(formatNumber(LinearAlgebra.distance(u, other))))
            }
            "angle" -> {
                val other = v ?: return err(ErrorKind.InvalidInput, "The angle needs a second vector.")
                val angle = LinearAlgebra.angle(u, other)
                    ?: return err(ErrorKind.InvalidInput, "Those vectors have no angle between them.")
                ok(
                    AiToolOutcome(
                        "${formatNumber(angle)}\u00B0",
                        approximate = true,
                        method = "The angle between the two vectors",
                    ),
                )
            }
            "inner_product" -> {
                val other = v ?: return err(ErrorKind.InvalidInput, "The inner product needs a second vector.")
                ok(AiToolOutcome(formatNumber(LinearAlgebra.innerProduct(u, other))))
            }
            "projection" -> {
                val other = v ?: return err(ErrorKind.InvalidInput, "The projection needs a second vector.")
                val projected = LinearAlgebra.projectOnto(u, other)
                    ?: return err(ErrorKind.InvalidInput, "That vector cannot be projected on.")
                ok(AiToolOutcome(projected.joinToString(", ") { formatNumber(it) }))
            }
            "gram_schmidt" -> {
                val basis = v?.let { listOf(u, it) } ?: listOf(u)
                val orthonormal = LinearAlgebra.gramSchmidt(basis)
                ok(
                    AiToolOutcome(
                        orthonormal.joinToString("\n") { vector ->
                            vector.joinToString(", ") { formatNumber(it) }
                        },
                    ),
                )
            }
            "least_squares" -> {
                val points = u
                if (points.size < 2 || points.size % 2 != 0) {
                    return err(
                        ErrorKind.InvalidInput,
                        "The least-squares fit needs the points as x,y pairs.",
                    )
                }
                val count = points.size / 2
                val matrix = Matrix(count, 2)
                val rhs = DoubleArray(count)
                for (i in 0 until count) {
                    matrix[i, 0] = points[i * 2]
                    matrix[i, 1] = 1.0
                    rhs[i] = points[i * 2 + 1]
                }
                val fit = LinearAlgebra.leastSquares(matrix, rhs)
                    ?: return err(ErrorKind.InvalidInput, "The fit could not be made.")
                // The coefficients are the slope and the intercept, in that
                // order, because the second column of the matrix is the ones.
                ok(
                    AiToolOutcome(
                        "y = ${formatNumber(fit.coefficients[0])}x + " +
                            formatNumber(fit.coefficients[1]),
                    ),
                )
            }
            else -> err(
                ErrorKind.InvalidInput,
                "The vector operation must be norm, distance, angle, inner_product, " +
                    "projection, gram_schmidt or least_squares.",
            )
        }
    }

    private fun measure(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val first = values.getValue("firstSet")
        val second = values["secondSet"]
        val parsedFirst = parseSet(first)
        if (parsedFirst is CalcResult.Err) return parsedFirst
        val set = (parsedFirst as CalcResult.Ok).value
        if (second == null) {
            return ok(
                AiToolOutcome(
                    formatNumber(MeasureEngine.measure(set)),
                    expression = first,
                ),
            )
        }
        val parsedSecond = parseSet(second)
        if (parsedSecond is CalcResult.Err) return parsedSecond
        val other = (parsedSecond as CalcResult.Ok).value
        val union = MeasureEngine.union(set, other)
        val overlap = MeasureEngine.intersect(set, other)
        return ok(
            AiToolOutcome(
                buildString {
                    append("The union measures ${formatNumber(union.unionMeasure)}\n")
                    append("The overlap measures ${formatNumber(MeasureEngine.measure(overlap))}")
                },
                expression = "$first and $second",
            ),
        )
    }

    private fun complex(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val point = values.getValue("point")
        checkExpression(expression)
        val parsed = CalculusParser.parse(expression)
        if (parsed is CalcResult.Err) return parsed
        val node = (parsed as CalcResult.Ok).value
        val parts = point.split(",").map { it.trim() }
        if (parts.size != 2) {
            return err(
                ErrorKind.InvalidInput,
                "The point is its real and imaginary parts, separated by a comma.",
            )
        }
        val real = parts[0].toDoubleOrNull()
            ?: return err(ErrorKind.InvalidInput, "The real part must be a number.")
        val imaginary = parts[1].toDoubleOrNull()
            ?: return err(ErrorKind.InvalidInput, "The imaginary part must be a number.")
        val value = ComplexAnalysis.evaluate(node, "z", ComplexNum(real, imaginary))
            ?: return err(ErrorKind.InvalidInput, "The function could not be evaluated there.")
        return ok(
            AiToolOutcome(
                headline = describeComplex(value),
                expression = ComplexAnalysis.render(node),
            ),
        )
    }

    private fun fourier(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val expression = values.getValue("expression")
        val variable = values["variable"] ?: "x"
        checkExpression(expression)
        val parsed = CalculusParser.parse(expression)
        if (parsed is CalcResult.Err) return parsed
        val node = (parsed as CalcResult.Ok).value
        val from = values["from"]?.toDoubleOrNull() ?: 0.0
        val to = values["to"]?.toDoubleOrNull() ?: 1.0
        // The engine takes the period of the function, which is the length of
        // the interval it is wanted over.
        val period = to - from
        if (period <= 0.0) {
            return err(ErrorKind.InvalidInput, "The interval must be longer than nothing.")
        }
        val series = FourierEngine.series(node, variable, period, harmonics = 8, from = from)
            ?: return err(ErrorKind.InvalidInput, "The series could not be built.")
        return ok(
            AiToolOutcome(
                series.render(variable),
                expression = NodePrinter.print(node),
                approximate = true,
                method = "The Fourier coefficients are worked out numerically",
            ),
        )
    }

    private fun pde(values: Map<String, String>): CalcResult<AiToolOutcome> {
        val kind = values.getValue("equation").lowercase()
        val kindValue = when {
            kind.startsWith("heat") -> PdeKind.HEAT
            kind.startsWith("wave") -> PdeKind.WAVE
            kind.startsWith("laplace") -> PdeKind.LAPLACE
            kind.startsWith("poisson") -> PdeKind.POISSON
            else -> return err(
                ErrorKind.InvalidInput,
                "The equation must be heat, wave, laplace or poisson.",
            )
        }
        val length = values["length"]?.toDoubleOrNull() ?: 1.0
        val initialText = values["initial"] ?: "sin(pi*x)"
        return when (kindValue) {
            PdeKind.HEAT, PdeKind.WAVE -> {
                val initial = CalculusParser.parse(initialText)
                if (initial is CalcResult.Err) return initial
                val answer = PdeEngine.solve1D(
                    kind = kindValue,
                    initial = (initial as CalcResult.Ok).value,
                    variable = "x",
                    length = length,
                    spaceSteps = 40,
                    timeSteps = 200,
                    timeEnd = 0.5,
                    left = 0.0,
                    right = 0.0,
                    alpha = 0.1,
                    speed = 1.0,
                ) ?: return err(ErrorKind.InvalidInput, "The numbers were not acceptable.")
                ok(
                    AiToolOutcome(
                        headline = answer.steps.lastOrNull() ?: "The field was computed.",
                        steps = answer.steps,
                        expression = initialText,
                        approximate = true,
                        method = "The field is worked out numerically",
                    ),
                )
            }
            PdeKind.LAPLACE, PdeKind.POISSON -> {
                val boundary = CalculusParser.parse(values["boundary"] ?: "sin(pi*x)")
                if (boundary is CalcResult.Err) return boundary
                val source = if (kindValue == PdeKind.POISSON) {
                    when (val parsed = CalculusParser.parse("1")) {
                        is CalcResult.Err -> null
                        is CalcResult.Ok -> parsed.value
                    }
                } else {
                    null
                }
                val answer = PdeEngine.solve2D(
                    kind = kindValue,
                    boundary = (boundary as CalcResult.Ok).value,
                    source = source,
                    xVar = "x",
                    yVar = "y",
                    size = length,
                    gridPoints = 30,
                    iterations = 500,
                ) ?: return err(ErrorKind.InvalidInput, "The numbers were not acceptable.")
                ok(
                    AiToolOutcome(
                        headline = answer.steps.lastOrNull() ?: "The field was computed.",
                        steps = answer.steps,
                        expression = "u_xx + u_yy = f(x, y)",
                        approximate = true,
                        method = "The field is worked out numerically",
                    ),
                )
            }
        }
    }

    /**
     * A complex number, written the way the app writes one.
     */
    private fun describeComplex(value: ComplexNum): String {
        if (value.isReal) {
            return formatNumber(value.re)
        }
        val sign = if (value.im < 0) "\u2212" else "+"
        return "${formatNumber(value.re)} $sign ${formatNumber(kotlin.math.abs(value.im))}i"
    }

    /**
     * Reads a set written as its bounds separated by a comma.
     *
     * The engine works with the intervals themselves, so the text is turned
     * into one here.
     */
    private fun parseSet(text: String): CalcResult<RealSet> {
        val parts = text.split(',').map { it.trim() }
        if (parts.size != 2) {
            return err(
                ErrorKind.InvalidInput,
                "A set is written as its two bounds separated by a comma.",
            )
        }
        val from = parts[0].toDoubleOrNull()
            ?: return err(ErrorKind.InvalidInput, "\"${parts[0]}\" is not a number.")
        val to = parts[1].toDoubleOrNull()
            ?: return err(ErrorKind.InvalidInput, "\"${parts[1]}\" is not a number.")
        if (to < from) {
            return err(ErrorKind.InvalidInput, "The upper bound must not be below the lower one.")
        }
        return ok(RealSet(listOf(Interval(from, to))))
    }

    /**
     * The solution of an inequality, read for the user.
     *
     * Each interval is shown with its ends, so that an open end is visible
     * as an open end.
     */
    private fun describeSolution(solution: SolutionSet): String {
        if (solution.intervals.isEmpty()) {
            return "There is no solution."
        }
        return solution.intervals.joinToString("\n") { interval ->
            val low = interval.lo?.display() ?: "\u2212\u221E"
            val high = interval.hi?.display() ?: "+\u221E"
            val left = if (interval.lo?.closed == false) "(" else "["
            val right = if (interval.hi?.closed == false) ")" else "]"
            "$left$low, $high$right"
        }
    }

    // ------------------------------------------------------------- helpers

    /** Reads a vector written as its numbers separated by commas. */
    private fun parseVector(text: String): CalcResult<DoubleArray> {
        val parts = text.split(',').map { it.trim() }
        if (parts.size < 2) {
            return err(ErrorKind.InvalidInput, "A vector needs at least two numbers.")
        }
        val numbers = DoubleArray(parts.size)
        for (i in parts.indices) {
            numbers[i] = parts[i].toDoubleOrNull()
                ?: return err(ErrorKind.InvalidInput, "\"${parts[i]}\" is not a number.")
        }
        return ok(numbers)
    }

    /** Reads two whole numbers. */
    private fun parseIntegers(
        first: String,
        second: String,
    ): CalcResult<Pair<BigInteger, BigInteger>> {
        val a = first.trim().toBigIntegerOrNull()
            ?: return err(ErrorKind.InvalidInput, "The first number must be a whole number.")
        val b = second.trim().toBigIntegerOrNull()
            ?: return err(ErrorKind.InvalidInput, "The second number must be a whole number.")
        return ok(a to b)
    }

    /** Refuses an expression that is too long to be sensible. */
    private fun checkExpression(expression: String) {
        require(expression.length <= MAX_EXPRESSION_LENGTH) {
            "The expression is too long."
        }
    }
}
