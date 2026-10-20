package com.calcsuite.engine.calculus.multivariable

import com.calcsuite.core.CalcResult
import com.calcsuite.core.ErrorKind
import com.calcsuite.core.err
import com.calcsuite.engine.advanced.Explanation
import com.calcsuite.engine.advanced.formatReal
import com.calcsuite.engine.calculus.CalculusParser
import com.calcsuite.engine.calculus.DifferentiationEngine
import com.calcsuite.engine.calculus.Evaluator
import com.calcsuite.engine.calculus.IntegrationEngine
import com.calcsuite.engine.calculus.Node
import com.calcsuite.engine.calculus.NodePrinter
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The operation a multivariable calculation performs.
 */
enum class MultiOperation(val label: String) {
    PARTIAL("Partial derivative"),
    GRADIENT("Gradient"),
    DIRECTIONAL("Directional derivative"),
    JACOBIAN("Jacobian"),
    HESSIAN("Hessian"),
    DIVERGENCE("Divergence"),
    CURL("Curl"),
    LAPLACIAN("Laplacian"),
    CRITICAL("Critical points"),
    DOUBLE_INTEGRAL("Double integral"),
    TRIPLE_INTEGRAL("Triple integral"),
    LINE_INTEGRAL("Line integral"),
}

/**
 * The result of a multivariable calculation.
 *
 * The explanation is built from the working that was actually done, so the
 * steps, the numbers and the answer always agree.
 */
data class MultiResult(
    val operation: MultiOperation,
    /** The main answer, one entry per part. */
    val values: List<String>,
    /** The value of the whole expression at the evaluation point, when asked for. */
    val valueAtPoint: Double?,
    /** The explanation. */
    val explanation: Explanation,
    /** The classification of a critical point, when the operation found one. */
    val classifications: List<PointClassification> = emptyList(),
    /** The points that were found, for the graph. */
    val points: List<DoubleArray> = emptyList(),
    /** The gradient vectors, for the graph. */
    val vectors: List<DoubleArray> = emptyList(),
)

/** How a critical point was classified. */
enum class PointClass(val label: String) {
    MINIMUM("Local minimum"),
    MAXIMUM("Local maximum"),
    SADDLE("Saddle point"),
    INCONCLUSIVE("Inconclusive"),
    DEGENERATE("Degenerate critical point"),
}

/** One critical point and its classification. */
data class PointClassification(
    /** The point, one entry per variable. */
    val point: DoubleArray,
    /** The value of the function there. */
    val value: Double,
    /** The class of the point. */
    val classification: PointClass,
    /** The Hessian at the point, row by row. */
    val hessian: List<DoubleArray>,
    /** Why the point was classified that way. */
    val reason: String,
)

/**
 * Multivariable calculus on the existing expression engine.
 *
 * The engine works on the same trees the ordinary calculus uses, so a
 * function of two or three variables is read, differentiated, integrated and
 * evaluated exactly as a function of one variable is. The partial derivatives
 * come from the engine's own differentiation, which holds the other variables
 * still, and the integrals reuse the adaptive Simpson routine.
 */
object MultivariableEngine {

    // ------------------------------------------------------------ derivatives

    /**
     * The partial derivative of a function with respect to one variable.
     */
    fun partialDerivative(
        function: Node,
        variable: String,
        order: Int = 1,
    ): CalcResult<Node> {
        var current = function
        repeat(order) {
            val next = DifferentiationEngine.differentiate(current, variable)
            if (next is CalcResult.Err) return next
            current = (next as CalcResult.Ok).value
        }
        return CalcResult.Ok(current)
    }

    /**
     * The partial derivative with respect to two variables, one after the
     * other — a mixed partial derivative.
     */
    fun mixedPartial(
        function: Node,
        first: String,
        second: String,
    ): CalcResult<Node> {
        val step = DifferentiationEngine.differentiate(function, first)
        if (step is CalcResult.Err) return step
        val second2 = DifferentiationEngine.differentiate((step as CalcResult.Ok).value, second)
        if (second2 is CalcResult.Err) return second2
        return CalcResult.Ok((second2 as CalcResult.Ok).value)
    }

    /**
     * The gradient: the vector of the first partial derivatives.
     */
    fun gradient(function: Node, variables: List<String>): CalcResult<List<Node>> {
        val out = mutableListOf<Node>()
        for (v in variables) {
            val derivative = DifferentiationEngine.differentiate(function, v)
            if (derivative is CalcResult.Err) return derivative
            out.add((derivative as CalcResult.Ok).value)
        }
        return CalcResult.Ok(out)
    }

    /**
     * The Hessian: the matrix of the second partial derivatives.
     */
    fun hessian(function: Node, variables: List<String>): CalcResult<List<List<Node>>> {
        val out = mutableListOf<List<Node>>()
        for (row in variables) {
            val line = mutableListOf<Node>()
            for (col in variables) {
                val derivative = mixedPartial(function, row, col)
                if (derivative is CalcResult.Err) return derivative
                line.add((derivative as CalcResult.Ok).value)
            }
            out.add(line)
        }
        return CalcResult.Ok(out)
    }

    /**
     * The Jacobian of a vector-valued function.
     *
     * The matrix has one row per component of the function and one column
     * per variable.
     */
    fun jacobian(
        components: List<Node>,
        variables: List<String>,
    ): CalcResult<List<List<Node>>> {
        val out = mutableListOf<List<Node>>()
        for (component in components) {
            val line = mutableListOf<Node>()
            for (v in variables) {
                val derivative = DifferentiationEngine.differentiate(component, v)
                if (derivative is CalcResult.Err) return derivative
                line.add((derivative as CalcResult.Ok).value)
            }
            out.add(line)
        }
        return CalcResult.Ok(out)
    }

    /**
     * The divergence of a vector field: the sum of the diagonal partial
     * derivatives.
     */
    fun divergence(field: List<Node>, variables: List<String>): CalcResult<Node> {
        if (field.size != variables.size) {
            return err(ErrorKind.InvalidInput, "A vector field in ${variables.size} dimensions needs " +
                    "${variables.size} components, but ${field.size} were given.",
            )
        }
        var sum: Node = Node.Num(0.0)
        for (i in field.indices) {
            val derivative = DifferentiationEngine.differentiate(field[i], variables[i])
            if (derivative is CalcResult.Err) return derivative
            sum = Node.Add(sum, (derivative as CalcResult.Ok).value)
        }
        return CalcResult.Ok(sum)
    }

    /**
     * The curl of a vector field in three dimensions.
     */
    fun curl(field: List<Node>, variables: List<String>): CalcResult<List<Node>> {
        if (field.size != 3 || variables.size != 3) {
            return err(ErrorKind.InvalidInput, "The curl is defined for a vector field in three dimensions.",
            )
        }
        val (p, q, r) = Triple(field[0], field[1], field[2])
        val (x, y, z) = Triple(variables[0], variables[1], variables[2])
        // ∇×F = (∂F₃/∂y − ∂F₂/∂z,  ∂F₁/∂z − ∂F₃/∂x,  ∂F₂/∂x − ∂F₁/∂y)
        val dqz = DifferentiationEngine.differentiate(q, z)
        val dry = DifferentiationEngine.differentiate(r, y)
        val dpz = DifferentiationEngine.differentiate(p, z)
        val drx = DifferentiationEngine.differentiate(r, x)
        val dqx = DifferentiationEngine.differentiate(q, x)
        val dpy = DifferentiationEngine.differentiate(p, y)
        val results = listOf(dqz, dry, dpz, drx, dqx, dpy)
        if (results.any { it is CalcResult.Err }) {
            return err(ErrorKind.InvalidInput, "One of the derivatives could not be formed.",
            )
        }
        val dqzValue = (results[0] as CalcResult.Ok).value
        val dryValue = (results[1] as CalcResult.Ok).value
        val dpzValue = (results[2] as CalcResult.Ok).value
        val drxValue = (results[3] as CalcResult.Ok).value
        val dqxValue = (results[4] as CalcResult.Ok).value
        val dpyValue = (results[5] as CalcResult.Ok).value
        return CalcResult.Ok(
            listOf(
                Node.Sub(dryValue, dqzValue),
                Node.Sub(dpzValue, drxValue),
                Node.Sub(dqxValue, dpyValue),
            ),
        )
    }

    /**
     * The Laplacian: the sum of the second partial derivatives.
     */
    fun laplacian(function: Node, variables: List<String>): CalcResult<Node> {
        var sum: Node = Node.Num(0.0)
        for (v in variables) {
            val second = partialDerivative(function, v, 2)
            if (second is CalcResult.Err) return second
            sum = Node.Add(sum, (second as CalcResult.Ok).value)
        }
        return CalcResult.Ok(sum)
    }

    // -------------------------------------------------------------- numerics

    /** The value of a tree at a point. */
    fun evaluate(node: Node, variables: List<String>, point: DoubleArray): Double? {
        if (variables.size != point.size) return null
        val environment = mutableMapOf<String, Double>()
        for (i in variables.indices) environment[variables[i].lowercase()] = point[i]
        return Evaluator.eval(node, environment).valueOrNull()
    }

    /**
     * The directional derivative at a point.
     *
     * The direction is normalised first when the caller asks for it, which is
     * the convention that makes the result the rate of change per unit
     * distance.
     */
    fun directionalDerivative(
        gradient: List<Node>,
        variables: List<String>,
        point: DoubleArray,
        direction: DoubleArray,
        normalise: Boolean,
    ): CalcResult<Double> {
        if (direction.size != variables.size) {
            return err(ErrorKind.InvalidInput, "The direction must have one entry for each variable.",
            )
        }
        var v = direction.copyOf()
        if (normalise) {
            val length = sqrt(v.sumOf { it * it })
            if (length < 1e-12) {
                return err(ErrorKind.InvalidInput, "The direction vector has no direction, because it is the zero vector.",
                )
            }
            for (i in v.indices) v[i] /= length
        }
        var total = 0.0
        val terms = mutableListOf<String>()
        for (i in gradient.indices) {
            val value = evaluate(gradient[i], variables, point) ?: return err(ErrorKind.Domain, "The gradient could not be evaluated at that point.",
            )
            total += value * v[i]
            terms.add("${formatReal(value)}·${formatReal(v[i])}")
        }
        return CalcResult.Ok(total)
    }

    /**
     * The critical points of a function of two variables, and how each one is
     * classified.
     *
     * The points are found by scanning a grid for where the gradient changes
     * sign, and then refined with Newton's method on the gradient system.
     * The classification comes from the Hessian; when the Hessian test is
     * inconclusive the point is reported as inconclusive rather than being
     * given a class it does not have.
     */
    fun criticalPoints(
        function: Node,
        variables: List<String>,
        box: List<DoubleArray>,
        samples: Int = 41,
    ): CalcResult<List<PointClassification>> {
        if (variables.size != 2) {
            return err(ErrorKind.InvalidInput, "The critical point search works for a function of two variables.",
            )
        }
        val gradient = gradient(function, variables)
        if (gradient is CalcResult.Err) return gradient
        val components = (gradient as CalcResult.Ok).value
        val hessianNodes = hessian(function, variables)
        if (hessianNodes is CalcResult.Err) return hessianNodes
        val hessianMatrix = (hessianNodes as CalcResult.Ok).value

        val xRange = box[0]
        val yRange = box[1]
        val stepX = (xRange[1] - xRange[0]) / (samples - 1)
        val stepY = (yRange[1] - yRange[0]) / (samples - 1)

        val candidates = mutableListOf<DoubleArray>()
        for (i in 0 until samples) {
            for (j in 0 until samples) {
                val x = xRange[0] + stepX * i
                val y = yRange[0] + stepY * j
                val gx = evaluate(components[0], variables, doubleArrayOf(x, y))
                val gy = evaluate(components[1], variables, doubleArrayOf(x, y))
                if (gx == null || gy == null) continue
                if (abs(gx) < 1e-9 && abs(gy) < 1e-9) {
                    candidates.add(doubleArrayOf(x, y))
                    continue
                }
                // Look for a sign change in both components between this
                // point and one of its eight neighbours. The neighbours along
                // the axes matter as much as the diagonal ones, because a
                // critical point often has the gradient changing sign in only
                // one direction.
                for (di in -1..1) {
                    for (dj in -1..1) {
                        if (di == 0 && dj == 0) continue
                        val nx = x + di * stepX
                        val ny = y + dj * stepY
                        if (nx < xRange[0] || nx > xRange[1]) continue
                        if (ny < yRange[0] || ny > yRange[1]) continue
                        val ngx = evaluate(components[0], variables, doubleArrayOf(nx, ny))
                        val ngy = evaluate(components[1], variables, doubleArrayOf(nx, ny))
                        if (ngx == null || ngy == null) continue
                        if (gx * ngx <= 0 && gy * ngy <= 0) {
                            candidates.add(doubleArrayOf(x, y))
                        }
                    }
                }
            }
        }

        val refined = mutableListOf<DoubleArray>()
        for (candidate in candidates) {
            val point = newtonRefine(components, hessianMatrix, variables, candidate)
            val duplicate = refined.any { existing ->
                abs(existing[0] - point[0]) < 1e-4 && abs(existing[1] - point[1]) < 1e-4
            }
            if (!duplicate) refined.add(point)
        }

        val out = refined.map { point ->
            val value = evaluate(function, variables, point) ?: 0.0
            val hessianAt = hessianMatrix.map { row ->
                DoubleArray(row.size) { c -> evaluate(row[c], variables, point) ?: 0.0 }
            }
            val classification = classify(hessianAt)
            PointClassification(
                point = point,
                value = value,
                classification = classification.first,
                hessian = hessianAt,
                reason = classification.second,
            )
        }
        return CalcResult.Ok(out)
    }

    /** Newton's method on the gradient system. */
    private fun newtonRefine(
        gradient: List<Node>,
        hessian: List<List<Node>>,
        variables: List<String>,
        start: DoubleArray,
    ): DoubleArray {
        var point = start.copyOf()
        repeat(24) {
            val g = DoubleArray(gradient.size) { i ->
                evaluate(gradient[i], variables, point) ?: 0.0
            }
            val h = Array(2) { r -> DoubleArray(2) { c -> evaluate(hessian[r][c], variables, point) ?: 0.0 } }
            val determinant = h[0][0] * h[1][1] - h[0][1] * h[1][0]
            if (abs(determinant) < 1e-14) return@repeat
            val dx = (-g[0] * h[1][1] + g[1] * h[0][1]) / determinant
            val dy = (-h[0][0] * g[1] + h[0][1] * g[0]) / determinant
            point = doubleArrayOf(point[0] + dx, point[1] + dy)
            if (abs(dx) < 1e-12 && abs(dy) < 1e-12) return@repeat
        }
        return point
    }

    /**
     * The classification of a critical point from its Hessian.
     *
     * The second-derivative test: a positive definite Hessian gives a local
     * minimum, a negative definite one a local maximum, and an indefinite one
     * a saddle point. When the determinant is zero the test says nothing,
     * and that is what is reported.
     */
    fun classify(hessian: List<DoubleArray>): Pair<PointClass, String> {
        val a = hessian[0][0]
        val b = hessian[0][1]
        val c = hessian[1][1]
        val determinant = a * c - b * b
        val trace = a + c
        if (abs(determinant) < 1e-10) {
            return PointClass.INCONCLUSIVE to
                "The determinant of the Hessian is zero, so the second-derivative test " +
                "says nothing about this point. A higher-order test would be needed."
        }
        if (determinant < 0) {
            return PointClass.SADDLE to
                "The determinant of the Hessian is negative, so the Hessian is indefinite " +
                "and the point is a saddle point."
        }
        if (trace > 0) {
            return PointClass.MINIMUM to
                "The determinant is positive and the trace is positive, so the Hessian is " +
                "positive definite and the point is a local minimum."
        }
        return PointClass.MAXIMUM to
            "The determinant is positive and the trace is negative, so the Hessian is " +
            "negative definite and the point is a local maximum."
    }

    // ------------------------------------------------------------ integration

    /**
     * A double integral over a rectangle, by nested adaptive Simpson.
     *
     * The inner integral is done for each value of the outer variable, and the
     * outer one is then integrated in the same way.
     */
    fun doubleIntegral(
        function: Node,
        variables: List<String>,
        bounds: List<DoubleArray>,
    ): CalcResult<Double> {
        if (variables.size != 2 || bounds.size != 2) {
            return err(ErrorKind.InvalidInput, "A double integral needs two variables and two pairs of bounds.",
            )
        }
        val x = variables[0]
        val y = variables[1]
        val outer = adaptiveSimpson(
            { point -> innerIntegral(function, x, y, bounds[1], point) },
            bounds[0][0],
            bounds[0][1],
        )
        if (outer == null) {
            return err(ErrorKind.Domain, "The integral could not be evaluated on the whole region; the function " +
                    "may be undefined somewhere inside it.",
            )
        }
        return CalcResult.Ok(outer)
    }

    /** The inner integral, for each value of the outer variable. */
    private fun innerIntegral(
        function: Node,
        x: String,
        y: String,
        yBounds: DoubleArray,
        at: Double,
    ): Double? {
        val withX = substitute(function, x, Node.Num(at))
        return adaptiveSimpson(
            { yPoint -> Evaluator.eval(withX, mapOf(y to yPoint)).valueOrNull() },
            yBounds[0], yBounds[1],
        )
    }

    /**
     * An adaptive Simpson rule for a function of one variable.
     *
     * The engine's own routine works on a tree, which is enough for a single
     * integral. A multiple integral has to integrate a value that is itself
     * the result of an integral, so the rule is repeated here with a
     * function.
     */
    fun adaptiveSimpson(
        f: (Double) -> Double?,
        lo: Double,
        hi: Double,
        depth: Int = 18,
    ): Double? {
        if (hi < lo) return adaptiveSimpson(f, hi, lo, depth)
        if (hi == lo) return f(lo) ?: return null
        val whole = simpson(f, lo, hi) ?: return null
        return refine(f, lo, hi, whole, depth)
    }

    private fun refine(
        f: (Double) -> Double?,
        lo: Double,
        hi: Double,
        whole: Double,
        depth: Int,
    ): Double? {
        val middle = (lo + hi) / 2
        val left = simpson(f, lo, middle)
        val right = simpson(f, middle, hi)
        if (left == null || right == null) return null
        val difference = left + right - whole
        if (depth <= 0 || abs(difference) < 1e-12) {
            return left + right + difference / 15
        }
        val refinedLeft = refine(f, lo, middle, left, depth - 1) ?: return null
        val refinedRight = refine(f, middle, hi, right, depth - 1) ?: return null
        return refinedLeft + refinedRight
    }

    private fun simpson(f: (Double) -> Double?, lo: Double, hi: Double): Double? {
        val a = f(lo) ?: return null
        val b = f((lo + hi) / 2) ?: return null
        val c = f(hi) ?: return null
        return (hi - lo) / 6 * (a + 4 * b + c)
    }

    /**
     * A triple integral over a box, by nested adaptive Simpson.
     */
    fun tripleIntegral(
        function: Node,
        variables: List<String>,
        bounds: List<DoubleArray>,
    ): CalcResult<Double> {
        if (variables.size != 3 || bounds.size != 3) {
            return err(ErrorKind.InvalidInput, "A triple integral needs three variables and three pairs of bounds.",
            )
        }
        val (x, y, z) = Triple(variables[0], variables[1], variables[2])
        val outer = adaptiveSimpson(
            { point ->
                val withX = substitute(function, x, Node.Num(point))
                adaptiveSimpson(
                    { yPoint ->
                        val withY = substitute(withX, y, Node.Num(yPoint))
                        adaptiveSimpson(
                            { zPoint ->
                                val withZ = substitute(withY, z, Node.Num(zPoint))
                                Evaluator.eval(withZ, emptyMap()).valueOrNull()
                            },
                            bounds[2][0], bounds[2][1],
                        )
                    },
                    bounds[1][0], bounds[1][1],
                )
            },
            bounds[0][0], bounds[0][1],
        )
        if (outer == null) {
            return err(ErrorKind.Domain, "The integral could not be evaluated on the whole box.",
            )
        }
        return CalcResult.Ok(outer)
    }

    /**
     * A line integral of a scalar function along a curve, `∫ f·ds`.
     *
     * The curve is given parametrically, and the arc length element is
     * `√(x′(t)² + y′(t)²)·dt`.
     */
    fun lineIntegral(
        function: Node,
        variables: List<String>,
        curve: List<Node>,
        from: Double,
        to: Double,
    ): CalcResult<Double> {
        if (variables.size != 2 || curve.size != 2) {
            return err(ErrorKind.InvalidInput, "A line integral needs two variables and a curve given by two functions.",
            )
        }
        val t = "t"
        val dx = DifferentiationEngine.differentiate(curve[0], t)
        val dy = DifferentiationEngine.differentiate(curve[1], t)
        if (dx is CalcResult.Err || dy is CalcResult.Err) {
            return err(ErrorKind.InvalidInput, "The curve could not be differentiated.",
            )
        }
        val speed = Node.Fun(
            "sqrt",
            listOf(
                Node.Add(
                    Node.Pow((dx as CalcResult.Ok).value, Node.Num(2.0)),
                    Node.Pow((dy as CalcResult.Ok).value, Node.Num(2.0)),
                ),
            ),
        )
        val pulledBack = substitute(substitute(function, variables[0], curve[0]), variables[1], curve[1])
        val integrand = Node.Mul(pulledBack, speed)
        val value = adaptiveSimpson(
            { at -> Evaluator.eval(integrand, mapOf(t to at)).valueOrNull() },
            from, to,
        )
        if (value == null) {
            return err(ErrorKind.Domain, "The integral could not be evaluated along the whole curve.",
            )
        }
        return CalcResult.Ok(value)
    }

    /**
     * Replaces a variable by an expression.
     */
    fun substitute(node: Node, variable: String, replacement: Node): Node = when (node) {
        is Node.Var ->
            if (node.name.equals(variable, ignoreCase = true)) replacement else node
        is Node.Num -> node
        is Node.Const -> node
        is Node.Neg -> Node.Neg(substitute(node.a, variable, replacement))
        is Node.Add -> Node.Add(
            substitute(node.a, variable, replacement),
            substitute(node.b, variable, replacement),
        )
        is Node.Sub -> Node.Sub(
            substitute(node.a, variable, replacement),
            substitute(node.b, variable, replacement),
        )
        is Node.Mul -> Node.Mul(
            substitute(node.a, variable, replacement),
            substitute(node.b, variable, replacement),
        )
        is Node.Div -> Node.Div(
            substitute(node.a, variable, replacement),
            substitute(node.b, variable, replacement),
        )
        is Node.Pow -> Node.Pow(
            substitute(node.a, variable, replacement),
            substitute(node.b, variable, replacement),
        )
        is Node.Fun -> Node.Fun(node.name, node.args.map { substitute(it, variable, replacement) })
    }

    /** Parses an expression. */
    fun parse(text: String): CalcResult<Node> = CalculusParser.parse(text)

    /**
     * Parses an expression when the variables are known.
     *
     * The multiplication signs between adjacent variables are put in first, so
     * that `3xy` is read as `3·x·y` rather than as three times a variable
     * called `xy`.
     */
    fun parse(text: String, variables: List<String>): CalcResult<Node> =
        CalculusParser.parse(insertImplicitProducts(text, variables))

    /**
     * Puts in the multiplication signs the expression parser cannot see.
     *
     * The parser reads a run of letters as one name, so `3xy` would be read as
     * three times a variable called `xy` rather than as `3·x·y`. When the
     * variables are known, the text can be prepared: wherever two of them sit
     * next to each other, a multiplication sign goes between them.
     */
    fun insertImplicitProducts(text: String, variables: List<String>): String {
        if (variables.isEmpty()) return text
        val names = variables
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
        if (names.isEmpty()) return text
        val out = StringBuilder()
        var index = 0
        // True when the last thing copied was a whole variable name, in which
        // case the next name starts a new factor rather than continuing a word.
        var previousWasName = false
        while (index < text.length) {
            val name = names.firstOrNull { candidate ->
                text.startsWith(candidate, index) &&
                    (previousWasName || !isInsideAWord(text, index))
            }
            if (name == null) {
                out.append(text[index])
                index++
                previousWasName = false
                continue
            }
            val previous = out.lastOrNull()
            if (previous != null && (previous.isLetterOrDigit() || previous == '_')) {
                out.append('*')
            }
            out.append(name)
            index += name.length
            previousWasName = true
        }
        return out.toString()
    }

    /**
     * True when the position is inside a longer word, so it is not a name.
     *
     * A letter before the position means it is inside a word; a digit does
     * not, because a digit is a number and the letter after it starts a new
     * factor.
     */
    private fun isInsideAWord(text: String, index: Int): Boolean {
        if (index == 0) return false
        val previous = text[index - 1]
        return previous.isLetter() || previous == '_'
    }

    /** The printed form of a tree. */
    fun render(node: Node): String = NodePrinter.print(node)
}
